package com.desikitchen.app.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desikitchen.app.data.model.CookbookWithRecipes
import com.desikitchen.app.data.model.MealPlanWithRecipe
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.repo.CookbookRepository
import com.desikitchen.app.data.repo.MealPlanRepository
import com.desikitchen.app.data.repo.RecipeRepository
import com.desikitchen.app.util.RecipeSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class SavedFilter(val label: String) { ALL("All"), FAVORITES("Favourites") }

data class SavedUiState(
    val loading: Boolean = true,
    val query: String = "",
    val filter: SavedFilter = SavedFilter.ALL,
    val recipes: List<Recipe> = emptyList(),
    val totalCount: Int = 0,
    val favoriteCount: Int = 0,
    val collections: List<CookbookWithRecipes> = emptyList(),
    /** Today → +6 days, for the "Meal plan" section (Sai lock #5: meal plan lives inside Saved). */
    val upcoming: List<MealPlanWithRecipe> = emptyList(),
)

/** Saved tab: personal library (all / favourites + search), text-first collections, and the meal plan entry. */
class SavedViewModel(
    private val recipes: RecipeRepository,
    private val cookbooks: CookbookRepository,
    mealPlan: MealPlanRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(SavedFilter.ALL)
    private val today = LocalDate.now()

    private val library = combine(recipes.observeAll(), recipes.observeIngredientIndex(), query, filter) { all, index, q, f ->
        val ranked = RecipeSearch.rank(all, q).filter { RecipeSearch.matches(it, q, index[it.id].orEmpty()) }
        val shown = if (f == SavedFilter.FAVORITES) ranked.filter { it.isFavorite } else ranked
        SavedUiState(
            loading = false, query = q, filter = f,
            recipes = if (q.isBlank()) shown.sortedByDescending { it.createdAt } else shown,
            totalCount = all.size, favoriteCount = all.count { it.isFavorite },
        )
    }

    val uiState: StateFlow<SavedUiState> = combine(
        library, cookbooks.observeAll(), mealPlan.observeRange(today, today.plusDays(6)),
    ) { s, books, plan ->
        s.copy(
            collections = books.sortedBy { it.cookbook.name.lowercase() },
            upcoming = plan.filter { it.recipe != null }.sortedWith(compareBy({ it.entry.epochDay }, { it.entry.slot.ordinal }, { it.entry.position })),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedUiState())

    fun setQuery(q: String) { query.value = q }
    fun setFilter(f: SavedFilter) { filter.value = f }
    fun createCollection(name: String) = viewModelScope.launch { if (name.isNotBlank()) cookbooks.create(name, "") }
    fun renameCollection(id: Long, name: String) = viewModelScope.launch { if (name.isNotBlank()) cookbooks.rename(id, name) }
    fun deleteCollection(id: Long) = viewModelScope.launch { cookbooks.delete(id) }
    fun toggleFavorite(r: Recipe) = viewModelScope.launch { recipes.setFavorite(r.id, !r.isFavorite) }
}
