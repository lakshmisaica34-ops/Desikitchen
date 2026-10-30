package com.desikitchen.app.feature

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.desikitchen.app.data.db.DesiKitchenDatabase
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.model.SourcePlatform
import com.desikitchen.app.data.repo.RecipeRepository
import com.desikitchen.app.util.ImportProgress
import com.desikitchen.app.util.ImportStage
import com.desikitchen.app.util.InstagramImporter
import com.desikitchen.app.util.RecipeDedup
import com.desikitchen.app.util.SearchResults
import com.desikitchen.app.util.SearchState
import com.desikitchen.app.util.WebRecipeFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** Phase 1 gaps: duplicate detection, real import stages + cancel, Search Results states. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase1GapsTest {
    private val ig = WebRecipeFetcher.instagramImport
    @After fun restore() { WebRecipeFetcher.instagramImport = ig }

    private fun fixture(name: String): String {
        val f = listOf(File("src/test/resources/scraper/$name.html.gz"), File("app/src/test/resources/scraper/$name.html.gz")).first { it.exists() }
        return GZIPInputStream(f.inputStream()).bufferedReader().readText()
    }

    // ---- Duplicate detection ----

    @Test fun youtubeLinkShapesAreTheSameRecipe() {
        val a = RecipeDedup.key("https://youtu.be/ETGWelKhgfc?si=xyz")!!
        val b = RecipeDedup.key("https://www.youtube.com/watch?v=ETGWelKhgfc&feature=share")!!
        val c = RecipeDedup.key("https://m.youtube.com/shorts/ETGWelKhgfc")!!
        assertEquals(SourcePlatform.YOUTUBE, a.platform); assertEquals("ETGWelKhgfc", a.videoId)
        assertTrue(RecipeDedup.same(a, b) && RecipeDedup.same(b, c))
        assertFalse(RecipeDedup.same(a, RecipeDedup.key("https://youtu.be/AAAAAAAAAAA")!!))
    }

    @Test fun instagramVariantsAreTheSameRecipe() {
        val a = RecipeDedup.key("https://www.instagram.com/reel/DF6dMW5vwgw/?igsh=abc123")!!
        val b = RecipeDedup.key("https://instagram.com/reels/DF6dMW5vwgw")!!
        val c = RecipeDedup.key("https://www.instagram.com/brekkyattiffanys/p/DF6dMW5vwgw/")!!
        assertEquals("DF6dMW5vwgw", a.videoId)
        assertTrue(RecipeDedup.same(a, b) && RecipeDedup.same(a, c))
        assertEquals("https://instagram.com/reel/DF6dMW5vwgw", RecipeDedup.normaliseUrl("https://www.instagram.com/reel/DF6dMW5vwgw/?igsh=abc#x"))
    }

    @Test fun webUrlsNormaliseTrackingAway() {
        assertEquals(
            "https://example.com/dal-tadka?b=2&id=7",
            RecipeDedup.normaliseUrl("https://WWW.Example.com/dal-tadka/?utm_source=ig&id=7&fbclid=zz&b=2#comments"),
        )
        val a = RecipeDedup.key("https://example.com/dal-tadka/?utm_medium=social")!!
        val b = RecipeDedup.key("http://www.example.com/dal-tadka")!!
        assertTrue(RecipeDedup.same(a, b))
        assertFalse(RecipeDedup.same(a, RecipeDedup.key("https://example.com/paneer-tikka")!!))
        assertNull(RecipeDedup.key("  "))
    }

    @Test fun repositoryFindsDuplicatesByVideoIdAndUrl() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DesiKitchenDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.recipeDao()
            val repo = RecipeRepository(dao)
            val reel = dao.insert(Recipe(title = "Mushroom Butter Rice", sourceUrl = "https://www.instagram.com/reel/DF6dMW5vwgw/",
                sourcePlatform = SourcePlatform.INSTAGRAM, sourceVideoId = "DF6dMW5vwgw"))
            val yt = dao.insert(Recipe(title = "Dal Tadka", sourceUrl = "https://www.youtube.com/watch?v=ETGWelKhgfc",
                sourcePlatform = SourcePlatform.YOUTUBE, sourceVideoId = null)) // older row without a stored id
            val web = dao.insert(Recipe(title = "Paneer Tikka", sourceUrl = "https://example.com/paneer-tikka/", sourcePlatform = SourcePlatform.WEB))
            dao.insert(Recipe(title = "Manual Poha"))

            assertEquals(reel, dao.findBySourceVideo(SourcePlatform.INSTAGRAM, "DF6dMW5vwgw")?.id)
            assertNull(dao.findBySourceVideo(SourcePlatform.YOUTUBE, "DF6dMW5vwgw"))
            assertEquals(listOf(reel, yt, web), dao.sourceLinks().map { it.id })

            assertEquals(reel to "Mushroom Butter Rice", repo.findDuplicate("https://instagram.com/reels/DF6dMW5vwgw?igsh=q"))
            assertEquals(yt to "Dal Tadka", repo.findDuplicate("https://youtu.be/ETGWelKhgfc?si=1"))
            assertEquals(web to "Paneer Tikka", repo.findDuplicate("https://www.example.com/paneer-tikka?utm_source=x"))
            assertNull(repo.findDuplicate("https://example.com/chole"))
            assertNull(repo.findDuplicate("https://www.instagram.com/reel/OTHERcode1/"))
            assertNull(repo.findDuplicate(null))
        } finally { db.close() }
    }

    // ---- Import stages (real cascade progress) + cancel ----

    @Test fun instagramCaptionImportReportsRealStages() {
        val embed = fixture("ig_embed_tiffany_mushroom")
        val seen = mutableListOf<ImportStage>()
        val r = ImportProgress.withListener({ seen += it }) {
            InstagramImporter.import("https://www.instagram.com/reel/DF6dMW5vwgw/", download = { embed }, creators = null, video = null)
        }
        assertTrue(r is WebRecipeFetcher.Result.Success)
        assertEquals(listOf(ImportStage.CAPTION, ImportStage.FINISHING), seen)
    }

    @Test fun noListenerMeansNoOp() {
        ImportProgress.report(ImportStage.CAPTION) // must not throw
    }

    @Test fun fetchForwardsStagesInOrder() = runBlocking {
        WebRecipeFetcher.instagramImport = {
            ImportProgress.report(ImportStage.CAPTION); ImportProgress.report(ImportStage.POST_DETAILS)
            WebRecipeFetcher.Result.Failure("nothing here")
        }
        val seen = java.util.Collections.synchronizedList(mutableListOf<ImportStage>())
        WebRecipeFetcher.fetch("https://www.instagram.com/reel/DF6dMW5vwgw/") { seen += it }
        assertEquals(listOf(ImportStage.READING_LINK, ImportStage.CAPTION, ImportStage.POST_DETAILS), seen.toList())
    }

    @Test fun cancelStopsCascadeAtNextStage() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reachedVideo = CompletableDeferred<Boolean>()
        WebRecipeFetcher.instagramImport = {
            ImportProgress.report(ImportStage.CAPTION)
            entered.countDown()
            release.await(5, TimeUnit.SECONDS) // a blocking network call in flight
            ImportProgress.report(ImportStage.VIDEO) // the next stage boundary must throw
            reachedVideo.complete(true)
            WebRecipeFetcher.Result.Failure("should not get here")
        }
        val call = async(Dispatchers.Default) { WebRecipeFetcher.fetch("https://www.instagram.com/reel/DF6dMW5vwgw/") }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        call.cancel()
        release.countDown()
        var cancelled = false
        try { withTimeout(5_000) { call.await() } } catch (e: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertFalse(reachedVideo.isCompleted)
    }

    // ---- Search Results states ----

    private val library = listOf(
        Recipe(id = 1, title = "Paneer Butter Masala", cuisine = "North Indian"),
        Recipe(id = 2, title = "Palak Dal"),
        Recipe(id = 3, title = "Masala Dosa", cuisine = "South Indian"),
    )
    private val index = mapOf(1L to listOf("paneer", "butter", "tomato"), 2L to listOf("spinach", "toor dal"), 3L to listOf("rice", "potato"))

    @Test fun searchStates() {
        assertEquals(SearchState.Hint, SearchResults.compute("   ", library, index))
        assertEquals(SearchState.NoResults("biryani"), SearchResults.compute(" biryani ", library, index))

        val masala = SearchResults.compute("masala", library, index) as SearchState.Results
        assertEquals(setOf(1L, 3L), masala.hits.map { it.recipe.id }.toSet())
        assertTrue(masala.hits.all { it.matchedIngredients.isEmpty() }) // title hit, no ingredient line

        val spinach = SearchResults.compute("spinach", library, index) as SearchState.Results
        assertEquals(listOf(2L), spinach.hits.map { it.recipe.id })
        assertEquals(listOf("spinach"), spinach.hits.single().matchedIngredients)

        assertNotNull(SearchResults.ERROR)
        assertFalse(SearchResults.ERROR.any { Character.getType(it) == Character.OTHER_SYMBOL.toInt() || Character.isSurrogate(it) }) // no emoji
    }
}
