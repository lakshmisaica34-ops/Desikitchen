package com.desikitchen.app.feature

import com.desikitchen.app.data.model.GroceryItem
import com.desikitchen.app.data.model.GrocerySource
import com.desikitchen.app.data.model.Ingredient
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.model.RecipeWithDetails
import com.desikitchen.app.data.model.Step
import com.desikitchen.app.data.repo.PantryStaples
import com.desikitchen.app.data.repo.PlanGrocerySync
import com.desikitchen.app.ui.grocery.GroceryFilter
import com.desikitchen.app.ui.grocery.GroceryListBuilder
import com.desikitchen.app.ui.grocery.GrocerySort
import com.desikitchen.app.util.MarkdownText
import com.desikitchen.app.util.OcrTextAssembler
import com.desikitchen.app.util.OcrTextAssembler.Block
import com.desikitchen.app.util.OcrTextAssembler.Box
import com.desikitchen.app.util.OcrTextAssembler.Line
import com.desikitchen.app.util.OcrTextAssembler.Page
import com.desikitchen.app.util.OpenRouterClient
import com.desikitchen.app.util.RecipeAiAssistant
import com.desikitchen.app.util.RecipePdf
import com.desikitchen.app.util.RecipeSearch
import com.desikitchen.app.util.SharedPayload
import com.desikitchen.app.util.UnitSystem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the 1.1.0 feature plumbing: OCR text, share payloads, grocery sync/list, PDF layout, search, AI. */
class FeatureLogicTest {

    // ---- OCR ----
    private fun block(text: String, l: Int, t: Int, r: Int, b: Int) =
        Block(text.lines().mapIndexed { i, s -> Line(s, Box(l, t + i * 20, r, t + i * 20 + 18)) }, Box(l, t, r, b))

    @Test fun ocrReadsTwoColumnCookbookPageLeftThenRight() {
        val page = Page(listOf(
            block("Method\n1. Heat ghee", 520, 200, 1000, 260),
            block("Paneer Butter Masala", 0, 0, 1000, 60),
            block("Ingredients\n200 g paneer", 0, 200, 480, 260),
            block("2. Add toma-\ntoes", 520, 300, 1000, 360),
            block("l tsp salt", 0, 300, 480, 330),
        ))
        val text = OcrTextAssembler.assemble(page)
        val order = listOf("Paneer Butter Masala", "Ingredients", "1 tsp salt", "Method", "Add tomatoes")
        val idx = order.map { text.indexOf(it) }
        assertTrue("all found: $text", idx.none { it < 0 })
        assertEquals(idx.sorted(), idx)
    }

    @Test fun ocrJoinsPagesAndCleansBullets() {
        val a = Page(listOf(block("• 1 cup rice", 0, 0, 100, 20)))
        val b = Page(listOf(block("Cook 10 min", 0, 0, 100, 20)))
        assertEquals("- 1 cup rice\n\nCook 10 min", OcrTextAssembler.assemble(listOf(a, Page(emptyList()), b)))
    }

    // ---- Share sheet / files ----
    @Test fun sharedTextKeepsEverythingAndDropsDuplicates() {
        val p = SharedPayload.from("Dal Tadka", "Dal Tadka\nIngredients\n1 cup toor dal", listOf("notes.txt" to "Serves 4"))
        assertEquals("Dal Tadka\nIngredients\n1 cup toor dal\n\nServes 4", (p as SharedPayload.Text).text)
        val img = SharedPayload.from(null, "caption", imageUris = List(8) { "content://x/$it" })
        assertEquals(SharedPayload.MAX_IMAGES, (img as SharedPayload.Images).uris.size)
        assertEquals("caption", img.caption)
        assertNull(SharedPayload.from(" ", null))
    }

    @Test fun markdownFileIsFlattened() {
        val md = "# Chai\n\n**Serves** 2\n\n## Ingredients\n- [ ] 1 cup [milk](http://x)\n* 2 tsp _sugar_\n\n```\ncode\n```"
        assertTrue(MarkdownText.looksLikeMarkdown("chai.md", md))
        assertEquals("Chai\n\nServes 2\n\nIngredients\n- 1 cup milk\n* 2 tsp sugar\n\ncode", MarkdownText.strip(md))
        val p = SharedPayload.from(null, null, listOf("text/markdown" to md)) as SharedPayload.Text
        assertFalse(p.text.contains("#"))
    }

