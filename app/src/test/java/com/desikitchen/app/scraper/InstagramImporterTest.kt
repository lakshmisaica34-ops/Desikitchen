package com.desikitchen.app.scraper

import com.desikitchen.app.util.CaptionRecipeParser
import com.desikitchen.app.util.CreatorsApiClient
import com.desikitchen.app.util.InstagramImporter
import com.desikitchen.app.util.RecipeHtmlParser
import com.desikitchen.app.util.VideoRecipeExtractor
import com.desikitchen.app.util.WebRecipeFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/** Instagram reels: public embed caption → recipe; no caption recipe → honest partial + paste fallback. */
class InstagramImporterTest {

    private fun fixture(name: String): String {
        val f = listOf(File("src/test/resources/scraper/$name.html.gz"), File("app/src/test/resources/scraper/$name.html.gz")).first { it.exists() }
        return GZIPInputStream(f.inputStream()).bufferedReader().readText()
    }

    @Test fun shortcodes() {
        assertEquals("reel" to "DF6dMW5vwgw", InstagramImporter.shortcode("https://www.instagram.com/reel/DF6dMW5vwgw/?igsh=abc"))
        assertEquals("p" to "CcOkDyrpHkD", InstagramImporter.shortcode("https://instagram.com/healthygirlkitchen/p/CcOkDyrpHkD/"))
        assertEquals("reel" to "Abc_12-xy", InstagramImporter.shortcode("https://www.instagram.com/reels/Abc_12-xy"))
        assertNull(InstagramImporter.shortcode("https://www.instagram.com/healthygirlkitchen/"))
        assertTrue(InstagramImporter.isInstagram("https://www.instagram.com/reel/x/"))
    }

    @Test fun mushroomButterRiceReel() {
        val info = InstagramImporter.parseEmbed(fixture("ig_embed_tiffany_mushroom"))
        assertEquals("brekkyattiffanys", info.author)
        assertTrue(info.imageUrl!!.startsWith("https://"))
        assertNotNull(info.videoUrl)
        val r = InstagramImporter.fromCaption(info, "https://www.instagram.com/reel/DF6dMW5vwgw/")!!
        val p = r.draft.parsed
        println(p)
        assertEquals("Mushroom Butter Rice", p.title)
        assertEquals(8, p.ingredients.size)
        assertEquals(4, p.steps.size)
        assertEquals(RecipeHtmlParser.Completeness.FULL, r.completeness)
        val rice = p.ingredients.first()
        assertEquals(1.0, rice.quantity!!, 0.001); assertEquals("cup", rice.unit); assertEquals("jasmine rice", rice.name)
        assertTrue(p.steps.first().startsWith("wash your rice"))
        assertTrue(p.ingredients.none { it.name.contains("#") } && p.steps.none { it.contains("#") })
    }

    @Test fun onePotCreamyMushroomRiceReel() {
        val info = InstagramImporter.parseEmbed(fixture("ig_embed_healthygirl_mushroom"))
        val r = InstagramImporter.fromCaption(info, "https://www.instagram.com/reel/CcOkDyrpHkD/")!!
        val p = r.draft.parsed
        println(p)
        assertEquals("One-Pot Creamy Mushroom Rice", p.title)
        assertEquals(11, p.ingredients.size)
        assertEquals(5, p.steps.size)
        assertTrue(p.steps.none { it.contains("tag your", true) })
        assertEquals(1.0 / 3, p.ingredients.first { it.name.contains("coconut") }.quantity!!, 0.01)
    }

    @Test fun captionWithoutRecipeGivesPartialAndPasteFallback() {
        val embed = fixture("ig_embed_tiffany_mushroom")
        val start = embed.indexOf("<div class=\"Caption\">")
        val end = embed.indexOf("<div class=\"CaptionComments\">", start)
        val noRac = embed.substring(0, start) +
            "<div class=\"Caption\"><a class=\"CaptionUsername\" href=\"#\">brekkyattiffanys</a><br /><br />mushroom butter rice 🍚 so cozy, recipe on my blog! <a href=\"#\">#ricecooker</a>" +
            embed.substring(end)
        val page = fixture("ig_page_nocaption")
        val r = InstagramImporter.import(
            "https://www.instagram.com/reel/DF6dMW5vwgw/",
            download = { u -> if (u.contains("/embed/")) noRac else page },
            creators = null, video = null,
        )
        assertTrue(r is WebRecipeFetcher.Result.Failure)
        r as WebRecipeFetcher.Result.Failure
        val partial = r.partial!!
        assertEquals("Mushroom Butter Rice", partial.parsed.title)
        assertTrue(partial.parsed.ingredients.isEmpty() && partial.parsed.steps.isEmpty()) // never invented
        assertTrue(partial.imageUrl!!.startsWith("https://"))
        assertNotNull(r.pasteText)
        assertTrue(r.message.contains("Paste import"))
    }

