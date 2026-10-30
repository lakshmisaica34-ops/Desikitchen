package com.desikitchen.app.scraper

import com.desikitchen.app.util.CaptionRecipeParser
import com.desikitchen.app.util.ImportParsing
import com.desikitchen.app.util.InstagramImporter
import com.desikitchen.app.util.RecipeHtmlParser
import com.desikitchen.app.util.RecipeSanitizer
import com.desikitchen.app.util.RecipeTextParser
import com.desikitchen.app.util.VideoRecipeExtractor
import com.desikitchen.app.util.WebRecipeFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Sai's 2026-10-29 bug (docs/bugs/scraper-2026-10-29): steps + nutrition saved as ingredients (rice-paper mochi),
 * and a salsa reel saved with no steps. Offline fixtures only; nothing here touches the network.
 */
class ScraperMangleTest {

    private fun caption(name: String): String =
        listOf(File("src/test/resources/captions/$name.txt"), File("app/src/test/resources/captions/$name.txt")).first { it.exists() }.readText()

    private val nutritionWords = Regex("(?i)\\b(kcal|calories|protein|carbs|fats?|nutrition)\\b")
    private val methodOpeners = listOf("Dip", "Now", "Add", "Repeat", "Freeze", "Dice", "Finely chop", "Mix", "Spoon", "Place", "Fold")

    private fun line(i: RecipeTextParser.ParsedIngredient) = listOfNotNull(i.quantity?.toString(), i.unit, i.name).joinToString(" ")

    private fun assertNoGarbage(p: RecipeTextParser.ParsedRecipe) {
        p.ingredients.forEach { i ->
            assertFalse("nutrition in ingredients: ${line(i)}", nutritionWords.containsMatchIn(i.name) && !i.name.contains("powder"))
            assertFalse("method line in ingredients: ${line(i)}", methodOpeners.any { i.name.startsWith(it, ignoreCase = true) })
        }
        p.steps.forEach { s -> assertFalse("nutrition in steps: $s", RecipeSanitizer.isNutritionLine(s)) }
    }

    // ---------- Screenshot 1: rice-paper mochi caption with an unheaded method + Nutrition block ----------

    @Test fun mochiCaption_methodGoesToSteps_nutritionDropped() {
        val r = CaptionRecipeParser.parse(caption("rice_paper_mochi_2026-10-29"))
        val p = r.parsed
        assertNoGarbage(p)
        assertEquals(listOf("rice paper sheets", "Greek yogurt", "ripe mango, diced", "honey"), p.ingredients.map { it.name })
        assertEquals(6, p.steps.size)
        // Whole sentences, not truncated, in the creator's order.
        assertEquals("Dip 1 rice paper sheet in warm water for 5 seconds.", p.steps.first())
        assertEquals("Freeze for an hour, and enjoy your mochi!", p.steps.last())
        assertTrue(r.isComplete)
    }

    @Test fun mochiCaption_throughImporter_isFullNotGarbage() {
        val res = InstagramImporter.fromCaption(InstagramImporter.PostInfo(caption("rice_paper_mochi_2026-10-29"), "someone", null, null),
            "https://www.instagram.com/reel/Dc07ThlI0EU/")
        assertNotNull(res)
        assertEquals(RecipeHtmlParser.Completeness.FULL, res!!.completeness)
        assertNoGarbage(res.draft.parsed)
        assertTrue(res.draft.parsed.steps.isNotEmpty())
    }

    @Test fun stepsOnlyUnderIngredientsHeading_movedToSteps_ingredientsLeftEmpty() {
        // Exactly the screenshot's rows: no real ingredient lines, only method + nutrition under "Ingredients".
        val text = """
            Rice paper mochi
            Ingredients:
            Dip 1 rice paper sheet in water for a few seconds.
            Now place it flat on a plate.
            Add 1 tbsp yogurt and some mango in the centre.
            Repeat to make another layer.
            Now fold it nicely and tuck the edges.
            Freeze for an hour, and enjoy!
            Nutrition (per mochi)
            65 kcal Calories
            2 g Protein
            12 g Carbs
            1 g Fats
        """.trimIndent()
        val p = CaptionRecipeParser.parse(text).parsed
        assertEquals(emptyList<RecipeTextParser.ParsedIngredient>(), p.ingredients)
        assertEquals(6, p.steps.size)
        assertNoGarbage(p)
        val res = InstagramImporter.fromCaption(InstagramImporter.PostInfo(text, null, null, null), "https://www.instagram.com/reel/x1234/")!!
        assertTrue("empty ingredients must be PARTIAL + editable", res.draft.partial)
        assertTrue(res.warnings.any { it.contains("no ingredient list") })
        // Paste / OCR path agrees.
        val pasted = ImportParsing.parse(text)!!
        assertTrue(pasted.ingredients.isEmpty()); assertEquals(6, pasted.steps.size)
    }

