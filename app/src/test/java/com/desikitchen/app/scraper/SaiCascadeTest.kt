package com.desikitchen.app.scraper

import com.desikitchen.app.util.CreatorsApiClient
import com.desikitchen.app.util.InstagramImporter
import com.desikitchen.app.util.OpenRouterClient
import com.desikitchen.app.util.RecipeGrounding
import com.desikitchen.app.util.StillsRecipeExtractor
import com.desikitchen.app.util.TextRecipeExtractor
import com.desikitchen.app.util.VideoRecipeExtractor
import com.desikitchen.app.util.WebRecipeFetcher
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * 1.1.3: Sai's 4-step Instagram cascade, end to end against a local fake server (no network, no keys):
 * 1 public embed → 2 ONE SC /instagram/post → 3 cheap text model on top comments → 4 keyframes + audio → Gemini,
 * full video only as a thin-result re-read.
 */
class SaiCascadeTest {
    private lateinit var server: ServerSocket
    private val routes = mutableMapOf<String, () -> String>()
    private lateinit var base: String
    private val hits = mutableMapOf<String, Int>()
    private val bodies = mutableMapOf<String, String>()
    private var stillsAnswer = """{"title":"Bread Pockets","ingredients":["bread","capsicum","carrot","cheese","butter"],"steps":["Fill","Air fry"]}"""
    private var videoAnswer = """{"title":"Bread Pockets","ingredients":["bread","capsicum","carrot","cheese","butter","oregano"],"steps":["Fill"]}"""
    private val tmp = File(System.getProperty("java.io.tmpdir"), "dk_test_${System.nanoTime()}").apply { mkdirs() }

    private val chitChatEmbed = """<html><div class="Caption"><a class="CaptionUsername">cook</a>Mere ghrwale bhi chatore ho gye 😏 #reels</div></html>"""

    private fun scJson(comments: List<Pair<String, String>>) = JSONObject().put("success", true).put("data", JSONObject().put("xdt_shortcode_media",
        JSONObject().put("video_url", "$base/cdn/v.mp4").put("has_audio", true).put("owner", JSONObject().put("username", "cook"))
            .put("edge_media_to_caption", JSONObject().put("edges", org.json.JSONArray().put(JSONObject().put("node", JSONObject().put("text", "Mere ghrwale bhi chatore ho gye 😏 #reels")))))
            .put("edge_media_to_parent_comment", JSONObject().put("edges", org.json.JSONArray().apply {
                comments.forEach { (u, t) -> put(JSONObject().put("node", JSONObject().put("text", t).put("owner", JSONObject().put("username", u)))) }
            })))).toString()

    private var comments = listOf("fan" to "Use liquid cheese for cheese pull", "fan2" to "Which air fryer?")

    private fun orReply(content: String) = JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", content))))
        .put("usage", JSONObject().put("prompt_tokens", 5400).put("completion_tokens", 300).put("cost", 0.0011)).toString()

