package com.desikitchen.app.scraper

import com.desikitchen.app.util.OpenRouterClient
import com.desikitchen.app.util.SocialCreatorsClient
import com.desikitchen.app.util.SocialRecipeImporter
import com.desikitchen.app.util.SocialRecipeImporter.Platform
import com.desikitchen.app.util.TextRecipeExtractor
import com.desikitchen.app.util.WebRecipeFetcher
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** TikTok / YouTube / Pinterest / Facebook: public data → caption parse → creators → AI → honest failure. Offline. */
class SocialRecipeImporterTest {

    private val caption = """
        Paneer butter masala in 20 mins 🧈 full recipe below
        Ingredients:
        - 200 g paneer
        - 2 tbsp butter
        - 1 cup tomato puree
        Method:
        1. Melt butter and cook the puree for 5 minutes.
        2. Add paneer and simmer.
        #paneer #recipe
    """.trimIndent()

    private val noDeps = SocialRecipeImporter.Deps(
        download = { u, _ -> error("unexpected $u") }, web = { WebRecipeFetcher.Result.Failure("no") },
        creators = null, text = null, video = null,
    )

    @Test fun platforms() {
        assertEquals(Platform.TIKTOK, SocialRecipeImporter.platformOf("https://www.tiktok.com/@chef/video/123"))
        assertEquals(Platform.TIKTOK, SocialRecipeImporter.platformOf("https://vm.tiktok.com/ZMabc/"))
        assertEquals(Platform.YOUTUBE, SocialRecipeImporter.platformOf("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(Platform.YOUTUBE, SocialRecipeImporter.platformOf("https://m.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals(Platform.PINTEREST, SocialRecipeImporter.platformOf("https://in.pinterest.com/pin/123/"))
        assertEquals(Platform.PINTEREST, SocialRecipeImporter.platformOf("https://pin.it/abc"))
        assertEquals(Platform.FACEBOOK, SocialRecipeImporter.platformOf("https://fb.watch/xyz/"))
        assertNull(SocialRecipeImporter.platformOf("https://www.hebbarskitchen.com/x/"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", SocialRecipeImporter.canonical(Platform.YOUTUBE, "https://youtube.com/shorts/dQw4w9WgXcQ?si=1"))
    }

    @Test fun tiktokOEmbedCaptionBecomesRecipe() {
        val oembed = JSONObject().put("title", caption).put("author_unique_id", "chef").put("thumbnail_url", "https://p16.example/t.jpg").toString()
        val r = SocialRecipeImporter.import("https://www.tiktok.com/@chef/video/7300000000000000001",
            noDeps.copy(download = { u, _ -> assertTrue(u.startsWith("https://www.tiktok.com/oembed?url=")); WebRecipeFetcher.Page(oembed, u) }))
        r as WebRecipeFetcher.Result.Success
        assertEquals(3, r.draft.parsed.ingredients.size)
        assertEquals(2, r.draft.parsed.steps.size)
        assertEquals("By @chef on TikTok", r.draft.description)
        assertEquals("https://p16.example/t.jpg", r.draft.imageUrl)
    }

    @Test fun youtubeDescriptionFromWatchPage() {
        val desc = JSONObject.quote(caption).removeSurrounding("\"")
        val watch = """<html><head><meta property="og:title" content="og"></head><script>var ytInitialPlayerResponse = {"videoDetails":{"videoId":"abcdefghijk","title":"Paneer Butter Masala","shortDescription":"$desc","isLive":false}};</script></html>"""
        val post = SocialRecipeImporter.parseYoutubeWatch(watch)
        assertEquals("Paneer Butter Masala", post.title)
        assertTrue(post.caption!!.contains("200 g paneer\n"))
        val r = SocialRecipeImporter.import("https://youtu.be/abcdefghijk", noDeps.copy(download = { u, _ ->
            if (u.contains("oembed")) WebRecipeFetcher.Page("""{"title":"Paneer Butter Masala","author_name":"Desi Chef"}""", u) else WebRecipeFetcher.Page(watch, u)
        }))
        r as WebRecipeFetcher.Result.Success
        assertEquals("By Desi Chef on YouTube", r.draft.description)
        assertEquals(3, r.draft.parsed.ingredients.size)
    }

    @Test fun pinterestUsesOutboundRecipeSite() {
        val pin = """<html><head><meta property="og:title" content="Best dal"><meta property="og:image" content="https://i.pinimg.com/x.jpg">
            <meta property="og:description" content="So good"></head><script>{"link":"https:\/\/www.myfoodstory.com\/dal\/","id":"1"}</script></html>"""
        val post = SocialRecipeImporter.parsePinterest(pin)
        assertEquals("https://www.myfoodstory.com/dal/", post.outboundUrl)
        var webUrl: String? = null
        val web = WebRecipeFetcher.Result.Success(
            com.desikitchen.app.util.ImportDraft.Draft(com.desikitchen.app.util.RecipeTextParser.ParsedRecipe("Dal", 2, emptyList(), listOf("Cook"))), true)
        val r = SocialRecipeImporter.import("https://www.pinterest.com/pin/1/", noDeps.copy(
            download = { u, _ -> WebRecipeFetcher.Page(pin, u) }, web = { webUrl = it; web }))
        r as WebRecipeFetcher.Result.Success
        assertEquals("https://www.myfoodstory.com/dal/", webUrl)
        assertEquals("https://i.pinimg.com/x.jpg", r.draft.imageUrl)
    }

    @Test fun facebookLoginWallIsHonestFailure() {
        val wall = """<html><head><meta property="og:title" content="Facebook"><meta property="og:description" content="Log into Facebook to start sharing"></head></html>"""
        val r = SocialRecipeImporter.import("https://www.facebook.com/reel/123", noDeps.copy(download = { u, _ -> WebRecipeFetcher.Page(wall, u) }))
        r as WebRecipeFetcher.Result.Failure
        assertTrue(r.message, r.message.contains("Paste import"))
        assertTrue(r.message.contains("isn't set up"))
    }

    @Test fun noRecipeInCaptionNeverInvents() {
        val oembed = """{"title":"POV: dinner in 10 minutes #foodtok","author_name":"chef"}"""
        val r = SocialRecipeImporter.import("https://www.tiktok.com/@chef/video/1", noDeps.copy(download = { u, _ -> WebRecipeFetcher.Page(oembed, u) }))
        r as WebRecipeFetcher.Result.Failure
        assertTrue(r.message.contains("doesn't contain the recipe"))
        assertEquals("POV: dinner in 10 minutes #foodtok", r.pasteText)
        assertTrue(r.partial == null || r.partial!!.parsed.ingredients.isEmpty())
    }

    @Test fun creatorsPostOnlyNeverTranscript() {
        val calls = mutableListOf<String>()
        val creators = SocialCreatorsClient("https://api.scrapecreators.com/v1", "k", get = { u, h ->
            calls += u; assertEquals("k", h["x-api-key"])
            200 to """{"aweme_detail":{"desc":"rice bowl tonight, so easy: take 2 cups rice, wash it, add 1 tsp salt and 3 cups water and cook","author":{"unique_id":"chef"},"video":{"play_addr":{"url_list":["https://v.example/v.mp4"]}}}}"""
        })
        var sent = ""
        val ai = TextRecipeExtractor(OpenRouterClient("key", "m", transport = { _, _, body ->
            sent = body
            200 to JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject()
                .put("content", """{"title":"Rice bowl","servings":null,"ingredients":["2 cups rice"],"steps":[]}""")))).toString()
        }))
        val r = SocialRecipeImporter.import("https://www.tiktok.com/@chef/video/2", noDeps.copy(
            download = { u, _ -> WebRecipeFetcher.Page("""{"title":"rice bowl"}""", u) }, creators = creators, text = ai))
        assertEquals(1, calls.size)
        assertTrue(calls[0].startsWith("https://api.scrapecreators.com/v2/tiktok/video?url="))
        assertTrue(calls.none { it.contains("transcript") })
        r as WebRecipeFetcher.Result.Success
        if (sent.isNotEmpty()) assertTrue(sent.contains("2 cups rice"))
        assertTrue(r.draft.partial)
    }

    @Test fun chitChatCaptionSkipsTextAi() {
        var aiCalls = 0
        val creators = SocialCreatorsClient("https://api.scrapecreators.com/v1", "k", get = { _, _ ->
            200 to """{"aweme_detail":{"desc":"my family loves this one 😋 #foodtok","author":{"unique_id":"chef"}}}"""
        })
        val ai = TextRecipeExtractor(OpenRouterClient("key", "m", transport = { _, _, _ -> aiCalls++; 200 to "{}" }))
        val r = SocialRecipeImporter.import("https://www.tiktok.com/@chef/video/3", noDeps.copy(
            download = { u, _ -> WebRecipeFetcher.Page("""{"title":"yum"}""", u) }, creators = creators, text = ai))
        assertTrue(r is WebRecipeFetcher.Result.Failure)
        assertEquals(0, aiCalls)
    }

    @Test fun creatorsParsers() {
        val yt = SocialCreatorsClient.parsePost(Platform.YOUTUBE, """{"title":"T","description":"D","thumbnail":"https://i.ytimg.com/x.jpg","channel":{"title":"C"}}""")
        assertEquals("D", yt.caption); assertEquals("C", yt.author)
        val fb = SocialCreatorsClient.parsePost(Platform.FACEBOOK, """{"description":"D","image_url":"https://x/i.jpg","video":{"sd_url":"https://x/v.mp4"},"author":{"name":"A"}}""")
        assertEquals("https://x/v.mp4", fb.videoUrl); assertEquals("A", fb.author)
        val pin = SocialCreatorsClient.parsePost(Platform.PINTEREST, """{"data":{"description":"D","imageSpec_orig":{"url":"https://i.pinimg.com/o.jpg"}}}""")
        assertEquals("https://i.pinimg.com/o.jpg", pin.imageUrl)
    }

    @Test fun openRouterRequestAndErrors() {
        val c = OpenRouterClient("secret", "google/gemini-2.5-flash", transport = { _, _, _ -> 401 to """{"error":{"message":"bad key"}}""" })
        val body = JSONObject(c.buildBody(listOf(OpenRouterClient.Message("user", "hi")), json = true, temperature = 0.0, maxTokens = 10))
        assertEquals("google/gemini-2.5-flash", body.getString("model"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        assertEquals("Bearer secret", c.headers()["Authorization"])
        val e = runCatching { c.chat(listOf(OpenRouterClient.Message("user", "hi"))) }.exceptionOrNull()!!
        assertTrue(e.message!!.contains("rejected"))
        assertFalse(e.message!!.contains("secret"))
        assertEquals("hi", OpenRouterClient.parseContent("""{"choices":[{"message":{"content":" hi "}}]}"""))
        assertEquals("ab", OpenRouterClient.parseContent("""{"choices":[{"message":{"content":[{"type":"text","text":"a"},{"type":"text","text":"b"}]}}]}"""))
    }
}
