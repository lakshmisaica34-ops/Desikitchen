package com.desikitchen.app.util

import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import java.net.URL

/**
 * Instagram post / reel import: Sai's 4-step cost cascade (1.1.3, see SCRAPER.md). Stops at the first stage with a
 * trustworthy recipe.
 *  1. FREE: public embed `instagram.com/<p|reel>/<code>/embed/captioned/`, else the post page's og:description →
 *     [CaptionRecipeParser]. Confident RAC (both buckets filled, nothing re-bucketed) → stop, no AI.
 *     1b (2026-10-29, DfEAPhVzLrs): RAC that is incomplete / mixed (e.g. method lines under "Ingredients:") → ONE
 *     cheap text-model call splits the caption into ingredients vs steps ([TextRecipeExtractor.CAPTION_SPLIT_SYSTEM]),
 *     grounded against the caption + [RecipeSanitizer]. Both buckets filled → stop (PARTIAL). Otherwise the best
 *     clean caption draft is kept as a fallback and the cascade continues.
 *  2. ONE Scrape Creators request, GET /instagram/post only (1 credit): video URL, has_audio, fuller caption and the
 *     top comments, all in the same reply. Same confident-or-AI-split rule on that caption. Never an SC transcript.
 *  3. Cheapest OpenRouter text model (OPENROUTER_TEXT_MODEL, default mistralai/mistral-nemo) on the top comments
 *     (+ caption). Ingredients are grounded against that text ([RecipeGrounding]); ≥ 3 → stop, PARTIAL.
 *  4. Keyframes + audio → Gemini ([StillsRecipeExtractor], default google/gemini-2.5-flash-lite): video streamed to a
 *     temp file, ≤ 12 keyframes ~512 px + audio, one call, temp deleted. Only if that returns < 4 ingredients:
 *     one optional full-video re-read via the remote URL ([VideoRecipeExtractor.extractRemoteOnly]), logged in notes.
 *     Photo posts (no video) send the still image instead.
 *  Otherwise: the best clean caption fallback from 1/1b/2 (PARTIAL), else a Failure carrying a partial draft (title + cover photo only) and the caption for Paste import.
 * We never invent ingredients or steps; every AI result is PARTIAL with a "check it" warning.
 */
object InstagramImporter {

    private val codeRegex = SafeRegex.of("InstagramImporter.codeRegex", "instagram\\.com/(?:[A-Za-z0-9_.]+/)?(p|reel|reels|tv)/([A-Za-z0-9_-]{5,})", RegexOption.IGNORE_CASE)

    fun isInstagram(url: String): Boolean {
        val host = runCatching { URL(url).host.lowercase() }.getOrDefault("")
        return host == "instagram.com" || host.endsWith(".instagram.com") || host == "instagr.am"
    }

    /** ("reel", "DF6dMW5vwgw") from any post / reel / tv link, ignoring query strings. */
    fun shortcode(url: String): Pair<String, String>? = codeRegex.find(url)?.let {
        val kind = it.groupValues[1].lowercase().let { k -> if (k == "reels") "reel" else k }
        kind to it.groupValues[2]
    }

    fun embedUrl(kind: String, code: String) = "https://www.instagram.com/$kind/$code/embed/captioned/"
    fun canonicalUrl(kind: String, code: String) = "https://www.instagram.com/$kind/$code/"

    data class PostInfo(
        val caption: String?, val author: String?, val imageUrl: String?, val videoUrl: String?,
        /** Post owner's `profile_pic_url` from the embed JSON: HTTPS hotlink only (signed CDN URL, expires); never downloaded. */
        val avatarUrl: String? = null,
    )