    @Test fun blockedEverywhereStillHelpful() {
        val r = InstagramImporter.import(
            "https://www.instagram.com/reel/DF6dMW5vwgw/",
            download = { throw java.io.IOException("HTTP 429") }, creators = null, video = null,
        )
        r as WebRecipeFetcher.Result.Failure
        assertNull(r.partial)
        assertTrue(r.message.contains("Paste import"))
    }

    @Test fun postPageMetaDescriptionFallback() {
        // When the embed is unavailable, the reel page's meta description can still carry the caption.
        val info = InstagramImporter.parsePostPage(fixture("ig_page_nocaption"))
        val r = InstagramImporter.fromCaption(info, "https://www.instagram.com/reel/CcOkDyrpHkD/")!!
        assertEquals("One-Pot Creamy Mushroom Rice", r.draft.parsed.title)
        assertTrue(r.draft.parsed.ingredients.size >= 10)
        // A page with only og:image (what some networks get) has no caption → no recipe.
        val bare = InstagramImporter.parsePostPage("<html><head><meta property=\"og:image\" content=\"https://x/y.jpg\"><title>Instagram</title></head></html>")
        assertNull(bare.caption)
        assertNull(InstagramImporter.fromCaption(bare, "https://www.instagram.com/reel/x/"))
    }

    @Test fun ogDescriptionCaption() {
        val html = """<html><head><meta property="og:description" content="1,234 likes, 5 comments - chef on March 3, 2024: &quot;Jeera rice ✨
Ingredients:
1 cup basmati rice
1 tsp cumin seeds
2 tbsp ghee
Method:
1. Temper cumin in ghee.
2. Add rice and 2 cups water, cook 12 min.
#jeerarice&quot;." /><meta property="og:image" content="https://x/y.jpg" /></head></html>"""
        val info = InstagramImporter.parsePostPage(html)
        val r = InstagramImporter.fromCaption(info, "https://www.instagram.com/p/abcde/")!!
        assertEquals("Jeera rice", r.draft.parsed.title)
        assertEquals(3, r.draft.parsed.ingredients.size)
        assertEquals(2, r.draft.parsed.steps.size)
    }

    @Test fun captionShapes() {
        // Emoji bullets, inline header, sub-group labels, outro lines.
        val c = CaptionRecipeParser.parse("""
            Paneer Bhurji in 10 mins 🔥 Save this!
            ✅ Ingredients:
            🔸 200g paneer, crumbled
            🔸 1 onion, chopped
            For tempering:
            🔸 1 tsp jeera
            👉 Method:
            1️⃣ Heat oil, add jeera.
            2️⃣ Add onion and saute.
            3️⃣ Add paneer and masala, cook 3 min.
            Follow @desichef for more!
            #paneer #bhurji
        """.trimIndent()).parsed
        assertEquals("Paneer Bhurji in 10 mins", c.title)
        assertEquals(listOf("paneer, crumbled", "onion, chopped", "jeera"), c.ingredients.map { it.name })
        assertEquals(3, c.steps.size)
        // No recipe at all → nothing invented.
        val none = CaptionRecipeParser.parse("Sunday vibes ☀️ who wants some? #foodie")
        assertTrue(!none.hasRecipe && none.parsed.ingredients.isEmpty() && none.parsed.steps.isEmpty())
        // Method-only prose caption → steps, flagged incomplete.
        val prose = CaptionRecipeParser.parse("Mushroom rice\nRecipe: Heat up 3 tbsp butter in a pan, add sliced mushrooms and fry. Add 1 cup rice and 2 cups water and cook for 15 min.")
        assertTrue(prose.hasRecipe && !prose.isComplete)
    }

    /** Reel DeH0TduS8lG (apple jam): "👩‍🍳 Quick Steps:" used to be missed → 9 ingredients / 0 steps. */
    @Test fun appleJamQuickStepsReel() {
        val info = InstagramImporter.parseEmbed(fixture("ig_embed_tastiva_applejam"))
        val r = InstagramImporter.fromCaption(info, "https://www.instagram.com/reel/DeH0TduS8lG/")!!
        val p = r.draft.parsed
        assertEquals(RecipeHtmlParser.Completeness.FULL, r.completeness)
        assertEquals(5, p.ingredients.size)
        assertEquals(4, p.steps.size)
        assertTrue(p.ingredients.none { it.name.contains("lemon juice daalkar", ignoreCase = true) })
        assertTrue(p.steps.first().startsWith("Pot me chopped apples"))
        assertTrue(p.steps.last().startsWith("Clean glass jar"))
        assertTrue(p.steps.none { it.contains("Save this reel") || it.contains("comment karke") })
    }

