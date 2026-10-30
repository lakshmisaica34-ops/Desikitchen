package com.desikitchen.app.util

import com.desikitchen.app.data.model.Recipe

/**
 * Home / All-recipes search: every word of the query must appear in the title, description, cuisine, meal type,
 * diet tags or one of the recipe's ingredients ("paneer peas" finds Matar Paneer). Title hits rank first.
 */
object RecipeSearch {
    fun tokens(query: String): List<String> = query.lowercase().split(Regex("[\\s,]+")).map { it.trim() }.filter { it.isNotEmpty() }

    fun matches(recipe: Recipe, query: String, ingredients: List<String> = emptyList()): Boolean {
        val words = tokens(query)
        if (words.isEmpty()) return true
        val hay = haystack(recipe)
        return words.all { w -> hay.contains(w) || ingredients.any { it.contains(w, ignoreCase = true) } }
    }

    /** Which fields matched — used to show "matches ingredient: paneer" under a result. */
    fun matchedIngredients(query: String, ingredients: List<String>): List<String> {
        val words = tokens(query)
        return ingredients.filter { ing -> words.any { ing.contains(it, ignoreCase = true) } }.distinct()
    }

    fun rank(recipes: List<Recipe>, query: String): List<Recipe> {
        val words = tokens(query)
        if (words.isEmpty()) return recipes
        return recipes.sortedByDescending { r -> words.count { r.title.contains(it, ignoreCase = true) } }
    }

    private fun haystack(r: Recipe) =
        listOf(r.title, r.description, r.cuisine, r.mealType, r.dietTags).joinToString(" ").lowercase()
}
