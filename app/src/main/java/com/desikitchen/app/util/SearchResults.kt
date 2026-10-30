package com.desikitchen.app.util

import com.desikitchen.app.data.model.Recipe

/** States of the dedicated Search Results screen. Pure, so the state logic is unit-tested. */
sealed interface SearchState {
    data object Hint : SearchState
    data object Loading : SearchState
    data class Results(val query: String, val hits: List<Hit>) : SearchState
    data class NoResults(val query: String) : SearchState
    data class Error(val message: String) : SearchState

    data class Hit(val recipe: Recipe, val matchedIngredients: List<String>)
}

object SearchResults {
    const val ERROR = "Search isn't working right now. Try again."

    /** [RecipeSearch] over the saved library: blank → hint, no match → no results, else ranked hits. */
    fun compute(query: String, all: List<Recipe>, index: Map<Long, List<String>>): SearchState {
        val q = query.trim()
        if (q.isEmpty()) return SearchState.Hint
        val hits = RecipeSearch.rank(all, q)
            .filter { RecipeSearch.matches(it, q, index[it.id].orEmpty()) }
            .map { r ->
                val titleHit = RecipeSearch.tokens(q).all { r.title.contains(it, ignoreCase = true) }
                SearchState.Hit(r, if (titleHit) emptyList() else RecipeSearch.matchedIngredients(q, index[r.id].orEmpty()))
            }
        return if (hits.isEmpty()) SearchState.NoResults(q) else SearchState.Results(q, hits)
    }
}
