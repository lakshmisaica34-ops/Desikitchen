package com.desikitchen.app.scraper

import com.desikitchen.app.util.LenientJson
import com.desikitchen.app.util.RecipeHtmlParser
import com.desikitchen.app.util.RecipeTextParser
import com.desikitchen.app.util.WebRecipeFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Offline regression suite: real recipe pages saved (gzipped) under src/test/resources/scraper/.
 * Refresh a fixture with: curl -sL <url> | gzip -9 > app/src/test/resources/scraper/<name>.html.gz
 */
class ScraperFixtureTest {

    private data class Expect(
        val name: String,
        val url: String,
        val titleContains: String,
        val minIngredients: Int,
        val minSteps: Int,
        val image: Boolean = true,
        val servings: Int? = null,
        val prepOrCook: Boolean = true,
        val maxSteps: Int = 40,
    )

    private val cases = listOf(
        Expect("hebbarskitchen", "https://hebbarskitchen.com/paneer-butter-masala-recipe/", "Paneer Butter Masala", 20, 10, servings = 5),
        Expect("indianhealthyrecipes", "https://www.indianhealthyrecipes.com/paneer-butter-masala-restaurant-style/", "Paneer Butter Masala", 15, 6, servings = 4, maxSteps = 12),
        Expect("cookwithmanali", "https://www.cookwithmanali.com/paneer-butter-masala/", "Paneer", 15, 6, servings = 4),
        Expect("ministryofcurry", "https://www.ministryofcurry.com/paneer-butter-masala/", "Paneer", 12, 4),
        Expect("tarladalal", "https://www.tarladalal.com/italian-pasta-with-mushroom-sauce-quick-veg-pasta-1622r", "Mushroom", 6, 5),
        Expect("ranveerbrar", "https://ranveerbrar.com/recipes/paneer-butter-masala/", "Paneer Butter Masala", 10, 8),
        Expect("sanjeevkapoor", "https://www.sanjeevkapoor.com/Recipe/Paneer-Makhani.html", "Paneer", 8, 4, prepOrCook = false),
        Expect("chefkunalkapur", "https://www.chefkunalkapur.com/recipe/paneer-butter-masala/", "Paneer Butter Masala", 8, 3, prepOrCook = false, maxSteps = 6),
        Expect("sharmispassions", "https://www.sharmispassions.com/paneer-butter-masala-recipe/", "Paneer Butter Masala", 15, 8),
        Expect("pipingpotcurry", "https://www.pipingpotcurry.com/instant-pot-dal-tadka/", "Dal", 8, 4),
        Expect("teaforturmeric", "https://www.teaforturmeric.com/chana-masala/", "Chana Masala", 12, 3),
        Expect("myfoodstory", "https://www.myfoodstory.com/dal-makhani/", "Dal Makhani", 15, 8),
        Expect("madhurasrecipe", "https://www.madhurasrecipe.com/paneer-butter-masala/", "Paneer Butter Masala", 10, 10, prepOrCook = false),
        Expect("allrecipes", "https://www.allrecipes.com/recipe/212721/indian-chicken-curry-murgh-kari/", "Chicken Curry", 15, 5, servings = 6, maxSteps = 10),
        Expect("bbcgoodfood", "https://www.bbcgoodfood.com/recipes/chicken-tikka-masala", "tikka masala", 10, 4),
        Expect("simplyrecipes", "https://www.simplyrecipes.com/recipes/chana_masala/", "Chana Masala", 10, 4),
        Expect("bonappetit", "https://www.bonappetit.com/recipe/chana-masala", "Chana Masala", 12, 4, prepOrCook = false),
    )

    private fun fixture(name: String): String {
        val f = listOf(File("src/test/resources/scraper/$name.html.gz"), File("app/src/test/resources/scraper/$name.html.gz")).first { it.exists() }
        return GZIPInputStream(f.inputStream()).bufferedReader().readText()
    }

