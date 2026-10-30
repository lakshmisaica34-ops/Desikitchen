package com.desikitchen.app.util

import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URL
import java.net.URLEncoder

/**
 * TikTok, YouTube, Pinterest and Facebook import — public data only, never a private app API (see SCRAPER.md):
 *  1. Public endpoints: TikTok + YouTube oEmbed, YouTube watch-page description, Pinterest pin page (and the
 *     recipe site it links to), Facebook OpenGraph tags. Honest User-Agent, no cookies, no login.
 *  2. Caption / description → [CaptionRecipeParser] (copied text only).
 *  3. When CREATORS_API_* is configured: Scrape Creators public-post endpoint only (never a transcript) for the full caption.
 *  4. When OPENROUTER_API_KEY is configured: AI reads a recipe-like caption ([TextRecipeExtractor]) or the video
 *     ([VideoRecipeExtractor]); always PARTIAL with a "check it" warning; the prompt forbids invented content.
 *  5. Otherwise an honest Failure with whatever was read (title / photo) and the caption pre-filled for Paste import.
 */
object SocialRecipeImporter {
    enum class Platform(val label: String) { TIKTOK("TikTok"), YOUTUBE("YouTube"), PINTEREST("Pinterest"), FACEBOOK("Facebook") }

    data class PublicPost(
        val caption: String? = null,
        val title: String? = null,
        val author: String? = null,
        val imageUrl: String? = null,
        val videoUrl: String? = null,
        /** Pinterest: the website the pin links to (usually the real recipe). */
        val outboundUrl: String? = null,
        val canonicalUrl: String? = null,
        /** Creator profile/channel URL as reported (YouTube oEmbed `author_url` / Creators `channel.url`). */
        val authorUrl: String? = null,
    )

    fun platformOf(url: String): Platform? {
        val host = runCatching { URL(url).host.lowercase().removePrefix("www.").removePrefix("m.") }.getOrDefault("")
        fun h(vararg d: String) = d.any { host == it || host.endsWith(".$it") }
        return when {
            h("tiktok.com") -> Platform.TIKTOK
            h("youtube.com", "youtu.be", "youtube-nocookie.com") -> Platform.YOUTUBE
            h("pinterest.com", "pin.it") || Regex("^([a-z]{2}\\.)?pinterest\\.[a-z.]{2,6}$").matches(host) -> Platform.PINTEREST
            h("facebook.com", "fb.watch", "fb.com") -> Platform.FACEBOOK
            else -> null
        }
    }

