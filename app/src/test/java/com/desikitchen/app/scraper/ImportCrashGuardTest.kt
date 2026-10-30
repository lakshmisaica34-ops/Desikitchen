package com.desikitchen.app.scraper

import com.desikitchen.app.util.CreatorsApiClient
import com.desikitchen.app.util.InstagramImporter
import com.desikitchen.app.util.SocialCreatorsClient
import com.desikitchen.app.util.SocialRecipeImporter
import com.desikitchen.app.util.WebRecipeFetcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.1.1 crash fix: an import must end in Result.Failure, never an exception that closes the app. */
class ImportCrashGuardTest {
    private val ig = WebRecipeFetcher.instagramImport
    private val social = WebRecipeFetcher.socialImport

    @After fun restore() {
        WebRecipeFetcher.instagramImport = ig
        WebRecipeFetcher.socialImport = social
    }

    @Test fun fetchNeverThrowsWhenInstagramPathThrows() = runBlocking {
        WebRecipeFetcher.instagramImport = { throw IllegalStateException("boom inside IG path") }
        val r = WebRecipeFetcher.fetch("https://www.instagram.com/reel/Ddk4k1PoM-k/?xtok=abc")
        assertTrue(r is WebRecipeFetcher.Result.Failure)
        assertTrue((r as WebRecipeFetcher.Result.Failure).message.contains("Paste import"))
    }

    @Test fun fetchNeverThrowsOnOutOfMemoryFromVideo() = runBlocking {
        WebRecipeFetcher.instagramImport = { throw OutOfMemoryError("Failed to allocate 74 MB") }
        val r = WebRecipeFetcher.fetch("https://www.instagram.com/reel/Ddk4k1PoM-k/")
        r as WebRecipeFetcher.Result.Failure
        assertTrue(r.message.contains("too large"))
    }

    @Test fun fetchNeverThrowsWhenSocialPathThrows() = runBlocking {
        WebRecipeFetcher.socialImport = { throw org.json.JSONException("Value <html> cannot be converted") }
        val r = WebRecipeFetcher.fetch("https://www.youtube.com/shorts/ETGWelKhgfc")
        assertTrue(r is WebRecipeFetcher.Result.Failure)
    }

    @Test fun creatorsNotFoundJsonIsCleanError() {
        val body = """{"success":false,"credits_remaining":850,"credits_charged":0,"error":"not_found","errorStatus":404,"message":"Post not found"}"""
        val msg = CreatorsApiClient.errorOf(404, body)!!
        assertTrue(msg.contains("not found"))
        // Same body with HTTP 200 (success:false) is still an error.
        assertNotNull(CreatorsApiClient.errorOf(200, body))
        // parse() never throws on error / garbage bodies.
        val m = CreatorsApiClient.parse(body)
        assertNull(m.videoUrl); assertNull(m.caption)
        val g = CreatorsApiClient.parse("<html>502 Bad Gateway</html>")
        assertNull(g.videoUrl)
        assertNotNull(CreatorsApiClient.errorOf(502, "<html>502 Bad Gateway</html>"))
        assertNotNull(CreatorsApiClient.errorOf(200, "not json"))
        assertNull(CreatorsApiClient.errorOf(200, """{"success":true,"data":{}}"""))
        assertEquals("credits", CreatorsApiClient.errorOf(402, "{}")!!.let { if (it.contains("credits")) "credits" else it })
    }

    @Test fun creatorsFetchOnUnreachableHostIsOutcomeError() {
        val o = CreatorsApiClient("http://127.0.0.1:9/v1", "k").fetch("https://www.instagram.com/reel/x/")
        assertTrue(o is CreatorsApiClient.Outcome.Error)
    }

    /** 1.1.2: Sai's rule — no Scrape Creators transcript endpoint anywhere (IG or social). */
    @Test fun noCreatorsTranscriptCodePath() {
        for (c in listOf(CreatorsApiClient::class.java, SocialCreatorsClient::class.java, SocialCreatorsClient.Companion::class.java, CreatorsApiClient.Companion::class.java)) {
            assertTrue("${c.simpleName} has a transcript method", c.declaredMethods.none { it.name.contains("transcript", ignoreCase = true) })
        }
        assertEquals("https://api.example.com/v1/instagram/post?url=https%3A%2F%2Fwww.instagram.com%2Freel%2Fx%2F",
            CreatorsApiClient("https://api.example.com/v1/", "").request("https://www.instagram.com/reel/x/"))
    }

    @Test fun recipeLikeCaptionGate() {
        assertFalse(InstagramImporter.looksLikeRecipeText("Mere sth sth ghrwale bhi chatore ho gye h😏 Follow- @_small_home_kitchen_ #trending #reels #food"))
        assertTrue(InstagramImporter.looksLikeRecipeText("Soak 1 cup rice for an hour, grind with 2 tbsp curd, add 1/2 tsp salt and rest overnight before steaming"))
    }

    @Test fun descriptionRecipeLink() {
        assertEquals("https://hebbarskitchen.com/sponge-dosa-recipe-curd-dosa-set-dosa/",
            SocialRecipeImporter.recipeLinkIn("Full recipe: https://hebbarskitchen.com/sponge-dosa-recipe-curd-dosa-set-dosa/ \nFollow https://instagram.com/hebbars.kitchen"))
        assertNull(SocialRecipeImporter.recipeLinkIn("Shop https://amzn.to/abc and https://www.instagram.com/x https://youtu.be/q https://x.com/a"))
        assertEquals("https://spicex.com/r", SocialRecipeImporter.recipeLinkIn("see https://spicex.com/r."))
    }
}