    @Test fun realPagesParse() {
        val failures = mutableListOf<String>()
        for (c in cases) {
            val r = WebRecipeFetcher.parseHtml(fixture(c.name), c.url)
            if (r !is WebRecipeFetcher.Result.Success) { failures += "${c.name}: $r"; println("FAIL ${c.name}: $r"); continue }
            val d = r.draft
            val p = d.parsed
            val problems = buildList {
                if (!p.title.contains(c.titleContains, ignoreCase = true)) add("title='${p.title}'")
                if (p.ingredients.size < c.minIngredients) add("ingredients=${p.ingredients.size}<${c.minIngredients}")
                if (p.steps.size < c.minSteps) add("steps=${p.steps.size}<${c.minSteps}")
                if (p.steps.size > c.maxSteps) add("steps=${p.steps.size}>${c.maxSteps}")
                val ingLines = p.ingredients.map { it.name.lowercase() }.filter { it.length > 12 }.toSet()
                if (p.steps.any { it.lowercase() in ingLines }) add("ingredient line repeated as a step")
                if (c.image && d.imageUrl?.startsWith("http") != true) add("no image")
                if (c.servings != null && p.servings != c.servings) add("servings=${p.servings}")
                if (c.prepOrCook && d.prepMinutes + d.cookMinutes == 0) add("no times")
                if (p.ingredients.any { it.name.isBlank() || it.name.length > 220 }) add("bad ingredient text")
                if (p.steps.any { it.contains('<') && it.contains('>') && Regex("<[a-z/]").containsMatchIn(it) }) add("html left in steps")
                if (p.ingredients.any { it.name.contains("&#") || it.name.contains("&amp;") }) add("entities left")
            }
            val withUnits = p.ingredients.count { it.unit.isNotEmpty() }
            val line = "${if (problems.isEmpty()) "PASS" else "FAIL"} ${c.name.padEnd(20)} title='${p.title.take(50)}' ing=${p.ingredients.size} (units=$withUnits) steps=${p.steps.size} " +
                "serv=${p.servings} prep=${d.prepMinutes} cook=${d.cookMinutes} img=${d.imageUrl != null} structured=${r.fromStructuredData} ${r.completeness} ${problems.joinToString()}"
            println(line)
            if (problems.isNotEmpty()) failures += line
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    @Test fun cloudflareChallengeGivesHelpfulMessage() {
        val r = WebRecipeFetcher.parseHtml(fixture("cloudflare_challenge"), "https://www.vegrecipesofindia.com/paneer-butter-masala/")
        assertTrue(r is WebRecipeFetcher.Result.Failure)
        assertTrue((r as WebRecipeFetcher.Result.Failure).message.contains("Paste import"))
    }

    @Test fun wprmCardWithoutJsonLdStillParses() {
        val html = fixture("cookwithmanali").replace(Regex("(?is)<script[^>]*ld\\+json[^>]*>.*?</script>"), "")
        val o = RecipeHtmlParser.parse(html, "https://www.cookwithmanali.com/paneer-butter-masala/")!!
        assertEquals(RecipeHtmlParser.Source.RECIPE_CARD, o.ingredientSource)
        assertTrue(o.draft.parsed.ingredients.size >= 15)
        assertTrue(o.draft.parsed.steps.size >= 6)
        assertNotNull(o.draft.imageUrl) // from og:image
    }

    @Test fun microdataRecipe() {
        val html = """<html><head><title>x</title></head><body>
            <div itemscope itemtype="https://schema.org/Recipe">
              <h1 itemprop="name">Jeera Aloo</h1><img itemprop="image" src="/img/jeera.jpg">
              <meta itemprop="prepTime" content="PT10M"><meta itemprop="cookTime" content="PT15M">
              <span itemprop="recipeYield">Serves 3</span>
              <ul><li itemprop="recipeIngredient">3 medium potatoes, boiled</li><li itemprop="recipeIngredient">1 teaspoon cumin seeds</li>
              <li itemprop="recipeIngredient">2 tablespoons oil</li></ul>
              <ol itemprop="recipeInstructions"><li>Heat oil, add cumin.</li><li>Add potatoes and spices.</li></ol>
            </div></body></html>"""
        val o = RecipeHtmlParser.parse(html, "https://example.com/r/jeera-aloo")!!
        val p = o.draft.parsed
        assertEquals("Jeera Aloo", p.title)
        assertEquals(3, p.ingredients.size)
        assertEquals("tsp", p.ingredients[1].unit)
        assertEquals(2, p.steps.size)
        assertEquals(3, p.servings)
        assertEquals(10, o.draft.prepMinutes); assertEquals(15, o.draft.cookMinutes)
        assertEquals("https://example.com/img/jeera.jpg", o.draft.imageUrl)
    }

    @Test fun headingLayoutWithOpenGraph() {
        val html = """<html><head><meta property="og:title" content="Poha | My Blog"><meta property="og:image" content="https://cdn.example.com/poha.jpg"></head>
            <body><nav><ul><li>Home</li><li>Recipes</li></ul></nav><h1>Kanda Poha</h1><p>Long story about mornings…</p>
            <h2>Ingredients</h2><p><strong>For tempering</strong></p><ul><li>2 cups thick poha</li><li>1 tsp mustard seeds</li><li>Salt &amp; sugar to taste</li></ul>
            <h2>Method</h2><ol><li>Rinse poha and drain.</li><li>Temper mustard, add onion.</li><li>Mix in poha, steam 2 minutes.</li></ol>
            <h2>Notes</h2><p>Use thick poha.</p></body></html>"""
        val o = RecipeHtmlParser.parse(html, "https://example.com/poha")!!
        val p = o.draft.parsed
        assertEquals(3, p.ingredients.size)
        assertEquals("Salt & sugar to taste", p.ingredients[2].name)
        assertEquals(3, p.steps.size)
        assertEquals("https://cdn.example.com/poha.jpg", o.draft.imageUrl)
        assertEquals("Kanda Poha", p.title)
        assertEquals(RecipeHtmlParser.Completeness.FULL, o.completeness)
    }

    @Test fun neverInventsMissingFields() {
        val html = """<html><head><script type="application/ld+json">{"@context":"https://schema.org","@type":"Recipe","name":"Mystery Dal",
            "recipeInstructions":[{"@type":"HowToStep","text":"Cook dal."},{"@type":"HowToStep","text":"Temper."}]}</script></head><body></body></html>"""
        val o = RecipeHtmlParser.parse(html, "https://example.com/dal")!!
        assertTrue(o.draft.parsed.ingredients.isEmpty())
        assertEquals(RecipeHtmlParser.Completeness.PARTIAL, o.completeness)
        assertTrue(o.warnings.any { it.contains("ingredient", true) })
        assertNull(RecipeHtmlParser.parse("<html><body><p>Hello</p></body></html>", "https://example.com"))
    }

    @Test fun lenientJsonRepairsRealWorldBlocks() {
        assertNotNull(LenientJson.parse("{\"name\": \"line one\nline two\", \"a\": [1,2,],}"))
        assertNotNull(LenientJson.parse("<!--{\"@type\":\"Recipe\"}-->"))
        assertNotNull(LenientJson.parse("//<![CDATA[\n{\"@type\":\"Recipe\"}\n//]]>"))
        assertNull(LenientJson.parse("not json"))
    }

    @Test fun durations() {
        val t = RecipeHtmlParser.Text
        assertEquals(90, t.isoMinutes("PT1H30M")); assertEquals(90, t.isoMinutes("PT90M"))
        assertEquals(20, t.isoMinutes("P0DT0H20M")); assertEquals(0, t.isoMinutes("PT"))
        assertEquals(1500, t.isoMinutes("P1DT1H")); assertEquals(70, t.isoMinutes("1 hour 10 minutes"))
        assertEquals(4, t.servings("Serves 4-6")); assertNull(t.servings(""))
    }

    @Test fun ingredientLines() {
        fun ing(s: String) = RecipeTextParser.parseIngredient(s)
        ing("2 tablespoons ghee").let { assertEquals(2.0, it.quantity); assertEquals("tbsp", it.unit); assertEquals("ghee", it.name) }
        ing("½ teaspoon turmeric powder").let { assertEquals(0.5, it.quantity); assertEquals("tsp", it.unit) }
        ing("1½ cups basmati rice").let { assertEquals(1.5, it.quantity); assertEquals("cups", it.unit) }
        ing("1 1/2 inches ginger").let { assertEquals(1.5, it.quantity); assertEquals("inch", it.unit) }
        ing("200 grams paneer, cubed").let { assertEquals(200.0, it.quantity); assertEquals("g", it.unit); assertEquals("paneer, cubed", it.name) }
        ing("250 gm Paneer").let { assertEquals("g", it.unit) }
        ing("200g paneer").let { assertEquals(200.0, it.quantity); assertEquals("g", it.unit) }
        ing("7-8 garlic cloves").let { assertEquals(7.0, it.quantity); assertEquals("garlic cloves (7–8)", it.name) }
        ing("3~4 tsp Butter").let { assertEquals(3.0, it.quantity); assertEquals("tsp", it.unit) }
        ing("Onion chopped – 1 cup").let { assertEquals(1.0, it.quantity); assertEquals("cup", it.unit); assertEquals("Onion chopped", it.name) }
        ing("1 tablespoon (15 ml) oil").let { assertEquals("tbsp", it.unit); assertEquals("oil (15 ml)", it.name) }
        ing("Salt to taste").let { assertNull(it.quantity); assertEquals("Salt to taste", it.name) }
        ing("2 pounds chicken").let { assertEquals("lb", it.unit) }
        ing("3 cloves (laung, optional)").let { assertEquals(3.0, it.quantity); assertEquals("", it.unit); assertEquals("cloves (laung, optional)", it.name) }
        ing("1 teaspoon sea salt ( (adjust to taste))").let { assertEquals("tsp", it.unit); assertEquals("sea salt (adjust to taste)", it.name) }
    }

    @Test fun mojibakeIsRepaired() {
        assertEquals("sauté onions – it’s done", RecipeHtmlParser.Text.fixMojibake("sautÃ© onions â€“ itâ€™s done"))
        assertEquals("पनीर sauté", RecipeHtmlParser.Text.fixMojibake("पनीर sauté"))
    }

    @Test fun urlNormalisation() {
        assertEquals("https://example.com/r/dal", WebRecipeFetcher.extractUrl("Try this (https://example.com/r/dal)."))
        assertEquals("https://hebbarskitchen.com/x/", WebRecipeFetcher.normalise("http://hebbarskitchen.com/x/"))
        assertEquals("https://hebbarskitchen.com/x", WebRecipeFetcher.normalise("hebbarskitchen.com/x"))
        assertNull(WebRecipeFetcher.normalise("paneer butter masala"))
    }
}
