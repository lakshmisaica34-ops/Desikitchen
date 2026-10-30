package com.desikitchen.app.util

/**
 * 2026-10-29 scraper hardening (Sai's rice-paper / salsa bugs): one place that decides whether a line is an
 * ingredient, a method step or nutrition info, shared by the caption parser, the plain-text parser and every AI path.
 *
 * Rules (never invents anything; it only moves or drops lines that were already there):
 *  - Nutrition headers ("Nutrition (per mochi)", "Macros:") and macro rows ("65 kcal Calories", "2 g Protein",
 *    "Calories: 600 kcal/ Fat: 18g") are never ingredients and never steps. They are dropped.
 *  - Imperative / method sentences ("Dip 1 rice paper sheet in…", "Now place it flat…", "Freeze for an hour…")
 *    are never ingredients. They go to steps when the recipe has no steps, otherwise they are dropped.
 *  - Empty + editable beats garbage: if nothing valid is left, the bucket stays empty and the draft is PARTIAL.
 *
 * All patterns go through [SafeRegex] and avoid "[:" class starts (Android ICU, see IcuRegexCompatTest).
 */
object RecipeSanitizer {

    /** Shared by every AI prompt (text, keyframes, video, photo) so the model's buckets match what we validate. */
    const val PROMPT_BUCKET_RULES =
        "\"ingredients\": ONLY food items, each one short line like \"2 tbsp olive oil\" or \"1 mango, diced\" (quantity and unit only if stated). " +
            "Never put an instruction or sentence in ingredients (\"Dip the rice paper…\", \"Now place…\", \"Freeze for an hour\" are steps). " +
            "\"steps\": the cooking instructions in order, one action per item, full sentences, never truncated. " +
            "Nutrition info (calories, kcal, protein, carbs, fat, fibre, \"Nutrition (per serving)\") is neither an ingredient nor a step: leave it out. " +
            "If only part of the recipe is stated, return only that part and leave the other list empty."

    private const val MACRO =
        "(?:calories|calorie|kcals?|cals?|energy|protein|proteins|carbs?|carbohydrates?|net carbs|fats?|total fat|saturated fat|" +
            "sat\\.? fat|fib(?:re|er)|sodium|cholesterol|potassium|macros)"

    /** "Nutrition", "Nutrition (per mochi)", "Nutrition facts per serving:", "Macros:", "Per serving:" (not "Nutritional yeast"). */
    private val nutritionHeader = SafeRegex.of("RecipeSanitizer.nutritionHeader",
        "^(?:approx\\.?\\s+|estimated\\s+)?(?:nutrition(?:al)?(?!\\s+yeast)(?:\\s+(?:facts|info|information|values?|breakdown))?|macros|macro breakdown|" +
            "per (?:serving|portion)|serving size)\\b.{0,50}$",
        RegexOption.IGNORE_CASE)

    /** "65 kcal Calories", "2 g Protein", "12g carbs", "Protein 2 g", "Calories: 600 kcal", "~65 cal". */
    private val macroRow = SafeRegex.of("RecipeSanitizer.macroRow",
        "^[~≈±]?\\s*\\d+(?:[.,]\\d+)?\\s*(?:k?cal|kcals?|g|gm|grams?|mg)?\\s*(?:of\\s+)?$MACRO\\s*[.!]?$|" +
            "^$MACRO\\s*[\\:：\\-–=]?\\s*[~≈]?\\s*\\d+(?:[.,]\\d+)?\\s*(?:k?cal|kcals?|g|gm|grams?|mg|%)?\\b",
        RegexOption.IGNORE_CASE)