    @Before fun start() {
        routes["/v1/instagram/post"] = { scJson(comments) }
        routes["/cdn/v.mp4"] = { "x".repeat(50_000) }
        routes["/or/stills"] = { orReply(stillsAnswer) }
        routes["/or/video"] = { orReply(videoAnswer) }
        server = ServerSocket(0)
        base = "http://127.0.0.1:${server.localPort}"
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val sock = runCatching { server.accept() }.getOrNull() ?: break
                sock.use { s ->
                    val inp = BufferedInputStream(s.getInputStream())
                    val reqLine = line(inp)
                    val headers = generateSequence { line(inp).takeIf { it.isNotEmpty() } }.associate { h -> h.substringBefore(':').lowercase() to h.substringAfter(':').trim() }
                    val body = when {
                        headers["transfer-encoding"]?.contains("chunked") == true -> buildString {
                            while (true) { val n = line(inp).trim().toInt(16); if (n == 0) { line(inp); break }; append(String(readN(inp, n))); line(inp) }
                        }
                        headers["content-length"] != null -> String(readN(inp, headers["content-length"]!!.toInt()))
                        else -> ""
                    }
                    val path = reqLine.split(' ')[1].substringBefore('?')
                    synchronized(hits) { hits[path] = (hits[path] ?: 0) + 1; bodies[path] = body }
                    val out = (routes[path]?.invoke() ?: "").toByteArray()
                    s.getOutputStream().apply {
                        write("HTTP/1.1 ${if (routes[path] != null) 200 else 404} OK\r\nContent-Type: application/json\r\nContent-Length: ${out.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(out); flush()
                    }
                }
            }
        }
    }

    private fun line(i: InputStream): String { val b = StringBuilder(); while (true) { val c = i.read(); if (c < 0 || c == '\n'.code) break; if (c != '\r'.code) b.append(c.toChar()) }; return b.toString() }
    private fun readN(i: InputStream, n: Int): ByteArray { val a = ByteArray(n); var o = 0; while (o < n) { val r = i.read(a, o, n - o); if (r < 0) break; o += r }; return a }

    @After fun stop() { server.close(); tmp.deleteRecursively() }

    /** Fake on-device sampler: 3 frames (one duplicate) + an audio file, written into the temp dir. */
    private val sampler = StillsRecipeExtractor.MediaSampler { video, out, _ ->
        assertTrue("video must be streamed to a temp file", video.length() == 50_000L)
        val f = (0 until 3).map { i -> File(out, "f_$i.jpg").apply { writeBytes(byteArrayOf(1, 2, if (i == 2) 1 else i.toByte())) } }
        val a = File(out, "a.m4a").apply { writeBytes(ByteArray(2000) { 7 }) }
        StillsRecipeExtractor.Sampled(f, a, "m4a")
    }

    private var textAnswer = """{"title":null,"ingredients":["liquid cheese"],"steps":[]}"""
    private var textCalls = 0
    private val text get() = TextRecipeExtractor(OpenRouterClient("k", "mistralai/mistral-nemo", transport = { _, _, body ->
        textCalls++; bodies["text"] = body; 200 to orReply(textAnswer)
    }))

    private fun run(): WebRecipeFetcher.Result = InstagramImporter.import(
        "https://www.instagram.com/reel/Ddk4k1PoM-k/?xtok=abc",
        download = { chitChatEmbed },
        creators = CreatorsApiClient("$base/v1", "k"),
        video = VideoRecipeExtractor("k", "google/gemini-2.5-flash", endpoint = "$base/or/video"),
        text = text,
        stills = StillsRecipeExtractor("k", "google/gemini-2.5-flash-lite", sampler, endpoint = "$base/or/stills", tempDir = tmp),
    )

    @Test fun keyframesAndAudioWinWhenCommentsHaveNoRecipe() {
        val r = run() as WebRecipeFetcher.Result.Success
        val t = InstagramImporter.lastTrace!!
        assertEquals("4_keyframes_audio", t.winner)
        assertEquals(1, hits["/v1/instagram/post"]); assertEquals(1, t.scPostCalls)
        assertEquals(1, textCalls)                     // stage 3 ran once on the comments
        assertTrue(bodies["text"]!!.contains("liquid cheese") && bodies["text"]!!.contains("mistral-nemo"))
        assertEquals(1, hits["/or/stills"])            // stage 4: one Gemini call
        val b = bodies["/or/stills"]!!
        assertEquals(2, Regex("\"type\":\"image_url\"").findAll(b).count()) // duplicate keyframe dropped
        assertTrue(b.contains("\"input_audio\"") && b.contains("\"format\":\"m4a\"") && b.contains("gemini-2.5-flash-lite"))
        assertFalse("never the full video first", b.contains("video_url"))
        assertEquals(null, hits["/or/video"])          // 5 ingredients ≥ 4: no thin-result re-read
        assertEquals(2, t.openRouterCalls)
        assertEquals(5, r.draft.parsed.ingredients.size)
        assertEquals("PARTIAL", r.completeness.name)
        assertTrue(tmp.listFiles()!!.isEmpty())        // temp media discarded
    }

    @Test fun commentsWinStopsBeforeStills() {
        comments = listOf("cook" to "Recipe: 4 bread slices, 1 capsicum, 1 carrot, 50 g cheese. Fill and air fry 8 min.")
        textAnswer = """{"title":"Bread Pockets","ingredients":["4 bread slices","1 capsicum","1 carrot","50 g cheese","2 tbsp ghee"],"steps":["Fill and air fry 8 min"]}"""
        val r = run() as WebRecipeFetcher.Result.Success
        assertEquals("3_cheap_text_comments", InstagramImporter.lastTrace!!.winner)
        assertEquals(4, r.draft.parsed.ingredients.size) // "ghee" was invented → dropped by grounding
        assertTrue(bodies["text"]!!.contains("[creator] Recipe"))
        assertEquals(null, hits["/or/stills"]); assertEquals(null, hits["/or/video"])
        assertEquals(1, hits["/v1/instagram/post"])
    }

    @Test fun thinKeyframeResultTriggersOneFullVideoReread() {
        stillsAnswer = """{"title":"Bread Pockets","ingredients":["bread","cheese"],"steps":[]}"""
        val r = run() as WebRecipeFetcher.Result.Success
        assertEquals(1, hits["/or/stills"]); assertEquals(1, hits["/or/video"])
        assertTrue(bodies["/or/video"]!!.contains("\"video_url\""))
        assertEquals("4d_full_video_reread", InstagramImporter.lastTrace!!.winner)
        assertEquals(6, r.draft.parsed.ingredients.size)
        assertTrue(InstagramImporter.lastTrace!!.notes.any { it.contains("thin result") })
    }

    @Test fun topCommentsCreatorFirstThenLikes() {
        val o = JSONObject("""{"owner":{"username":"cook"},"edge_media_to_parent_comment":{"edges":[
            {"node":{"text":"nice","owner":{"username":"a"},"edge_liked_by":{"count":2}}},
            {"node":{"text":"Ingredients: rice","owner":{"username":"cook"},"edge_liked_by":{"count":0}}},
            {"node":{"text":"wow","owner":{"username":"b"},"edge_liked_by":{"count":9}}}]},
            "edge_media_preview_comment":{"edges":[{"node":{"text":"wow","owner":{"username":"b"}}}]}}""")
        assertEquals(listOf("[creator] Ingredients: rice", "wow", "nice"), CreatorsApiClient.topComments(o, "cook"))
        val m = CreatorsApiClient.parse("""{"data":{"xdt_shortcode_media":{"has_audio":true,"owner":{"username":"cook"}}}}""")
        assertEquals(true, m.hasAudio)
    }

    @Test fun groundingDropsInventedIngredients() {
        val src = "Caption: bread pockets\n- 2 cups maida, 1 tsp jeera, paneer"
        assertEquals(listOf("2 cups maida", "jeera", "200 g paneer"), RecipeGrounding.grounded(listOf("2 cups maida", "jeera", "ghee", "200 g paneer", "1 cup"), src))
    }
}