    @Test fun mochiPaste_plainParserAlsoClean() {
        val p = ImportParsing.parse(caption("rice_paper_mochi_2026-10-29"))!!
        assertNoGarbage(p)
        assertEquals(4, p.ingredients.size)
        assertEquals(6, p.steps.size)
        val plain = RecipeTextParser.parse("Mochi\nIngredients\n4 rice paper sheets\n2 tbsp yogurt\nDip 1 rice paper sheet in water.\nFreeze for an hour.\nNutrition (per mochi)\n65 kcal Calories\n2 g Protein")
        assertEquals(2, plain.ingredients.size); assertEquals(2, plain.steps.size); assertNoGarbage(plain)
    }

    // ---------- Screenshot 2: salsa caption whose method had no heading → steps were empty ----------

    @Test fun salsaCaption_unheadedMethod_yieldsSteps() {
        val r = CaptionRecipeParser.parse(caption("salsa_dish_by_david_2026-10-29"))
        val p = r.parsed
        assertNoGarbage(p)
        assertEquals(6, p.ingredients.size)
        assertEquals(4, p.steps.size)
        assertTrue(p.steps.first().startsWith("Dice the tomatoes"))
        assertTrue(r.isComplete)
        assertEquals("I Haven't Bought a Jar of Salsa in Years".lowercase(), p.title.lowercase())
    }

    @Test fun salsaCaption_emojiStepHeading_andNoHeadingVariants() {
        val emojiHeading = "I haven't bought a jar of salsa in years\nIngredients 👇\n6 Roma tomatoes\n1 white onion\n2 jalapeños\n" +
            "Instructions 👇\nDice everything.\nMix with lime juice and salt.\n#salsa"
        val a = CaptionRecipeParser.parse(emojiHeading).parsed
        assertEquals(3, a.ingredients.size); assertEquals(listOf("Dice everything.", "Mix with lime juice and salt."), a.steps)
        assertFalse(a.ingredients.any { it.name.contains("👇") })

        val noHeadings = "I haven't bought a jar of salsa in years\n6 Roma tomatoes\n1 white onion\n2 jalapeños\n1 tsp salt\n" +
            "Dice the tomatoes, onion and jalapeños.\nMix everything with the salt and some lime juice.\n#salsa"
        val b = CaptionRecipeParser.parse(noHeadings).parsed
        assertEquals(4, b.ingredients.size); assertEquals(2, b.steps.size); assertNoGarbage(b)
    }

    // ---------- AI path: model JSON that put steps + nutrition under "ingredients" ----------

    @Test fun aiJson_screenshotShape_isRepairedHonestly() {
        val json = """{"title":"Rice Paper Mochi","servings":null,
            "ingredients":["Dip 1 rice paper sheet in water for 5 seconds","Now place it flat on a plate","Add 1 tbsp yogurt and mango",
              "Repeat to make another layer","Now fold it nicely and tuck the edges","Freeze for an hour, and enjoy",
              "Nutrition (per mochi)","65 kcal Calories","2 g Protein","12 g Carbs","1 g Fats"],
            "steps":[]}"""
        val x = VideoRecipeExtractor.parseModelJson(json)!!
        assertTrue("no invented ingredients", x.ingredients.isEmpty())
        assertEquals(6, x.steps.size)
        assertEquals("Dip 1 rice paper sheet in water for 5 seconds", x.steps.first())
        assertEquals(5, x.dropped); assertEquals(6, x.movedToSteps)
        val w = RecipeSanitizer.aiWarning(x)!!
        assertTrue(w, w.contains("nutrition") && w.contains("no ingredient list"))
    }