    @Test fun qualifiedStepHeadings() {
        for (h in listOf("👩‍🍳 Quick Steps:", "Easy Method", "Simple Steps:", "Full Recipe:", "Step-by-step method:", "Detailed Instructions -", "Quick Directions")) {
            val r = CaptionRecipeParser.parse("Masala chai\nIngredients:\n1 cup water\n1 cup milk\n2 tsp tea\n$h\nBoil water with tea.\nAdd milk and simmer.\n#chai")
            assertEquals(h, 3, r.parsed.ingredients.size)
            assertEquals(h, listOf("Boil water with tea.", "Add milk and simmer."), r.parsed.steps)
            assertTrue(h, r.isComplete)
        }
        // A qualifier is not a heading on its own, and "Full recipe below 👇" is still not a method heading.
        val teaser = CaptionRecipeParser.parse("Masala chai full recipe below 👇\nFull recipe below 👇\nIngredients:\n1 cup water\n1 cup milk\nQuick tip: use fresh ginger")
        assertTrue(teaser.parsed.steps.isEmpty())
    }

    @Test fun creatorsApiShapes() {
        val a = CreatorsApiClient.parse("""{"video_url":"https://v/1.mp4","caption":"hi","thumbnail_url":"https://t"}""")
        assertEquals("https://v/1.mp4", a.videoUrl); assertEquals("hi", a.caption)
        val b = CreatorsApiClient.parse("""{"data":{"video_versions":[{"url":"https://v/2.mp4"}],"caption":{"text":"cap"}}}""")
        assertEquals("https://v/2.mp4", b.videoUrl); assertEquals("cap", b.caption)
        assertEquals("https://api.example.com/v1/instagram/post?url=https%3A%2F%2Fwww.instagram.com%2Freel%2Fx%2F",
            CreatorsApiClient("https://api.example.com/v1/", "").request("https://www.instagram.com/reel/x/"))
    }

    @Test fun creatorsApiScrapeCreatorsShape() {
        // Minimal fixture mimicking the Scrape Creators /instagram/post docs sample (no real data / keys).
        val m = CreatorsApiClient.parse("""
            {"success":true,"data":{"xdt_shortcode_media":{
              "__typename":"XDTGraphVideo","shortcode":"ABC123","is_video":true,
              "video_url":"https://scontent.cdninstagram.com/v/reel.mp4",
              "display_url":"https://scontent.cdninstagram.com/v/display.jpg",
              "thumbnail_src":"https://scontent.cdninstagram.com/v/thumb.jpg",
              "owner":{"id":"1","username":"desi_chef"},
              "edge_media_to_caption":{"edges":[{"node":{"text":"Dal tadka\nIngredients:\n1 cup toor dal"}}]}
            }}}
        """.trimIndent())
        assertEquals("https://scontent.cdninstagram.com/v/reel.mp4", m.videoUrl)
        assertEquals("Dal tadka\nIngredients:\n1 cup toor dal", m.caption)
        assertEquals("https://scontent.cdninstagram.com/v/thumb.jpg", m.thumbnailUrl)
        assertEquals("desi_chef", m.author)
        // display_url only, empty caption edges → thumbnail falls back, caption null.
        val d = CreatorsApiClient.parse("""{"data":{"xdt_shortcode_media":{"display_url":"https://d.jpg","owner":{"username":"u"},"edge_media_to_caption":{"edges":[]}}}}""")
        assertEquals("https://d.jpg", d.thumbnailUrl); assertNull(d.caption); assertNull(d.videoUrl); assertEquals("u", d.author)
        // Flat shape still works, string owner still accepted.
        val f = CreatorsApiClient.parse("""{"videoUrl":"https://v/3.mp4","caption":"c","owner":"o"}""")
        assertEquals("https://v/3.mp4", f.videoUrl); assertEquals("c", f.caption); assertEquals("o", f.author)
    }

    @Test fun creatorsCommentsOwnerOnly() {
        val m = CreatorsApiClient.parse("""
            {"data":{"xdt_shortcode_media":{"owner":{"username":"chef"},
              "edge_media_to_parent_comment":{"edges":[
                {"node":{"text":"yum 😍","owner":{"username":"fan"}}},
                {"node":{"text":"Ingredients:\n1 cup rice\n2 cups water\nMethod:\nBoil the water and cook the rice for 12 minutes.","owner":{"username":"chef"}}}]},
              "edge_media_preview_comment":{"edges":[{"node":{"text":"Ingredients:\n1 kg sugar\n2 eggs","owner":{"username":"stranger"}}}]}
            }}}
        """.trimIndent())
        assertEquals(1, m.comments.size)
        assertTrue(m.comments[0].contains("1 cup rice"))
    }

    @Test fun videoModelJson() {
        assertNull(VideoRecipeExtractor.parseModelJson("""{"title":null,"ingredients":[],"steps":[]}"""))
        val x = VideoRecipeExtractor.parseModelJson("```json\n{\"title\":\"Dal\",\"ingredients\":[\"1 cup toor dal\",3],\"steps\":[\"Boil\"]}\n```")!!
        assertEquals(listOf("1 cup toor dal"), x.ingredients)
        assertEquals("Dal", x.title)
    }
}
