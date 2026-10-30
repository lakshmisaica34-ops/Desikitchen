package com.desikitchen.app.util

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.nio.charset.Charset

/**
 * Pure (network-free) recipe extraction from an HTML page. Used by [WebRecipeFetcher] and by the
 * fixture tests, so everything here must run on both Android and a plain JVM.
 *
 * Strategy, cheapest / most trustworthy first, then merged by richness:
 *  1. schema.org Recipe in JSON-LD (Yoast `@graph`, arrays, `mainEntity`, multi-type `@type`).
 *  2. schema.org Recipe microdata (`itemtype=…/Recipe`, `itemprop=recipeIngredient`).
 *  3. Recipe-card plugins (WP Recipe Maker, Tasty Recipes, Mediavine Create, generic `*ingredient*` lists).
 *  4. Heading heuristics: a "Ingredients" heading followed by a list / paragraphs, and a
 *     "Method / Instructions / Steps" heading followed by steps (Sanjeev Kapoor, Chef Kunal, Ranveer Brar…).
 *  5. OpenGraph / `<title>` / `<h1>` for title, image and description only.
 *
 * Nothing is invented: if a source has no ingredients or steps, the field stays empty and
 * [Completeness] is demoted so the review screen can be trusted.
 */
object RecipeHtmlParser {

    enum class Source { JSON_LD, MICRODATA, RECIPE_CARD, HEADINGS }
    enum class Completeness { FULL, PARTIAL }

    data class Outcome(
        val draft: ImportDraft.Draft,
        /** Where ingredients / steps came from (may differ: e.g. steps from JSON-LD, ingredients from the card). */
        val ingredientSource: Source?,
        val stepSource: Source?,
        val completeness: Completeness,
        val warnings: List<String>,
    ) {
        val fromStructuredData: Boolean get() = ingredientSource == Source.JSON_LD || stepSource == Source.JSON_LD ||
            ingredientSource == Source.MICRODATA || stepSource == Source.MICRODATA
    }

    /** One extraction attempt; fields left empty when a strategy didn't find them. */
    internal data class Candidate(
        val source: Source,
        val title: String = "",
        val description: String = "",
        val image: String? = null,
        val ingredients: List<String> = emptyList(),
        val steps: List<String> = emptyList(),
        val servings: Int? = null,
        val prep: Int = 0,
        val cook: Int = 0,
        val total: Int = 0,
    )

    /** Returns null when the page has no ingredients AND no steps anywhere. */
    fun parse(html: String, url: String): Outcome? {
        SafeRegex.requireOk("RecipeHtmlParser") // 1.1.6: readable error instead of silently wrong output
        val doc = Jsoup.parse(html, url)
        val candidates = buildList {
            addAll(fromJsonLd(doc))
            fromMicrodata(doc)?.let(::add)
            fromRecipeCard(doc)?.let(::add)
            fromHeadings(doc)?.let(::add)
        }
        val ingPick = pickRichest(candidates) { it.ingredients }
        val stepPick = pickRichest(candidates) { it.steps }
        if (ingPick == null && stepPick == null) return null

        // Metadata: prefer the structured candidate that supplied the content, then any structured one, then OG.
        val structured = candidates.filter { it.source == Source.JSON_LD || it.source == Source.MICRODATA }
        val meta = (listOfNotNull(ingPick, stepPick) + structured + candidates).distinct()
        fun <T> firstOf(sel: (Candidate) -> T?, ok: (T) -> Boolean): T? = meta.map(sel).firstOrNull { it != null && ok(it) }

        val og = openGraph(doc)
        val title = firstOf({ it.title }, { it.isNotBlank() }) ?: og.title ?: "Imported recipe"
        val image = firstOf({ it.image }, { it.startsWith("http") }) ?: og.image
        val description = firstOf({ it.description }, { it.isNotBlank() }) ?: og.description.orEmpty()
        val servings = firstOf({ it.servings }, { it in 1..100 })
        var prep = firstOf({ it.prep }, { it > 0 }) ?: 0
        var cook = firstOf({ it.cook }, { it > 0 }) ?: 0
        val total = firstOf({ it.total }, { it > 0 }) ?: 0
        if (prep == 0 && cook == 0 && total > 0) cook = total
        if (prep > 24 * 60) prep = 0 // e.g. "soak overnight" encoded as prep; keeps the editor sane
        if (cook > 24 * 60) cook = 0

        val ingredients = ingPick?.ingredients.orEmpty()
        val steps = stepPick?.steps.orEmpty()
        val warnings = buildList {
            if (ingredients.isEmpty()) add("No ingredient list found on the page; add them in review.")
            if (steps.isEmpty()) add("No method / steps found on the page; add them in review.")
            if (ingPick != null && ingPick.source == Source.HEADINGS) add("Ingredients were read from the page layout, please double-check.")
            if (stepPick != null && stepPick.source == Source.HEADINGS) add("Steps were read from the page layout, please double-check.")
        }
        val draft = ImportDraft.Draft(
            parsed = RecipeTextParser.ParsedRecipe(
                title = Text.clean(title).take(140),
                servings = servings,
                ingredients = ingredients.map { RecipeTextParser.parseIngredient(it) },
                steps = steps,
            ),
            description = Text.clean(description).take(400),
            imageUrl = image,
            sourceUrl = url,
            prepMinutes = prep,
            cookMinutes = cook,
        )
        return Outcome(
            draft = draft,
            ingredientSource = ingPick?.source,
            stepSource = stepPick?.source,
            completeness = if (ingredients.isNotEmpty() && steps.isNotEmpty()) Completeness.FULL else Completeness.PARTIAL,
            warnings = warnings,
        )
    }

