package com.desikitchen.app.util

import com.desikitchen.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * OPTIONAL: reads a recipe that is only spoken / shown on screen in a reel, via OpenRouter (Gemini by default).
 * Off unless OPENROUTER_API_KEY is set at build time (local.properties / env). Results are always marked PARTIAL
 * with a warning: the model transcribes, the user verifies. The prompt forbids guessing and asks for empty lists
 * when the video does not state a recipe; we drop anything that isn't a plain string.
 *
 * TODO(direct Gemini): a GEMINI_API_KEY path (generativelanguage.googleapis.com, inline video bytes) is not wired;
 *   OpenRouter covers Gemini today. Add it here if you want to skip OpenRouter.
 * TODO(security): a key compiled into the APK can be extracted. For anything beyond personal use, put the key on
 *   your own server and have it run this step, then return the recipe JSON.
 */
class VideoRecipeExtractor(
    private val apiKey: String,
    private val model: String,
    private val endpoint: String = "https://openrouter.ai/api/v1/chat/completions",
) {
    data class Extracted(
        val title: String?, val servings: Int?, val ingredients: List<String>, val steps: List<String>,
        /** Lines [RecipeSanitizer] removed (nutrition rows, chatter, method lines duplicated in ingredients). */
        val dropped: Int = 0,
        /** Instructions the model put under ingredients that were moved to steps (only when it gave no steps). */
        val movedToSteps: Int = 0,
    )

    /**
     * Blocking; call from Dispatchers.IO. Returns null when the model found no recipe. Throws only clean
     * [IllegalStateException]s (never OutOfMemoryError):
     *  1. The video URL is sent to OpenRouter as-is (no download on the phone, no memory spike).
     *  2. Only if that is refused: download ≤ [MAX_VIDEO_BYTES] to a temp file and stream it base64-encoded
     *     into a chunked request body, so the video is never held in memory (1.1.0 buffered ~20 MB × 4 → OOM crash).
     */
    fun extract(videoUrl: String, caption: String?): Extracted? {
        val remote = runCatching { post(textPart(caption), JSONObject.quote(videoUrl), null) }
        // The model read the URL and found no recipe: stop (don't pay twice to upload the same video).
        if (remote.isSuccess) return remote.getOrNull()
        val file = try { downloadVideo(videoUrl) } catch (e: OutOfMemoryError) { error("video is too large to read on this phone") }
        try {
            return try {
                post(textPart(caption), null, file)
            } catch (e: OutOfMemoryError) {
                error("video is too large to read on this phone")
            }
        } finally { file.delete() }
    }

    /** 1.1.3 thin-result re-read only: the remote video URL to OpenRouter, never a download. Null = nothing found. */
    fun extractRemoteOnly(videoUrl: String, caption: String?): Extracted? = post(textPart(caption), JSONObject.quote(videoUrl), null)

    /** Photo / carousel post: send the still image URL (cheaper than video). Null when no recipe is visible. */
    fun extractStill(imageUrl: String, caption: String?): Extracted? =
        post(JSONObject().put("type", "text").put("text", prompt(caption, still = true)).toString(), JSONObject.quote(imageUrl), null, part = "image_url")

    private fun textPart(caption: String?) = JSONObject().put("type", "text").put("text", prompt(caption)).toString()

    /** Writes the request JSON by hand so a video file can be streamed through a Base64 encoder. */
    private fun post(textPartJson: String, quotedUrl: String?, file: java.io.File?, part: String = "video_url"): Extracted? {
        val head = "{\"model\":${JSONObject.quote(model)},\"response_format\":{\"type\":\"json_object\"},\"temperature\":0," +
            "\"messages\":[{\"role\":\"user\",\"content\":[$textPartJson,{\"type\":\"$part\",\"$part\":{\"url\":"
        val tail = "}}]}]}"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 15_000; readTimeout = 150_000
            setChunkedStreamingMode(64 * 1024) // don't let HttpURLConnection buffer the whole body
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Title", "DesiKitchen")
        }
        try {
            conn.outputStream.use { out ->
                out.write(head.toByteArray())
                if (quotedUrl != null) out.write(quotedUrl.toByteArray()) else {
                    out.write("\"data:video/mp4;base64,".toByteArray())
                    val nonClosing = object : java.io.FilterOutputStream(out) {
                        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
                        override fun close() = flush()
                    }
                    Base64.getEncoder().wrap(nonClosing).use { enc -> file!!.inputStream().use { it.copyTo(enc, 64 * 1024) } }
                    out.write("\"".toByteArray())
                }
                out.write(tail.toByteArray())
            }
            val code = conn.responseCode
            val text = ((if (code >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.use { it.readText().take(1_000_000) }).orEmpty()
            if (code !in 200..299) error(OpenRouterClient.errorMessage(code, text))
            val content = runCatching {
                JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
            }.getOrElse { error("OpenRouter returned an unreadable answer") }
            return parseModelJson(content)
        } finally { conn.disconnect() }
    }

    private fun downloadVideo(url: String): java.io.File {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000; readTimeout = 60_000
            setRequestProperty("User-Agent", WebRecipeFetcher.USER_AGENT)
        }
        val file = java.io.File.createTempFile("dk_video", ".mp4")
        try {
            if (conn.responseCode !in 200..299) error("video HTTP ${conn.responseCode}")
            val declared = conn.contentLengthLong
            if (declared > MAX_VIDEO_BYTES) error("video is larger than ${MAX_VIDEO_BYTES / 1_000_000} MB")
            var total = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        total += n
                        if (total > MAX_VIDEO_BYTES) error("video is larger than ${MAX_VIDEO_BYTES / 1_000_000} MB")
                        out.write(buf, 0, n)
                    }
                }
            }
            return file
        } catch (e: Throwable) {
            file.delete(); throw e
        } finally { conn.disconnect() }
    }

    companion object {
        /** Fallback upload cap. Streamed from disk, so this bounds upload time and cost, not phone memory. */
        internal const val MAX_VIDEO_BYTES = 8_000_000

        fun fromBuildConfig(): VideoRecipeExtractor? =
            BuildConfig.OPENROUTER_API_KEY.takeIf { it.isNotBlank() }?.let { VideoRecipeExtractor(it, BuildConfig.OPENROUTER_MODEL) }

        internal fun prompt(caption: String?, still: Boolean = false) = """
            ${if (still) "You read recipe photos. Write down ONLY the recipe text visible in this image." else "You transcribe cooking videos. Write down ONLY the recipe that is spoken aloud or shown as on-screen text in this video."}
            Rules: never guess or add ingredients, quantities or steps that are not said or shown; if a quantity isn't stated, write the ingredient without one;
            keep the creator's wording, translated to English if needed; if it does not present a recipe, return empty lists.
            "title" is the dish name in plain English (e.g. "Bread Pockets"), not the caption's chit-chat or hashtags.
            ${RecipeSanitizer.PROMPT_BUCKET_RULES}
            Reply with JSON only: {"title": string|null, "servings": number|null, "ingredients": [string], "steps": [string]}
            ${caption?.takeIf { it.isNotBlank() }?.let { "The post caption (only a hint for the dish name; never take ingredients from it unless they are also said or shown):\n" + it.take(1500) } ?: ""}
        """.trimIndent()

        fun parseModelJson(content: String): Extracted? {
            val json = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
            return runCatching { fromJson(o) }.getOrNull()
        }

        private fun fromJson(o: JSONObject): Extracted? {
            fun list(key: String): List<String> = o.optJSONArray(key)?.let { a ->
                (0 until a.length()).mapNotNull { (a.opt(it) as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
            }.orEmpty()
            val ing = list("ingredients"); val steps = list("steps")
            if (ing.isEmpty() && steps.isEmpty()) return null
            // 2026-10-29: validate the model's buckets. Ingredients must be food items, steps instructions; nutrition
            // rows are dropped everywhere. Nothing is added. Null when nothing valid is left (caller falls through).
            val cleaned = RecipeSanitizer.cleanAi(Extracted(
                o.optString("title").takeIf { it.isNotBlank() && it != "null" },
                o.optInt("servings", 0).takeIf { it > 0 }, ing, steps,
            ))
            return cleaned.takeIf { it.ingredients.isNotEmpty() || it.steps.isNotEmpty() }
        }
    }
}
