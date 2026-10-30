package com.desikitchen.app.util

import com.desikitchen.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Scrape Creators (https://docs.scrapecreators.com) public-post endpoints for TikTok, YouTube, Facebook and Pinterest.
 * Same key / base URL as the Instagram client (CREATORS_API_URL ends in /v1; CREATORS_API_KEY sent as x-api-key).
 * Only public posts; this is the documented paid API, not a private app API.
 */
class SocialCreatorsClient(
    baseUrl: String,
    private val apiKey: String,
    private val get: (String, Map<String, String>) -> Pair<Int, String> = ::httpGet,
) {
    data class Post(
        val caption: String?, val title: String?, val author: String?, val imageUrl: String?, val videoUrl: String?,
        /** Creator profile/channel URL the API reported (YouTube `channel.url`); never derived here. */
        val authorUrl: String? = null,
    )

    private val v1 = baseUrl.trimEnd('/')
    private val root = v1.removeSuffix("/v1")

    fun postRequest(platform: SocialRecipeImporter.Platform, url: String): String {
        val u = URLEncoder.encode(url, "UTF-8")
        return when (platform) {
            SocialRecipeImporter.Platform.TIKTOK -> "$root/v2/tiktok/video?url=$u"
            SocialRecipeImporter.Platform.YOUTUBE -> "$v1/youtube/video?url=$u"
            SocialRecipeImporter.Platform.FACEBOOK -> "$v1/facebook/post?url=$u"
            SocialRecipeImporter.Platform.PINTEREST -> "$v1/pinterest/pin?url=$u"
        }
    }

    fun fetchPost(platform: SocialRecipeImporter.Platform, url: String): Post = parsePost(platform, call(postRequest(platform, url)))

    // 1.1.2: no transcript endpoints on purpose (cost); OpenRouter Gemini reads the video instead.

    private fun call(url: String): String {
        val (code, body) = get(url, mapOf("x-api-key" to apiKey, "Accept" to "application/json", "User-Agent" to WebRecipeFetcher.USER_AGENT))
        if (code !in 200..299) error("creators API HTTP $code")
        return body
    }

    companion object {
        fun fromBuildConfig(): SocialCreatorsClient? =
            BuildConfig.CREATORS_API_URL.takeIf { it.startsWith("https://") && BuildConfig.CREATORS_API_KEY.isNotBlank() }
                ?.let { SocialCreatorsClient(it, BuildConfig.CREATORS_API_KEY) }

        private fun clean(v: String?): String? = v?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
        private fun JSONObject.path(vararg keys: String): Any? {
            var cur: Any? = this
            for (k in keys) cur = when (cur) {
                is JSONObject -> cur.opt(k)
                is JSONArray -> k.toIntOrNull()?.let { cur.opt(it) }
                else -> null
            }
            return cur
        }
        private fun JSONObject.str(vararg keys: String): String? = clean(path(*keys) as? String)

        fun parsePost(platform: SocialRecipeImporter.Platform, json: String): Post {
            val o = JSONObject(json)
            return when (platform) {
                SocialRecipeImporter.Platform.TIKTOK -> {
                    val d = o.optJSONObject("aweme_detail") ?: o
                    Post(
                        caption = d.str("desc"),
                        title = null,
                        author = d.str("author", "unique_id") ?: d.str("author", "nickname"),
                        imageUrl = d.str("video", "cover", "url_list", "0") ?: d.str("video", "origin_cover", "url_list", "0"),
                        videoUrl = d.str("video", "play_addr", "url_list", "0") ?: d.str("video", "download_addr", "url_list", "0"),
                    )
                }
                SocialRecipeImporter.Platform.YOUTUBE -> Post(
                    caption = o.str("description"),
                    title = o.str("title"),
                    author = o.str("channel", "title") ?: o.str("channel", "handle") ?: o.str("channel", "name"),
                    imageUrl = o.str("thumbnail") ?: o.str("thumbnails", "0", "url"),
                    videoUrl = null,
                    authorUrl = o.str("channel", "url") ?: o.str("ownerByline", "url") ?: o.str("uploader", "url"),
                )
                SocialRecipeImporter.Platform.FACEBOOK -> Post(
                    caption = o.str("description") ?: o.str("text"),
                    title = o.str("title"),
                    author = o.str("author", "name") ?: o.str("author"),
                    imageUrl = o.str("image_url") ?: o.str("thumbnail"),
                    videoUrl = o.str("video", "sd_url") ?: o.str("video", "hd_url"),
                )
                SocialRecipeImporter.Platform.PINTEREST -> {
                    val d = o.optJSONObject("data") ?: o
                    Post(
                        caption = d.str("description") ?: d.str("seoAltText") ?: d.str("auto_alt_text"),
                        title = d.str("title") ?: d.str("grid_title"),
                        author = d.str("pinner", "username") ?: d.str("originPinner", "username"),
                        imageUrl = d.str("imageSpec_orig", "url") ?: d.str("images", "orig", "url"),
                        videoUrl = null,
                    )
                }
            }
        }

        private fun httpGet(url: String, headers: Map<String, String>): Pair<Int, String> {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000; readTimeout = 45_000
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                val code = conn.responseCode
                val text = ((if (code >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.use { it.readText() }).orEmpty()
                return code to text
            } finally { conn.disconnect() }
        }
    }
}