    // ---- Grocery auto-sync & list ----
    private fun gi(id: Long, name: String, q: Double?, unit: String = "g", recipe: Long? = 1, src: String = GrocerySource.PLAN,
                   checked: Boolean = false, home: Boolean = false, at: Long = 0) =
        GroceryItem(id = id, name = name, quantity = q, unit = unit, recipeId = recipe, recipeTitle = recipe?.let { "R$it" },
            checked = checked, source = src, haveAtHome = home, createdAt = at)

    @Test fun planSyncIsNoOpWhenUnchangedAndKeepsTicks() {
        val existing = listOf(gi(1, "onion", 200.0, checked = true, at = 5))
        assertNull(PlanGrocerySync.reconcile(existing, listOf(gi(0, "Onion", 200.0))))
        val next = PlanGrocerySync.reconcile(existing, listOf(gi(0, "onion", 100.0), gi(0, "onion", 200.0), gi(0, "salt", null, unit = "")), now = 9)!!
        val onion = next.first { it.name == "onion" }
        assertEquals(300.0, onion.quantity!!, 0.0)
        assertTrue(onion.checked); assertEquals(5L, onion.createdAt)
        assertEquals(9L, next.first { it.name == "salt" }.createdAt)
        assertTrue(next.all { it.source == GrocerySource.PLAN && it.id == 0L })
    }

    @Test fun legacyDuplicatesAndPantryStaples() {
        val legacy = listOf(gi(7, "onion", 1.0, src = GrocerySource.RECIPE), gi(8, "milk", 1.0, src = GrocerySource.RECIPE, recipe = 2))
        assertEquals(listOf(7L), PlanGrocerySync.legacyDuplicates(legacy, listOf(gi(0, "onion", 2.0))))
        assertTrue(PantryStaples.isStaple("Salt")); assertTrue(PantryStaples.isStaple("haldi (turmeric) powder"))
        assertFalse(PantryStaples.isStaple("Paneer")); assertFalse(PantryStaples.isStaple("Sugar snap peas".replace("Sugar", "Snow")))
    }

    @Test fun groceryListSectionsSortsAndShareText() {
        val items = listOf(
            gi(1, "onion", 200.0, at = 1), gi(2, "onion", 100.0, at = 3), gi(3, "salt", null, unit = "", home = true),
            gi(4, "milk", 1.0, unit = "l", recipe = null, src = GrocerySource.MANUAL, at = 9),
            gi(5, "rice", 1.0, unit = "kg", checked = true, src = GrocerySource.RECIPE),
        )
        val s = GroceryListBuilder.build(items, GrocerySort.RECENT, GroceryFilter.ALL, UnitSystem.METRIC)
        assertEquals(listOf("Milk", "Onion"), s.sections.single().rows.map { it.name })
        assertEquals(2, s.remaining)
        assertEquals(listOf("Salt"), s.haveAtHome.map { it.name })
        assertEquals(listOf("Rice"), s.checked.map { it.name })
        assertTrue(s.shareText.contains("Onion — 300 g")); assertFalse(s.shareText.contains("Salt")); assertFalse(s.shareText.contains("Rice"))
        val manual = GroceryListBuilder.build(items, GrocerySort.AZ, GroceryFilter.MANUAL, UnitSystem.METRIC)
        assertEquals(listOf("Milk"), manual.sections.flatMap { it.rows }.map { it.name })
        val byRecipe = GroceryListBuilder.build(items, GrocerySort.RECIPE, GroceryFilter.ALL, UnitSystem.METRIC)
        assertEquals(GroceryListBuilder.ADDED_BY_YOU, byRecipe.sections.last().title)
        assertEquals(listOf(1L), byRecipe.recipes.filter { it.fromPlan }.map { it.id })
    }