    /** Two or more "macro + number" pairs on one line: "Calories: 600 kcal/ Fat: 18g/ Carbs: 65g/ Protein: 44g". */
    private val macroPair = SafeRegex.of("RecipeSanitizer.macroPair",
        "(?:$MACRO\\s*[\\:：\\-–=]?\\s*\\d+(?:[.,]\\d+)?\\s*(?:k?cal|g|mg)?|\\d+(?:[.,]\\d+)?\\s*(?:k?cal|kcals?|g|mg)\\s*(?:of\\s+)?$MACRO)",
        RegexOption.IGNORE_CASE)

    private val kcal = SafeRegex.of("RecipeSanitizer.kcal", "\\b\\d+(?:[.,]\\d+)?\\s*(?:kcals?|calories|cals?)\\b", RegexOption.IGNORE_CASE)

    /** Bullets / "1." / "Step 2:" / emoji in front of a line. */
    private val leading = SafeRegex.of("RecipeSanitizer.leading",
        "^(?:[^\\p{L}\\p{N}½¼¾⅓⅔⅛(]+|\\s*step\\s*\\d+\\s*[.\\:)\\-]?\\s*|\\d{1,2}\\s*[.)]\\s+)+", RegexOption.IGNORE_CASE)

    /** Cooking verbs / sequence words that open an instruction, not a food item. Whole word, followed by a space. */
    private val methodStart = SafeRegex.of("RecipeSanitizer.methodStart",
        "^(?:now|then|next|first|firstly|finally|lastly|after(?:wards)?|once|when|meanwhile|repeat|dip|place|put|add|mix|stir|combine|" +
            "heat|preheat|cook|bake|boil|simmer|fry|deep[ -]fry|saute|sauté|blend|pour|transfer|serve|garnish|let|cover|remove|chop|dice|" +
            "slice|cut|mince|whisk|beat|knead|roll|spread|sprinkle|season|marinate|bring|reduce|keep|enjoy|wrap|cool|refrigerate|chill|" +
            "freeze|toss|drain|rinse|wash|soak|grind|roast|grill|toast|melt|layer|assemble|arrange|flip|press|shape|divide|sear|" +
            "squeeze|tuck|fill|brush|finish|take|start|fold|scoop|seal|shake|strain|steam|temper|pressure[ -]cook|microwave|" +
            "air[ -]fry|grease|pulse|mash|stuff|refresh|leave|wait|use|make|prepare|crack|peel|grate|" +
            "zest|scrape|smash|drizzle|coat|dredge|soften|chop up|dice up|throw|toss in|pop)\\s(?!of\\b)",
        RegexOption.IGNORE_CASE)

    private val qtyStart = SafeRegex.of("RecipeSanitizer.qtyStart", "^(?:\\d|[½¼¾⅓⅔⅛]|a (?:pinch|few|handful|cup|tbsp|tsp|dash)\\b)", RegexOption.IGNORE_CASE)

    private val socialNoise = SafeRegex.of("RecipeSanitizer.socialNoise",
        "^(?:#\\S+\\s*)+$|^(?:follow|like (?:and|&)|comment |save (?:this|for)|share (?:this|with)|link in (?:my )?bio|dm (?:me|us)|tag (?:a|your))",
        RegexOption.IGNORE_CASE)

    private fun stripLead(s: String): String = s.trim().replace(leading, "").trim()

    fun isNutritionHeader(line: String): Boolean = stripLead(line).let { it.isNotEmpty() && nutritionHeader.matches(it) }

    /** A nutrition header, a macro row, or a line with ≥ 2 macro/number pairs or a kcal figure plus a macro. */
    fun isNutritionLine(line: String): Boolean {
        val s = stripLead(line)
        if (s.isEmpty()) return false
        if (nutritionHeader.matches(s)) return true
        macroRow.find(s)?.let { m ->
            // "50 g protein powder" is food: a bare "<n> g protein" row must END at the macro word (anchored $ branch),
            // the "Protein: 2 g" branch must not be followed by more words that read like food.
            val tail = s.substring(m.range.last + 1).trim()
            if (tail.isEmpty() || tail.first() in "/|,;·•(" || macroPair.containsMatchIn(tail)) return true
        }
        if (macroPair.findAll(s).count() >= 2) return true
        if (kcal.containsMatchIn(s) && s.length <= 40 && !qtyStartsFood(s)) return true
        return false
    }