    /** youtu.be/ID, /shorts/ID, /watch?v=ID, /live/ID, /embed/ID → watch URL. */
    fun youtubeId(url: String): String? =
        Regex("(?:youtu\\.be/|/shorts/|/live/|/embed/|[?&]v=)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)

    fun canonical(platform: Platform, url: String): String = when (platform) {
        Platform.YOUTUBE -> youtubeId(url)?.let { "https://www.youtube.com/watch?v=$it" } ?: url
        else -> url.substringBefore('#')
    }

    // ---------- public parsers (network-free, unit-tested) ----------

    fun parseOEmbed(json: String): PublicPost {
        val o = JSONObject(json)
        fun s(k: String) = o.optString(k, "").trim().takeIf { it.isNotEmpty() && it != "null" }
        return PublicPost(caption = s("title"), author = s("author_unique_id") ?: s("author_name"), imageUrl = s("thumbnail_url"), authorUrl = s("author_url"))
    }

    /** YouTube watch page: full description lives in ytInitialPlayerResponse.videoDetails.shortDescription. */
    fun parseYoutubeWatch(html: String): PublicPost {
        val desc = SafeRegex.of("SocialRecipeImporter.desc", "\"shortDescription\":\"((?:\\\\.|[^\"\\\\])*)\"").find(html)?.groupValues?.get(1)
            ?.let { runCatching { JSONObject("{\"d\":\"$it\"}").getString("d") }.getOrNull() }
        val title = SafeRegex.of("SocialRecipeImporter.title", "\"videoDetails\":\\{[^{}]*?\"title\":\"((?:\\\\.|[^\"\\\\])*)\"").find(html)?.groupValues?.get(1)
            ?.let { runCatching { JSONObject("{\"d\":\"$it\"}").getString("d") }.getOrNull() }
        val og = parseOpenGraph(html)
        return og.copy(caption = desc?.takeIf { it.isNotBlank() } ?: og.caption, title = title ?: og.title)
    }

    /** OpenGraph / twitter / JSON-LD description for Pinterest, Facebook and fallbacks. */
    fun parseOpenGraph(html: String): PublicPost {
        val doc = Jsoup.parse(html)
        fun meta(vararg names: String) = names.firstNotNullOfOrNull { n ->
            (doc.selectFirst("meta[property=$n]") ?: doc.selectFirst("meta[name=$n]"))?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
        }
        return PublicPost(
            caption = meta("og:description", "twitter:description", "description"),
            title = meta("og:title", "twitter:title"),
            imageUrl = meta("og:image", "twitter:image"),
            videoUrl = meta("og:video:secure_url", "og:video:url", "og:video")?.takeIf { it.startsWith("https://") },
            canonicalUrl = meta("og:url"),
        )
    }

    /** Pinterest pin page: description, image and the outbound "link" to the source website. */
    fun parsePinterest(html: String): PublicPost {
        val og = parseOpenGraph(html)
        val doc = Jsoup.parse(html)
        val jsonLd = doc.select("script[type=application/ld+json]").mapNotNull { runCatching { JSONObject(it.data()) }.getOrNull() }
        val ldDesc = jsonLd.firstNotNullOfOrNull { it.optString("articleBody").takeIf(String::isNotBlank) ?: it.optString("description").takeIf(String::isNotBlank) }
        val outbound = listOfNotNull(
            doc.selectFirst("meta[property=pinterestapp:source]")?.attr("content"),
            doc.selectFirst("meta[property=og:see_also]")?.attr("content"),
            Regex("\"link\":\"(https?:[^\"]+)\"").findAll(html).map { it.groupValues[1].replace("\\/", "/").replace("\\u0026", "&") }
                .firstOrNull { platformOf(it) != Platform.PINTEREST },
        ).firstOrNull { it.startsWith("http") && platformOf(it) != Platform.PINTEREST }
        return og.copy(caption = listOfNotNull(ldDesc, og.caption).maxByOrNull { it.length }, outboundUrl = outbound)
    }

    private val notRecipeHosts = SafeRegex.of("SocialRecipeImporter.notRecipeHosts", "(^|\\.)(instagram|facebook|fb|tiktok|twitter|x|threads|amazon|amzn|flipkart|meesho|whatsapp|t|bit|linktr|spotify|apple|google|wa)\\.(com|me|ly|ee|in|net|co|to)$|youtu", RegexOption.IGNORE_CASE)

    /** First link in a caption / description that points at a website (not a social app, shop or shortener). */
    fun recipeLinkIn(caption: String?): String? = caption?.let { c ->
        Regex("https?://[^\\s\"'<>)\\]]+").findAll(c).map { it.value.trimEnd('.', ',', '!', ';') }.firstOrNull { link ->
            val host = runCatching { URL(link).host.lowercase().removePrefix("www.") }.getOrDefault("")
            host.contains('.') && platformOf(link) == null && !notRecipeHosts.containsMatchIn(host)
        }
    }

    // ---------- import ----------

    data class Deps(
        val download: (url: String, accept: String) -> WebRecipeFetcher.Page = { u, a -> WebRecipeFetcher.download(u, a) },
        /** Full web import for a Pinterest pin's source site. */
        val web: (String) -> WebRecipeFetcher.Result = { u -> runCatching { WebRecipeFetcher.download(u).let { WebRecipeFetcher.parseHtml(it.html, it.finalUrl) } }.getOrElse { WebRecipeFetcher.Result.Failure(it.message ?: "") } },
        val creators: SocialCreatorsClient? = SocialCreatorsClient.fromBuildConfig(),
        val text: TextRecipeExtractor? = TextRecipeExtractor.fromBuildConfig(),
        val video: VideoRecipeExtractor? = VideoRecipeExtractor.fromBuildConfig(),
    )

    /**
     * Blocking; WebRecipeFetcher.fetch calls it on Dispatchers.IO.
     * YouTube drafts (success or failure partial) carry the channel URL the source reported
     * ([ImportDraft.Draft.creatorProfileUrl]) so "Visit YouTube channel" works; nothing is derived or invented.
     */
    fun import(rawUrl: String, deps: Deps = Deps()): WebRecipeFetcher.Result {
        val profile = arrayOfNulls<String>(1)
        val r = importCore(rawUrl, deps, profile)
        return if (platformOf(rawUrl) == Platform.YOUTUBE) CreatorProfile.attach(r, null, CreatorProfile.safeProfileUrl(profile[0], com.desikitchen.app.data.model.SourcePlatform.YOUTUBE)) else r
    }

    private fun importCore(rawUrl: String, deps: Deps, profileOut: Array<String?>): WebRecipeFetcher.Result {
        val platform = platformOf(rawUrl) ?: return WebRecipeFetcher.Result.Failure("That link isn't a TikTok, YouTube, Pinterest or Facebook post.")
        val notes = mutableListOf<String>()
        var url = canonical(platform, rawUrl)
        var post = PublicPost()

        // 1. Public data.
        runCatching {
            when (platform) {
                Platform.TIKTOK -> {
                    if (!Regex("/video/\\d+|/photo/\\d+").containsMatchIn(url)) url = deps.download(url, HTML).finalUrl.substringBefore('?')
                    post = parseOEmbed(deps.download("https://www.tiktok.com/oembed?url=" + enc(url), JSON).html)
                }
                Platform.YOUTUBE -> {
                    val o = runCatching { parseOEmbed(deps.download("https://www.youtube.com/oembed?format=json&url=" + enc(url), JSON).html) }.getOrNull()
                    val w = runCatching { parseYoutubeWatch(deps.download(url, HTML).html) }.getOrNull()
                    post = PublicPost(
                        caption = w?.caption, title = o?.caption ?: w?.title, author = o?.author,
                        imageUrl = o?.imageUrl ?: w?.imageUrl, authorUrl = o?.authorUrl,
                    )
                    profileOut[0] = post.authorUrl
                }
                Platform.PINTEREST -> {
                    val page = deps.download(url, HTML)
                    url = page.finalUrl.substringBefore('?')
                    post = parsePinterest(page.html)
                }
                Platform.FACEBOOK -> {
                    val page = deps.download(url, HTML)
                    post = parseOpenGraph(page.html).let { og ->
                        // Login walls put a generic sentence in og:description; ignore those.
                        if (og.caption?.contains("log in", true) == true || og.caption?.contains("log into facebook", true) == true) og.copy(caption = null) else og
                    }
                }
            }
        }.onFailure { notes += "public page: ${it.message}" }

        // Pinterest: the pin usually links to the real recipe page.
        post.outboundUrl?.let { out ->
            val r = deps.web(out)
            if (r is WebRecipeFetcher.Result.Success) {
                return r.copy(draft = r.draft.copy(
                    imageUrl = r.draft.imageUrl ?: post.imageUrl,
                    description = r.draft.description.ifBlank { "Saved from Pinterest" },
                    warnings = r.draft.warnings + "Imported from the website this pin links to.",
                ))
            } else notes += "pin source: ${(r as WebRecipeFetcher.Result.Failure).message.take(80)}"
        }

        // 2. Caption / description has the recipe.
        ImportProgress.report(ImportStage.CAPTION)
        // Soft photo-led: if we have a recipe but no cover yet, wait for Creators (step 3) to fill imageUrl.
        var captionWin: WebRecipeFetcher.Result.Success? = fromCaption(platform, post, url)
        if (captionWin != null && (captionWin!!.draft.imageUrl != null || deps.creators == null)) {
            ImportProgress.report(ImportStage.FINISHING); return captionWin!!
        }

        // 2b. Description only links to the creator's recipe page ("Full Recipe: https://…") → normal web import.
        //     Harness: Hebbar's curd dosa short → hebbarskitchen.com JSON-LD, FULL.
        recipeLinkIn(post.caption)?.let { link ->
            ImportProgress.report(ImportStage.LINKED_PAGE)
            val r = runCatching { deps.web(link) }.getOrElse { WebRecipeFetcher.Result.Failure(it.message ?: "") }
            if (r is WebRecipeFetcher.Result.Success) {
                return r.copy(draft = r.draft.copy(
                    imageUrl = r.draft.imageUrl ?: post.imageUrl,
                    description = r.draft.description.ifBlank { byline(platform, post.author) },
                    warnings = r.draft.warnings + "Imported from the recipe page linked in the ${platform.label} description.",
                ))
            } else notes += "description link: ${(r as WebRecipeFetcher.Result.Failure).message.take(60)}"
        }

        // 3. Scrape Creators public-post endpoint only (1 credit): fuller caption, video URL. Never a transcript (1.1.2).
        deps.creators?.let { c ->
            ImportProgress.report(ImportStage.POST_DETAILS)
            runCatching { c.fetchPost(platform, url) }.onSuccess { p ->
                post = post.copy(
                    caption = listOfNotNull(p.caption, post.caption).maxByOrNull { it.length },
                    title = post.title ?: p.title, author = post.author ?: p.author,
                    imageUrl = post.imageUrl ?: p.imageUrl, videoUrl = p.videoUrl ?: post.videoUrl,
                    authorUrl = post.authorUrl ?: p.authorUrl,
                )
                profileOut[0] = post.authorUrl
                fromCaption(platform, post, url)?.let { return it }
                // Creators may have filled imageUrl; commit an earlier caption-only win with the cover.
                captionWin?.let { early ->
                    ImportProgress.report(ImportStage.FINISHING)
                    return early.copy(draft = early.draft.copy(imageUrl = early.draft.imageUrl ?: ImportImageStore.persist(post.imageUrl, key(platform, url))))
                }
            }.onFailure { notes += "creators API: ${it.message}" }
        }
        captionWin?.let { early ->
            ImportProgress.report(ImportStage.FINISHING)
            return early.copy(draft = early.draft.copy(imageUrl = early.draft.imageUrl ?: ImportImageStore.persist(post.imageUrl, key(platform, url))))
        }

        // 4. Cheap text AI only when the caption already looks like a recipe the rules missed; then Gemini reads the
        //    remote video URL (no download on the phone unless OpenRouter refuses the URL).
        val aiText = listOfNotNull(post.title?.let { "Title: $it" }, post.caption?.let { "Caption/description:\n$it" }).joinToString("\n\n")
        if (deps.text != null && InstagramImporter.looksLikeRecipeText(post.caption)) {
            runCatching { deps.text.extract(platform.label, aiText) }.onSuccess { x ->
                // 2026-10-29: text AI may only copy; ground ingredients and steps against the caption.
                x?.let { e -> e.copy(ingredients = RecipeGrounding.grounded(e.ingredients, aiText), steps = RecipeSanitizer.groundedSteps(e.steps, aiText)) }
                    ?.takeIf { it.ingredients.isNotEmpty() || it.steps.isNotEmpty() }
                    ?.let { return aiResult(platform, post, url, it, "the caption") }
            }.onFailure { notes += "AI: ${it.message}" }
        }
        if (deps.video != null && post.videoUrl != null) {
            ImportProgress.report(ImportStage.VIDEO)
            runCatching { deps.video.extract(post.videoUrl!!, post.caption) }.onSuccess { x ->
                x?.let { return aiResult(platform, post, url, it, "the video (speech and on-screen text)") }
            }.onFailure { notes += "video AI: ${it.message}" }
        }

        // 5. Honest failure + paste prefill.
        val title = (post.title ?: post.caption?.let { CaptionRecipeParser.parse(it).parsed.title })?.takeIf { it != "Imported recipe" }
            ?.let(CaptionRecipeParser::cleanTitle)
        val partial = if (title != null || post.imageUrl != null) ImportDraft.Draft(
            RecipeTextParser.ParsedRecipe(title ?: "${platform.label} recipe", null, emptyList(), emptyList()),
            description = byline(platform, post.author),
            imageUrl = ImportImageStore.persist(post.imageUrl, key(platform, url)), sourceUrl = url,
            warnings = listOf("The post doesn't include the recipe text. Add the ingredients and steps yourself."),
            partial = true,
        ) else null
        val why = when {
            post.caption == null && post.title == null ->
                "${platform.label} didn't share this post publicly (it may be private, removed, or need a login)."
            else -> "This ${platform.label} post's caption doesn't contain the recipe (no ingredient list or steps)."
        }
        val setup = when {
            deps.creators == null && deps.text == null && deps.video == null -> " Reading recipes from the video isn't set up in this build."
            deps.text == null && deps.video == null -> " AI reading isn't set up in this build."
            else -> ""
        }
        val pasteText = listOfNotNull(post.title, post.caption).distinct().joinToString("\n\n").ifBlank { null }
        return WebRecipeFetcher.Result.Failure(
            "$why$setup If the creator posted the recipe in a comment or on their site, copy it and use Paste import." +
                if (notes.isNotEmpty()) "\n(${notes.joinToString("; ").take(200)})" else "",
            partial = partial,
            pasteText = pasteText,
        )
    }

    private const val HTML = "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8"
    private const val JSON = "application/json"
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun key(p: Platform, url: String) = p.name.lowercase() + "_" + url.hashCode().toUInt().toString(16)
    private fun byline(p: Platform, author: String?) = author?.let { "By ${if (p == Platform.YOUTUBE || it.contains(' ')) it else "@$it"} on ${p.label}" }.orEmpty()

    private fun fromCaption(platform: Platform, post: PublicPost, url: String): WebRecipeFetcher.Result.Success? {
        val caption = post.caption ?: return null
        val text = if (post.title != null && !caption.startsWith(post.title)) post.title + "\n" + caption else caption
        val r = InstagramImporter.fromCaption(
            InstagramImporter.PostInfo(text, null, post.imageUrl, post.videoUrl), url,
            via = if (platform == Platform.YOUTUBE) "description" else "caption",
        ) ?: return null
        return r.copy(draft = r.draft.copy(
            description = byline(platform, post.author),
            imageUrl = ImportImageStore.persist(r.draft.imageUrl, key(platform, url)),
        ))
    }

    private fun aiResult(platform: Platform, post: PublicPost, url: String, x: VideoRecipeExtractor.Extracted, from: String): WebRecipeFetcher.Result.Success {
        val title = x.title?.let(CaptionRecipeParser::cleanTitle)?.takeIf { it.isNotBlank() }
            ?: post.title?.let(CaptionRecipeParser::cleanTitle) ?: "${platform.label} recipe"
        val c = RecipeSanitizer.cleanAi(x)
        val parsed = RecipeSanitizer.clean(RecipeTextParser.ParsedRecipe(title, c.servings, c.ingredients.map(RecipeTextParser::parseIngredient), c.steps))
        val warnings = listOfNotNull("Read by AI from $from. Check every quantity against the post before cooking.", RecipeSanitizer.aiWarning(c))
        val draft = ImportDraft.Draft(
            parsed, description = byline(platform, post.author),
            imageUrl = ImportImageStore.persist(post.imageUrl, key(platform, url)), sourceUrl = url, warnings = warnings, partial = true,
        )
        return WebRecipeFetcher.Result.Success(draft, false, RecipeHtmlParser.Completeness.PARTIAL, warnings)
    }
}