    // ---- PDF ----
    @Test fun pdfBlocksAndPagination() {
        val b = RecipePdf.blocks("Chole", "Punjabi chickpeas", listOf("Serves 4", "", "40 min"), listOf("1 cup chana"), listOf("Soak", "Cook"), null)
        assertEquals(RecipePdf.Style.TITLE, b.first().style)
        assertEquals("Serves 4  ·  40 min", b.first { it.style == RecipePdf.Style.META }.text)
        assertEquals(listOf("1.", "2."), b.filter { it.style == RecipePdf.Style.STEP }.map { it.marker })
        assertEquals("Exported from DesiKitchen", b.last().text)
        // heading (index 2) must move to page 2 together with block 3
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), RecipePdf.paginate(listOf(40, 40, 15, 30, 500), 100, setOf(2)))
        assertEquals(listOf(listOf(0), listOf(1)), RecipePdf.paginate(listOf(300, 10), 100))
        assertEquals("Aloo-Gobi-Mom-s.pdf", RecipePdf.fileName("Aloo Gobi (Mom-s)!"))
        assertEquals("recipe.pdf", RecipePdf.fileName("🍛"))
    }

    // ---- Search ----
    @Test fun searchMatchesTitleTagsAndIngredients() {
        val r = Recipe(id = 1, title = "Matar Paneer", cuisine = "North Indian", dietTags = "Vegetarian")
        assertTrue(RecipeSearch.matches(r, "paneer"))
        assertTrue(RecipeSearch.matches(r, "veg north"))
        assertFalse(RecipeSearch.matches(r, "peas"))
        assertTrue(RecipeSearch.matches(r, "peas paneer", listOf("green peas", "paneer")))
        val dal = Recipe(id = 2, title = "Dal", description = "with paneer garnish")
        assertEquals(listOf(1L, 2L), RecipeSearch.rank(listOf(dal, r), "paneer").map { it.id })
    }

    // ---- Ask DesiKitchen (OpenRouter) ----
    private val details = RecipeWithDetails(
        Recipe(id = 1, title = "Jeera Rice", servings = 2),
        listOf(Ingredient(name = "basmati rice", quantity = 1.0, unit = "cup"), Ingredient(position = 1, name = "jeera", quantity = 1.0, unit = "tsp")),
        listOf(Step(text = "Rinse rice"), Step(position = 1, text = "Temper jeera in ghee")),
    )

    @Test fun aiPromptIsRecipeScopedAndScaled() {
        val prompt = RecipeAiAssistant.systemPrompt(details, servings = 4, step = 1)
        assertTrue(prompt, prompt.contains("Jeera Rice")); assertTrue(prompt.contains("2 cup")); assertTrue(prompt.contains("Temper jeera"))
        val history = List(10) { RecipeAiAssistant.Turn("q$it", "a$it", true) }
        val msgs = RecipeAiAssistant.messages(details, "Can I use brown rice?", history, 2, null)
        assertEquals("system", msgs.first().role)
        assertEquals("Can I use brown rice?", msgs.last().content)
        assertEquals(1 + 12 + 1, msgs.size) // last 6 turns only
    }

    @Test fun aiAnswersThenFallsBackOffline() {
        var sentBody = ""
        val ok = OpenRouterClient("test-key", "m", transport = { _, _, body ->
            sentBody = body
            200 to JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", "Yes, soak it 30 min.")))).toString()
        })
        val a = RecipeAiAssistant(ok).ask(details, "Brown rice?", emptyList(), 2)
        assertTrue(a.ai); assertEquals("Yes, soak it 30 min.", a.text)
        assertTrue(sentBody.contains("Jeera Rice")); assertFalse(sentBody.contains("test-key"))
        val down = OpenRouterClient("test-key", "m", transport = { _, _, _ -> 429 to "{}" })
        val b = RecipeAiAssistant(down).ask(details, "Brown rice?", emptyList(), 2)
        assertFalse(b.ai); assertNotNull(b.note); assertTrue(b.text.isNotBlank()); assertFalse(b.note!!.contains("test-key"))
        val offline = RecipeAiAssistant(null).ask(details, "How long?", emptyList(), 2)
        assertFalse(offline.ai); assertNull(offline.note)
    }
}
