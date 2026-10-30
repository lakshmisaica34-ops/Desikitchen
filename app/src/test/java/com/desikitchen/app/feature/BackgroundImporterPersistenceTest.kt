package com.desikitchen.app.feature

import com.desikitchen.app.util.BackgroundImporter
import com.desikitchen.app.util.ImportDraft
import com.desikitchen.app.util.ImportDraftCodec
import com.desikitchen.app.util.ImportJob
import com.desikitchen.app.util.ImportPersistence
import com.desikitchen.app.util.ImportStage
import com.desikitchen.app.util.RecipeTextParser
import com.desikitchen.app.util.WebRecipeFetcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/** Imports survive app kill: queue + parked review drafts are persisted and restored on the next start. */
class BackgroundImporterPersistenceTest {
    private val reel = "https://www.instagram.com/reel/DF6dMW5vwgw/"
    private val yt = "https://www.youtube.com/shorts/abc123"
    private val reel2 = "https://www.instagram.com/reel/C1abcDEF/"

    /** In-memory stand-in for the Room table; outlives the importer like the database outlives the process. */
    private class FakeStore(@Volatile var snap: ImportPersistence.Snapshot = ImportPersistence.Snapshot()) : ImportPersistence {
        @Volatile var loads = 0
        override suspend fun load(): ImportPersistence.Snapshot { loads++; return snap }
        override suspend fun save(snapshot: ImportPersistence.Snapshot) { snap = snapshot }
    }

    private fun rich(url: String) = ImportDraft.Draft(
        parsed = RecipeTextParser.ParsedRecipe(
            "Paneer Tikka", 4,
            listOf(
                RecipeTextParser.ParsedIngredient(250.0, "g", "paneer", "Dairy"),
                RecipeTextParser.ParsedIngredient(null, "", "salt", "Spices"),
                RecipeTextParser.ParsedIngredient(1.5, "tbsp", "curd", "Dairy"),
            ),
            listOf("Marinate", "Grill"),
        ),
        description = "By @chefsai\nSmoky tikka",
        imageUrl = "https://cdn.example/thumb.jpg",
        sourceUrl = url,
        prepMinutes = 10, cookMinutes = 15,
        warnings = listOf("Caption had no steps"),
        partial = true,
        creatorAvatarUrl = "https://cdn.example/avatar.jpg",
    )
    private fun simple(url: String) = ImportDraft.Draft(RecipeTextParser.ParsedRecipe("R $url", null, emptyList(), listOf("Boil")), sourceUrl = url)

    private fun importer(
        store: ImportPersistence,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        dup: (String) -> Pair<Long, String>? = { null },
        fetch: suspend (String, (ImportStage) -> Unit) -> WebRecipeFetcher.Result,
    ) = BackgroundImporter(scope, findDuplicate = { dup(it) }, canImport = { true }, fetch = fetch, persistence = store)

    private val noFetch: suspend (String, (ImportStage) -> Unit) -> WebRecipeFetcher.Result = { u, _ -> error("re-fetched $u") }

    @Test fun codecRoundTripsEveryDraftField() {
        val d = rich(reel)
        assertEquals(d, ImportDraftCodec.decode(ImportDraftCodec.encode(d)))
        val bare = ImportDraft.Draft(RecipeTextParser.ParsedRecipe("", null, emptyList(), emptyList()))
        assertEquals(bare, ImportDraftCodec.decode(ImportDraftCodec.encode(bare)))
        assertNull("unreadable rows are dropped, not guessed", ImportDraftCodec.decode("{not json"))
        assertNull(ImportDraftCodec.decode(null))
    }

    @Test fun persistAndReloadParkedDrafts() = runBlocking {
        val store = FakeStore()
        val a = importer(store) { url, _ ->
            if (url == reel) WebRecipeFetcher.Result.Success(rich(url), false)
            else WebRecipeFetcher.Result.Failure("No recipe in caption", partial = simple(url))
        }
        a.startAll(listOf(reel, yt))
        withTimeout(5_000) { a.state.first { it is ImportJob.Ready && it.more == 1 } }
        a.awaitPersisted()
        assertTrue(store.snap.queue.isEmpty())
        assertEquals(2, store.snap.parked.size)

        // "Restart": a new importer over the same store; nothing is fetched again.
        val b = importer(store, fetch = noFetch)
        b.awaitPersisted()
        val ready = b.state.value as ImportJob.Ready
        assertEquals("every field survives (ingredients, steps, creator byline, image, warnings, partial)", rich(reel), ready.draft)
        assertEquals(1, ready.more)
        assertEquals(2, b.readyCount)
        assertEquals(rich(reel), b.takeDraftForReview())
        val failed = b.state.value as ImportJob.Failed
        assertEquals("No recipe in caption", failed.message)
        assertEquals(simple(yt), failed.partial)
    }

    @Test fun reloadDropsDraftsAlreadySavedAsRecipes() = runBlocking {
        val store = FakeStore(ImportPersistence.Snapshot(parked = listOf(ImportPersistence.ParkedDraft(simple(reel)), ImportPersistence.ParkedDraft(simple(yt)))))
        val imp = importer(store, dup = { if (it == reel) 4L to "Dal" else null }, fetch = noFetch)
        imp.awaitPersisted()
        assertEquals(1, imp.readyCount)
        assertEquals(yt, (imp.state.value as ImportJob.Ready).draft.sourceUrl)
        assertEquals(listOf(yt), store.snap.parked.map { it.draft.sourceUrl })
    }