    /**
     * Picks the candidate with the best list for a field. Candidates are already in trust order
     * (JSON-LD first); a later, less-trusted source only wins when it is clearly richer (≥ 2× and
     * at least 3 more items), which fixes sites whose JSON-LD lists just "Paneer" or nothing.
     */
    private fun pickRichest(cands: List<Candidate>, field: (Candidate) -> List<String>): Candidate? {
        var best: Candidate? = null
        for (c in cands) {
            val n = field(c).size
            if (n == 0) continue
            val b = best?.let { field(it).size } ?: 0
            val replace = when {
                best == null -> true
                // Structured data (JSON-LD / microdata / card) is trusted unless it is nearly empty.
                b < 3 -> n >= b + 3
                // Two structured sources: the clearly richer one wins.
                c.source != Source.HEADINGS -> n >= b * 2 && n >= b + 3
                else -> false
            }
            if (replace) best = c
        }
        return best
    }

    // ---------------------------------------------------------------- JSON-LD

    private fun fromJsonLd(doc: Document): List<Candidate> =
        doc.select("script[type*=ld+json]").mapNotNull { script ->
            val obj = LenientJson.parse(script.data()) ?: return@mapNotNull null
            val recipe = findRecipe(obj, 0) ?: return@mapNotNull null
            val ingredients = when (val ri = recipe.opt("recipeIngredient") ?: recipe.opt("ingredients")) {
                is JSONArray -> (0 until ri.length()).flatMap { i -> splitLines(ri.opt(i)?.toString().orEmpty()) }
                is String -> splitLines(ri)
                else -> emptyList()
            }.map(Text::clean).filter { it.isNotBlank() }
            val steps = mutableListOf<String>()
            collectSteps(recipe.opt("recipeInstructions"), steps, 0)
            Candidate(
                source = Source.JSON_LD,
                title = Text.clean(recipe.optString("name")),
                description = Text.clean(recipe.optString("description")),
                image = imageUrl(recipe.opt("image"), doc.location()),
                ingredients = ingredients,
                steps = Text.normaliseSteps(steps),
                servings = Text.servings(yieldText(recipe.opt("recipeYield"))),
                prep = Text.isoMinutes(recipe.optString("prepTime")),
                cook = Text.isoMinutes(recipe.optString("cookTime")),
                total = Text.isoMinutes(recipe.optString("totalTime")),
            )
        }.sortedByDescending { it.ingredients.size + it.steps.size }

