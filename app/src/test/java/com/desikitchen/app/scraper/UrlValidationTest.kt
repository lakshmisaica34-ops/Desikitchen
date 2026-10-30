package com.desikitchen.app.scraper

import com.desikitchen.app.util.WebRecipeFetcher
import com.desikitchen.app.util.ImportUrlValidator
import com.desikitchen.app.util.ImportUrlValidator.UrlCheck
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.1.5: a link that clearly isn't a recipe/IG URL gets a clear message before any importer runs. */
class UrlValidationTest {

    private fun ok(raw: String) = (WebRecipeFetcher.validate(raw) as UrlCheck.Ok).url
    private fun invalid(raw: String) = (WebRecipeFetcher.validate(raw) as UrlCheck.Invalid).message

    @Test fun acceptsRealLinks() {
        // A full share link whose visible tail is "k=MWpoODZ3bHgzdHF4ag==" is a valid reel link.
        assertEquals("https://www.instagram.com/reel/Ddk4k1PoM-k/?igsh=MWpoODZ3bHgzdHF4ag==", ok("https://www.instagram.com/reel/Ddk4k1PoM-k/?igsh=MWpoODZ3bHgzdHF4ag=="))
        assertEquals("https://www.instagram.com/reel/DF6dMW5vwgw/?igsh=abc", ok("https://www.instagram.com/reel/DF6dMW5vwgw/?igsh=abc"))
        assertEquals("https://www.instagram.com/share/reel/BAxyz123/", ok("Check this out https://www.instagram.com/share/reel/BAxyz123/"))
        assertEquals("https://hebbarskitchen.com/x/", ok("http://hebbarskitchen.com/x/"))
        assertEquals("https://hebbarskitchen.com/x", ok("hebbarskitchen.com/x"))
        assertEquals("https://www.youtube.com/shorts/abc", ok("  https://www.youtube.com/shorts/abc  "))
        assertEquals("https://m.example.co.in/recipe?id=3", ok("m.example.co.in/recipe?id=3"))
    }

    @Test fun rejectsFragmentsAndWords() {
        // The exact text seen in the 1.1.4 bug report's URL field.
        assertEquals(ImportUrlValidator.NOT_A_LINK, invalid("G/?srtk=amp0d3ZkZWVudmNr"))
        // …and the 1.1.5 report's field (the tail of an "?igsh=" share link).
        assertEquals(ImportUrlValidator.NOT_A_LINK, invalid("k=MWpoODZ3bHgzdHF4ag=="))
        assertEquals(ImportUrlValidator.NOT_A_LINK, invalid("igsh=MWpoODZ3bHgzdHF4ag=="))
        assertEquals(ImportUrlValidator.NOT_A_LINK, invalid("paneer butter masala"))
        assertEquals(ImportUrlValidator.NOT_A_LINK, invalid("srtk=amp0d3.ZkZWVudmNr/x y"))
        assertEquals(ImportUrlValidator.NOT_A_LINK, invalid("https://localhost/recipe"))
        assertEquals(ImportUrlValidator.NOT_A_LINK, invalid("https://"))
        assertTrue(invalid("   ").isNotBlank())
    }

    @Test fun rejectsInstagramLinksThatArentPosts() {
        assertEquals(ImportUrlValidator.NOT_AN_IG_POST, invalid("https://www.instagram.com/G/?srtk=amp0d3ZkZWVudmNr"))
        assertEquals(ImportUrlValidator.NOT_AN_IG_POST, invalid("instagram.com/healthygirlkitchen/"))
    }

    @Test fun invalidLinkNeverReachesTheCascade() {
        val saved = WebRecipeFetcher.instagramImport
        var called = false
        try {
            WebRecipeFetcher.instagramImport = { _ -> called = true; error("must not run") }
            for (bad in listOf("G/?srtk=amp0d3ZkZWVudmNr", "k=MWpoODZ3bHgzdHF4ag==", "https://www.instagram.com/G/?srtk=amp0d3ZkZWVudmNr")) {
                val r = runBlocking { WebRecipeFetcher.fetch(bad) }
                assertTrue(r is WebRecipeFetcher.Result.Failure)
            }
            assertFalse(called)
        } finally { WebRecipeFetcher.instagramImport = saved }
    }

    /** The validator must not load or initialise any parser/importer class (so a broken parser can't break it). */
    @Test fun validatorLoadsNoParserClasses() {
        val urls = (System.getProperty("java.class.path").split(java.io.File.pathSeparator)).map { java.io.File(it).toURI().toURL() }
        class Isolated : java.net.URLClassLoader(urls.toTypedArray(), ClassLoader.getSystemClassLoader().parent) {
            fun loaded(name: String) = findLoadedClass(name) != null
        }
        val cl = Isolated()
        val v = Class.forName("com.desikitchen.app.util.ImportUrlValidator", true, cl)
        val inst = v.getField("INSTANCE").get(null)
        val validate = v.getMethod("validate", String::class.java)
        for (u in listOf("k=MWpoODZ3bHgzdHF4ag==", "G/?srtk=amp0d3ZkZWVudmNr", "https://www.instagram.com/reel/Ddk4k1PoM-k/?igsh=MWpoODZ3bHgzdHF4ag==", "hebbarskitchen.com/x")) {
            validate.invoke(inst, u)
        }
        for (c in listOf("CaptionRecipeParser", "InstagramImporter", "WebRecipeFetcher", "RecipeTextParser", "RecipeHtmlParser", "SocialRecipeImporter", "ImportParsing")) {
            assertFalse("$c was loaded by validation", cl.loaded("com.desikitchen.app.util.$c"))
        }
        cl.close()
    }
}
