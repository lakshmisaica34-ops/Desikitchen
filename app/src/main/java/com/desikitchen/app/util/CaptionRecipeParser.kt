package com.desikitchen.app.util

/**
 * Parses a recipe written in a social caption ("RAC": recipe-as-caption), e.g. an Instagram reel:
 *
 *   mushroom butter rice 🍚🧈 full recipe below @someone
 *   ingredients
 *   - 1 cup jasmine rice
 *   instructions
 *   1. wash your rice …
 *   #ricecooker #onepot
 *
 * Everything returned is copied from the caption; nothing is guessed or generated. If the caption has no
 * ingredient list or no steps, the result says so via [Result.hasRecipe] / [Result.isComplete].
 */
object CaptionRecipeParser {

    data class Result(
        val parsed: RecipeTextParser.ParsedRecipe,
        /** True when the caption had an explicit ingredients or method section. */
        val hadSections: Boolean,
        /**
         * 2026-10-29 (DfEAPhVzLrs): lines the parser had to re-bucket or drop by heuristics because the caption's own
         * structure was wrong, e.g. method sentences under an "Ingredients:" heading, chatter in the ingredient block,
         * or lines [RecipeSanitizer] removed at the end. Nutrition blocks are not counted (always skipped, never mixed).
         */
        val rebucketed: Int = 0,
    ) {
        val hasRecipe: Boolean get() = parsed.ingredients.size >= 2 || (hadSections && parsed.steps.isNotEmpty())
        val isComplete: Boolean get() = parsed.ingredients.size >= 2 && parsed.steps.isNotEmpty()
        /**
         * Trust the rule parse as-is only when both buckets are filled and nothing had to be moved or dropped.
         * Otherwise the importer asks the text model to split the caption (grounded + sanitized), see InstagramImporter.
         */
        val isConfident: Boolean get() = isComplete && rebucketed == 0
    }