    @Test fun aiJson_withRealSteps_dropsDuplicateMethodAndNutrition() {
        val json = """{"title":"Mochi","ingredients":["4 rice paper sheets","4 tbsp Greek yogurt","Dip 1 rice paper sheet in water","65 kcal Calories","Protein: 2 g"],
            "steps":["Dip 1 rice paper sheet in water.","Fill with yogurt and fold.","Nutrition per mochi: 65 kcal, 2 g protein"]}"""
        val x = VideoRecipeExtractor.parseModelJson(json)!!
        assertEquals(listOf("4 rice paper sheets", "4 tbsp Greek yogurt"), x.ingredients)
        assertEquals(listOf("Dip 1 rice paper sheet in water.", "Fill with yogurt and fold."), x.steps)
        assertEquals(0, x.movedToSteps)
    }

    @Test fun aiJson_onlyNutrition_isNoRecipe() {
        assertEquals(null, VideoRecipeExtractor.parseModelJson("""{"title":"x","ingredients":["65 kcal Calories","2 g Protein"],"steps":[]}"""))
    }

    @Test fun promptsCarryBucketRules() {
        listOf(VideoRecipeExtractor.prompt(null), com.desikitchen.app.util.TextRecipeExtractor.SYSTEM,
            com.desikitchen.app.util.TextRecipeExtractor.COMMENTS_SYSTEM).forEach {
            assertTrue(it.contains("Never put an instruction or sentence in ingredients"))
            assertTrue(it.contains("Nutrition info"))
        }
    }

    // ---------- Live-observed reel (Dc07ThlI0EU): nutrition-only caption must not look like a recipe ----------

    @Test fun halloumiCaption_nutritionIsNotARecipe_andParaphrasedStepsAreUngrounded() {
        val cap = caption("halloumi_live_Dc07ThlI0EU")
        val r = CaptionRecipeParser.parse(cap)
        assertFalse(r.hasRecipe)
        assertTrue(r.parsed.ingredients.isEmpty() && r.parsed.steps.isEmpty())
        assertFalse("macro grams are not recipe quantities", InstagramImporter.looksLikeRecipeText(cap))
        assertFalse(RecipeSanitizer.hasRecipeSignal(cap))
        // What the cheap text model actually returned live on 2026-10-29: steps written from a dish description.
        val invented = listOf("Cook halloumi until crispy", "Mix hot honey with chickpeas", "Prepare a tangy coriander dressing",
            "Assemble the bowls with halloumi, chickpeas, and dressing")
        assertEquals(emptyList<String>(), RecipeSanitizer.groundedSteps(invented, cap))
        // A step that IS written in the text survives.
        assertEquals(listOf("Mix the chickpeas with hot honey"),
            RecipeSanitizer.groundedSteps(listOf("Mix the chickpeas with hot honey"), "Mix the chickpeas with hot honey and serve"))
    }

    // ---------- Food lines that must stay ingredients ----------

    @Test fun realIngredientsAreNotMisclassified() {
        listOf("50 g protein powder", "Cream cheese 200 g", "Top sirloin steak", "Nutritional yeast 2 tbsp", "Sugar - 50 g",
            "Squeeze of lemon", "Store-bought puff pastry 1 sheet", "1 cup rice, washed and soaked for 30 minutes",
            "Salt to taste", "Juice of 2 limes", "2 tbsp butter, melted", "Warm water 1 cup", "Fresh coriander", "12 g sugar",
        ).forEach { assertTrue("should be an ingredient: $it", RecipeSanitizer.isIngredientText(it)) }
        listOf("Nutrition (per mochi)", "65 kcal Calories", "2 g Protein", "12 g Carbs", "1 g Fats", "Calories: 600 kcal/ Fat: 18g/ Carbs: 65g",
            "Protein: 44g", "Macros:", "Nutrition facts per serving",
        ).forEach { assertTrue("should be nutrition: $it", RecipeSanitizer.isNutritionLine(it)) }
        listOf("Dip 1 rice paper sheet in water", "Now place it flat on a plate", "Repeat to make another", "Freeze for an hour, and enjoy",
            "2. Add 1 tbsp yogurt and mango",
        ).forEach { assertTrue("should be a step: $it", RecipeSanitizer.isMethodLine(it)) }
    }

    @Test fun existingFullResultTypeStillSuccess() {
        val res = InstagramImporter.fromCaption(InstagramImporter.PostInfo(caption("salsa_dish_by_david_2026-10-29"), "dish.by.david", null, null),
            "https://www.instagram.com/reel/abcde/")
        assertTrue(res is WebRecipeFetcher.Result.Success)
        assertTrue(res!!.draft.parsed.steps.isNotEmpty())
        assertFalse(res.draft.partial)
    }
}
