package com.desikitchen.app.feature

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.desikitchen.app.data.db.DesiKitchenDatabase
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.model.SourcePlatform
import com.desikitchen.app.data.repo.RecipeRepository
import com.desikitchen.app.util.CreatorProfile
import com.desikitchen.app.util.ImportDraft
import com.desikitchen.app.util.ImportDraftCodec
import com.desikitchen.app.util.RecipeTextParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Creator page data layer: v6 avatar column + migration, handle-normalised DAO query, link derivation. Device-only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreatorDataTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun migrate5To6AddsNullableAvatarAndKeepsRecipes() {
        val schema = JSONObject(File("schemas/com.desikitchen.app.data.db.DesiKitchenDatabase/5.json").readText()).getJSONObject("database")
        val file = ctx.getDatabasePath("mig56.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).apply {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                e.optJSONArray("indices")?.let { idx -> for (j in 0 until idx.length()) execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName"))) }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) execSQL(setup.getString(i))
            execSQL(
                "INSERT INTO recipes (id, title, description, cuisine, mealType, dietTags, prepMinutes, cookMinutes, servings, emoji, isFavorite, isUserCreated, createdAt, cookedCount, origin, updatedAt, creatorHandle, sourcePlatform) " +
                    "VALUES (1, 'Poha', '', '', '', '', 5, 10, 2, '', 0, 1, 0, 0, 'import', 0, '@cook.book', 'instagram')",
            )
            version = 5
            close()
        }
        val room = Room.databaseBuilder(ctx, DesiKitchenDatabase::class.java, "mig56.db")
            .addMigrations(DesiKitchenDatabase.MIGRATION_5_6).allowMainThreadQueries().build()
        val db = room.openHelper.writableDatabase // runs 5→6 + Room's v6 schema validation
        assertEquals(6, db.version)
        db.query("SELECT title, creatorHandle, creatorAvatarUrl, creatorProfileUrl FROM recipes").use { c ->
            c.moveToFirst(); assertEquals("Poha", c.getString(0)); assertEquals("@cook.book", c.getString(1)); assertTrue(c.isNull(2)); assertTrue(c.isNull(3))
        }
        room.close()
    }

    @Test fun recipesByCreatorHandleNormalisesAtAndCase() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, DesiKitchenDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.recipeDao()
            val a = dao.insert(Recipe(title = "A", creatorHandle = "@Cook.Book", sourcePlatform = SourcePlatform.INSTAGRAM, createdAt = 1))
            val b = dao.insert(Recipe(title = "B", creatorHandle = " cook.book ", sourcePlatform = SourcePlatform.INSTAGRAM, createdAt = 2))
            dao.insert(Recipe(title = "C", creatorHandle = "@other", createdAt = 3))
            dao.insert(Recipe(title = "D", creatorHandle = null, createdAt = 4))
            dao.insert(Recipe(title = "E", creatorHandle = "", createdAt = 5))
            assertEquals(listOf(b, a), dao.recipesByCreatorHandle("@cook.book").first().map { it.id })
            assertEquals(listOf(b, a), RecipeRepository(dao).observeByCreator("COOK.BOOK").first().map { it.id })
            assertEquals(emptyList<Long>(), dao.recipesByCreatorHandle("@").first().map { it.id })
        } finally { db.close() }
    }

    @Test fun profileUrlDerivationNeverInvents() {
        assertEquals("https://www.instagram.com/cook.book/", CreatorProfile.profileUrl(SourcePlatform.INSTAGRAM, "@cook.book"))
        assertEquals("https://www.instagram.com/cook_book/", CreatorProfile.profileUrl(SourcePlatform.INSTAGRAM, "cook_book"))
        assertNull(CreatorProfile.profileUrl(SourcePlatform.INSTAGRAM, "Hebbars Kitchen")) // not an IG username
        assertNull(CreatorProfile.profileUrl(SourcePlatform.YOUTUBE, "Hebbars Kitchen")) // no channel URL stored
        assertEquals("https://www.youtube.com/@hebbars", CreatorProfile.profileUrl(SourcePlatform.YOUTUBE, "x", "https://www.youtube.com/@hebbars"))
        assertNull(CreatorProfile.profileUrl(SourcePlatform.WEB, "@site"))
        assertNull(CreatorProfile.profileUrl(SourcePlatform.INSTAGRAM, null))
        assertEquals("Follow on Instagram", CreatorProfile.followLabel(SourcePlatform.INSTAGRAM))
        assertEquals("cook book", CreatorProfile.key(" @Cook   Book "))
    }

    @Test fun avatarMustBeHttps() {
        assertEquals("https://cdn.example/a.jpg", CreatorProfile.safeAvatarUrl(" https://cdn.example/a.jpg "))
        assertNull(CreatorProfile.safeAvatarUrl("http://cdn.example/a.jpg"))
        assertNull(CreatorProfile.safeAvatarUrl("file:///data/a.jpg"))
        assertNull(CreatorProfile.safeAvatarUrl(null))
    }

    @Test fun draftCodecRoundTripsAvatar() {
        val d = ImportDraft.Draft(RecipeTextParser.ParsedRecipe(title = "T", servings = null, ingredients = emptyList(), steps = emptyList()),
            creatorAvatarUrl = "https://cdn.example/a.jpg")
        assertEquals("https://cdn.example/a.jpg", ImportDraftCodec.decode(ImportDraftCodec.encode(d))?.creatorAvatarUrl)
        assertNull(ImportDraftCodec.decode(ImportDraftCodec.encode(d.copy(creatorAvatarUrl = null)))?.creatorAvatarUrl)
    }

    private fun fixture(name: String): String {
        val f = listOf(File("src/test/resources/scraper/$name.html.gz"), File("app/src/test/resources/scraper/$name.html.gz")).first { it.exists() }
        return java.util.zip.GZIPInputStream(f.inputStream()).bufferedReader().readText()
    }

    @Test fun igEmbedExposesOwnerAvatarAsHttpsHotlink() {
        for (n in listOf("ig_embed_tiffany_mushroom", "ig_embed_healthygirl_mushroom", "ig_embed_tastiva_applejam")) {
            val a = com.desikitchen.app.util.InstagramImporter.parseEmbed(fixture(n)).avatarUrl
            assertTrue("$n: $a", a != null && a.startsWith("https://scontent-") && ".cdninstagram.com/v/" in a && '\\' !in a && "\\u" !in a)
        }
    }

    @Test fun igImportCarriesAvatarOnSuccessAndPartial() {
        val embed = fixture("ig_embed_tiffany_mushroom")
        val r = com.desikitchen.app.util.InstagramImporter.import("https://www.instagram.com/reel/DF6dMW5vwgw/",
            download = { embed }, creators = null, video = null, text = null, stills = null)
        val d = when (r) {
            is com.desikitchen.app.util.WebRecipeFetcher.Result.Success -> r.draft
            is com.desikitchen.app.util.WebRecipeFetcher.Result.Failure -> r.partial
        }
        assertTrue(d?.creatorAvatarUrl?.startsWith("https://") == true)
        assertNull(d?.creatorProfileUrl)
    }

    @Test fun youtubeChannelUrlFromOEmbedAndCreators() {
        val o = com.desikitchen.app.util.SocialRecipeImporter.parseOEmbed(
            """{"title":"Curd dosa","author_name":"Hebbars Kitchen","author_url":"https://www.youtube.com/@HebbarsKitchen","thumbnail_url":"https://i.ytimg.com/vi/x/hq.jpg"}""")
        assertEquals("https://www.youtube.com/@HebbarsKitchen", o.authorUrl)
        val p = com.desikitchen.app.util.SocialCreatorsClient.parsePost(com.desikitchen.app.util.SocialRecipeImporter.Platform.YOUTUBE,
            """{"title":"t","description":"d","channel":{"id":"UC1","url":"https://www.youtube.com/@afroballer8906","handle":"afroballer8906","title":"Afroballer"}}""")
        assertEquals("https://www.youtube.com/@afroballer8906", p.authorUrl)
        val r = Recipe(title = "Dosa", sourcePlatform = SourcePlatform.YOUTUBE, creatorHandle = "Hebbars Kitchen", creatorProfileUrl = o.authorUrl)
        assertEquals("https://www.youtube.com/@HebbarsKitchen", CreatorProfile.profileUrl(r))
        assertEquals("Visit YouTube channel", CreatorProfile.followLabel(r.sourcePlatform))
        // Wrong host for platform / non-profile hosts are rejected; http upgraded.
        assertNull(CreatorProfile.profileUrl(SourcePlatform.INSTAGRAM, "Hebbars Kitchen", "https://www.youtube.com/@x"))
        assertNull(CreatorProfile.safeProfileUrl("https://evil.example/@x"))
        assertNull(CreatorProfile.safeProfileUrl("javascript:alert(1)"))
        assertEquals("https://www.youtube.com/@x", CreatorProfile.safeProfileUrl("http://www.youtube.com/@x"))
        assertEquals("https://www.youtube.com/@h_k", CreatorProfile.profileUrl(SourcePlatform.YOUTUBE, "@h_k"))
    }

    @Test fun claimDefaultsToUnclaimedAndCodecCarriesProfile() {
        assertTrue(!CreatorProfile.isClaimed("@cook.book"))
        assertTrue(CreatorProfile.UNCLAIMED_COPY.contains("creators@desikitchen.app"))
        val d = ImportDraft.Draft(RecipeTextParser.ParsedRecipe("T", null, emptyList(), emptyList()), creatorProfileUrl = "https://www.youtube.com/@x")
        assertEquals("https://www.youtube.com/@x", ImportDraftCodec.decode(ImportDraftCodec.encode(d))?.creatorProfileUrl)
    }
}