    private val ingredientHeader = SafeRegex.of("CaptionRecipeParser.ingredientHeader", 
        "^(ingredients?( list)?|you('| wi)ll need|what you('ll)? need|things you need|shopping list)\\b\\s*[\\:：\\-–]?\\s*(.*)$",
        RegexOption.IGNORE_CASE,
    )
    /**
     * Short qualifier allowed before a step heading: "Quick Steps:", "Easy Method", "Simple Steps", "Full Recipe",
     * "Step-by-step method". A fixed list (not "any word") so "Mushroom rice recipe:" stays a title line.
     */
    private const val stepQualifier =
        "(?:(?:quick|easy|simple|full|basic|detailed|short|cooking|making|recipe|step[ -]by[ -]step|the|my|our)\\s+){0,2}"
    private val stepHeader = SafeRegex.of("CaptionRecipeParser.stepHeader", 
        "^$stepQualifier(instructions?|method|steps|directions?|preparation|procedure|process|how to make( it)?|how to cook|recipe( method)?)\\b\\s*[\\:：\\-–]?\\s*(.*)$",
        RegexOption.IGNORE_CASE,
    )
    private val servesRegex = SafeRegex.of("CaptionRecipeParser.servesRegex", "(serves|servings?|yield|makes)\\s*[\\:\\-]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
    /** Leading emoji / bullets / arrows that people put before every line. Keeps digits, letters, fractions and "(". */
    private val leadingJunk = SafeRegex.of("CaptionRecipeParser.leadingJunk", "^[^\\p{L}\\p{N}½¼¾⅓⅔⅛(\"'“]+")
    /**
     * 1.1.5: Android's regex engine is ICU, which reads a class starting with "[:" as a POSIX "[:name:]" property.
     * The old `[:.)\\-]?|…[.)\\-:]` therefore failed to compile on the phone (PatternSyntaxException inside this
     * object's static init → ExceptionInInitializerError for every Instagram / social / Paste import), while the JVM
     * tests passed. Colons are escaped and never first in a class; IcuRegexCompatTest guards every pattern.
     */
    private val stepNumber = SafeRegex.of("CaptionRecipeParser.stepNumber", "^\\s*(step\\s*\\d+\\s*[.\\:)\\-]?|\\d{1,2}\\s*[.)\\-\\:]|\\d{1,2}️⃣)\\s*", RegexOption.IGNORE_CASE)
    private val hashtagOnly = SafeRegex.of("CaptionRecipeParser.hashtagOnly", "^([#@][\\p{L}\\p{N}_.]+[\\s,.]*)+$")
    private val outro = SafeRegex.of("CaptionRecipeParser.outro", 
        "^(tag (a|your)|follow (@|me|us|for)|save (this|for later)|share (this|with)|comment |dm (me|us)|link in (my )?bio|" +
            "like (and|&) (save|share)|turn on (post )?notifications|for more (recipes|such)|subscribe|music:|song:|audio:)",
        RegexOption.IGNORE_CASE,
    )
    private val quantityStart = SafeRegex.of("CaptionRecipeParser.quantityStart", "^(\\d|[½¼¾⅓⅔⅛]|a (pinch|few|handful|cup|tbsp|tsp)|half |few |pinch )", RegexOption.IGNORE_CASE)
    private val subHeading = SafeRegex.of("CaptionRecipeParser.subHeading", "^[\\p{L} &/'()-]{2,40}[\\:：]$")

    fun parse(caption: String): Result {
        SafeRegex.requireOk("CaptionRecipeParser") // 1.1.6: readable error instead of silently wrong output
        val rawLines = caption.replace("\r", "").lines().map { it.trim() }
        var title = ""
        var servings: Int? = null
        val ingredients = mutableListOf<RecipeTextParser.ParsedIngredient>()
        val steps = mutableListOf<String>()
        var section = 0 // 0 = intro, 1 = ingredients, 2 = steps, 3 = outro
        var sawSection = false
        var inNutrition = false
        var rebucketed = 0
        val introLines = mutableListOf<String>()

        for (raw in rawLines) {
            if (raw.isEmpty()) continue
            val line = stripHashtagsAtEnd(raw)
            if (line.isEmpty() || hashtagOnly.matches(line)) continue
            val clean = line.replace(leadingJunk, "").trim()
            if (clean.isEmpty()) continue

            val ingMatch = ingredientHeader.matchEntire(clean)
            if (ingMatch != null) {
                section = 1; sawSection = true
                val rest = ingMatch.groupValues.last().trim()
                if (rest.any { it.isLetterOrDigit() }) inlineItems(rest).forEach { ingredients += RecipeTextParser.parseIngredient(it) }
                continue
            }
            val stepMatch = stepHeader.matchEntire(clean)?.takeIf { looksLikeHeader(clean, it.groupValues.last()) }
            if (stepMatch != null) {
                section = 2; sawSection = true
                val rest = stepMatch.groupValues.last().trim()
                if (rest.any { it.isLetterOrDigit() }) steps += splitProse(rest)
                continue
            }
            if (outro.containsMatchIn(clean) && section != 0) { section = 3; continue }
            // 2026-10-29: a "Nutrition (per mochi)" block (header + "65 kcal Calories" / "2 g Protein" rows) is neither an
            // ingredient nor a step. Skip it wherever it appears; the first non-nutrition line ends the block.
            if (RecipeSanitizer.isNutritionHeader(clean)) { inNutrition = true; continue }
            if (RecipeSanitizer.isNutritionLine(clean)) continue
            if (inNutrition) inNutrition = false
            if (section == 0) servesRegex.find(clean)?.let { servings = servings ?: it.groupValues[2].toIntOrNull() }

            when (section) {
                0 -> introLines += clean
                1 -> when {
                    subHeading.matches(clean) -> Unit // "For the gravy:" – a group label, not an ingredient
                    stepNumber.containsMatchIn(clean) && clean.length > 60 && !quantityStart.containsMatchIn(clean.replace(stepNumber, "")) -> {
                        // A numbered sentence inside the ingredient block means the method started without a heading.
                        section = 2; rebucketed++; steps += clean.replace(stepNumber, "").trim()
                    }
                    // 2026-10-29: an unheaded method ("Dip 1 rice paper sheet in…", "Now place it flat…") inside the
                    // ingredient block starts the steps. Never keep an instruction as an ingredient.
                    RecipeSanitizer.isMethodLine(clean) -> {
                        section = 2; rebucketed++
                        val text = clean.replace(stepNumber, "").trim()
                        if (text.isNotEmpty()) steps += splitProse(text)
                    }
                    !RecipeSanitizer.isIngredientText(clean) -> rebucketed++ // chatter / overlong prose: drop, don't guess
                    else -> ingredients += RecipeTextParser.parseIngredient(clean)
                }
                2 -> if (!subHeading.matches(clean)) {
                    val text = clean.replace(stepNumber, "").trim()
                    if (text.isNotEmpty()) steps += splitProse(text)
                }
                else -> Unit
            }
        }

        // No headings at all: fall back to line shapes (quantity lines → ingredients, numbered lines → steps).
        if (!sawSection) {
            val qtyLines = introLines.drop(1).filter { quantityStart.containsMatchIn(it) && it.length <= 80 && RecipeSanitizer.isIngredientText(it) }
            val numbered = rawLines.map { it.replace(leadingJunk, "") }.filter { stepNumber.containsMatchIn(it) && it.length > 25 }
            if (qtyLines.size >= 3) qtyLines.forEach { ingredients += RecipeTextParser.parseIngredient(it) }
            if (numbered.size >= 2) numbered.forEach { steps += it.replace(stepNumber, "").trim() }
            // 2026-10-29: unheaded, unnumbered method after a quantity list ("Dice the tomatoes…", "Mix everything…").
            if (qtyLines.size >= 3 && numbered.size < 2) {
                val firstQty = introLines.indexOf(qtyLines.first())
                introLines.drop(firstQty + 1).filter { RecipeSanitizer.isMethodLine(it) }.forEach { steps += it.replace(stepNumber, "").trim() }
            }
        }

        title = cleanTitle(introLines.firstOrNull().orEmpty())
        // Last guard (shared with Paste / OCR / AI): no nutrition or instruction survives in ingredients.
        val raw = RecipeTextParser.ParsedRecipe(title.ifBlank { "Imported recipe" }, servings, ingredients, steps.filter { it.length > 1 })
        val cleaned = RecipeSanitizer.clean(raw)
        rebucketed += (raw.ingredients.size - cleaned.ingredients.size).coerceAtLeast(0) +
            (raw.steps.size - cleaned.steps.size).let { if (it < 0) -it else it }
        return Result(
            cleaned,
            hadSections = sawSection,
            rebucketed = rebucketed,
        )
    }

    /** "recipe: heat oil…" is a method; "Recipe" alone is a heading. Avoid treating "Recipe below 👇" as a heading. */
    private fun looksLikeHeader(line: String, rest: String): Boolean {
        if (rest.isEmpty()) return true
        // "Instructions 👇", "Method ⬇️", "Steps ->" : nothing but arrows / emoji after the word is still a heading.
        if (rest.none { it.isLetterOrDigit() }) return true
        if (Regex("^(below|in (the )?(caption|comments|bio))", RegexOption.IGNORE_CASE).containsMatchIn(rest)) return false
        return line.contains(':') || line.contains('：') || line.contains('-') || line.contains('–')
    }

    private fun inlineItems(rest: String): List<String> =
        if (rest.count { it == ',' } >= 2) rest.split(',').map { it.trim() }.filter { it.isNotEmpty() } else listOf(rest)

    /** A one-paragraph method ("Heat butter, add mushrooms… Then add rice…") becomes one step per sentence. */
    private fun splitProse(text: String): List<String> {
        if (text.length < 220) return listOf(text)
        return text.split(Regex("(?<=[.!])\\s+(?=[A-Z\\p{Lu}])")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun stripHashtagsAtEnd(line: String): String =
        line.replace(Regex("(\\s*#[\\p{L}\\p{N}_]+)+\\s*$"), "").trim()

    private val titleNoise = SafeRegex.of("CaptionRecipeParser.titleNoise", 
        "^(bookmark|save|try|make|here'?s|here is|presenting|introducing|the (best|easiest)|easy|quick|recipe( for)?|full recipe)\\b( this| my| the| these| a| an)?\\s*",
        RegexOption.IGNORE_CASE,
    )

    internal fun cleanTitle(line: String): String {
        var t = line
        // Cut at the first emoji / "follow @" / "full recipe" / sentence end.
        t = t.split(Regex("[\\p{So}\\p{Cs}]|\\s(follow|full recipe|recipe below|👇)|[!?|]|\\.\\s", RegexOption.IGNORE_CASE)).first()
        t = t.replace(Regex("@[\\w.]+"), "").replace(Regex("#[\\w]+"), "").trim()
        repeat(2) { t = t.replace(titleNoise, "").trim() }
        // "bookmark this vegan ONE-POT CREAMY MUSHROOM RICE" → the shouted dish name is the title.
        Regex("[A-Z][A-Z'&-]+(\\s+[A-Z][A-Z'&-]+)+").findAll(t).maxByOrNull { it.value.length }
            ?.takeIf { it.value.length >= 8 && it.value.length < t.length }?.let { t = it.value }
        t = t.trim(' ', '-', ':', ',', '.').replace(Regex("\\s+"), " ")
        if (t.length > 70) t = t.take(70).substringBeforeLast(' ')
        return titleCase(t)
    }

    private fun titleCase(s: String): String {
        val letters = s.filter { it.isLetter() }
        val allUpper = letters.isNotEmpty() && letters.all { it.isUpperCase() }
        val allLower = letters.isNotEmpty() && letters.all { it.isLowerCase() }
        if (!allUpper && !allLower) return s
        val small = setOf("and", "or", "with", "of", "in", "a", "an", "the", "for", "on")
        return s.lowercase().split(' ').mapIndexed { i, w ->
            if (i > 0 && w in small) w else w.split('-').joinToString("-") { p -> p.replaceFirstChar { it.uppercaseChar() } }
        }.joinToString(" ")
    }
}
