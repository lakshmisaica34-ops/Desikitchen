package com.desikitchen.app.util

import com.desikitchen.app.data.model.RecipeWithDetails

/**
 * "Ask DesiKitchen" — an offline, rule-based helper scoped to one recipe. It answers the four
 * suggested prompts from the recipe's own ingredients and steps; no network or LLM involved.
 */
object RecipeAssistant {

    enum class Prompt(val label: String, val emoji: String) {
        SUBSTITUTE("Substitute an ingredient", "🔁"),
        HEALTHIER("Make it healthier", "🥗"),
        SIMPLER("Make it simpler", "⚡"),
        UNDERSTAND("Understand the recipe", "📖"),
    }

    private val substitutions = listOf(
        "paneer" to "firm tofu or soya chunks (soak first) for a vegan, higher-protein swap",
        "cream" to "cashew paste (10–12 cashews blended with ¼ cup water) or full-fat milk + 1 tsp cornflour",
        "ghee" to "neutral oil or butter; for Jain/vegan, use groundnut oil",
        "butter" to "ghee, or oil + a pinch of salt for a dairy-free version",
        "curd" to "plain Greek yoghurt, or coconut yoghurt for vegan",
        "dahi" to "plain Greek yoghurt, or coconut yoghurt for vegan",
        "yogurt" to "curd/dahi or coconut yoghurt",
        "maida" to "whole-wheat atta (texture will be denser) or half atta + half maida",
        "sugar" to "jaggery (gur) or coconut sugar, same quantity",
        "chicken" to "paneer, mushrooms or soya chunks — reduce cooking time by half",
        "mutton" to "chicken thighs (cook 40% less) or jackfruit for a veg version",
        "kasuri methi" to "a little dried oregano, or skip — flavour will be milder",
        "garam masala" to "a pinch each of cinnamon, clove, cardamom and black pepper",
        "onion" to "cabbage or extra tomato + hing for a Jain version",
        "garlic" to "a generous pinch of hing (asafoetida)",
        "ginger" to "¼ tsp dry ginger powder (saunth) per inch of fresh ginger",
        "basmati" to "any long-grain rice, or quinoa / brown rice (adjust water and time)",
        "rice" to "millets (foxtail, little millet) or cauliflower rice",
        "khoya" to "milk powder mixed with a little cream or ghee",
        "tamarind" to "lemon juice or amchur (dry mango powder)",
        "coconut" to "desiccated coconut soaked in warm water, or cashew paste",
        "urad" to "a mix of moong dal and poha for a lighter batter",
        "besan" to "rice flour or a mix of oat flour + cornflour",
        "chilli" to "paprika for colour without much heat",
        "egg" to "1 tbsp flax meal + 3 tbsp water per egg (in batters)",
    )

    fun answer(prompt: Prompt, details: RecipeWithDetails): String = when (prompt) {
        Prompt.SUBSTITUTE -> substitute(details)
        Prompt.HEALTHIER -> healthier(details)
        Prompt.SIMPLER -> simpler(details)
        Prompt.UNDERSTAND -> understand(details)
    }

    /** Free-text question: finds the ingredient / steps that mention the user's keywords. */
    fun ask(question: String, details: RecipeWithDetails): String {
        val q = question.lowercase()
        when {
            listOf("substitut", "replace", "instead", "swap", "don't have", "dont have").any { it in q } -> return substitute(details, q)
            listOf("health", "calor", "light", "diet", "oil").any { it in q } -> return healthier(details)
            listOf("simple", "quick", "fast", "easy", "shortcut").any { it in q } -> return simpler(details)
        }
        val words = q.split(Regex("[^a-z]+")).filter { it.length > 3 }.toSet()
        val ing = details.sortedIngredients.filter { i -> words.any { it in i.name.lowercase() } }
        val steps = details.sortedSteps.withIndex().filter { (_, s) -> words.any { it in s.text.lowercase() } }
        if (ing.isEmpty() && steps.isEmpty()) {
            return "I can only answer from this recipe (I'm fully offline). Try asking about one of its ingredients, " +
                "or tap a suggested prompt.\n\n" + understand(details)
        }
        return buildString {
            if (ing.isNotEmpty()) {
                appendLine("From the ingredients:")
                ing.forEach { appendLine("• ${Units.convert(it.quantity, it.unit, UnitSystem.METRIC).format()} ${it.name}${if (it.note.isNotBlank()) " (${it.note})" else ""}".replace("  ", " ")) }
                appendLine()
            }
            if (steps.isNotEmpty()) {
                appendLine("Where it comes up:")
                steps.forEach { (i, s) -> appendLine("Step ${i + 1}: ${s.text}") }
            }
        }.trim()
    }

    private fun substitute(details: RecipeWithDetails, focus: String? = null): String {
        val names = details.sortedIngredients.map { it.name.lowercase() }
        val hits = substitutions.filter { (key, _) -> names.any { key in it } && (focus == null || key in focus || names.none { n -> focus.contains(n) }) }
        val list = if (hits.isEmpty()) substitutions.filter { (key, _) -> names.any { key in it } } else hits
        if (list.isEmpty()) return "Every ingredient here is a pantry staple — no common swaps needed. If you're missing a spice, " +
            "garam masala or a pinch of chaat masala usually fills the gap."
        return buildString {
            appendLine("Swaps that work for ${details.recipe.title}:")
            appendLine()
            list.distinctBy { it.second }.take(8).forEach { (k, v) -> appendLine("• ${k.replaceFirstChar { it.uppercase() }} → $v") }
        }.trim()
    }

