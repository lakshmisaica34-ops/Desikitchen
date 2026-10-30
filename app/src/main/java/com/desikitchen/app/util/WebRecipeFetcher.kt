package com.desikitchen.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

/**
 * Fetches a public recipe web page and extracts a recipe with [RecipeHtmlParser]
 * (JSON-LD → microdata → recipe-card plugins → heading heuristics, OpenGraph for title/image),
 * falling back to [RecipeTextParser] on the page text. No logins, cookies or private content.
 * See SCRAPER.md in the project root.
 */
object WebRecipeFetcher {

    sealed interface Result {
        data class Success(
            val draft: ImportDraft.Draft,
            val fromStructuredData: Boolean,
            val completeness: RecipeHtmlParser.Completeness = RecipeHtmlParser.Completeness.FULL,
            val warnings: List<String> = emptyList(),
        ) : Result
        /**
         * [partial] carries whatever could be read honestly (title, photo, caption) so the UI can offer
         * "open as draft" next to "paste the recipe text"; [pasteText] pre-fills the paste screen.
         */
        data class Failure(
            val message: String,
            val partial: ImportDraft.Draft? = null,
            val pasteText: String? = null,
        ) : Result
    }

    /** Honest app identifier; we don't impersonate a desktop browser and never send cookies. */
    internal val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) DesiKitchen/${com.desikitchen.app.BuildConfig.VERSION_NAME} (recipe import)"
    private const val MAX_BYTES = 5_000_000

    /**
     * Instagram has [InstagramImporter]; TikTok / YouTube / Pinterest / Facebook have [SocialRecipeImporter].
     * These still need a login and have no public post data we use.
     */
    private val loginOnlyHosts = listOf("threads.net", "x.com", "twitter.com")

    /** Importers behind [fetch]; swappable in tests (e.g. to prove a throwing importer can't escape). */
    @Volatile internal var instagramImport: (String) -> Result = { InstagramImporter.import(it) }
    @Volatile internal var socialImport: (String) -> Result = { SocialRecipeImporter.import(it) }

    /**
     * Never throws (except coroutine cancellation): every path, including Instagram / social importers, AI calls and
     * OutOfMemoryError from a large video, ends in [Result.Failure] so an import can't close the app.
     */
    suspend fun fetch(rawUrl: String, onStage: ((ImportStage) -> Unit)? = null): Result = withContext(Dispatchers.IO) {
        val job = coroutineContext[kotlinx.coroutines.Job]
        try {
            // Each real cascade stage is reported to [onStage]; a cancelled job stops the cascade at the next stage.
            ImportProgress.withListener({ stage -> job?.ensureActive(); onStage?.invoke(stage) }) {
                ImportProgress.report(ImportStage.READING_LINK)
                fetchUnsafe(rawUrl)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            // OOM, ExceptionInInitializerError / NoClassDefFoundError (static init), network, JSON, … → readable text.
            ImportErrors.log("web import failed", e)
            Result.Failure(ImportErrors.userMessage(e))
        }
    }

    private fun fetchUnsafe(rawUrl: String): Result {
        val url = when (val v = validate(rawUrl)) {
            is ImportUrlValidator.UrlCheck.Ok -> v.url
            is ImportUrlValidator.UrlCheck.Invalid -> return Result.Failure(v.message)
        }
        val host = runCatching { URL(url).host.lowercase() }.getOrDefault("")
        if (InstagramImporter.isInstagram(url)) return instagramImport(url)
        if (SocialRecipeImporter.platformOf(url) != null) return socialImport(url)
        if (loginOnlyHosts.any { host == it || host.endsWith(".$it") }) {
            return Result.Failure("Posts on social apps need a login, so they can't be read here. Open the post, copy the caption and use Paste import.")
        }
        val page = try {
            download(url)
        } catch (e: BlockedException) {
            return Result.Failure(e.message ?: "The site blocked the request.")
        } catch (e: Exception) {
            return Result.Failure("Couldn't open the page (${e.message ?: e.javaClass.simpleName}). Check the link or paste the recipe text instead.")
        }
        return parseHtml(page.html, page.finalUrl)
    }

    /** Network-free entry point (used by tests and the share flow). */
    fun parseHtml(html: String, url: String): Result {
        if (looksLikeChallenge(html)) return Result.Failure(BLOCKED_MESSAGE)
        RecipeHtmlParser.parse(html, url)?.let {
            return Result.Success(it.draft, it.fromStructuredData, it.completeness, it.warnings)
        }
        // Last resort: plain page text through the paste parser.
        val text = RecipeHtmlParser.Text.htmlToText(
            html.replace(Regex("<(script|style|noscript|nav|header|footer)[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " "),
        )
        val parsed = RecipeTextParser.parse(text)
        if (parsed.ingredients.isEmpty() && parsed.steps.isEmpty()) {
            return Result.Failure("No recipe found on that page. Copy the recipe text and use Paste import.")
        }
        val title = SafeRegex.of("WebRecipeFetcher.title", "<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.let(RecipeHtmlParser.Text::clean)
        return Result.Success(
            ImportDraft.Draft(parsed.copy(title = title?.takeIf { it.isNotBlank() } ?: parsed.title), sourceUrl = url),
            fromStructuredData = false,
            completeness = RecipeHtmlParser.Completeness.PARTIAL,
            warnings = listOf("Read from the page text, please review carefully."),
        )
    }

    fun extractUrl(text: String): String? = ImportUrlValidator.extractUrl(text)

    internal fun normalise(raw: String): String? = (validate(raw) as? ImportUrlValidator.UrlCheck.Ok)?.url

    /** 1.1.6: see [ImportUrlValidator]; kept here so existing callers compile. */
    fun validate(raw: String): ImportUrlValidator.UrlCheck = ImportUrlValidator.validate(raw)

    internal class BlockedException(message: String) : Exception(message)
    private const val BLOCKED_MESSAGE =
        "This site shows a bot check (Cloudflare) instead of the recipe, so it can't be imported automatically. Open it in Chrome, copy the recipe and use Paste import."

    private fun looksLikeChallenge(html: String): Boolean {
        val head = html.take(4000)
        return head.contains("<title>Just a moment...</title>", true) ||
            head.contains("challenges.cloudflare.com") || head.contains("cf-browser-verification") ||
            head.contains("<title>Attention Required! | Cloudflare</title>", true)
    }

    data class Page(val html: String, val finalUrl: String)

    internal fun download(url: String, accept: String = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"): Page {
        var current = url
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", accept)
                setRequestProperty("Accept-Language", "en-IN,en;q=0.9")
                setRequestProperty("Accept-Encoding", "gzip")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: error("redirect without location")
                    current = URL(URL(current), loc).toString()
                    if (current.startsWith("http://")) current = "https://" + current.substring(7)
                    return@repeat
                }
                val stream = (if (code >= 400) conn.errorStream else conn.inputStream) ?: error("HTTP $code")
                val bytes = (if ("gzip".equals(conn.contentEncoding, true)) GZIPInputStream(stream) else stream).use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (out.size() < MAX_BYTES) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
                val html = String(bytes, charsetOf(conn.contentType, bytes))
                if (code in 200..299) return Page(html, current)
                if (code == 403 || code == 429 || code == 503) {
                    if (looksLikeChallenge(html) || conn.getHeaderField("cf-mitigated") != null) throw BlockedException(BLOCKED_MESSAGE)
                    throw BlockedException("The site refused the request (HTTP $code). Open it in Chrome, copy the recipe and use Paste import.")
                }
                if (code == 404 || code == 410) error("page not found, HTTP $code")
                error("HTTP $code")
            } finally {
                conn.disconnect()
            }
        }
        error("too many redirects")
    }

    private fun charsetOf(contentType: String?, bytes: ByteArray): Charset {
        val fromHeader = contentType?.let { Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        val fromMeta = fromHeader ?: Regex("<meta[^>]+charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE)
            .find(String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1))?.groupValues?.get(1)
        return fromMeta?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
    }
}
