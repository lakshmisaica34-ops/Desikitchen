package com.desikitchen.app.util

import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.model.SourcePlatform
import java.net.URI

/**
 * Creator identity derived ONLY from what an import stored on the recipe ([Recipe.creatorHandle],
 * [Recipe.sourcePlatform], [Recipe.creatorAvatarUrl], [Recipe.creatorProfileUrl]). Device-only; nothing is invented.
 *
 * Profile URL ([profileUrl]), first match wins:
 *  1. explicit URL the source reported (YouTube `channel.url` / oEmbed `author_url`, …) when it is https on the
 *     platform's own host (instagram.com for IG, youtube.com for YT);
 *  2. Instagram: `https://www.instagram.com/<handleWithoutAt>/` when the handle is a valid IG username;
 *  3. YouTube: `https://www.youtube.com/@<handle>` only when the stored handle itself is an "@handle" (no spaces);
 *     a plain channel title ("Hebbars Kitchen") gives null;
 *  4. web / manual / unknown: null.
 */
object CreatorProfile {
    /** Claim inbox shown on unclaimed creator pages. */
    const val CLAIM_EMAIL = "creators@desikitchen.app"
    const val UNCLAIMED_COPY =
        "This creator hasn\u2019t claimed their DesiKitchen page yet. If that\u2019s you, email $CLAIM_EMAIL to claim it."

    private val IgUsername = Regex("^[A-Za-z0-9._]{1,30}$")
    private val YtHandle = Regex("^[A-Za-z0-9._-]{3,30}$")

    /** Stored handle trimmed; null when blank. */
    fun handleOf(r: Recipe): String? = r.creatorHandle?.trim()?.takeIf { it.isNotEmpty() }

    /** Match key (same rule as the DAO): trim, strip leading "@", collapse spaces, case-fold. */
    fun key(handle: String): String = handle.trim().removePrefix("@").trim().replace(Regex("\\s+"), " ").lowercase()

    /**
     * Claim status. Device-only build: there is no claim registry yet (no cloud), so every creator is UNCLAIMED and
     * Soft's banner is always shown. When a backend exists, swap this for a lookup; callers don't change.
     */
    @Suppress("UNUSED_PARAMETER")
    fun isClaimed(handle: String): Boolean = false

    fun profileUrl(platform: String?, handle: String?, explicitUrl: String? = null): String? {
        safeProfileUrl(explicitUrl, platform)?.let { return it }
        val raw = handle?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val h = raw.removePrefix("@").trim()
        return when (platform) {
            SourcePlatform.INSTAGRAM -> if (IgUsername.matches(h)) "https://www.instagram.com/$h/" else null
            SourcePlatform.YOUTUBE -> if (raw.startsWith("@") && YtHandle.matches(h)) "https://www.youtube.com/@$h" else null
            else -> null
        }
    }

    fun profileUrl(r: Recipe): String? = profileUrl(r.sourcePlatform, r.creatorHandle, r.creatorProfileUrl)

    /** "Follow on Instagram" / "Visit YouTube channel" for a non-null [profileUrl]; null otherwise. */
    fun followLabel(platform: String?): String? = when (platform) {
        SourcePlatform.INSTAGRAM -> "Follow on Instagram"
        SourcePlatform.YOUTUBE -> "Visit YouTube channel"
        else -> null
    }

    /**
     * A source-reported profile URL worth storing: https (http is upgraded) on instagram.com / youtube.com, and — when
     * [platform] is given — on that platform's host. Anything else → null.
     */
    fun safeProfileUrl(url: String?, platform: String? = null): String? {
        val u = url?.trim()?.takeIf { it.isNotEmpty() && it.length <= 512 && it.none(Char::isWhitespace) } ?: return null
        val https = if (u.startsWith("http://", ignoreCase = true)) "https://" + u.substring(7) else u
        if (!https.startsWith("https://", ignoreCase = true)) return null
        val host = runCatching { URI(https).host?.lowercase() }.getOrNull() ?: return null
        fun on(d: String) = host == d || host.endsWith(".$d")
        val p = when {
            on("instagram.com") -> SourcePlatform.INSTAGRAM
            on("youtube.com") -> SourcePlatform.YOUTUBE
            else -> return null
        }
        return https.takeIf { platform == null || platform == p }
    }

    /** Only an https URL may be stored/hotlinked as an avatar; anything else → null (monogram). */
    fun safeAvatarUrl(url: String?): String? =
        url?.trim()?.takeIf { it.length in 9..2048 && it.startsWith("https://", ignoreCase = true) && it.none(Char::isWhitespace) }

    /**
     * Importer helper: stamps the creator avatar / profile the scraper already exposed onto the result's draft (and a
     * failure's partial draft). Values are validated; null inputs leave the draft untouched. Never fetches anything.
     */
    fun attach(r: WebRecipeFetcher.Result, avatarUrl: String?, profileUrl: String?): WebRecipeFetcher.Result {
        val a = safeAvatarUrl(avatarUrl)
        val p = safeProfileUrl(profileUrl)
        if (a == null && p == null) return r
        fun ImportDraft.Draft.with() = copy(creatorAvatarUrl = creatorAvatarUrl ?: a, creatorProfileUrl = creatorProfileUrl ?: p)
        return when (r) {
            is WebRecipeFetcher.Result.Success -> r.copy(draft = r.draft.with())
            is WebRecipeFetcher.Result.Failure -> r.copy(partial = r.partial?.with())
        }
    }

    /** Instagram JSON blobs escape URLs (`https:\/\/…`, `\u0026`, double-escaped in embed HTML); returns a plain URL. */
    internal fun unescapeJsonUrl(s: String): String {
        var t = s.replace("\\\\\\/", "/").replace("\\\\/", "/").replace("\\/", "/")
        t = Regex("\\\\{1,2}u([0-9a-fA-F]{4})").replace(t) { it.groupValues[1].toInt(16).toChar().toString() }
        return t
    }
}
