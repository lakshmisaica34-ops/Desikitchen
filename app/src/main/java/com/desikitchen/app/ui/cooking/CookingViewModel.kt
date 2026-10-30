package com.desikitchen.app.ui.cooking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desikitchen.app.data.model.RecipeWithDetails
import com.desikitchen.app.data.repo.RecipeRepository
import com.desikitchen.app.data.repo.SettingsRepository
import com.desikitchen.app.util.UnitSystem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CookingUiState(
    val loading: Boolean = true,
    val details: RecipeWithDetails? = null,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
)

class CookingViewModel(
    savedStateHandle: SavedStateHandle,
    private val recipes: RecipeRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val recipeId: Long = savedStateHandle.get<Long>("id") ?: -1L

    val uiState: StateFlow<CookingUiState> =
        combine(recipes.observeDetails(recipeId), settings.unitSystem) { d, u -> CookingUiState(false, d, u) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CookingUiState())

    /** Finish in cook mode = "I cooked this": bumps the persisted cooked count and last-cooked date. */
    fun markCooked(onDone: () -> Unit) = viewModelScope.launch {
        recipes.markCooked(recipeId, System.currentTimeMillis())
        onDone()
    }
}