    /** Parses the public embed page. Caption = the `.Caption` block minus username and "View all comments". */
    fun parseEmbed(html: String): PostInfo {
        val doc = Jsoup.parse(html)
        val captionEl = doc.selectFirst("div.Caption")
        val author = captionEl?.selectFirst(".CaptionUsername")?.text()?.trim()
            ?: doc.selectFirst(".UsernameText")?.text()?.trim()
        val caption = captionEl?.clone()?.let { el ->
            el.select(".CaptionUsername, .CaptionComments").remove()
            el.select("br").forEach { it.replaceWith(TextNode("\n")) }
            el.wholeText().replace('\u00A0', ' ').lines().joinToString("\n") { it.trim() }.trim()
        }?.takeIf { it.isNotBlank() }
        val image = doc.selectFirst("img.EmbeddedMediaImage")?.attr("src")?.takeIf { it.startsWith("http") }
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
        val video = SafeRegex.of("InstagramImporter.video", "\\\\?\"video_url\\\\?\":\\s*\\\\?\"(https:[^\"]+?)\\\\?\"").find(html)?.groupValues?.get(1)
            ?.replace("\\\\\\/", "/")?.replace("\\/", "/")?.replace("\\\\u0026", "&")?.replace("\\u0026", "&")
        val avatar = SafeRegex.of("InstagramImporter.ownerAvatar", "owner\\\\*\":\\{[^{}]{0,800}?profile_pic_url\\\\*\":\\s*\\\\*\"(https:[^\"]+?)\\\\*\"")
            .find(html)?.groupValues?.get(1)?.let { CreatorProfile.safeAvatarUrl(CreatorProfile.unescapeJsonUrl(it)) }
        return PostInfo(caption, author, image, video, avatar)
    }

    /** Post page fallback: `og:description` = `123 likes, 4 comments - user on March 1, 2024: "caption…".` */
    fun parsePostPage(html: String): PostInfo {
        val doc = Jsoup.parse(html)
        fun meta(p: String) = doc.selectFirst("meta[property=$p]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[name=$p]")?.attr("content")?.takeIf { it.isNotBlank() }
        val desc = meta("og:description") ?: meta("description")
        val caption = desc?.let { d ->
            Regex("^.*?\\bon [^\\:]{3,40}:\\s*[\"“](.*)[\"”]\\.?\\s*$", RegexOption.DOT_MATCHES_ALL).find(d)?.groupValues?.get(1) ?: d
        }?.takeIf { it.length > 40 && !it.contains("See Instagram photos and videos", true) }
        val author = meta("twitter:title")?.let { Regex("\\(@([\\w.]+)\\)").find(it)?.groupValues?.get(1) }
        return PostInfo(caption, author, meta("og:image"), meta("og:video"))
    }

    /** Network-free: turns a caption into a result, or null when the caption has no recipe. */
    fun fromCaption(info: PostInfo, sourceUrl: String, via: String = "caption"): WebRecipeFetcher.Result.Success? {
        val caption = info.caption ?: return null
        val r = CaptionRecipeParser.parse(caption)
        if (!r.hasRecipe) return null
        val p = r.parsed
        val warnings = buildList {
            if (p.ingredients.size < 2) add("The $via has steps but no ingredient list. Add the ingredients from the video.")
            if (p.steps.isEmpty()) add("The $via lists ingredients but no method. Add the steps from the video.")
            if (p.title == "Imported recipe") add("No dish name in the $via. Please add a title.")
        }
        val draft = ImportDraft.Draft(
            parsed = p,
            description = listOfNotNull(info.author?.let { "By @$it on Instagram" }).joinToString(),
            imageUrl = info.imageUrl,
            sourceUrl = sourceUrl,
            warnings = warnings,
            partial = !r.isComplete,
        )
        return WebRecipeFetcher.Result.Success(
            draft, fromStructuredData = false,
            completeness = if (r.isComplete) RecipeHtmlParser.Completeness.FULL else RecipeHtmlParser.Completeness.PARTIAL,
            warnings = warnings,
        )
    }

    /** Per-import trace for logs / the live check: stage → seconds, plus which stage won. Counts only, no content. */
    data class Trace(val stageSeconds: MutableMap<String, Double> = linkedMapOf(), var winner: String? = null,
                     var scPostCalls: Int = 0, var openRouterCalls: Int = 0, val notes: MutableList<String> = mutableListOf(),
                     /** Caption AI-split calls (stage 1b / 2b). */
                     var captionSplitCalls: Int = 0)
    @Volatile var lastTrace: Trace? = null
        private set

