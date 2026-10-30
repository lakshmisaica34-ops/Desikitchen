package com.desikitchen.app.util

import com.desikitchen.app.data.model.Aisles

/**
 * Very small "paste recipe text" importer. Understands text like:
 *
 *   Jeera Rice
 *   Serves 4
 *   Ingredients
 *   1 cup basmati rice
 *   1 tsp cumin seeds
 *   Instructions
 *   1. Rinse rice.
 *   2. Temper cumin in ghee...
 */
object RecipeTextParser {

    data class ParsedIngredient(val quantity: Double?, val unit: String, val name: String, val aisle: String)
    data class ParsedRecipe(
        val title: String,
        val servings: Int?,
        val ingredients: List<ParsedIngredient>,
        val steps: List<String>,
    )

    private val knownUnits = setOf(
        "g", "kg", "ml", "l", "tsp", "tbsp", "cup", "cups", "oz", "lb", "lbs", "pinch", "clove", "cloves",
        "inch", "pcs", "piece", "pieces", "sprig", "sprigs", "bunch", "handful", "can", "fl oz",
    )
    private val ingredientHeaders = SafeRegex.of("RecipeTextParser.ingredientHeaders", "^(ingredients?|you will need|what you need)\\s*:?$", RegexOption.IGNORE_CASE)
    private val stepHeaders = SafeRegex.of("RecipeTextParser.stepHeaders", "^(instructions?|method|steps|directions|preparation)\\s*:?$", RegexOption.IGNORE_CASE)
    private val servesRegex = SafeRegex.of("RecipeTextParser.servesRegex", "(serves|servings?|yield)\\s*:?\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val bulletPrefix = SafeRegex.of("RecipeTextParser.bulletPrefix", "^\\s*([-*•·]|\\d+[.)]|step\\s*\\d+[.\\:)]?)\\s*", RegexOption.IGNORE_CASE)

    fun parse(text: String): ParsedRecipe {
        SafeRegex.requireOk("RecipeTextParser") // 1.1.6: readable error instead of silently wrong output
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var title = ""
        var servings: Int? = null
        val ingredients = mutableListOf<ParsedIngredient>()
        val steps = mutableListOf<String>()
        var section = 0 // 0 = header, 1 = ingredients, 2 = steps

        for (line in lines) {
            when {
                ingredientHeaders.matches(line) -> { section = 1; continue }
                stepHeaders.matches(line) -> { section = 2; continue }
            }
            servesRegex.find(line)?.let {
                if (section == 0) { servings = it.groupValues[2].toIntOrNull(); return@let }
            }
            // 2026-10-29: nutrition rows are never ingredients or steps.
            if (section != 0 && RecipeSanitizer.isNutritionLine(line)) continue
            when (section) {
                0 -> if (title.isEmpty()) title = line
                1 -> if (RecipeSanitizer.isMethodLine(line)) {
                    // Method started without a heading: route it (and what follows) to steps, never to ingredients.
                    section = 2; line.replace(bulletPrefix, "").takeIf { it.isNotBlank() }?.let { steps += it }
                } else ingredients += parseIngredient(line.replace(bulletPrefix, ""))
                2 -> line.replace(bulletPrefix, "").takeIf { it.isNotBlank() }?.let { steps += it }
            }
        }
        return RecipeSanitizer.clean(ParsedRecipe(title.ifBlank { "Imported recipe" }, servings, ingredients, steps))
    }

    /** Spelled-out / regional unit words → the short units the rest of the app (Units.kt) understands. */
    private val unitAliases = mapOf(
        "teaspoon" to "tsp", "teaspoons" to "tsp", "tsps" to "tsp", "tea spoon" to "tsp",
        "tablespoon" to "tbsp", "tablespoons" to "tbsp", "tbsps" to "tbsp", "tbs" to "tbsp", "tbl" to "tbsp",
        "gram" to "g", "grams" to "g", "gm" to "g", "gms" to "g", "gr" to "g", "grm" to "g", "grms" to "g",
        "kilogram" to "kg", "kilograms" to "kg", "kgs" to "kg", "kilo" to "kg",
        "milliliter" to "ml", "milliliters" to "ml", "millilitre" to "ml", "millilitres" to "ml", "mls" to "ml",
        "liter" to "l", "liters" to "l", "litre" to "l", "litres" to "l", "ltr" to "l", "ltrs" to "l",
        "ounce" to "oz", "ounces" to "oz", "pound" to "lb", "pounds" to "lb",
        "inches" to "inch", "pinches" to "pinch", "pc" to "pcs", "nos" to "pcs", "no" to "pcs",
        "cans" to "can", "bunches" to "bunch", "handfuls" to "handful", "c" to "cup",
    )

    private val rangeRegex = SafeRegex.of("RecipeTextParser.rangeRegex", "^(\\d+(?:[.,]\\d+)?|[½¼¾⅓⅔⅛])\\s*(?:-|–|~|to)\\s*(\\d+(?:[.,]\\d+)?|[½¼¾⅓⅔⅛])$")
    private val trailingQty = SafeRegex.of("RecipeTextParser.trailingQty", "^(.+?)\\s+[-–—:]\\s+(\\d.*|[½¼¾⅓⅔⅛].*)$")

    fun parseIngredient(line: String): ParsedIngredient {
        var text = line.trim().replace(Regex("^[-*•·▪◦]\\s*"), "").replace(Regex("\\s+"), " ")
            // WPRM-style "( (adjust to taste))" / "()" / " ," noise
            .replace(Regex("\\(\\s*\\("), "(").replace(Regex("\\)\\s*\\)"), ")").replace("()", "")
            .replace(Regex("\\s+,"), ",").trim()
        // "Onion chopped – 1 cup" / "Salt - 1 tsp" (common on Indian sites) → "1 cup Onion chopped"
        trailingQty.matchEntire(text)?.let { m ->
            val right = m.groupValues[2]
            if (right.length <= 25 && parseQuantityPrefix(right) != null) text = "$right ${m.groupValues[1]}"
        }
        // Split glued quantities: "1½cups", "200g", "2tbsp"
        text = text.replace(Regex("^(\\d+(?:[.,]\\d+)?[½¼¾⅓⅔⅛]?|[½¼¾⅓⅔⅛])([a-zA-Z])"), "$1 $2")
            .replace(Regex("^(\\d+)([½¼¾⅓⅔⅛])"), "$1 $2")
        val tokens = text.split(" ").filter { it.isNotEmpty() }.toMutableList()
        var qty: Double? = null
        var rangeNote = ""
        if (tokens.isNotEmpty()) {
            // Ranges written as one token ("7-8") or three ("7 - 8", "2 to 3").
            val joined3 = tokens.take(3).joinToString(" ")
            val r = rangeRegex.matchEntire(tokens.first()) ?: if (tokens.size >= 3) rangeRegex.matchEntire(joined3) else null
            if (r != null) {
                qty = parseNumber(r.groupValues[1])
                rangeNote = "(${r.groupValues[1]}–${r.groupValues[2]})"
                repeat(if (rangeRegex.matches(tokens.first())) 1 else 3) { tokens.removeAt(0) }
            } else {
                qty = parseNumber(tokens.first())
                if (qty != null) {
                    tokens.removeAt(0)
                    tokens.firstOrNull()?.let { next ->
                        if (next.contains('/') || next.singleOrNull() in fractionChars) parseNumber(next)?.let { qty = qty!! + it; tokens.removeAt(0) }
                    }
                }
            }
        }
        var unit = ""
        var unitWordUsed: String? = null
        if (qty != null && tokens.isNotEmpty()) {
            unitWordUsed = tokens.first()
            val two = tokens.take(2).joinToString(" ").lowercase().trimEnd('.', ',')
            val one = tokens.first().lowercase().trimEnd('.', ',')
            when {
                two == "fl oz" || two == "tea spoon" -> { unit = unitAliases[two] ?: two; tokens.removeAt(0); tokens.removeAt(0) }
                one in knownUnits -> { unit = one; tokens.removeAt(0) }
                one in unitAliases -> {
                    // "1 in" only counts as inch when something follows; "1 no" only as a count word.
                    unit = unitAliases.getValue(one); tokens.removeAt(0)
                }
            }
        }
        // "3 cloves (laung)" – the unit word is actually the ingredient; keep it as the name.
        if (unit.isNotEmpty() && Regex("^(\\([^)]*\\))?$").matches(tokens.joinToString(" "))) {
            tokens.add(0, unitWordUsed ?: unit); unit = ""
        }
        var name = tokens.joinToString(" ").removePrefix("of ").trim()
        // "1 tbsp (15 ml) oil" → "oil (15 ml)"
        Regex("^(\\([^)]*\\))\\s*(.+)$").matchEntire(name)?.let { name = "${it.groupValues[2]} ${it.groupValues[1]}" }
        name = name.trim().trimStart(',', ' ').trim()
        if (rangeNote.isNotEmpty()) name = "$name $rangeNote".trim()
        if (name.isBlank()) name = line.trim()
        return ParsedIngredient(qty, unit, name, guessAisle(name))
    }

    private val fractionChars = setOf('½', '¼', '¾', '⅓', '⅔', '⅛')

    /** True when [s] starts with a number we would read as a quantity. */
    private fun parseQuantityPrefix(s: String): Double? = parseNumber(s.split(" ").first().replace(Regex("[a-zA-Z]+$"), ""))

    private fun parseNumber(token: String): Double? {
        val unicode = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 0.333, '⅔' to 0.667, '⅛' to 0.125)
        unicode[token.singleOrNull()]?.let { return it }
        // "1½"
        if (token.length > 1 && token.last() in unicode) {
            token.dropLast(1).toDoubleOrNull()?.let { return it + unicode.getValue(token.last()) }
        }
        if (token.contains('/')) {
            val parts = token.split('/')
            val a = parts.getOrNull(0)?.toDoubleOrNull()
            val b = parts.getOrNull(1)?.toDoubleOrNull()
            return if (a != null && b != null && b != 0.0) a / b else null
        }
        return token.replace(',', '.').toDoubleOrNull()?.takeIf { it < 100_000 }
    }

    fun guessAisle(name: String): String {
        val n = name.lowercase()
        return when {
            listOf("chicken", "mutton", "lamb", "fish", "prawn").any { it in n } && "broth" !in n && "stock" !in n -> Aisles.MEAT
            Regex("\\beggs?\\b").containsMatchIn(n) -> Aisles.MEAT
            listOf("paneer", "milk", "curd", "yogurt", "cream", "butter", "khoya", "dahi").any { it in n } -> Aisles.DAIRY
            listOf("ghee", "oil", "salt", "vinegar", "paste", "puree", "water").any { it in n } -> Aisles.PANTRY
            listOf("dal", "lentil", "chana", "rajma", "chickpea", "urad", "moong", "toor").any { it in n } -> Aisles.LENTILS
            listOf("rice", "atta", "flour", "maida", "rava", "semolina", "poha").any { it in n } -> Aisles.GRAINS
            listOf("sugar", "jaggery", "baking", "cardamom syrup", "saffron").any { it in n } -> Aisles.SWEETS
            listOf("masala", "powder", "seeds", "cumin", "jeera", "turmeric", "haldi", "garam", "chilli powder",
                "clove", "cinnamon", "cardamom", "bay", "hing", "asafoetida", "methi", "kasuri", "mustard").any { it in n } -> Aisles.SPICES
            listOf("onion", "tomato", "garlic", "ginger", "chilli", "chili", "coriander", "potato", "spinach",
                "palak", "lemon", "carrot", "peas", "beans", "mint", "curry leaves", "cauliflower").any { it in n } -> Aisles.PRODUCE
            else -> Aisles.OTHER
        }
    }
}
