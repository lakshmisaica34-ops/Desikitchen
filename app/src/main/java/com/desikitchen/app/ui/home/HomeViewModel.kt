package com.desikitchen.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.repo.RecipeRepository
import com.desikitchen.app.data.repo.SettingsRepository
import com.desikitchen.app.util.RecipeSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val query: String = "",
    val selectedTags: Set<String> = emptySet(),
    val availableTags: List<String> = emptyList(),
    val pinned: List<Recipe> = emptyList(),
    /** Search/filter result over the whole library. */
    val recipes: List<Recipe> = emptyList(),
    /** Newest first — Discover's "Recently saved" row. */
    val recent: List<Recipe> = emptyList(),
    val totalCount: Int = 0,
    val loading: Boolean = true,
    val displayName: String = "",
) {
    /** "What's the plan, <first name>?" — falls back to "chef" when no name is set. */
    val greetingName: String get() = firstName(displayName)

    companion object {
        fun firstName(displayName: String): String =
            displayName.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() } ?: "chef"
    }
}

/** Powers Discover (greeting, recently saved, search) and the Saved library list. */
class HomeViewModel(
    private val recipes: RecipeRepository,
    settings: SettingsRepository? = null,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val tags = MutableStateFlow<Set<String>>(emptySet())
    private val name = settings?.displayName ?: MutableStateFlow("")

    private val allWithIngredients = combine(recipes.observeAll(), recipes.observeIngredientIndex(), ::Pair)

    val uiState: StateFlow<HomeUiState> = combine(allWithIngredients, query, tags, name) { (all, index), q, selected, who ->
        val available = all.flatMap { listOf(it.cuisine, it.mealType) + it.dietTagList }
            .filter { it.isNotBlank() }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
        val filtered = RecipeSearch.rank(all, q).filter { r ->
            RecipeSearch.matches(r, q, index[r.id].orEmpty()) && selected.all { t -> tagsOf(r).contains(t) }
        }
        HomeUiState(
            query = q,
            selectedTags = selected,
            availableTags = available,
            pinned = if (q.isBlank() && selected.isEmpty()) all.filter { it.isFavorite } else emptyList(),
            recipes = filtered,
            recent = all.sortedByDescending { it.createdAt }.take(12),
            totalCount = all.size,
            loading = false,
            displayName = who,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(q: String) { query.value = q }
    fun toggleTag(tag: String) { tags.value = tags.value.let { if (tag in it) it - tag else it + tag } }
    fun clearFilters() { tags.value = emptySet(); query.value = "" }
    fun togglePin(recipe: Recipe) = viewModelScope.launch { recipes.setFavorite(recipe.id, !recipe.isFavorite) }

    private fun tagsOf(r: Recipe) = (listOf(r.cuisine, r.mealType) + r.dietTagList).toSet()
}
