package com.desikitchen.app.ui.cookbooks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desikitchen.app.data.model.CookbookWithRecipes
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.repo.CookbookRepository
import com.desikitchen.app.data.repo.RecipeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CookbooksViewModel(private val cookbooks: CookbookRepository) : ViewModel() {
    val cookbookList: StateFlow<List<CookbookWithRecipes>?> = cookbooks.observeAll()
        .map<List<CookbookWithRecipes>, List<CookbookWithRecipes>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, emoji: String) = viewModelScope.launch {
        cookbooks.create(name, "")
    }
}

data class CookbookDetailUiState(
    val cookbook: CookbookWithRecipes? = null,
    val allRecipes: List<Recipe> = emptyList(),
    val loading: Boolean = true,
)

class CookbookDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val cookbooks: CookbookRepository,
    recipes: RecipeRepository,
) : ViewModel() {
    val cookbookId: Long = savedStateHandle.get<Long>("id") ?: -1L

    val uiState: StateFlow<CookbookDetailUiState> =
        combine(cookbooks.observe(cookbookId), recipes.observeAll()) { cb, all ->
            CookbookDetailUiState(cb, all, loading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CookbookDetailUiState())

    fun addRecipe(recipeId: Long) = viewModelScope.launch { cookbooks.addRecipe(cookbookId, recipeId) }
    fun removeRecipe(recipeId: Long) = viewModelScope.launch { cookbooks.removeRecipe(cookbookId, recipeId) }
    fun rename(name: String) = viewModelScope.launch { cookbooks.rename(cookbookId, name) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { cookbooks.delete(cookbookId); onDone() }
}
