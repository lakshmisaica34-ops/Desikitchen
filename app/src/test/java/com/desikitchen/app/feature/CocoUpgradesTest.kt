package com.desikitchen.app.feature

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.desikitchen.app.data.db.DesiKitchenDatabase
import com.desikitchen.app.data.model.CocoActionState
import com.desikitchen.app.data.model.Ingredient
import com.desikitchen.app.data.model.MealSlot
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.repo.CocoChatRepository
import com.desikitchen.app.data.repo.GroceryRepository
import com.desikitchen.app.data.repo.MealPlanRepository
import com.desikitchen.app.data.repo.RecipeRepository
import com.desikitchen.app.util.CocoActionExecutor
import com.desikitchen.app.util.CocoActions
import com.desikitchen.app.util.LibraryAssistant
import com.desikitchen.app.util.OpenRouterClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/** Phase 1 Coco upgrades: persisted history, streamed replies, library-only meal-plan / grocery actions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CocoUpgradesTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val today = LocalDate.of(2026, 10, 29) // a Thursday

    private fun build(name: String) = Room.databaseBuilder(ctx, DesiKitchenDatabase::class.java, name)
        .addMigrations(DesiKitchenDatabase.MIGRATION_1_2, DesiKitchenDatabase.MIGRATION_2_3, DesiKitchenDatabase.MIGRATION_3_4, DesiKitchenDatabase.MIGRATION_4_5, DesiKitchenDatabase.MIGRATION_5_6)
        .allowMainThreadQueries().build()

    // ---- Feature 1: persisted chat history ----

    @Test fun historyPersistsAcrossReopenAndNewChatClearsIt() = runBlocking {
        ctx.getDatabasePath("coco-history.db").delete()
        val db1 = build("coco-history.db")
        val repo1 = CocoChatRepository(db1.cocoChatDao())
        repo1.append("What can I make with paneer?", "Paneer Butter Masala fits.", ai = true, recipeIds = listOf(1L, 1L, 3L))
        val proposalId = repo1.append("add it to my grocery list", "Add …?", ai = false, recipeIds = listOf(1L), action = CocoActions.Draft(CocoActions.Kind.GROCERY, 1).encode(), actionState = CocoActionState.PROPOSED)
        repo1.setActionState(proposalId, CocoActionState.DONE)
        db1.close()

        // Reopen = app restart: the thread is restored in order, with chips and action state.
        val db2 = build("coco-history.db")
        val repo2 = CocoChatRepository(db2.cocoChatDao())
        val restored = repo2.observe().first()
        assertEquals(listOf("What can I make with paneer?", "add it to my grocery list"), restored.map { it.question })
        assertEquals(listOf(1L, 3L), restored[0].recipeIdList)
        assertTrue(restored[0].ai)
        assertEquals(CocoActionState.DONE, restored[1].actionState)
        assertEquals(CocoActions.Draft(CocoActions.Kind.GROCERY, 1), CocoActions.decode(restored[1].action))

        repo2.clear()
        db2.close()
        val db3 = build("coco-history.db")
        assertTrue(CocoChatRepository(db3.cocoChatDao()).getAll().isEmpty()) // the clear is persisted
        db3.close()
    }

    @Test fun migrate3To4KeepsRecipesAndAddsChatTable() {
        val schema = JSONObject(File("schemas/com.desikitchen.app.data.db.DesiKitchenDatabase/3.json").readText()).getJSONObject("database")
        val file = ctx.getDatabasePath("mig34.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).apply {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                e.optJSONArray("indices")?.let { idx -> for (j in 0 until idx.length()) execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName"))) }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) execSQL(setup.getString(i))
            execSQL("INSERT INTO recipes (id, title, description, cuisine, mealType, dietTags, emoji, prepMinutes, cookMinutes, servings, isFavorite, isUserCreated, createdAt, cookedCount, origin, updatedAt) VALUES (7, 'Poha', '', '', '', '', '', 5, 10, 2, 0, 1, 0, 0, 'manual', 0)")
            version = 3
            close()
        }
        val room = build("mig34.db")
        val db = room.openHelper.writableDatabase // runs 3→4 and Room's v4 schema validation
        assertEquals(6, db.version)
        db.query("SELECT title FROM recipes WHERE id = 7").use { c -> assertTrue(c.moveToFirst()); assertEquals("Poha", c.getString(0)) }
        runBlocking {
            CocoChatRepository(room.cocoChatDao()).append("q", "a", ai = false)
            assertEquals(1, room.cocoChatDao().getAll().size)
        }
        room.close()
    }

    // ---- Feature 2: streaming ----

    private fun sse(vararg deltas: String) = deltas.map { "data: " + JSONObject().put("choices", JSONArray().put(JSONObject().put("delta", JSONObject().put("content", it)))) }

    @Test fun streamClientContract() {
        var sentBody = ""; var sentHeaders = emptyMap<String, String>()
        val lines = listOf(": OPENROUTER PROCESSING", "") + sse("Hel", "", "lo", " there") + listOf("data: [DONE]") + sse("IGNORED")
        val c = OpenRouterClient("secret-key", "m", transport = { _, _, _ -> error("non-stream path must not be used") }, streamTransport = { _, h, body, onLine ->
            sentBody = body; sentHeaders = h
            for (l in lines) if (!onLine(l)) break
            200 to ""
        })
        val deltas = mutableListOf<String>()
        val text = c.chatStream(listOf(OpenRouterClient.Message("user", "hi"))) { deltas += it }
        assertEquals("Hello there", text)
        assertEquals(listOf("Hel", "lo", " there"), deltas)
        assertTrue(JSONObject(sentBody).getBoolean("stream"))
        assertFalse(sentBody.contains("secret-key"))
        assertEquals("text/event-stream", sentHeaders["Accept"])

        assertNull(OpenRouterClient.parseStreamLine(": keep-alive"))
        assertEquals(OpenRouterClient.StreamEvent.Done, OpenRouterClient.parseStreamLine("data: [DONE]"))
        val busy = OpenRouterClient("k", "m", streamTransport = { _, _, _, _ -> 429 to "{}" })
        val e = runCatching { busy.chatStream(listOf(OpenRouterClient.Message("user", "hi"))) {} }.exceptionOrNull()!!
        assertTrue(e.message!!.contains("429"))
        val midError = OpenRouterClient("k", "m", streamTransport = { _, _, _, onLine ->
            onLine(sse("par")[0]); onLine("data: {\"error\":{\"message\":\"provider died\"}}"); 200 to ""
        })
        assertTrue(runCatching { midError.chatStream(listOf(OpenRouterClient.Message("user", "hi"))) {} }.exceptionOrNull()!!.message!!.contains("provider died"))
    }

    @Test fun cocoStreamsPartialsThenFallsBackGracefully() {
        val ok = OpenRouterClient("k", "m", streamTransport = { _, _, _, onLine -> (sse("Try ", "Jeera Rice.") + "data: [DONE]").forEach { onLine(it) }; 200 to "" })
        val partials = mutableListOf<String>()
        val a = LibraryAssistant(ok).askStreaming(lib, "rice?", emptyList()) { partials += it }
        assertTrue(a.ai); assertEquals("Try Jeera Rice.", a.text)
        assertEquals(listOf("Try ", "Try Jeera Rice."), partials)
        assertEquals(listOf(2L), a.recipeIds)

        // Stream fails → one non-streamed request.
        val nonStream = OpenRouterClient("k", "m",
            transport = { _, _, _ -> 200 to JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", "Full answer")))).toString() },
            streamTransport = { _, _, _, _ -> 500 to "{}" })
        assertEquals("Full answer", LibraryAssistant(nonStream).askStreaming(lib, "rice?", emptyList()) {}.text)

        // Both fail → offline matcher, quoting only the library.
        val down = OpenRouterClient("k", "m", transport = { _, _, _ -> 429 to "{}" }, streamTransport = { _, _, _, _ -> 429 to "{}" })
        val off = LibraryAssistant(down).askStreaming(lib, "anything with cauliflower?", emptyList()) {}
        assertFalse(off.ai); assertTrue(off.text.contains("Aloo Gobi"))

        // Cancellation (New chat) stops the stream and is not swallowed by the fallback.
        var nonStreamCalls = 0
        val long = OpenRouterClient("k", "m", transport = { _, _, _ -> nonStreamCalls++; 200 to "{}" },
            streamTransport = { _, _, _, onLine -> sse("a", "b", "c").forEach { onLine(it) }; 200 to "" })
        var seen = 0
        val thrown = runCatching { LibraryAssistant(long).askStreaming(lib, "q", emptyList()) { seen++; if (seen == 1) throw CancellationException("new chat") } }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
        assertEquals(1, seen); assertEquals(0, nonStreamCalls)
    }

    // ---- Feature 3: meal-plan / grocery actions, library only ----

    private val lib = listOf(
        LibraryAssistant.Entry(1, "Paneer Butter Masala", listOf("paneer", "butter", "tomato", "cream")),
        LibraryAssistant.Entry(2, "Jeera Rice", listOf("basmati rice", "cumin seeds", "ghee")),
        LibraryAssistant.Entry(3, "Aloo Gobi", listOf("potato", "cauliflower", "turmeric")),
    )

    @Test fun ordinaryQuestionsAreNotActions() {
        assertNull(CocoActions.respond(lib, "What can I make with paneer?", today))
        assertNull(CocoActions.respond(lib, "What's on my meal plan?", today))
        assertNull(CocoActions.respond(lib, "Something quick for tonight", today))
        assertNull(CocoActions.respond(lib, "Should I add ghee for dinner?", today))
    }

    @Test fun proposesOnlySavedRecipes() {
        val plan = CocoActions.respond(lib, "Add Jeera Rice to tomorrow's dinner", today) as CocoActions.Reply.Propose
        assertEquals(CocoActions.Draft(CocoActions.Kind.PLAN, 2, today.plusDays(1).toEpochDay(), MealSlot.DINNER), plan.draft)
        assertTrue(plan.text.contains("Jeera Rice") && plan.text.contains("tomorrow"))

        val grocery = CocoActions.respond(lib, "put aloo gobi on my grocery list", today) as CocoActions.Reply.Propose
        assertEquals(CocoActions.Draft(CocoActions.Kind.GROCERY, 3), grocery.draft)
        assertTrue(grocery.text.contains("3 saved ingredients"))

        // A recipe that isn't saved is never proposed (and never invented); Coco asks, offering saved titles only.
        val unknown = CocoActions.respond(lib, "add chicken biryani to my grocery list", today)
        assertTrue(unknown is CocoActions.Reply.Ask)
        assertNull((unknown as CocoActions.Reply.Ask).draft.recipeId)
        assertFalse(unknown.text.contains("biryani", ignoreCase = true))
        assertTrue(lib.map { it.title }.containsAll(CocoActions.options(unknown.draft, lib, today)))

        // "it" = the one recipe Coco just pointed at.
        val it = CocoActions.respond(lib, "add it to my grocery list", today, contextRecipeIds = listOf(1L)) as CocoActions.Reply.Propose
        assertEquals(1L, it.draft.recipeId)

        // Recipe with no saved ingredients: nothing to propose.
        val bare = lib + LibraryAssistant.Entry(9, "Masala Chai", emptyList())
        assertTrue(CocoActions.respond(bare, "add masala chai to my shopping list", today) is CocoActions.Reply.Info)
    }

    @Test fun ambiguityAsksThenFollowUpsFillTheDraft() {
        val two = lib + LibraryAssistant.Entry(4, "Palak Paneer", listOf("spinach", "paneer"))
        val ask = CocoActions.respond(two, "add paneer to friday lunch", today) as CocoActions.Reply.Ask
        assertEquals(setOf(1L, 4L), ask.draft.candidates.toSet())
        assertEquals(LocalDate.of(2026, 10, 30).toEpochDay(), ask.draft.epochDay)
        assertEquals(setOf("Paneer Butter Masala", "Palak Paneer"), CocoActions.options(ask.draft, two, today).toSet())
        val decoded = CocoActions.decode(ask.draft.encode())!!
        val done = CocoActions.respond(two, "Palak Paneer", today, previous = decoded) as CocoActions.Reply.Propose
        assertEquals(CocoActions.Draft(CocoActions.Kind.PLAN, 4, LocalDate.of(2026, 10, 30).toEpochDay(), MealSlot.LUNCH), done.draft)

        // Missing day and meal: ask step by step.
        val a1 = CocoActions.respond(lib, "add Jeera Rice to my meal plan", today) as CocoActions.Reply.Ask
        assertEquals(listOf("Today", "Tomorrow"), CocoActions.options(a1.draft, lib, today).take(2))
        val a2 = CocoActions.respond(lib, "tomorrow", today, previous = a1.draft) as CocoActions.Reply.Ask
        assertEquals(MealSlot.entries.map { it.label }, CocoActions.options(a2.draft, lib, today))
        val p = CocoActions.respond(lib, "Dinner", today, previous = a2.draft) as CocoActions.Reply.Propose
        assertEquals(CocoActions.Draft(CocoActions.Kind.PLAN, 2, today.plusDays(1).toEpochDay(), MealSlot.DINNER), p.draft)

        // An unrelated question after a follow-up is ordinary chat; "cancel" drops it.
        assertNull(CocoActions.respond(lib, "how spicy is it?", today, previous = a1.draft))
        assertTrue(CocoActions.respond(lib, "cancel", today, previous = a1.draft) is CocoActions.Reply.Info)
        assertTrue(CocoActions.isYes("Yes!") && CocoActions.isNo("never mind"))
    }

    @Test fun executorWritesStoredDataOnlyAndRefusesMissingRecipes() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, DesiKitchenDatabase::class.java).allowMainThreadQueries().build()
        val recipes = RecipeRepository(db.recipeDao())
        val mealPlan = MealPlanRepository(db.mealPlanDao())
        val grocery = GroceryRepository(db.groceryDao(), db.recipeDao())
        val id = recipes.save(
            Recipe(title = "Jeera Rice", servings = 2),
            listOf(Ingredient(name = "basmati rice", quantity = 1.0, unit = "cup"), Ingredient(position = 1, name = "ghee")),
            emptyList(),
        )
        val exec = CocoActionExecutor(recipes, mealPlan, grocery) { today }

        val g = exec.execute(CocoActions.Draft(CocoActions.Kind.GROCERY, id))
        assertTrue(g, g.contains("Added 2 ingredients"))
        val items = grocery.observeAll().first().sortedBy { it.name }
        assertEquals(listOf("basmati rice", "ghee"), items.map { it.name })
        assertEquals(listOf(1.0, null), items.map { it.quantity }) // no quantity invented for "ghee"
        assertTrue(items.all { it.recipeId == id })

        val p = exec.execute(CocoActions.Draft(CocoActions.Kind.PLAN, id, today.plusDays(1).toEpochDay(), MealSlot.DINNER))
        assertTrue(p, p.contains("tomorrow") && p.contains("serves 2"))
        val week = mealPlan.getRange(today, today.plusDays(6))
        assertEquals(1, week.size)
        assertEquals(MealSlot.DINNER, week[0].entry.slot); assertEquals(2, week[0].entry.servings); assertEquals(id, week[0].entry.recipeId)

        // A recipe that isn't (or is no longer) in the library writes nothing.
        val missing = exec.execute(CocoActions.Draft(CocoActions.Kind.GROCERY, 999))
        assertTrue(missing.contains("can't find"))
        assertEquals(2, grocery.observeAll().first().size)
        exec.execute(CocoActions.Draft(CocoActions.Kind.PLAN, 999, today.toEpochDay(), MealSlot.LUNCH))
        assertEquals(1, mealPlan.getRange(today, today.plusDays(6)).size)
        // Incomplete drafts never write.
        assertTrue(exec.execute(CocoActions.Draft(CocoActions.Kind.PLAN, id)).contains("more detail"))
        assertEquals(1, mealPlan.getRange(today, today.plusDays(6)).size)
        db.close()
    }
}
