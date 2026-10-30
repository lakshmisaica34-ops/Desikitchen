package com.desikitchen.app.util

import com.desikitchen.app.data.model.SourcePlatform
import java.net.URI

/**
 * Phase 1 duplicate detection: two links point at the same recipe when they share the platform + video id
 * (Instagram shortcode / YouTube video id) or, failing that, the same normalised URL.
 * Pure (no Android) so it's unit-testable; [com.desikitchen.app.data.repo.RecipeRepository.findDuplicate] uses it.
 */
object RecipeDedup {
    data class Key(val platform: String?, val videoId: String?, val normalisedUrl: String)

    fun key(url: String?): Key? {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val norm = normaliseUrl(raw) ?: return null
        val platform = SourcePlatform.fromUrl(raw)
        val id = SourcePlatform.videoId(raw)
        return Key(platform, id, norm)
    }

    /**
     * Lower-cases scheme and host, drops `www.` / `m.`, the fragment, tracking query params and a trailing slash.
     * YouTube keeps only `v=`; Instagram drops the whole query (`?igsh=…`).
     */
    fun normaliseUrl(raw: String): String? {
        val withScheme = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "https://$raw"
        val u = runCatching { URI(withScheme.replace(" ", "%20")) }.getOrNull() ?: return null
        val host = u.host?.lowercase()?.trimEnd('.')?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val path = (u.rawPath ?: "").trimEnd('/')
        val query = u.rawQuery?.split('&')?.filter { p ->
            val k = p.substringBefore('=').lowercase()
            when {
                host.endsWith("youtube.com") -> k == "v"
                host.endsWith("instagram.com") -> false
                else -> !k.startsWith("utm_") && k !in trackingKeys
            }
        }?.sorted()?.joinToString("&")?.takeIf { it.isNotEmpty() }
        return "https://$host$path" + (query?.let { "?$it" } ?: "")
    }

    fun same(a: Key, b: Key): Boolean {
        if (a.videoId != null && b.videoId != null && a.platform != null && a.platform == b.platform) return a.videoId == b.videoId
        return a.normalisedUrl == b.normalisedUrl
    }

    private val trackingKeys = setOf("igsh", "igshid", "si", "feature", "fbclid", "gclid", "ref", "share")
}
