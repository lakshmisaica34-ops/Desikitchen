package com.desikitchen.app.feature

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.desikitchen.app.data.db.DesiKitchenDatabase
import com.desikitchen.app.data.model.PendingImport
import com.desikitchen.app.data.repo.RecipeRepository
import com.desikitchen.app.data.repo.RoomImportPersistence
import com.desikitchen.app.ui.edit.AddEditRecipeViewModel
import com.desikitchen.app.ui.edit.RecipeForm
import com.desikitchen.app.ui.edit.RecipeFormCodec
import com.desikitchen.app.ui.importer.ImportStatusViewModel
import com.desikitchen.app.util.BackgroundImporter
import com.desikitchen.app.util.ImportDraft
import com.desikitchen.app.util.ImportJob
import com.desikitchen.app.util.ImportPersistence
import com.desikitchen.app.util.RecipeTextParser
import com.desikitchen.app.util.ReviewHandoff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The draft open in the Review editor survives process death until Save or discard (editor session in
 * `pending_imports`), including the user's in-progress edits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReviewEditorSessionTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val reel = "https://www.instagram.com/reel/DF6dMW5vwgw/"
    private val yt = "https://www.youtube.com/shorts/abc123"

    private class FakeStore(@Volatile var snap: ImportPersistence.Snapshot = ImportPersistence.Snapshot()) : ImportPersistence {
        override suspend fun load() = snap
        override suspend fun save(snapshot: ImportPersistence.Snapshot) { snap = snapshot }
    }

    private fun draft(url: String) = ImportDraft.Draft(
        RecipeTextParser.ParsedRecipe("Rajma", 3, listOf(RecipeTextParser.ParsedIngredient(1.0, "cup", "rajma", "Pantry")), listOf("Soak", "Cook")),
        description = "By @auntyji on Instagram\nComfort food", imageUrl = "https://cdn.example/r.jpg", sourceUrl = url,
        warnings = listOf("w"), partial = true, creatorProfileUrl = "https://www.instagram.com/auntyji/",
    )

    private lateinit var db: DesiKitchenDatabase
    private lateinit var repo: RecipeRepository
    private val scopes = mutableListOf<CoroutineScope>()

    @Before fun setUp() {
        ImportDraft.take()
        db = Room.inMemoryDatabaseBuilder(ctx, DesiKitchenDatabase::class.java).build()
        repo = RecipeRepository(db.recipeDao())
    }

    @After fun tearDown() { scopes.forEach { it.cancel() }; db.close(); ImportDraft.take() }

    /** A fresh importer = a fresh process; [store] plays the database that outlives it. */
    private fun importer(store: ImportPersistence): BackgroundImporter {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        return BackgroundImporter(scope, findDuplicate = { repo.findDuplicate(it) }, canImport = { true },
            fetch = { u, _ -> error("re-fetched $u") }, persistence = store)
    }

    private fun parked(vararg d: ImportDraft.Draft) = FakeStore(ImportPersistence.Snapshot(parked = d.map { ImportPersistence.ParkedDraft(it) }))

    private fun editor(session: BackgroundImporter?) =
        AddEditRecipeViewModel(SavedStateHandle(mapOf("id" to -1L, "draft" to true)), repo, reviewSession = session)

    private fun idleUntil(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!cond()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < end) { "timed out: $what" }
            Thread.sleep(5)
        }
    }

    private fun persisted(imp: BackgroundImporter) = runBlocking { imp.awaitPersisted() }

    private fun pillOpen(imp: BackgroundImporter) = ImportStatusViewModel(imp, MutableStateFlow(emptyList())).handOffDraft()

    @Test fun formCodecRoundTripsEveryField() {
        val f = RecipeForm(
            title = "T", description = "D", imageUrl = "https://x/y.jpg", cuisine = "Punjabi", mealType = "Dinner",
            dietTags = setOf("Vegetarian"), prep = "10", cook = "abc", servings = 6, sourceUrl = reel,
            ingredients = listOf(com.desikitchen.app.ui.edit.IngredientRow(quantity = "1 1/2", unit = "cup", name = "rajma", aisle = "Pantry", note = "soaked")),
            steps = listOf(com.desikitchen.app.ui.edit.StepRow(text = "Boil", timer = "20")),
        )
        val back = RecipeFormCodec.decode(RecipeFormCodec.encode(f))!!
        fun RecipeForm.noKeys() = copy(ingredients = ingredients.map { it.copy(key = 0) }, steps = steps.map { it.copy(key = 0) })
        assertEquals(f.noKeys(), back.noKeys())
        assertNull(RecipeFormCodec.decode("{oops"))
        assertNull(RecipeFormCodec.decode(null))
    }

    @Test fun handoffThenKillRestoresDraftAndEditsViaPill() {
        val store = parked(draft(reel))
        val a = importer(store); persisted(a)
        assertTrue(pillOpen(a))
        val vm = editor(a)
        assertEquals("Rajma", vm.form.title)
        vm.update { it.copy(title = "Aunty's rajma", cuisine = "Punjabi") }
        vm.updateIngredient(vm.form.ingredients.first().key) { it.copy(note = "soaked overnight") }
        persisted(a)
        assertTrue("the open draft is no longer parked", store.snap.parked.isEmpty())
        assertEquals(draft(reel), store.snap.editor?.draft)
        assertNotNull(store.snap.editor?.edits)

        // Process death: no Save, no discard. Next launch shows the draft on the pill again.
        val b = importer(store); persisted(b)
        val ready = b.state.value as ImportJob.Ready
        assertEquals(draft(reel), ready.draft)
        assertEquals(1, b.readyCount)
        assertTrue(pillOpen(b))
        assertEquals(ImportJob.Idle, b.state.value)
        val again = editor(b)
        assertTrue(again.fromImport)
        assertEquals("Aunty's rajma", again.form.title)
        assertEquals("Punjabi", again.form.cuisine)
        assertEquals("soaked overnight", again.form.ingredients.first().note)
        assertEquals(listOf("w"), again.importWarnings)
        assertTrue(again.importPartial)
    }

    @Test fun systemRestoredEditorReclaimsSessionWithoutPill() {
        val store = parked(draft(reel))
        val a = importer(store); persisted(a)
        pillOpen(a)
        editor(a).update { it.copy(title = "Edited") }
        persisted(a)

        val b = importer(store)
        // Nav state restored the edit route itself; ImportDraft.pending died with the process.
        assertNull(ImportDraft.pending)
        val vm = editor(b)
        idleUntil("resume") { !vm.loading }
        assertEquals("Edited", vm.form.title)
        assertTrue(vm.fromImport)
        persisted(b)
        assertEquals("claimed by the editor, so the pill doesn't offer it too", ImportJob.Idle, b.state.value)
        assertNotNull("still persisted until Save / discard", store.snap.editor)
    }

    @Test fun saveClearsEditorSession() {
        val store = parked(draft(reel), draft(yt))
        val a = importer(store); persisted(a)
        pillOpen(a)
        val vm = editor(a)
        vm.update { it.copy(title = "Saved rajma") }
        var saved: Long? = null
        vm.save { saved = it }
        idleUntil("save") { saved != null }
        persisted(a)
        assertNull(store.snap.editor)
        assertEquals("the other parked draft is untouched", listOf(yt), store.snap.parked.map { it.draft.sourceUrl })
        runBlocking { assertEquals("Saved rajma", repo.findDuplicate(reel)?.second) }

        val b = importer(store); persisted(b)
        assertEquals(yt, (b.state.value as ImportJob.Ready).draft.sourceUrl)
    }

    @Test fun discardClearsEditorSession() {
        val store = parked(draft(reel))
        val a = importer(store); persisted(a)
        pillOpen(a)
        val vm = editor(a)
        vm.update { it.copy(title = "Never mind") }
        vm.discardReview()
        vm.update { it.copy(title = "typed after leaving") } // must not resurrect the session
        persisted(a)
        assertTrue(store.snap.isEmpty)
        val b = importer(store); persisted(b)
        assertEquals(ImportJob.Idle, b.state.value)
        assertFalse(pillOpen(b))
    }

    @Test fun restoreDropsSessionWhoseRecipeWasAlreadySaved() {
        // Save reached the recipes table but the app died before the session clear was written.
        runBlocking { repo.save(com.desikitchen.app.data.model.Recipe(title = "Rajma", sourceUrl = reel), emptyList(), emptyList()) }
        val store = FakeStore(ImportPersistence.Snapshot(editor = ImportPersistence.EditorDraft(draft(reel), edits = "{}")))
        val b = importer(store); persisted(b)
        assertEquals(ImportJob.Idle, b.state.value)
        assertNull(store.snap.editor)
    }

    @Test fun dismissingRestoredSessionOnPillForgetsIt() {
        val store = FakeStore(ImportPersistence.Snapshot(editor = ImportPersistence.EditorDraft(draft(reel), failMessage = "No steps in caption")))
        val b = importer(store); persisted(b)
        val failed = b.state.value as ImportJob.Failed
        assertEquals("No steps in caption", failed.message)
        assertEquals(draft(reel), failed.partial)
        b.dismiss(); persisted(b)
        assertTrue(store.snap.isEmpty)
    }

    @Test fun finishForAnotherDraftIsIgnored() {
        val store = parked(draft(reel))
        val a = importer(store); persisted(a)
        assertEquals(ReviewHandoff(draft(reel)), a.takeReviewHandoff())
        a.finishReview(draft(yt))
        a.updateReviewEdits(draft(yt), "{}")
        persisted(a)
        assertEquals(draft(reel), store.snap.editor?.draft)
        assertNull(store.snap.editor?.edits)
    }

    @Test fun editorSessionSurvivesRoomReopen() {
        val name = "editor-session.db"
        ctx.getDatabasePath(name).delete()
        fun open() = Room.databaseBuilder(ctx, DesiKitchenDatabase::class.java, name).build()
        val db1 = open()
        runBlocking { RoomImportPersistence(db1.pendingImportDao()).save(ImportPersistence.Snapshot(parked = listOf(ImportPersistence.ParkedDraft(draft(reel))))) }
        val a2 = importer(RoomImportPersistence(db1.pendingImportDao())); persisted(a2)
        val h = a2.takeReviewHandoff()!!
        a2.updateReviewEdits(h.draft, "{\"title\":\"Mine\"}")
        persisted(a2)
        scopes.forEach { it.cancel() }
        val kinds = runBlocking { db1.pendingImportDao().getAll().map { it.kind }.toSet() }
        db1.close()
        assertEquals(setOf(PendingImport.EDITOR, PendingImport.EDITOR_EDITS), kinds)

        val db2 = open()
        try {
            val b = importer(RoomImportPersistence(db2.pendingImportDao())); persisted(b)
            assertEquals(ReviewHandoff(draft(reel), "{\"title\":\"Mine\"}"), b.takeReviewHandoff())
            b.finishReview(draft(reel)); persisted(b)
            runBlocking { assertTrue(db2.pendingImportDao().getAll().isEmpty()) }
        } finally { db2.close() }
    }
}