    private fun findRecipe(node: Any?, depth: Int): JSONObject? {
        if (depth > 8) return null
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) findRecipe(node.opt(i), depth + 1)?.let { return it }
            is JSONObject -> {
                if (isType(node.opt("@type"), "Recipe")) return node
                for (key in listOf("@graph", "mainEntity", "mainEntityOfPage", "hasPart", "itemListElement", "item")) {
                    findRecipe(node.opt(key), depth + 1)?.let { return it }
                }
            }
        }
        return null
    }

    private fun isType(type: Any?, name: String): Boolean = when (type) {
        is String -> type.substringAfterLast('/').equals(name, true)
        is JSONArray -> (0 until type.length()).any { type.optString(it).substringAfterLast('/').equals(name, true) }
        else -> false
    }

    private fun collectSteps(node: Any?, out: MutableList<String>, depth: Int) {
        if (depth > 6) return
        when (node) {
            is String -> splitLines(Text.htmlToText(node)).forEach { out += it }
            is JSONArray -> for (i in 0 until node.length()) collectSteps(node.opt(i), out, depth + 1)
            is JSONObject -> when {
                node.has("itemListElement") -> collectSteps(node.opt("itemListElement"), out, depth + 1)
                node.optString("text").isNotBlank() -> out += Text.htmlToText(node.optString("text"))
                node.optString("name").isNotBlank() -> out += Text.htmlToText(node.optString("name"))
                node.optString("description").isNotBlank() -> out += Text.htmlToText(node.optString("description"))
            }
        }
    }

    private fun yieldText(y: Any?): String = when (y) {
        is JSONArray -> (0 until y.length()).map { y.optString(it) }.firstOrNull { Regex("\\d").containsMatchIn(it) }.orEmpty()
        null, JSONObject.NULL -> ""
        else -> y.toString()
    }

    private fun imageUrl(img: Any?, base: String): String? {
        val raw = when (img) {
            is String -> img
            is JSONArray -> (0 until img.length()).firstNotNullOfOrNull { imageUrl(img.opt(it), base) }
            is JSONObject -> img.optString("url").ifBlank { img.optString("contentUrl") }
            else -> null
        }?.trim()
        return raw?.let { absolutise(it, base) }
    }

    private fun absolutise(u: String, base: String): String? = when {
        u.isBlank() -> null
        u.startsWith("http") -> u
        u.startsWith("//") -> "https:$u"
        else -> runCatching { java.net.URL(java.net.URL(base), u).toString() }.getOrNull()?.takeIf { it.startsWith("http") }
    }

    // ---------------------------------------------------------------- Microdata

    private fun fromMicrodata(doc: Document): Candidate? {
        val scope = doc.select("[itemtype~=(?i)schema.org/Recipe]").firstOrNull() ?: return null
        fun props(name: String) = scope.select("[itemprop=$name]")
        val ingredients = (props("recipeIngredient") + props("ingredients")).map { Text.clean(it.text()) }.filter { it.isNotBlank() }
        val stepEls = props("recipeInstructions")
        val steps = stepEls.flatMap { el ->
            val inner = el.select("[itemprop=text], li")
            if (inner.isNotEmpty()) inner.map { it.text() } else splitLines(Text.htmlToText(el.html()))
        }
        fun attr(el: Element?) = el?.let { it.attr("content").ifBlank { it.attr("datetime") }.ifBlank { it.text() } }.orEmpty()
        val image = props("image").firstOrNull()?.let { it.absUrl("src").ifBlank { it.attr("content").ifBlank { it.absUrl("href") } } }
        return Candidate(
            source = Source.MICRODATA,
            title = Text.clean(attr(props("name").firstOrNull())),
            description = Text.clean(attr(props("description").firstOrNull())),
            image = image?.takeIf { it.startsWith("http") },
            ingredients = ingredients,
            steps = Text.normaliseSteps(steps),
            servings = Text.servings(attr(props("recipeYield").firstOrNull())),
            prep = Text.isoMinutes(attr(props("prepTime").firstOrNull())),
            cook = Text.isoMinutes(attr(props("cookTime").firstOrNull())),
            total = Text.isoMinutes(attr(props("totalTime").firstOrNull())),
        ).takeIf { it.ingredients.isNotEmpty() || it.steps.isNotEmpty() }
    }

    // ---------------------------------------------------------------- Recipe-card plugins

    private val cardSelectors = listOf(
        // WP Recipe Maker (most Indian food blogs: Hebbar's, Swasthi's, Cook with Manali, Piping Pot Curry…)
        ".wprm-recipe-ingredient" to ".wprm-recipe-instruction-text",
        // Tasty Recipes
        ".tasty-recipes-ingredients li" to ".tasty-recipes-instructions li",
        // Mediavine Create
        ".mv-create-ingredients li" to ".mv-create-instructions li",
        // WP Ultimate Recipe / Zip / EasyRecipe / Cooked
        ".wpurp-recipe-ingredient" to ".wpurp-recipe-instruction",
        ".ERSIngredients li" to ".ERSInstructions li",
        ".cooked-single-ingredient" to ".cooked-single-direction",
    )

    private fun fromRecipeCard(doc: Document): Candidate? {
        for ((ingSel, stepSel) in cardSelectors) {
            val ings = doc.select(ingSel).map { Text.clean(it.text()) }.filter { it.isNotBlank() }.distinct()
            val steps = Text.normaliseSteps(doc.select(stepSel).map { it.text() })
            if (ings.isEmpty() && steps.isEmpty()) continue
            val title = doc.selectFirst(".wprm-recipe-name, .tasty-recipes-title, .mv-create-title")?.text().orEmpty()
            return Candidate(Source.RECIPE_CARD, title = Text.clean(title), ingredients = ings, steps = steps,
                servings = Text.servings(doc.selectFirst(".wprm-recipe-servings, .tasty-recipes-yield, .mv-create-yield")?.text().orEmpty()))
        }
        return null
    }

    // ---------------------------------------------------------------- Heading heuristics

    private val ingHeading = SafeRegex.of("RecipeHtmlParser.ingHeading", "^(main\\s+)?(ingredients?|ingrediants|you will need|what you(’|')?ll need|what you need)(\\s+(list|required|needed|for .{1,40}))?\\s*:?$", RegexOption.IGNORE_CASE)
    private val stepHeading = SafeRegex.of("RecipeHtmlParser.stepHeading", "^(method|instructions?|directions?|steps|preparation|procedure|process|how to (make|prepare|cook).{0,60}|cooking (method|instructions)|recipe method)\\s*:?$", RegexOption.IGNORE_CASE)
    private val stopHeading = SafeRegex.of("RecipeHtmlParser.stopHeading", "^(notes?|tips?|nutrition.*|faqs?|frequently asked.*|serving suggestions?|video|related.*|more recipes.*|comments?|leave a (reply|comment).*|you may also like.*|storage.*|variations?)\\s*:?$", RegexOption.IGNORE_CASE)

    private fun fromHeadings(doc: Document): Candidate? {
        val body = doc.body() ?: return null
        body.select("script, style, noscript, nav, header, footer, aside, form, iframe, svg, [role=navigation], .comments, #comments, .sidebar").remove()
        val ingredients = sectionAfter(body, ingHeading, maxItems = 80, maxLen = 220)
        // Step sections often repeat the ingredient list ("How to make…" + ingredients); drop those lines,
        // and anything that reads like an ingredient line rather than an instruction.
        val ingSet = ingredients.map { it.lowercase() }.toSet()
        val steps = sectionAfter(body, stepHeading, maxItems = 60, maxLen = 1500)
            .filterNot { it.lowercase() in ingSet || looksLikeIngredientLine(it) }
        if (ingredients.isEmpty() && steps.isEmpty()) return null
        return Candidate(Source.HEADINGS, title = Text.clean(body.selectFirst("h1")?.text().orEmpty()),
            ingredients = ingredients, steps = Text.normaliseSteps(steps))
    }

    private val qtyStart = SafeRegex.of("RecipeHtmlParser.qtyStart", "^([\\d½¼¾⅓⅔⅛]+[\\d/.\\s-]*)\\s*(cups?|tsp|tbsp|teaspoons?|tablespoons?|g|grams?|gms?|kg|ml|l|litres?|liters?|oz|lbs?|pounds?|inch(es)?|pinch|cloves?|nos?)\\b", RegexOption.IGNORE_CASE)
    private val ingDash = SafeRegex.of("RecipeHtmlParser.ingDash", "^.{1,40}\\s[–—-]\\s*([\\d½¼¾⅓⅔⅛].{0,20}|to taste|as needed|handful|a pinch)$", RegexOption.IGNORE_CASE)

    /** Short "2 tbsp ghee" / "Salt – to taste" lines with no verb-ish length are ingredients, not steps. */
    private fun looksLikeIngredientLine(s: String): Boolean =
        (s.length <= 90 && qtyStart.containsMatchIn(s) && !s.contains(". ")) || ingDash.matches(s)

    private fun isHeadingLike(el: Element): Boolean {
        if (el.tagName() in setOf("h1", "h2", "h3", "h4", "h5", "h6")) return true
        if (el.tagName() in setOf("p", "div") && el.children().size == 1) {
            val c = el.child(0)
            return c.tagName() in setOf("strong", "b") && c.text().length >= el.text().length - 2
        }
        return false
    }

    /** Finds the first heading matching [heading] that is followed by content, and returns its items. */
    private fun sectionAfter(body: Element, heading: Regex, maxItems: Int, maxLen: Int): List<String> {
        val heads = body.select("h1, h2, h3, h4, h5, h6, p, strong, b, span, div, dt, th, label")
            .filter { it.ownText().isNotBlank() || it.children().size <= 1 }
            .filter { heading.matches(Text.clean(it.text())) }
        for (h in heads) {
            // Inline heading (<p><strong>Ingredients</strong></p>): walk from the enclosing block.
            var block: Element = h
            while (block.tagName() in setOf("strong", "b", "span", "em") && block.parent() != null &&
                Text.clean(block.parent()!!.text()).length <= Text.clean(h.text()).length + 2) block = block.parent()!!
            val items = mutableListOf<String>()
            var sib = block.nextElementSibling()
            var guard = 0
            while (sib != null && guard++ < 200 && items.size < maxItems) {
                val text = Text.clean(sib.text())
                if (isHeadingLike(sib) && (stepHeading.matches(text) || ingHeading.matches(text) || stopHeading.matches(text))) break
                if (sib.tagName() in setOf("h1", "h2") && items.isNotEmpty()) break
                when {
                    sib.tagName() in setOf("ul", "ol") -> items += sib.select("> li").map { Text.clean(it.text()) }
                    isHeadingLike(sib) -> Unit // sub-section like "For the gravy"
                    sib.tagName() in setOf("p", "div", "section", "table", "dl") -> {
                        val lis = sib.select("li")
                        when {
                            lis.isNotEmpty() -> items += lis.map { Text.clean(it.text()) }
                            sib.select("p").isNotEmpty() -> items += sib.select("p")
                                .filterNot { isHeadingLike(it) || (it.text().trim().endsWith(":") && it.text().length < 60) }
                                .map { Text.clean(it.text()) }
                            text.isNotBlank() && !text.endsWith(":") -> items += text
                        }
                    }
                }
                sib = sib.nextElementSibling()
            }
            val clean = items.filter { it.isNotBlank() && it.length <= maxLen }.distinct()
            if (clean.size >= 2) return clean.take(maxItems)
        }
        return emptyList()
    }

    // ---------------------------------------------------------------- OpenGraph

    private data class Og(val title: String?, val image: String?, val description: String?)

    private fun openGraph(doc: Document): Og {
        fun meta(vararg keys: String) = keys.firstNotNullOfOrNull { k ->
            doc.selectFirst("meta[property=$k], meta[name=$k]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
        }
        val title = meta("og:title", "twitter:title") ?: doc.selectFirst("h1")?.text() ?: doc.title()
        val image = meta("og:image", "og:image:url", "og:image:secure_url", "twitter:image")?.let { absolutise(it, doc.location()) }
        return Og(title?.let(Text::clean)?.takeIf { it.isNotBlank() }, image, meta("og:description", "description"))
    }

    private fun splitLines(s: String): List<String> = s.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    // ---------------------------------------------------------------- Text helpers

    object Text {
        private val ws = SafeRegex.of("RecipeHtmlParser.ws", "\\s+")

        /** Decodes entities (incl. double-encoded), strips tags, collapses whitespace. */
        fun clean(s: String?): String {
            if (s.isNullOrBlank()) return ""
            var t: String = s
            repeat(2) { if (t.contains('&') || t.contains('<')) t = Jsoup.parse(t).text() }
            t = fixMojibake(t)
            return t.replace('\u00A0', ' ').replace("\u200B", "").replace("\uFEFF", "").replace(ws, " ").trim()
        }

        private val cp1252 = Charset.forName("windows-1252")
        private val mojibake = SafeRegex.of("RecipeHtmlParser.mojibake", "[ÃÂâ][\\u0080-\\u00BF€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ]{1,2}")

        /** Repairs UTF-8 text that a site double-encoded as Windows-1252 ("sautÃ©" → "sauté", "â€™" → "’"). */
        fun fixMojibake(s: String): String {
            if (!s.contains('Ã') && !s.contains('â') && !s.contains('Â')) return s
            return mojibake.replace(s) { m ->
                runCatching {
                    val bytes = m.value.toByteArray(cp1252)
                    val dec = String(bytes, Charsets.UTF_8)
                    if (dec.contains('\uFFFD') || dec.length >= m.value.length) m.value else dec
                }.getOrDefault(m.value)
            }
        }

        /** HTML fragment → text, keeping line breaks for block elements. */
        fun htmlToText(html: String): String {
            if (!html.contains('<')) return clean(html)
            val withBreaks = html.replace(Regex("(?i)<(br|/p|/li|/h[1-6]|/div)[^>]*>"), "\n")
            return withBreaks.split('\n').map(::clean).filter { it.isNotBlank() }.joinToString("\n")
        }

        /** Cleans, de-numbers, drops empties/dupes; splits a single giant blob into lines/sentences. */
        fun normaliseSteps(raw: List<String>): List<String> {
            var steps = raw.flatMap { it.split('\n') }
                .map { clean(it).replace(Regex("^(step\\s*\\d+\\s*[.\\:)-]?|\\d+\\s*[.)]\\s+)", RegexOption.IGNORE_CASE), "").trim() }
                .filter { it.length > 1 }
            steps = steps.filterIndexed { i, s -> i == 0 || s != steps[i - 1] }
            if (steps.size == 1 && steps[0].length > 600) {
                steps = steps[0].split(Regex("(?<=[.!?])\\s+(?=[A-Z])")).map { it.trim() }.filter { it.isNotBlank() }
            }
            return steps.map { if (it.length > 2000) it.take(2000) else it }
        }

        /** ISO-8601 duration (PT1H30M, P0DT0H20M, PT90M) → minutes. Also accepts "20 mins", "1 hour 10 minutes". */
        fun isoMinutes(iso: String?): Int {
            val s = iso?.trim().orEmpty()
            if (s.isBlank()) return 0
            if (s.startsWith("P", ignoreCase = true)) {
                val d = SafeRegex.of("RecipeHtmlParser.d", "(\\d+)D", RegexOption.IGNORE_CASE).find(s.substringBefore('T'))?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val t = s.substringAfter('T', "")
                val h = SafeRegex.of("RecipeHtmlParser.h", "(\\d+(?:\\.\\d+)?)H", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val m = SafeRegex.of("RecipeHtmlParser.m", "(\\d+(?:\\.\\d+)?)M", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                return (d * 1440 + h * 60 + m).toInt()
            }
            val h = SafeRegex.of("RecipeHtmlParser.h", "(\\d+)\\s*(h|hr|hrs|hour|hours)\\b", RegexOption.IGNORE_CASE).find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val m = SafeRegex.of("RecipeHtmlParser.m", "(\\d+)\\s*(m|min|mins|minute|minutes)\\b", RegexOption.IGNORE_CASE).find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            return h * 60 + m
        }

        /** "4", "Serves 4-6", "4 servings", "2 people" → first number. */
        fun servings(text: String?): Int? = Regex("\\d+").find(text.orEmpty())?.value?.toIntOrNull()?.takeIf { it in 1..100 }
    }
}
