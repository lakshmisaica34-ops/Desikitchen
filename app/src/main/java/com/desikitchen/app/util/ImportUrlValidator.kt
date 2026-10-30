package com.desikitchen.app.util

import java.net.URL

/**
 * 1.1.6: the offline link check that runs BEFORE any importer code is touched.
 *
 * It's self-contained on purpose: it only uses java.net.URL and its own patterns, and never references
 * WebRecipeFetcher, InstagramImporter, CaptionRecipeParser or any other parser. Validating a link can't load or
 * initialise a parser class, so a parser that's broken on a phone can't make the link check fail.
 */
object ImportUrlValidator {

    sealed interface UrlCheck {
        data class Ok(val url: String) : UrlCheck
        data class Invalid(val message: String) : UrlCheck
    }

    const val EMPTY = "Paste a recipe link first."
    const val NOT_A_LINK =
        "That doesn't look like a web address. Paste the full link, starting with https:// (on Instagram: Share → Copy link)."
    const val NOT_AN_IG_POST =
        "That Instagram link isn't a post or reel. Open the reel, tap Share → Copy link, and try again."

    const val UNSUPPORTED =
        "DesiKitchen imports from YouTube and Instagram for now. Paste a YouTube video/Short or an Instagram reel/post link."

    private val urlInText = SafeRegex.of("ImportUrlValidator.urlInText", "https?://[^\\s\"'<>]+")
    private val bareDomain = SafeRegex.of("ImportUrlValidator.bareDomain", "^[\\p{L}0-9-]+(\\.[\\p{L}0-9-]+)*\\.[\\p{L}]{2,}(:\\d{1,5})?([/?#]\\S*)?$")
    private val hostPattern = SafeRegex.of("ImportUrlValidator.hostPattern", "^[\\p{L}0-9-]+(\\.[\\p{L}0-9-]+)*\\.[\\p{L}0-9-]{2,}$")
    private val igPost = SafeRegex.of("ImportUrlValidator.igPost", "instagram\\.com/(?:[A-Za-z0-9_.]+/)?(p|reel|reels|tv)/([A-Za-z0-9_-]{5,})", RegexOption.IGNORE_CASE)

    fun extractUrl(text: String): String? = urlInText.find(text)?.value?.trimEnd(')', ']', '.', ',', '!', '?', ';')

    /**
     * Accepts an http(s) link anywhere in the text, or a bare "site.com/path". Rejects fragments such as
     * `G/?srtk=…` or `k=MWpo…==` (the tail of a link), plain words, hosts without a dot, and Instagram links that
     * aren't a post or reel. http:// is upgraded to https://.
     */
    fun validate(raw: String): UrlCheck {
        val t = raw.trim()
        if (t.isEmpty()) return UrlCheck.Invalid(EMPTY)
        val found = extractUrl(t) ?: if (bareDomain.matches(t)) "https://$t" else return UrlCheck.Invalid(NOT_A_LINK)
        val u = if (found.startsWith("http://", ignoreCase = true)) "https://" + found.substring(7) else found
        val host = runCatching { URL(u).host.lowercase().trimEnd('.') }.getOrNull()
        if (host.isNullOrBlank() || !hostPattern.matches(host)) return UrlCheck.Invalid(NOT_A_LINK)
        val isInstagram = host == "instagram.com" || host.endsWith(".instagram.com")
        if (isInstagram && igPost.find(u) == null) return UrlCheck.Invalid(NOT_AN_IG_POST)
        return UrlCheck.Ok(u)
    }

    /** True for youtube.com / youtu.be / instagram.com hosts (incl. subdomains such as m. / www.). */
    fun isSupportedHost(url: String): Boolean {
        val host = runCatching { URL(url).host.lowercase().trimEnd('.') }.getOrNull() ?: return false
        fun on(d: String) = host == d || host.endsWith(".$d")
        return on("youtube.com") || on("youtu.be") || on("instagram.com") || on("instagr.am")
    }