    private fun healthier(details: RecipeWithDetails): String {
        val text = (details.sortedIngredients.joinToString(" ") { it.name } + " " + details.sortedSteps.joinToString(" ") { it.text }).lowercase()
        val tips = mutableListOf<String>()
        if ("cream" in text) tips += "Replace cream with blended cashew–milk or hung curd (stir in off the heat so it doesn't split)."
        if ("butter" in text || "ghee" in text) tips += "Halve the butter/ghee; finish with 1 tsp on top for aroma instead of cooking in it."
        if ("deep" in text || "fry" in text) tips += "Air-fry or shallow-fry in a kadai with 1–2 tbsp oil instead of deep frying."
        if ("sugar" in text) tips += "Cut sugar by a third or use jaggery; a pinch of cardamom boosts perceived sweetness."
        if ("maida" in text) tips += "Use whole-wheat atta (or half-and-half) instead of maida."
        if ("rice" in text) tips += "Serve with brown rice, millets or extra salad to add fibre."
        if ("salt" in text) tips += "Go easy on salt and finish with lemon juice — acidity makes it taste seasoned."
        if (listOf("paneer", "chicken", "dal", "chana", "rajma").none { it in text }) tips += "Add protein: a handful of boiled chana, sprouts or paneer cubes."
        tips += "Load up on vegetables — spinach, peas or capsicum fold into most gravies."
        return "Lighter ${details.recipe.title}:\n\n" + tips.joinToString("\n") { "• $it" }
    }

    private fun simpler(details: RecipeWithDetails): String {
        val text = details.sortedSteps.joinToString(" ") { it.text }.lowercase()
        val tips = mutableListOf<String>()
        if ("marinat" in text) tips += "Skip the long marinade: 20 minutes is enough, or use store-bought tikka paste."
        if ("soak" in text || "overnight" in text) tips += "Use canned/boiled legumes or a pressure cooker to skip overnight soaking."
        if ("grind" in text || "blend" in text || "paste" in text) tips += "Use ready ginger-garlic paste and tomato purée instead of grinding."
        if ("ferment" in text || "batter" in text) tips += "Buy ready idli/dosa batter for weekday cooking."
        if ("pressure" !in text && listOf("dal", "rajma", "chana", "simmer").any { it in text }) tips += "Cook it in a pressure cooker/Instant Pot — 3–4 whistles instead of a long simmer."
        if ("dough" in text || "knead" in text) tips += "Use a stand mixer or ready atta dough; rest only 10 minutes."
        if ("dum" in text || "layer" in text) tips += "Make it one-pot: cook rice directly in the masala with measured water instead of layering."
        tips += "Prep all chopped veg and spices before you light the stove (mise en place) — it halves the stress."
        val steps = details.sortedSteps.size
        return "Simpler ${details.recipe.title} (${steps} steps, ${formatMinutes(details.recipe.totalMinutes)}):\n\n" +
            tips.joinToString("\n") { "• $it" }
    }

    private fun understand(details: RecipeWithDetails): String {
        val r = details.recipe
        val text = details.sortedSteps.joinToString(" ") { it.text }.lowercase()
        val techniques = listOf(
            "tadka" to "Tadka/tempering — whole spices bloomed in hot fat to release aroma.",
            "temper" to "Tadka/tempering — whole spices bloomed in hot fat to release aroma.",
            "marinat" to "Marination — yoghurt/acid tenderises and seasons the protein.",
            "dum" to "Dum — slow-cooking sealed so it steams in its own juices.",
            "ferment" to "Fermentation — natural yeasts make the batter light and tangy.",
            "knead" to "Kneading — develops gluten so the dough rolls and puffs.",
            "bhuno" to "Bhunao — frying the masala until oil separates for deep flavour.",
            "oil separates" to "Bhunao — frying the masala until oil separates for deep flavour.",
            "pressure" to "Pressure cooking — fast, soft legumes and meat.",
            "simmer" to "Simmering — gentle bubbling so flavours meld without scorching.",
        ).filter { it.first in text }.map { it.second }.distinct()
        return buildString {
            appendLine(r.title)
            if (r.description.isNotBlank()) appendLine(r.description)
            appendLine()
            appendLine("• Serves ${r.servings} · prep ${formatMinutes(r.prepMinutes)} · cook ${formatMinutes(r.cookMinutes)}")
            appendLine("• ${details.ingredients.size} ingredients, ${details.steps.size} steps")
            if (r.cuisine.isNotBlank()) appendLine("• Cuisine: ${r.cuisine}")
            if (r.dietTagList.isNotEmpty()) appendLine("• Diet: ${r.dietTagList.joinToString()}")
            if (techniques.isNotEmpty()) {
                appendLine()
                appendLine("Key techniques:")
                techniques.forEach { appendLine("• $it") }
            }
            appendLine()
            appendLine("The flow:")
            details.sortedSteps.take(6).forEachIndexed { i, s ->
                appendLine("${i + 1}. ${s.text.substringBefore('.').take(90)}")
            }
        }.trim()
    }
}
