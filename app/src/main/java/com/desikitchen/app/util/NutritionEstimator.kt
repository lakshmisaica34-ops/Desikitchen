package com.desikitchen.app.util

import com.desikitchen.app.data.model.Ingredient

/** Rough per-serving nutrition estimate from a keyword table (values per 100 g). */
object NutritionEstimator {
    data class Macros(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val fibre: Double = 0.0) {
        operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat, fibre + o.fibre)
        fun times(f: Double) = Macros(kcal * f, protein * f, carbs * f, fat * f, fibre * f)
    }

    data class Estimate(val perServing: Macros, val matched: Int, val total: Int)

    // Ordered: first match wins, so specific keys come before generic ones.
    private val table = listOf(
        "ghee" to Macros(900.0, 0.0, 0.0, 100.0),
        "butter" to Macros(717.0, 0.9, 0.1, 81.0),
        "oil" to Macros(884.0, 0.0, 0.0, 100.0),
        "cream" to Macros(340.0, 2.1, 2.8, 36.0),
        "paneer" to Macros(265.0, 18.0, 1.2, 21.0),
        "khoya" to Macros(421.0, 14.6, 20.0, 31.0),
        "milk powder" to Macros(496.0, 26.0, 38.0, 27.0),
        "milk" to Macros(61.0, 3.2, 4.8, 3.3),
        "curd" to Macros(61.0, 3.5, 4.7, 3.3), "dahi" to Macros(61.0, 3.5, 4.7, 3.3), "yogurt" to Macros(61.0, 3.5, 4.7, 3.3),
        "chicken" to Macros(165.0, 31.0, 0.0, 3.6),
        "mutton" to Macros(294.0, 25.0, 0.0, 21.0), "lamb" to Macros(294.0, 25.0, 0.0, 21.0),
        "egg" to Macros(155.0, 13.0, 1.1, 11.0),
        "fish" to Macros(206.0, 22.0, 0.0, 12.0), "prawn" to Macros(99.0, 24.0, 0.2, 0.3),
        "rice" to Macros(360.0, 7.0, 79.0, 0.6, 1.3),
        "atta" to Macros(340.0, 13.0, 72.0, 2.5, 11.0), "maida" to Macros(364.0, 10.0, 76.0, 1.0, 2.7),
        "flour" to Macros(364.0, 10.0, 76.0, 1.0, 2.7), "rava" to Macros(360.0, 12.7, 73.0, 1.0, 3.9),
        "besan" to Macros(387.0, 22.0, 58.0, 6.7, 11.0),
        "rajma" to Macros(333.0, 24.0, 60.0, 0.8, 25.0), "chana" to Macros(364.0, 19.0, 61.0, 6.0, 17.0),
        "chickpea" to Macros(364.0, 19.0, 61.0, 6.0, 17.0),
        "dal" to Macros(340.0, 24.0, 60.0, 1.5, 15.0), "urad" to Macros(341.0, 25.0, 59.0, 1.6, 18.0),
        "lentil" to Macros(352.0, 25.0, 63.0, 1.1, 11.0),
        "sugar" to Macros(387.0, 0.0, 100.0, 0.0), "jaggery" to Macros(383.0, 0.4, 98.0, 0.1),
        "potato" to Macros(77.0, 2.0, 17.0, 0.1, 2.2), "aloo" to Macros(77.0, 2.0, 17.0, 0.1, 2.2),
        "onion" to Macros(40.0, 1.1, 9.3, 0.1, 1.7), "tomato" to Macros(18.0, 0.9, 3.9, 0.2, 1.2),
        "spinach" to Macros(23.0, 2.9, 3.6, 0.4, 2.2), "palak" to Macros(23.0, 2.9, 3.6, 0.4, 2.2),
        "peas" to Macros(81.0, 5.4, 14.0, 0.4, 5.1), "carrot" to Macros(41.0, 0.9, 10.0, 0.2, 2.8),
        "cashew" to Macros(553.0, 18.0, 30.0, 44.0, 3.3), "coconut" to Macros(354.0, 3.3, 15.0, 33.0, 9.0),
        "nut" to Macros(600.0, 20.0, 20.0, 50.0, 7.0),
    )

    /** Approximate grams for an ingredient line. */
    private fun grams(ing: Ingredient): Double? {
        val q = ing.quantity ?: return null
        return when (ing.unit.lowercase().trim()) {
            "g" -> q
            "kg" -> q * 1000
            "ml" -> q
            "l" -> q * 1000
            "cup", "cups" -> q * 200
            "tbsp" -> q * 14
            "tsp" -> q * 5
            "oz" -> q * 28.35
            "lb", "lbs" -> q * 453.6
            "pinch" -> 0.3
            "" , "pcs", "piece", "pieces", "medium", "large", "small" -> q * 80
            else -> q * 30
        }
    }

    fun estimate(ingredients: List<Ingredient>, servings: Int): Estimate {
        var total = Macros(0.0, 0.0, 0.0, 0.0)
        var matched = 0
        for (ing in ingredients) {
            val name = ing.name.lowercase()
            if ("water" in name || "salt" in name) continue
            val macros = table.firstOrNull { it.first in name }?.second ?: continue
            val g = grams(ing) ?: continue
            total += macros.times(g / 100.0)
            matched++
        }
        return Estimate(total.times(1.0 / servings.coerceAtLeast(1)), matched, ingredients.size)
    }
}