    /**
     * Blocking; WebRecipeFetcher.fetch already runs on Dispatchers.IO. Network pieces are injectable for tests.
     * Every returned draft (success or failure partial) carries the owner avatar the embed / Creators reply exposed
     * ([ImportDraft.Draft.creatorAvatarUrl], HTTPS hotlink, never persisted).
     */
    fun import(
        rawUrl: String,
        download: (String) -> String = { WebRecipeFetcher.download(it).html },
        creators: CreatorsApiClient? = CreatorsApiClient.fromBuildConfig(),
        video: VideoRecipeExtractor? = VideoRecipeExtractor.fromBuildConfig(),
        text: TextRecipeExtractor? = if (creators != null) TextRecipeExtractor.cheapFromBuildConfig() else null,
        stills: StillsRecipeExtractor? = if (video != null) StillsRecipeExtractor.fromBuildConfig() else null,
    ): WebRecipeFetcher.Result {
        val avatar = arrayOfNulls<String>(1)
        val r = importCore(rawUrl, download, creators, video, text, stills, avatar)
        return CreatorProfile.attach(r, avatar[0], null)
    }

    private fun importCore(
        rawUrl: String,
        download: (String) -> String,
        creators: CreatorsApiClient?,
        video: VideoRecipeExtractor?,
        text: TextRecipeExtractor?,
        stills: StillsRecipeExtractor?,
        avatarOut: Array<String?>,
    ): WebRecipeFetcher.Result {
        val trace = Trace().also { lastTrace = it }
        var t0 = System.nanoTime()
        fun lap(stage: String) { val now = System.nanoTime(); trace.stageSeconds[stage] = (now - t0) / 1e9; t0 = now }
        val (kind, code) = shortcode(rawUrl) ?: return WebRecipeFetcher.Result.Failure(
            "That Instagram link isn't a post or reel. Open the reel, tap Share → Copy link, and try again.",
        )
        val source = canonicalUrl(kind, code)
        val notes = trace.notes

        // ---- Stage 1 (FREE): public embed, then the post page's OpenGraph tags → caption parse. ----
        var info = runCatching { parseEmbed(download(embedUrl(kind, code))) }
            .onFailure { notes += "embed: ${it.message}" }.getOrNull()
        if (info?.caption == null) {
            runCatching { parsePostPage(download(source)) }.onFailure { notes += "page: ${it.message}" }.getOrNull()?.let { page ->
                info = PostInfo(page.caption, info?.author ?: page.author, info?.imageUrl ?: page.imageUrl, info?.videoUrl ?: page.videoUrl, info?.avatarUrl)
            }
        }
        val post = info ?: PostInfo(null, null, null, null)
        avatarOut[0] = CreatorProfile.safeAvatarUrl(post.avatarUrl)
        var videoUrl = post.videoUrl
        var caption = post.caption
        var stillUrl = post.imageUrl
        var comments = emptyList<String>()
        fun coverOf(r: WebRecipeFetcher.Result.Success) = r.draft.imageUrl ?: stillUrl ?: post.imageUrl
        /** Every win carries the best cover we have so Soft photo-led heroes aren't monograms. Meta-safe: hotlink only. */
        fun win(stage: String, r: WebRecipeFetcher.Result.Success): WebRecipeFetcher.Result.Success {
            trace.winner = stage; ImportProgress.report(ImportStage.FINISHING)
            val withCover = if (r.draft.imageUrl != null) r else r.copy(draft = r.draft.copy(imageUrl = coverOf(r)))
            return withLocalImage(withCover, code)
        }
        fun aiSuccess(x: VideoRecipeExtractor.Extracted, warning: String, titleFrom: String? = caption): WebRecipeFetcher.Result.Success {
            val title = x.title?.let(CaptionRecipeParser::cleanTitle)?.takeIf { it.isNotBlank() }
                ?: titleFrom?.let { CaptionRecipeParser.parse(it).parsed.title } ?: "Imported recipe"
            // parseModelJson already validated the buckets; clean again in case a caller built Extracted by hand.
            val c = RecipeSanitizer.cleanAi(x)
            val parsed = RecipeSanitizer.clean(RecipeTextParser.ParsedRecipe(title, c.servings, c.ingredients.map(RecipeTextParser::parseIngredient), c.steps))
            val warnings = listOfNotNull(warning, RecipeSanitizer.aiWarning(c))
            val draft = ImportDraft.Draft(parsed, description = post.author?.let { "By @$it on Instagram" }.orEmpty(),
                imageUrl = post.imageUrl ?: stillUrl, sourceUrl = source, warnings = warnings, partial = true)
            return withLocalImage(WebRecipeFetcher.Result.Success(draft, false, RecipeHtmlParser.Completeness.PARTIAL, warnings), code)
        }

        // Best clean-but-incomplete caption draft so far (stage name → draft). Returned only if no later stage does better.
        var fallback: Pair<String, WebRecipeFetcher.Result.Success>? = null
        fun offerFallback(stage: String, r: WebRecipeFetcher.Result.Success) {
            fun score(x: WebRecipeFetcher.Result.Success) = x.draft.parsed.let { (if (it.ingredients.isNotEmpty()) 1000 else 0) + it.ingredients.size + it.steps.size }
            if (fallback == null || score(r) > score(fallback!!.second)) fallback = stage to r
        }
        val aiLater = text != null || creators != null || stills != null || video != null
        val splitCaptions = mutableSetOf<String>()

        /**
         * Caption stage (1 / 2): a confident rule parse wins with no AI. A recipe-looking caption that the rule parser
         * could only partly bucket goes to ONE cheap text-model split (1b / 2b), grounded against the caption itself.
         * Returns the winning result, or null to continue (keeping the best clean partial as a fallback).
         */
        fun captionStage(stage: String, pi: PostInfo): WebRecipeFetcher.Result.Success? {
            val cap = pi.caption ?: return null
            val split = "${stage.substringBefore('_')}b_caption_ai_split"
            val det = fromCaption(pi, source)
            val rule = CaptionRecipeParser.parse(cap)
            if (det != null && rule.isConfident) return win(stage, withLocalImage(det, code))
            if (det != null) notes += "$stage: caption RAC not confident (ing=${rule.parsed.ingredients.size}, steps=${rule.parsed.steps.size}, rebucketed=${rule.rebucketed})"
            val worthSplit = det != null || looksLikeRecipeText(cap)
            if (worthSplit && text != null && splitCaptions.add(cap.trim())) {
                trace.openRouterCalls++; trace.captionSplitCalls++
                try {
                    text.extract("Instagram caption", cap.take(6000), TextRecipeExtractor.CAPTION_SPLIT_SYSTEM)?.let { x ->
                        val ing = RecipeGrounding.grounded(x.ingredients, cap)
                        val st = RecipeSanitizer.groundedSteps(x.steps, cap)
                        val dropped = (x.ingredients.size - ing.size) + (x.steps.size - st.size)
                        if (dropped > 0) notes += "$split: dropped $dropped ungrounded line(s)"
                        val c = RecipeSanitizer.cleanAi(x.copy(ingredients = ing, steps = st, dropped = x.dropped + dropped))
                        if (c.ingredients.isEmpty() && c.steps.isEmpty()) { notes += "$split: empty after grounding"; return@let }
                        val r = aiSuccess(c, "Ingredients and steps were separated by AI from the caption. Check every line against the reel before cooking.",
                            titleFrom = cap).let { s ->
                            // Keep the rule parser's dish name when the model gave none.
                            if (x.title.isNullOrBlank() && rule.parsed.title != "Imported recipe")
                                s.copy(draft = s.draft.copy(parsed = s.draft.parsed.copy(title = rule.parsed.title))) else s
                        }
                        if (c.ingredients.size >= 2 && c.steps.isNotEmpty()) return win(split, r)
                        notes += "$split: incomplete (ing=${c.ingredients.size}, steps=${c.steps.size})"
                        offerFallback(split, r)
                    } ?: run { if (notes.none { it.startsWith(split) }) notes += "$split: no recipe returned" }
                } catch (e: Throwable) {
                    if (e is InterruptedException) throw e
                    notes += "$split: ${e.message}"
                }
            }
            // No AI anywhere in this build: the clean rule parse is the best we can do (old behaviour).
            if (det != null && !aiLater) return win(stage, withLocalImage(det, code))
            det?.let { offerFallback(stage, withLocalImage(it, code)) }
            return null
        }

        ImportProgress.report(ImportStage.CAPTION)
        val s1 = captionStage("1_public_html", post)
        lap("1_public_html")
        // Soft photo-led: if caption won but cover is missing and ScrapeCreators can supply a thumb, keep going.
        s1?.let { early ->
            if (early.draft.imageUrl != null || creators == null) return early
            offerFallback("1_public_html", early)
        }

        // ---- Stage 2: ONE Scrape Creators /instagram/post request (1 credit): video URL, audio flag, caption, top comments. ----
        if (creators != null) {
            ImportProgress.report(ImportStage.POST_DETAILS)
            trace.scPostCalls++
            when (val o = creators.fetch(source)) {
                is CreatorsApiClient.Outcome.Ok -> {
                    val m = o.media
                    if (avatarOut[0] == null) avatarOut[0] = CreatorProfile.safeAvatarUrl(m.avatarUrl)
                    videoUrl = m.videoUrl ?: videoUrl
                    if (caption.isNullOrBlank() || (m.caption?.length ?: 0) > caption!!.length) caption = m.caption ?: caption
                    stillUrl = stillUrl ?: m.thumbnailUrl
                    comments = m.topComments
                    val s2 = captionStage("2_scrapecreators_post", PostInfo(m.caption, m.author ?: post.author, post.imageUrl ?: m.thumbnailUrl, m.videoUrl))
                    lap("2_scrapecreators_post")
                    s2?.let { return it }
                }
                is CreatorsApiClient.Outcome.Error -> { notes += o.message; lap("2_scrapecreators_post") }
            }
        }
        // ---- Stage 3: cheapest text model on the top comments (+ caption). Grounded; ≥ 3 ingredients → stop. ----
        // Skip when there are no comments and this exact caption was already split by AI (same text, same answer).
        if (text != null && (comments.isNotEmpty() || (!caption.isNullOrBlank() && caption!!.trim() !in splitCaptions))) {
            ImportProgress.report(ImportStage.COMMENTS)
            val input = "Caption:\n${caption?.take(3000) ?: "(none)"}\n\nTop comments:\n" + comments.joinToString("\n") { "- $it" }
            trace.openRouterCalls++
            try {
                text.extract("Instagram", input, TextRecipeExtractor.COMMENTS_SYSTEM)?.let { x ->
                    val ing = RecipeGrounding.grounded(x.ingredients, input)
                    if (ing.size < x.ingredients.size) notes += "comments AI: dropped ${x.ingredients.size - ing.size} ungrounded ingredient(s)"
                    // 2026-10-29: steps must be written in the text too (the model paraphrased a dish description into
                    // "Cook halloumi until crispy"), and the text must actually carry a recipe, not just name the dish.
                    val st = RecipeSanitizer.groundedSteps(x.steps, input)
                    if (st.size < x.steps.size) notes += "comments AI: dropped ${x.steps.size - st.size} ungrounded step(s)"
                    val signal = RecipeSanitizer.hasRecipeSignal(input)
                    if (!signal) notes += "comments AI: text has no recipe signal (quantities / headings); trying the video"
                    if (ing.size >= 3 && signal) {
                        lap("3_cheap_text_comments")
                        return win("3_cheap_text_comments", aiSuccess(x.copy(ingredients = ing, steps = st, dropped = x.dropped + (x.steps.size - st.size)),
                            "Read by AI from the reel's caption / comments. Check every quantity against the reel before cooking."))
                    }
                }
            } catch (e: Throwable) {
                if (e is InterruptedException) throw e
                notes += "comments AI: ${e.message}"
            }
            lap("3_cheap_text_comments")
        }

        // ---- Stage 4: keyframes + audio → Gemini. Full video only as a thin-result (< 4 ingredients) re-read. ----
        if (videoUrl != null && (stills != null || video != null)) {
            ImportProgress.report(ImportStage.VIDEO_FRAMES)
            var best: VideoRecipeExtractor.Extracted? = null
            if (stills != null) {
                trace.openRouterCalls++
                try { best = stills.extract(videoUrl!!, caption) } catch (e: Throwable) {
                    if (e is InterruptedException) throw e
                    notes += "stills AI: ${if (e is OutOfMemoryError) "video too large for this phone" else e.message}"
                }
                lap("4_keyframes_audio")
            }
            if ((best?.ingredients?.size ?: 0) < StillsRecipeExtractor.THIN_RESULT_INGREDIENTS && video != null) {
                ImportProgress.report(ImportStage.VIDEO)
                notes += "thin result (${best?.ingredients?.size ?: 0} ingredients from keyframes): full-video re-read"
                trace.openRouterCalls++
                try {
                    video.extractRemoteOnly(videoUrl!!, caption)?.let { if (it.ingredients.size > (best?.ingredients?.size ?: 0)) {
                        lap("4d_full_video_reread")
                        return win("4d_full_video_reread", aiSuccess(it, "Read from the video by AI (speech and on-screen text). Check every quantity against the reel before cooking."))
                    } }
                } catch (e: Throwable) {
                    if (e is InterruptedException) throw e
                    notes += "video AI: ${e.message}"
                }
                lap("4d_full_video_reread")
            }
            best?.takeIf { it.ingredients.isNotEmpty() || it.steps.isNotEmpty() }?.let {
                return win("4_keyframes_audio", aiSuccess(it, "Read by AI from the reel's keyframes and audio. Check every quantity against the reel before cooking."))
            }
        } else if (video != null && stillUrl != null) {
            // Photo / carousel post (no video): the still image is the content.
            ImportProgress.report(ImportStage.PHOTO)
            trace.openRouterCalls++
            try {
                video.extractStill(stillUrl!!, caption)?.let {
                    lap("4_photo_still")
                    return win("4_photo_still", aiSuccess(it, "Read by AI from the post's photo. Check every quantity before cooking."))
                }
            } catch (e: Throwable) {
                if (e is InterruptedException) throw e
                notes += "photo AI: ${e.message}"
            }
            lap("4_photo_still")
        }

        // 5. Best clean caption draft from 1 / 1b / 2 / 2b (PARTIAL, honest warnings) before the empty fallback.
        fallback?.let { (stage, r) ->
            return win(stage, r.copy(draft = r.draft.copy(imageUrl = r.draft.imageUrl ?: stillUrl ?: post.imageUrl)))
        }

        // 6. Honest partial + paste fallback.
        val title = caption?.let { CaptionRecipeParser.parse(it).parsed.title }?.takeIf { it != "Imported recipe" }
        val partial = if (title != null || post.imageUrl != null) ImportDraft.Draft(
            RecipeTextParser.ParsedRecipe(title ?: "Instagram recipe", null, emptyList(), emptyList()),
            description = post.author?.let { "By @$it on Instagram" }.orEmpty(),
            imageUrl = ImportImageStore.persist(stillUrl ?: post.imageUrl, "ig_$code"), sourceUrl = source,
            warnings = listOf("The reel's caption doesn't include the recipe. Add the ingredients and steps yourself."),
            partial = true,
        ) else null
        val why = when {
            post.caption == null && caption == null -> "Instagram didn't share this post publicly (it may be private, removed or blocked on this network)."
            else -> "This reel's caption doesn't contain the recipe (no ingredient list or steps)."
        }
        val setup = when {
            creators == null && video == null -> " Reading recipes from the video isn't set up in this build."
            video == null -> " Video reading isn't set up in this build."
            else -> ""
        }
        return WebRecipeFetcher.Result.Failure(
            "$why$setup If the creator posted the recipe in a comment or on their site, copy it and use Paste import." +
                if (notes.isNotEmpty()) "\n(${notes.joinToString("; ").take(200)})" else "",
            partial = partial,
            pasteText = caption,
        )
    }

    /** True when a caption the rule parser rejected still carries several quantities (worth one cheap text-AI call). */
    fun looksLikeRecipeText(caption: String?): Boolean {
        if (caption.isNullOrBlank() || caption.length < 80) return false
        val qty = SafeRegex.of("InstagramImporter.qty", "(?i)\\b\\d+(?:[./]\\d+)?\\s*(?:cups?|tbsp|tsp|teaspoons?|tablespoons?|g|gm|grams?|kg|ml|l|litres?|pinch|inch)\\b|[½¼¾]")
        // Nutrition rows ("Fat: 18g/ Carbs: 65g/ Protein: 44g") are not recipe quantities.
        val body = caption.lines().filterNot { RecipeSanitizer.isNutritionLine(it) }.joinToString("\n")
        return qty.findAll(body).count() >= 3
    }

    private fun withLocalImage(r: WebRecipeFetcher.Result.Success, code: String): WebRecipeFetcher.Result.Success =
        r.copy(draft = r.draft.copy(imageUrl = ImportImageStore.persist(r.draft.imageUrl, "ig_$code")))
}
