package com.desikitchen.app.scraper

import com.desikitchen.app.util.CaptionRecipeParser
import com.desikitchen.app.util.InstagramImporter
import com.desikitchen.app.util.OpenRouterClient
import com.desikitchen.app.util.RecipeSanitizer
import com.desikitchen.app.util.TextRecipeExtractor
import com.desikitchen.app.util.WebRecipeFetcher
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-10-29, Sai's second bad scrape (reel DfEAPhVzLrs): the creator wrote the METHOD under an "Ingredients:" heading
 * plus a nutrition block. Rule parse is not confident → ONE caption AI split (grounded + sanitized) or an honest fallback.
 * No network: the text model is a fake transport.
 */
class CaptionAiSplitTest {
    private val caption = """
        Healthy Fruit Mochi

        Recipe by @naomi_senggg

        Ingredients:
        Dip 1 rice paper sheet in water for 5–10 seconds.
        Now place it flat on a plate.
        Add 1 tbsp yogurt and an equal portion of fruit of your choice. (I used mango for one and blueberries for the other.)
        Repeat to make another layer.
        Now fold it nicely and flip it over.
        Freeze for an hour, and your mochi is ready!

        Nutrition (per mochi)
        Calories - 65 kcal
        Protein - 2g
        Carbs - 12g
        Fats - 1g
    """.trimIndent()

    private fun embed(cap: String) = "<html><div class=\"Caption\"><a class=\"CaptionUsername\">naomi_senggg</a>" +
        cap.lines().joinToString("<br />") + "</div></html>"

    private var answer = ""
    private var calls = 0
    private val bodies = mutableListOf<String>()
    private val text = TextRecipeExtractor(OpenRouterClient("k", "mistralai/mistral-nemo", transport = { _, _, body ->
        calls++; bodies += body
        200 to JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", answer)))).toString()
    }))

    private fun run(cap: String = caption, t: TextRecipeExtractor? = text) = InstagramImporter.import(
        "https://www.instagram.com/reel/DfEAPhVzLrs/?dlrf=Zm04d2lnNTJrYm1i",
        download = { u -> if (u.contains("/embed/")) embed(cap) else "<html></html>" },
        creators = null, video = null, text = t, stills = null,
    )

    private fun assertCleanIngredients(r: WebRecipeFetcher.Result.Success) {
        r.draft.parsed.ingredients.forEach { i ->
            val line = "${i.quantity ?: ""} ${i.unit} ${i.name}".trim()
            assertFalse("nutrition in ingredients: $line", RecipeSanitizer.isNutritionLine(line) || i.name.contains("kcal", true) || i.name.matches(Regex("(?i)protein|carbs|fats?|calories")))
            assertFalse("method in ingredients: $line", RecipeSanitizer.isMethodLine(i.name) || RecipeSanitizer.isMethodLine(line))
        }
        r.draft.parsed.steps.forEach { assertFalse("nutrition in steps: $it", RecipeSanitizer.isNutritionLine(it)) }
    }

    @Test fun ruleParseIsNotConfidentForMislabelledCaption() {
        val r = CaptionRecipeParser.parse(caption)
        assertTrue(r.hasRecipe)
        assertFalse(r.isConfident)
        assertTrue(r.rebucketed > 0)
        assertTrue(r.parsed.ingredients.isEmpty()) // never the method / nutrition as ingredients
        assertEquals(6, r.parsed.steps.size)
    }

    @Test fun mixedCaptionFiresAiSplitAndKeepsOnlyGroundedCleanBuckets() {
        answer = """{"title":"Healthy Fruit Mochi","ingredients":["1 rice paper sheet","1 tbsp yogurt","mango","blueberries","2 tbsp honey",
            "65 kcal Calories","Now place it flat on a plate."],
            "steps":["Dip 1 rice paper sheet in water for 5–10 seconds.","Now place it flat on a plate.",
            "Add 1 tbsp yogurt and an equal portion of fruit of your choice.","Repeat to make another layer.",
            "Now fold it nicely and flip it over.","Freeze for an hour, and your mochi is ready!","Protein - 2g"]}"""
        val r = run() as WebRecipeFetcher.Result.Success
        val t = InstagramImporter.lastTrace!!
        assertEquals("1b_caption_ai_split", t.winner)
        assertEquals(1, calls); assertEquals(1, t.captionSplitCalls); assertEquals(1, t.openRouterCalls)
        assertTrue(bodies.single().contains("headings may be WRONG"))
        val names = r.draft.parsed.ingredients.map { it.name.lowercase() }
        assertEquals(4, names.size)                       // honey invented → dropped; kcal + method → dropped
        assertTrue(names.any { it.contains("yogurt") } && names.any { it.contains("rice paper") })
        assertFalse(names.any { it.contains("honey") })
        assertEquals(6, r.draft.parsed.steps.size)
        assertEquals("Healthy Fruit Mochi", r.draft.parsed.title)
        assertTrue(r.draft.partial)
        assertEquals("PARTIAL", r.completeness.name)
        assertCleanIngredients(r)
    }

    @Test fun emptyAiSplitFallsThroughToHonestCaptionDraft() {
        answer = """{"title":null,"ingredients":["65 kcal Calories","2 g Protein"],"steps":[]}"""
        val r = run() as WebRecipeFetcher.Result.Success
        val t = InstagramImporter.lastTrace!!
        assertEquals(1, calls)
        assertEquals("1_public_html", t.winner)           // fallback after every later stage had its chance
        assertTrue(r.draft.parsed.ingredients.isEmpty())  // empty + editable beats garbage
        assertEquals(6, r.draft.parsed.steps.size)
        assertCleanIngredients(r)
    }

    @Test fun confidentCaptionNeverCallsAi() {
        val ok = "Paneer Bhurji\nIngredients:\n200 g paneer\n1 onion, chopped\n1 tsp jeera\nMethod:\n1. Heat oil and add jeera.\n2. Add onion and paneer, cook 5 min."
        assertTrue(CaptionRecipeParser.parse(ok).isConfident)
        val r = run(ok) as WebRecipeFetcher.Result.Success
        assertEquals(0, calls)
        assertEquals("1_public_html", InstagramImporter.lastTrace!!.winner)
        assertEquals(3, r.draft.parsed.ingredients.size)
    }

    @Test fun withoutAnyAiTheCleanRuleParseStillWins() {
        val r = run(t = null) as WebRecipeFetcher.Result.Success
        assertEquals("1_public_html", InstagramImporter.lastTrace!!.winner)
        assertTrue(r.draft.parsed.ingredients.isEmpty())
        assertCleanIngredients(r)
    }
}
