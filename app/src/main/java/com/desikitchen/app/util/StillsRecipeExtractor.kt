package com.desikitchen.app.util

import com.desikitchen.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64

/**
 * Stage 4 of Sai's Instagram cascade (1.1.3): **keyframes + audio → Gemini** (never the full video first).
 *
 *  1. Stream the reel's CDN video URL to a temp file (never held in memory; capped at [MAX_DOWNLOAD_BYTES]).
 *  2. [MediaSampler] pulls ≤ [MAX_FRAMES] keyframes scaled to ~512 px plus the audio track:
 *     - on the phone [AndroidMediaSampler]: MediaMetadataRetriever `OPTION_CLOSEST_SYNC` (= keyframes, like ffmpeg
 *       `-skip_frame nokey`) at evenly spaced times, `getScaledFrameAtTime` (API 27+) so no full-size bitmap is decoded;
 *       audio = the AAC track remuxed (MediaExtractor → MediaMuxer, no re-encode) to .m4a, capped at [MAX_AUDIO_SECONDS].
 *     - in the JVM live check / box harness: ffmpeg keyframes + mono 32 kbps MP3 (Sai's exact Stage 4).
 *  3. One OpenRouter call (default `google/gemini-2.5-flash-lite`) with the stills + audio (+ caption as a dish-name hint).
 *     Strict JSON, never invent. The request body is streamed (chunked, base64 from disk) so nothing big sits in memory.
 *  4. All temp media is deleted in `finally`.
 * Every Throwable (OOM included) is turned into a clean [IllegalStateException] by the caller-facing [extract].
 */