    /**
     * Phase 1 gate used by the UI and the Android share sheet (Sai lock #3): only YouTube and Instagram links are
     * importable. [validate] stays generic so the parser fixtures/tests keep covering website recipes.
     */
    fun validateSupported(raw: String): UrlCheck = when (val c = validate(raw)) {
        is UrlCheck.Invalid -> c
        is UrlCheck.Ok -> if (isSupportedHost(c.url)) c else UrlCheck.Invalid(UNSUPPORTED)
    }

    /** Hard cap on links taken from one paste, so a giant paste can't queue hundreds of network imports. */
    const val MAX_LINKS_PER_PASTE = 20

    private val schemeStart = SafeRegex.of("ImportUrlValidator.schemeStart", "(?=https?://)", RegexOption.IGNORE_CASE)

    /**
     * Every link in a paste, in order: splits on whitespace/newlines, finds each http(s) link (also when two are
     * glued together, e.g. "https://a,https://b"), and keeps bare "site.com/path" tokens. Not validated or deduped.
     */
    fun extractAllUrls(text: String): List<String> = text.split(Regex("\\s+")).filter { it.isNotBlank() }.flatMap { token ->
        val pieces = token.split(schemeStart).filter { it.isNotBlank() }
        val urls = pieces.mapNotNull { extractUrl(it) }
        if (urls.isNotEmpty()) urls
        else token.trimEnd(')', ']', '.', ',', '!', '?', ';').takeIf { bareDomain.matches(it) }?.let(::listOf).orEmpty()
    }

    /**
     * Key used to drop the same link pasted twice: host lowercased without "www."/"m.", no fragment, no
     * trailing slash, and share-tracking params (igsh, si, utm_*, feature) removed.
     */
    fun dedupeKey(url: String): String = runCatching {
        val u = URL(url)
        val host = u.host.lowercase().trimEnd('.').removePrefix("www.").removePrefix("m.")
        val query = u.query.orEmpty().split('&').filter { it.isNotBlank() }
            .filterNot { p -> val k = p.substringBefore('=').lowercase(); k == "igsh" || k == "si" || k == "feature" || k.startsWith("utm_") }
            .sorted().joinToString("&")
        host + u.path.trimEnd('/') + (if (query.isEmpty()) "" else "?$query")
    }.getOrDefault(url.trim().trimEnd('/'))

    /** Result of reading a multi-link paste. [valid] are deduped, supported (YT/IG) links ready to import. */
    data class Batch(val valid: List<String>, val skipped: Int, val firstError: String?, val duplicatesInPaste: Int = 0)

    /**
     * Reads one paste that may hold several links: each is checked with [validateSupported]; duplicates in the
     * same paste are dropped. With no link at all, [firstError] is the same message a single-link paste gets.
     */
    fun parseMany(text: String): Batch {
        val found = extractAllUrls(text)
        if (found.isEmpty()) {
            val c = runCatching { validateSupported(text) }.getOrElse { UrlCheck.Invalid(NOT_A_LINK) }
            return if (c is UrlCheck.Ok) Batch(listOf(c.url), 0, null) else Batch(emptyList(), 0, (c as UrlCheck.Invalid).message)
        }
        val seen = HashSet<String>()
        val valid = ArrayList<String>()
        var skipped = 0
        var dupes = 0
        var firstError: String? = null
        for (raw in found) {
            when (val c = runCatching { validateSupported(raw) }.getOrElse { UrlCheck.Invalid(NOT_A_LINK) }) {
                is UrlCheck.Ok -> if (seen.add(dedupeKey(c.url))) valid += c.url else dupes++
                is UrlCheck.Invalid -> { skipped++; if (firstError == null) firstError = c.message }
            }
        }
        val capped = valid.take(MAX_LINKS_PER_PASTE)
        return Batch(capped, skipped + (valid.size - capped.size), firstError, dupes)
    }
}