    /** "300 kcal" alone is nutrition, but "1 cup (240 kcal) rice"… still food. */
    private fun qtyStartsFood(s: String): Boolean = Regex("^\\d+(?:[.,]\\d+)?\\s*(?:cups?|tbsp|tsp|ml|l|oz|lb|pcs|pieces?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(s)

    /**
     * An instruction rather than a food item: starts with a cooking verb / sequence word (whole word), or is a long
     * multi-clause sentence that doesn't start with a quantity.
     */
    fun isMethodLine(line: String): Boolean {
        val s = stripLead(line)
        if (s.isEmpty()) return false
        val words = s.split(Regex("\\s+")).size
        if (methodStart.containsMatchIn(s)) {
            // Short noun-like phrases that happen to start with a verb-ish word ("Top sirloin", "Cream cheese") stay food:
            // an instruction is ≥ 3 words or ends like a sentence.
            return words >= 3 || s.endsWith('.') || s.endsWith('!')
        }
        if (!qtyStart.containsMatchIn(s) && words >= 14 && Regex("[.!]\\s*$|\\.\\s+\\p{Lu}").containsMatchIn(s)) return true
        return false
    }

    fun isSocialNoise(line: String): Boolean = socialNoise.containsMatchIn(line.trim()) || socialNoise.containsMatchIn(stripLead(line))

    /** Validates one ingredient string: food-ish, not nutrition, not an instruction, not chatter, not absurdly long. */
    fun isIngredientText(line: String): Boolean {
        val s = stripLead(line)
        if (s.length < 2 || s.length > 120) return false
        if (!s.any { it.isLetter() }) return false
        if (isNutritionLine(s) || isMethodLine(s) || isSocialNoise(s)) return false
        return true
    }

    data class Cleaned(val ingredients: List<String>, val steps: List<String>, val droppedNutrition: Int, val movedToSteps: Int, val droppedOther: Int) {
        val changed: Boolean get() = droppedNutrition + movedToSteps + droppedOther > 0
    }

    /**
     * Cleans two string buckets. Method lines found among ingredients are moved to steps only when [steps] has no
     * real steps (keeps their order); otherwise dropped, because the real method is already there.
     */
    fun clean(ingredients: List<String>, steps: List<String>): Cleaned {
        var nutrition = 0; var other = 0
        val keptIng = mutableListOf<String>()
        val methodFromIng = mutableListOf<String>()
        for (raw in ingredients) {
            val i = raw.trim()
            when {
                i.isEmpty() -> Unit
                isNutritionLine(i) -> nutrition++
                isMethodLine(i) -> methodFromIng += stripLead(i)
                isIngredientText(i) -> keptIng += i
                else -> other++
            }
        }
        val keptSteps = steps.map { it.trim() }.filter { s ->
            when {
                s.isEmpty() -> false
                isNutritionLine(s) -> { nutrition++; false }
                isSocialNoise(s) -> { other++; false }
                else -> true
            }
        }
        val moved = if (keptSteps.isEmpty()) methodFromIng.size else 0
        if (keptSteps.isNotEmpty()) other += methodFromIng.size
        return Cleaned(keptIng, if (keptSteps.isEmpty()) methodFromIng else keptSteps, nutrition, moved, other)
    }

    /** Same rules on an already-parsed recipe (names are re-joined with qty/unit to judge the whole line). */
    fun clean(p: RecipeTextParser.ParsedRecipe): RecipeTextParser.ParsedRecipe {
        val lines = p.ingredients.map { it to lineOf(it) }
        val c = clean(lines.map { it.second }, p.steps)
        if (!c.changed) return p
        val keep = c.ingredients.toHashSet()
        return p.copy(ingredients = lines.filter { it.second in keep }.map { it.first }, steps = c.steps)
    }

    internal fun lineOf(i: RecipeTextParser.ParsedIngredient): String = buildString {
        i.quantity?.let { q -> append(if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()).append(' ') }
        if (i.unit.isNotEmpty()) append(i.unit).append(' ')
        append(i.name)
    }.trim()

    /** AI JSON → validated buckets. Never adds anything; counts what was removed so the importer can warn honestly. */
    fun cleanAi(x: VideoRecipeExtractor.Extracted): VideoRecipeExtractor.Extracted {
        val c = clean(x.ingredients, x.steps)
        return x.copy(ingredients = c.ingredients, steps = c.steps,
            dropped = x.dropped + c.droppedNutrition + c.droppedOther, movedToSteps = x.movedToSteps + c.movedToSteps)
    }

    /** Honest warning text for a cleaned AI result (null when nothing was removed and nothing is missing). */
    fun aiWarning(x: VideoRecipeExtractor.Extracted): String? {
        val parts = buildList {
            if (x.dropped > 0) add("removed ${x.dropped} line(s) that were nutrition info or not ingredients")
            if (x.movedToSteps > 0) add("moved ${x.movedToSteps} instruction(s) from ingredients to steps")
            if (x.ingredients.isEmpty()) add("no ingredient list was found, add them from the reel")
            if (x.steps.isEmpty()) add("no steps were found, add them from the reel")
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("; ")?.replaceFirstChar { it.uppercaseChar() }?.plus(".")
    }

    /**
     * Text-AI steps must come from the text: the step's opening verb and ≥ 60 % of its content words have to appear
     * in [source]. Stops "Cook halloumi until crispy" being written from a caption that only says "Crispy halloumi".
     */
    fun groundedSteps(steps: List<String>, source: String): List<String> {
        val src = source.lowercase()
        val word = Regex("[\\p{L}]{3,}")
        val stop = setOf("the", "and", "with", "for", "until", "into", "then", "your", "you", "this", "that", "from", "over", "some",
            "all", "now", "add", "them", "its", "about", "minutes", "minute", "well", "together")
        return steps.filter { st ->
            val ws = word.findAll(st.lowercase()).map { it.value }.toList()
            if (ws.isEmpty()) return@filter false
            val first = ws.first()
            val content = ws.filter { it !in stop }
            val hit = content.count { src.contains(it) }
            src.contains(first) && (content.isEmpty() || hit * 10 >= content.size * 6)
        }
    }

    /** Recipe signal for text AI: ≥ 3 quantities (with a unit, or a bare count + food word) outside nutrition lines, or a recipe/ingredient/method heading. */
    fun hasRecipeSignal(text: String): Boolean {
        val body = text.lines().filterNot { isNutritionLine(it) }.joinToString("\n")
        val qty = Regex("(?i)\\b\\d+(?:[./]\\d+)?\\s*(?:cups?|tbsp|tsp|teaspoons?|tablespoons?|g|gm|grams?|kg|ml|l|litres?|liters?|oz|lb|pinch|inch|cloves?)\\b|[½¼¾]")
        // Bare counts ("4 bread slices, 1 capsicum") also count, but not times / episodes ("up to 4 days", "Ep 12").
        val count = Regex("(?i)\\b\\d+(?:[./]\\d+)?\\s+(?!days?\\b|hours?\\b|hrs?\\b|minutes?\\b|mins?\\b|weeks?\\b|months?\\b|years?\\b|people\\b|servings?\\b|times\\b|of\\b)[a-z]{3,}")
        val heading = Regex("(?im)^\\W*(ingredients?|method|instructions?|steps|directions|how to make)\\b|\\b(recipe|ingredients?)\\s*[\\:：]")
        return qty.findAll(body).count() >= 3 || count.findAll(body).count() >= 3 || heading.containsMatchIn(body)
    }
}