class StillsRecipeExtractor(
    private val apiKey: String,
    val model: String,
    private val sampler: MediaSampler,
    private val endpoint: String = OpenRouterClient.ENDPOINT,
    private val tempDir: File? = null,
) {
    data class Sampled(val frames: List<File>, val audio: File?, val audioFormat: String?)

    /** Pulls keyframes + audio from a local video file into [outDir]. Must not throw OOM out (catch per frame). */
    fun interface MediaSampler { fun sample(video: File, outDir: File, maxFrames: Int): Sampled }

    /** What happened, for logs / tests (counts and sizes only). */
    data class Stats(val videoBytes: Long, val frames: Int, val stillsBytes: Long, val audioBytes: Long, val tempDeleted: Boolean)

    @Volatile var lastStats: Stats? = null
        private set

    /** Blocking; call from Dispatchers.IO. Null when the model found no recipe. Throws only IllegalStateException. */
    fun extract(videoUrl: String, caption: String?): VideoRecipeExtractor.Extracted? {
        val dir = File(tempDir ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp"), "dk_stills_${System.nanoTime()}").apply { mkdirs() }
        var videoBytes = 0L; var sampled: Sampled? = null
        try {
            val video = File(dir, "v.mp4")
            videoBytes = download(videoUrl, video)
            sampled = try { sampler.sample(video, dir, MAX_FRAMES) } catch (e: OutOfMemoryError) { error("video frames too large for this phone") }
            video.delete() // not needed any more; free the space before the upload
            val frames = dedupe(sampled.frames).take(MAX_FRAMES)
            if (frames.isEmpty() && sampled.audio == null) error("no keyframes or audio could be read from the video")
            return try { post(frames, sampled.audio, sampled.audioFormat, caption) } catch (e: OutOfMemoryError) { error("stills upload too large for this phone") }
        } finally {
            dir.deleteRecursively()
            lastStats = Stats(videoBytes, sampled?.frames?.size ?: 0, sampled?.frames?.sumOf { it.length() } ?: 0,
                sampled?.audio?.length() ?: 0, !dir.exists())
        }
    }

    private fun dedupe(frames: List<File>): List<File> {
        val seen = HashSet<String>()
        return frames.filter { f ->
            f.exists() && f.length() > 0 && seen.add(MessageDigest.getInstance("MD5").digest(f.readBytes()).joinToString("") { "%02x".format(it) })
        }
    }

    private fun download(url: String, out: File): Long {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000; readTimeout = 60_000
            setRequestProperty("User-Agent", WebRecipeFetcher.USER_AGENT)
        }
        try {
            if (conn.responseCode !in 200..299) error("video HTTP ${conn.responseCode}")
            if (conn.contentLengthLong > MAX_DOWNLOAD_BYTES) error("video is larger than ${MAX_DOWNLOAD_BYTES / 1_000_000} MB")
            var total = 0L
            conn.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        total += n
                        if (total > MAX_DOWNLOAD_BYTES) error("video is larger than ${MAX_DOWNLOAD_BYTES / 1_000_000} MB")
                        o.write(buf, 0, n)
                    }
                }
            }
            return total
        } finally { conn.disconnect() }
    }

    /** Streams {model, messages:[{user:[text, image_url×N, input_audio]}]} with base64 read from disk. */
    private fun post(frames: List<File>, audio: File?, audioFormat: String?, caption: String?): VideoRecipeExtractor.Extracted? {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 15_000; readTimeout = 150_000
            setChunkedStreamingMode(64 * 1024)
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Title", "DesiKitchen")
        }
        try {
            conn.outputStream.use { out ->
                fun w(s: String) = out.write(s.toByteArray())
                fun b64(f: File) {
                    val nonClosing = object : java.io.FilterOutputStream(out) {
                        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
                        override fun close() = flush()
                    }
                    Base64.getEncoder().wrap(nonClosing).use { enc -> f.inputStream().use { it.copyTo(enc, 64 * 1024) } }
                }
                w("{\"model\":${JSONObject.quote(model)},\"temperature\":0,\"max_tokens\":2000,\"response_format\":{\"type\":\"json_object\"}," +
                    "\"usage\":{\"include\":true},\"messages\":[{\"role\":\"user\",\"content\":[")
                w(JSONObject().put("type", "text").put("text", prompt(caption)).toString())
                for (f in frames) { w(",{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/jpeg;base64,"); b64(f); w("\"}}") }
                if (audio != null && audio.length() > 0) {
                    w(",{\"type\":\"input_audio\",\"input_audio\":{\"data\":\""); b64(audio); w("\",\"format\":${JSONObject.quote(audioFormat ?: "m4a")}}}")
                }
                w("]}]}")
            }
            val code = conn.responseCode
            val text = ((if (code >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.use { it.readText().take(1_000_000) }).orEmpty()
            if (code !in 200..299) error(OpenRouterClient.errorMessage(code, text))
            val root = runCatching { JSONObject(text) }.getOrElse { error("OpenRouter returned an unreadable answer") }
            lastUsage = root.optJSONObject("usage")?.let { u -> Usage(u.optInt("prompt_tokens"), u.optInt("completion_tokens"), u.optDouble("cost", Double.NaN)) }
            val content = runCatching { root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content") }
                .getOrElse { error("OpenRouter returned an unreadable answer") }
            return VideoRecipeExtractor.parseModelJson(content)
        } finally { conn.disconnect() }
    }

    data class Usage(val promptTokens: Int, val completionTokens: Int, val usd: Double)
    @Volatile var lastUsage: Usage? = null
        private set

    companion object {
        const val MAX_FRAMES = 12
        const val FRAME_PX = 512
        const val MAX_AUDIO_SECONDS = 180
        const val MAX_DOWNLOAD_BYTES = 60_000_000L
        /** Below this many ingredients the importer may do ONE optional full-video re-read (remote URL, logged). */
        const val THIN_RESULT_INGREDIENTS = 4

        fun fromBuildConfig(sampler: MediaSampler = AndroidMediaSampler, tempDir: File? = null): StillsRecipeExtractor? =
            BuildConfig.OPENROUTER_API_KEY.takeIf { it.isNotBlank() }?.let { StillsRecipeExtractor(it, BuildConfig.OPENROUTER_STILLS_MODEL, sampler, tempDir = tempDir) }

        internal fun prompt(caption: String?) = """
            These are keyframes (in order) and the audio track of one Instagram cooking reel. Write down ONLY the recipe that is spoken in the audio or shown in the frames (on-screen text, ingredients visibly added).
            Rules: never guess or add ingredients, quantities or steps that are not said or shown; if a quantity isn't stated, write the ingredient without one; translate to English if needed; if no recipe is presented, return empty lists.
            "title" is the dish name in plain English (e.g. "Bread Pockets"), not caption chit-chat or hashtags.
            ${RecipeSanitizer.PROMPT_BUCKET_RULES}
            Reply with JSON only: {"title": string|null, "servings": number|null, "ingredients": [string], "steps": [string]}
            ${caption?.takeIf { it.isNotBlank() }?.let { "The post caption (only a hint for the dish name; never take ingredients from it unless they are also said or shown):\n" + it.take(1500) } ?: ""}
        """.trimIndent()
    }
}

/**
 * On-device sampler: MediaMetadataRetriever keyframes (OPTION_CLOSEST_SYNC) at evenly spaced times, scaled to ~512 px,
 * JPEG q70 to disk one at a time (bitmap recycled immediately), plus the AAC audio track remuxed to .m4a (no re-encode).
 */
object AndroidMediaSampler : StillsRecipeExtractor.MediaSampler {
    override fun sample(video: File, outDir: File, maxFrames: Int): StillsRecipeExtractor.Sampled {
        val frames = mutableListOf<File>()
        val mmr = android.media.MediaMetadataRetriever()
        try {
            mmr.setDataSource(video.absolutePath)
            val durUs = (mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            val w = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
            val h = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280
            val scale = StillsRecipeExtractor.FRAME_PX.toFloat() / maxOf(1, minOf(w, h))
            val dw = (w * minOf(1f, scale)).toInt().coerceAtLeast(2); val dh = (h * minOf(1f, scale)).toInt().coerceAtLeast(2)
            val n = if (durUs > 0) maxFrames else 1
            for (i in 0 until n) {
                val t = if (n == 1) 0L else (durUs - 500_000L).coerceAtLeast(0) * i / (n - 1)
                try {
                    val bmp = if (android.os.Build.VERSION.SDK_INT >= 27)
                        mmr.getScaledFrameAtTime(t, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, dw, dh)
                    else mmr.getFrameAtTime(t, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { full ->
                        android.graphics.Bitmap.createScaledBitmap(full, dw, dh, true).also { if (it !== full) full.recycle() }
                    }
                    if (bmp != null) {
                        val f = File(outDir, "f_%02d.jpg".format(i))
                        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, it) }
                        bmp.recycle()
                        frames += f
                    }
                } catch (e: Throwable) {
                    if (e is InterruptedException) throw e // OOM / codec errors on one frame: skip that frame
                }
            }
        } catch (e: Throwable) {
            if (e is InterruptedException) throw e
        } finally { runCatching { mmr.release() } }
        val audio = runCatching { extractAac(video, File(outDir, "a.m4a")) }.getOrNull()
        return StillsRecipeExtractor.Sampled(frames, audio, audio?.let { "m4a" })
    }

    /** Copies the first AAC track into an .m4a container, capped at MAX_AUDIO_SECONDS. Null if there is no AAC audio. */
    private fun extractAac(video: File, out: File): File? {
        val ex = android.media.MediaExtractor()
        var muxer: android.media.MediaMuxer? = null
        try {
            ex.setDataSource(video.absolutePath)
            val track = (0 until ex.trackCount).firstOrNull {
                ex.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME) == "audio/mp4a-latm"
            } ?: return null
            ex.selectTrack(track)
            val m = android.media.MediaMuxer(out.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }
            val dst = m.addTrack(ex.getTrackFormat(track))
            m.start()
            val buf = java.nio.ByteBuffer.allocate(256 * 1024)
            val info = android.media.MediaCodec.BufferInfo()
            val capUs = StillsRecipeExtractor.MAX_AUDIO_SECONDS * 1_000_000L
            var wrote = false
            while (true) {
                val size = ex.readSampleData(buf, 0); if (size < 0) break
                if (ex.sampleTime > capUs) break
                info.set(0, size, ex.sampleTime, if (ex.sampleFlags and android.media.MediaExtractor.SAMPLE_FLAG_SYNC != 0) android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                m.writeSampleData(dst, buf, info); wrote = true
                ex.advance()
            }
            m.stop()
            return out.takeIf { wrote && it.length() > 1000 }
        } finally {
            runCatching { muxer?.release() }
            ex.release()
        }
    }
}

/** Never invent: an AI ingredient survives only if one of its content words (≥ 3 letters) appears in the source text. */
object RecipeGrounding {
    private val STOP = setOf("and", "the", "for", "with", "some", "few", "cup", "cups", "tbsp", "tsp", "gram", "grams", "taste", "needed",
        "little", "chopped", "finely", "fresh", "small", "large", "medium", "piece", "pieces", "slice", "slices", "pinch", "spoon")
    private val WORD = SafeRegex.of("StillsRecipeExtractor.WORD", "[a-z\\u0900-\\u097f]{3,}")

    fun grounded(items: List<String>, source: String): List<String> {
        val src = source.lowercase()
        return items.filter { it ->
            val words = WORD.findAll(it.lowercase()).map { m -> m.value }.filter { w -> w !in STOP }.toList()
            words.isNotEmpty() && words.any { w -> src.contains(w) }
        }
    }
}
