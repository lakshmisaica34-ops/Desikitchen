package com.desikitchen.app.util

import com.desikitchen.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Client for Scrape Creators (https://scrapecreators.com): given a public Instagram post/reel URL it returns the
 * reel's video URL, caption, thumbnail and owner. Used only when the public Instagram embed has no recipe in the
 * caption.
 *
 * Configured at build time from local.properties / env (never hard-coded):
 *   CREATORS_API_URL = https://api.scrapecreators.com/v1     (blank → feature off)
 *   CREATORS_API_KEY = …                                      (sent as x-api-key, which Scrape Creators requires;
 *                                                              also as Bearer for compatible providers)
 *
 * Contract (errors never throw out of [fetch] / [parse]; see [errorOf]):
 *   GET {CREATORS_API_URL}/instagram/post?url=<url-encoded post url>
 *   200 → { "data": { "xdt_shortcode_media": { "video_url", "display_url", "thumbnail_src",
 *            "owner": { "username" }, "edge_media_to_caption": { "edges": [ { "node": { "text" } } ] } } } }
 * [parse] also accepts flat / other vendor shapes: {"video_url","caption",…}, {"data":{…}}, {"result":{…}},
 * "videoUrl", "caption":{"text"}, "video_versions":[{"url":…}].
 */
class CreatorsApiClient(private val baseUrl: String, private val apiKey: String) {

    data class Media(
        val videoUrl: String?, val caption: String?, val thumbnailUrl: String?, val author: String?,
        /** The creator's own comments already inside the /instagram/post reply (no extra credit); strangers' comments are ignored. */
        val comments: List<String> = emptyList(),
        /** 1.1.3 Stage 3 input: the top comments in the same reply (anyone's; creator's first, then most-liked), ≤ 15, "[creator] " prefixed. */
        val topComments: List<String> = emptyList(),
        /** Instagram's has_audio flag (the audio itself is pulled from the video file in Stage 4). */
        val hasAudio: Boolean? = null,
        /** owner.profile_pic_url (HTTPS hotlink only; never downloaded). */
        val avatarUrl: String? = null,
    )

    /** Clean outcome: never throws for HTTP / JSON problems (404, `success:false`, HTML error pages, bad JSON). */
    sealed interface Outcome {
        data class Ok(val media: Media) : Outcome
        data class Error(val message: String, val httpCode: Int? = null) : Outcome
    }

    fun request(postUrl: String): String =
        "${baseUrl.trimEnd('/')}/instagram/post?url=${URLEncoder.encode(postUrl, "UTF-8")}"

    // No transcript endpoint on purpose (1.1.2+). Only GET /instagram/post (1 credit) is ever called: it gives the
    // video URL, the has_audio flag and the top comments in one reply (Sai: "we don't need transcript from SC").

    /** Blocking; call from Dispatchers.IO. Never throws (network errors included): returns [Outcome.Error]. */
    fun fetch(postUrl: String): Outcome = try {
        val (code, body) = get(request(postUrl))
        val err = errorOf(code, body)
        if (err != null) Outcome.Error(err, code) else Outcome.Ok(parse(body))
    } catch (e: Throwable) {
        if (e is InterruptedException) throw e
        Outcome.Error("creators API: ${e.javaClass.simpleName}${e.message?.let { ": " + it.take(80) } ?: ""}")
    }

    /** Kept for callers that prefer exceptions; throws [IllegalStateException] with a clean message. */
    fun fetchMedia(postUrl: String): Media = when (val o = fetch(postUrl)) {
        is Outcome.Ok -> o.media
        is Outcome.Error -> error(o.message)
    }

    private fun get(url: String): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000; readTimeout = 90_000
            setRequestProperty("User-Agent", WebRecipeFetcher.USER_AGENT)
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("x-api-key", apiKey)
            }
        }
        try {
            val code = conn.responseCode
            val body = ((if (code >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.use { it.readText().take(MAX_BODY) }).orEmpty()
            return code to body
        } finally { conn.disconnect() }
    }

    companion object {
        fun fromBuildConfig(): CreatorsApiClient? =
            BuildConfig.CREATORS_API_URL.takeIf { it.startsWith("https://") }?.let { CreatorsApiClient(it, BuildConfig.CREATORS_API_KEY) }

        private const val MAX_BODY = 2_000_000

        /**
         * Human-readable error for a non-2xx status or a `{"success": false, ...}` body, else null. Never throws.
         * Scrape Creators 404 body: {"success":false,"error":"not_found","errorStatus":404,"message":"Post not found"}.
         */
        fun errorOf(code: Int, body: String): String? {
            val o = runCatching { JSONObject(body) }.getOrNull()
            val failed = code !in 200..299 || o == null || (o.has("success") && !o.optBoolean("success", true))
            if (!failed) return null
            val kind = o?.optString("error").orEmpty()
            val status = o?.optInt("errorStatus", code) ?: code
            return when {
                code == 404 || status == 404 || kind == "not_found" -> "Instagram post not found via the creators API (it may be deleted, private or the link is wrong)"
                code == 401 || code == 403 -> "creators API key was rejected (HTTP $code)"
                code == 402 -> "creators API is out of credits (HTTP 402)"
                code == 429 -> "creators API is busy (HTTP 429)"
                o == null -> "creators API returned an unreadable reply (HTTP $code)"
                else -> "creators API error (HTTP $code${o.optString("message").takeIf { it.isNotBlank() }?.let { ": " + it.take(80) } ?: ""})"
            }
        }

        /** edge_media_to_parent_comment / edge_media_preview_comment texts written by the post owner, deduped, ≤ 10. */
        internal fun comments(o: JSONObject, owner: String?): List<String> {
            val out = mutableListOf<Pair<Boolean, String>>()
            for (key in listOf("edge_media_to_parent_comment", "edge_media_preview_comment", "edge_media_to_comment")) {
                val edges = o.optJSONObject(key)?.optJSONArray("edges") ?: continue
                for (i in 0 until edges.length()) {
                    val n = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
                    val t = n.optString("text").takeIf { it.isNotBlank() } ?: continue
                    out += (owner != null && n.optJSONObject("owner")?.optString("username") == owner) to t
                }
            }
            return out.filter { it.first }.map { it.second }.distinct().take(10)
        }

        /** All top-level comments in the reply (no extra credit): creator's own first, then most-liked, deduped, ≤ [max]. */
        internal fun topComments(o: JSONObject, owner: String?, max: Int = 15): List<String> {
            data class C(val own: Boolean, val likes: Int, val text: String)
            val seen = HashSet<String>(); val out = mutableListOf<C>()
            for (key in listOf("edge_media_to_parent_comment", "edge_media_preview_comment", "edge_media_to_comment")) {
                val edges = o.optJSONObject(key)?.optJSONArray("edges") ?: continue
                for (i in 0 until edges.length()) {
                    val n = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
                    val t = n.optString("text").trim().takeIf { it.isNotBlank() } ?: continue
                    if (!seen.add(t)) continue
                    out += C(owner != null && n.optJSONObject("owner")?.optString("username") == owner,
                        n.optJSONObject("edge_liked_by")?.optInt("count") ?: 0, t)
                }
            }
            return out.sortedWith(compareBy<C>({ !it.own }, { -it.likes })).take(max).map { (if (it.own) "[creator] " else "") + it.text.take(500) }
        }

        /** Never throws: unreadable / error JSON gives an empty [Media]. */
        fun parse(json: String): Media = runCatching { parseOrThrow(json) }.getOrElse { Media(null, null, null, null) }

        private fun parseOrThrow(json: String): Media {
            val root = JSONObject(json)
            val data = root.optJSONObject("data")
            val o = data?.optJSONObject("xdt_shortcode_media") ?: data ?: root.optJSONObject("result") ?: root
            fun clean(v: String?): String? = v?.takeIf { it.isNotBlank() && it != "null" }
            fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { k -> clean(o.optString(k, "")) }
            val video = str("video_url", "videoUrl", "video") ?: (o.optJSONArray("video_versions") ?: JSONArray())
                .let { arr -> (0 until arr.length()).firstNotNullOfOrNull { arr.optJSONObject(it)?.optString("url")?.takeIf(String::isNotBlank) } }
            val edgeCaption = o.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")
                ?.optJSONObject(0)?.optJSONObject("node")?.optString("text")?.let(::clean)
            val caption = edgeCaption
                ?: clean(o.optJSONObject("caption")?.optString("text"))
                ?: str("caption", "caption_text", "description")
            val author = clean(o.optJSONObject("owner")?.optString("username"))
                ?: str("author", "username")
                ?: clean(o.optString("owner", "")).takeIf { o.optJSONObject("owner") == null }
            val thumb = str("thumbnail_src", "display_url", "thumbnail_url", "thumbnailUrl", "image")
            val hasAudio = if (o.has("has_audio")) o.optBoolean("has_audio") else null
            val avatar = o.optJSONObject("owner")?.let { clean(it.optString("profile_pic_url_hd", "")) ?: clean(it.optString("profile_pic_url", "")) }
                ?.let { CreatorProfile.safeAvatarUrl(it) }
            return Media(video, caption, thumb, author, comments(o, author), topComments(o, author), hasAudio, avatar)
        }
    }
}