    @Test fun queueSurvivesRestart() = runBlocking {
        val store = FakeStore()
        val killed = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val never = CompletableDeferred<Unit>()
        val a = importer(store, scope = killed) { url, _ -> never.await(); WebRecipeFetcher.Result.Success(simple(url), false) }
        assertEquals(3, a.startAll(listOf(reel, yt, reel2)))
        withTimeout(5_000) { a.state.first { it is ImportJob.Running } }
        a.awaitPersisted()
        assertEquals("running link first, then the waiting ones", listOf(reel, yt, reel2), store.snap.queue)
        killed.cancel() // process killed mid-import

        val fetched = Collections.synchronizedList(mutableListOf<String>())
        val b = importer(store) { url, _ -> fetched += url; WebRecipeFetcher.Result.Success(simple(url), false) }
        val ready = withTimeout(5_000) { b.state.first { it is ImportJob.Ready && it.more == 2 } } as ImportJob.Ready
        assertEquals(listOf(reel, yt, reel2), fetched.toList())
        assertEquals(reel, ready.draft.sourceUrl)
        b.awaitPersisted()
        assertTrue(store.snap.queue.isEmpty())
        assertEquals(listOf(reel, yt, reel2), store.snap.parked.map { it.draft.sourceUrl })
    }

    @Test fun restoredQueueSkipsLinksThatAlreadyHaveADraftOrAreSaved() = runBlocking {
        val store = FakeStore(ImportPersistence.Snapshot(queue = listOf(reel, yt, reel2), parked = listOf(ImportPersistence.ParkedDraft(simple(reel)))))
        val fetched = Collections.synchronizedList(mutableListOf<String>())
        val imp = importer(store, dup = { if (it == reel2) 8L to "Poha" else null }) { url, _ -> fetched += url; WebRecipeFetcher.Result.Success(simple(url), false) }
        withTimeout(5_000) { imp.state.first { it is ImportJob.Notice || (it is ImportJob.Ready && it.more == 1) } }
        assertEquals("reel already parked; reel2 is a saved recipe (dup check, no fetch)", listOf(yt), fetched.toList())
        assertEquals(listOf(reel, yt), listOf(imp.takeDraftForReview()?.sourceUrl, imp.takeDraftForReview()?.sourceUrl))
    }

    @Test fun takeDraftForReviewMovesEntryToEditorSessionUntilFinished() = runBlocking {
        val store = FakeStore()
        val a = importer(store) { url, _ -> WebRecipeFetcher.Result.Success(simple(url), false) }
        a.startAll(listOf(reel, yt))
        withTimeout(5_000) { a.state.first { it is ImportJob.Ready && it.more == 1 } }
        assertEquals(reel, a.takeDraftForReview()?.sourceUrl)
        a.awaitPersisted()
        assertEquals(listOf(yt), store.snap.parked.map { it.draft.sourceUrl })
        assertEquals("handed-off draft is kept as the editor session", simple(reel), store.snap.editor?.draft)
        a.dismiss() // dismissing the next parked draft also forgets it
        a.awaitPersisted()
        assertTrue(store.snap.parked.isEmpty())
        a.finishReview(simple(reel)) // Save / discard in the editor
        a.awaitPersisted()
        assertTrue(store.snap.isEmpty)

        val b = importer(store, fetch = noFetch)
        b.awaitPersisted()
        assertEquals(ImportJob.Idle, b.state.value)
    }

    @Test fun cancelKeepsParkedButClearsQueue() = runBlocking {
        val store = FakeStore()
        val gate = CompletableDeferred<Unit>()
        val a = importer(store) { url, _ -> if (url == yt) gate.await(); WebRecipeFetcher.Result.Success(simple(url), false) }
        a.startAll(listOf(reel, yt, reel2))
        withTimeout(5_000) { a.state.first { it is ImportJob.Running && it.url == yt } }
        a.awaitPersisted()
        assertEquals(listOf(yt, reel2), store.snap.queue)
        assertEquals(listOf(reel), store.snap.parked.map { it.draft.sourceUrl })
        a.cancel()
        gate.complete(Unit)
        a.awaitPersisted()
        assertTrue(store.snap.queue.isEmpty())
        assertEquals(listOf(reel), store.snap.parked.map { it.draft.sourceUrl })

        val b = importer(store, fetch = noFetch)
        b.awaitPersisted()
        assertEquals(reel, (b.state.value as ImportJob.Ready).draft.sourceUrl)
    }

    @Test fun batchNoticeSurvivesRestartUntilDismissed() = runBlocking {
        val store = FakeStore()
        val a = importer(store, dup = { if (it == yt) 5L to "Poha" else null }) { url, _ ->
            if (url == reel2) WebRecipeFetcher.Result.Failure("Blocked") else WebRecipeFetcher.Result.Success(simple(url), false)
        }
        a.startAll(listOf(reel, yt, reel2))
        withTimeout(5_000) { a.state.first { it is ImportJob.Notice } }
        a.awaitPersisted()
        val b = importer(store, fetch = noFetch)
        b.awaitPersisted()
        assertEquals(ImportJob.Notice("1 already saved \u00b7 1 couldn't be imported"), b.state.value)
        b.dismiss()
        assertEquals(reel, (b.state.value as ImportJob.Ready).draft.sourceUrl)
        b.awaitPersisted()
        assertNull(store.snap.notice)
    }
}
