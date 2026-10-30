package com.desikitchen.app.ui.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.repo.RecipeRepository
import com.desikitchen.app.data.repo.SettingsRepository
import com.desikitchen.app.util.UnitSystem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek

data class MoreUiState(
    val favorites: List<Recipe> = emptyList(),
    val recipeCount: Int = 0,
    val myRecipeCount: Int = 0,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val keepScreenOn: Boolean = true,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val displayName: String = "",
    val autoGrocerySync: Boolean = true,
    val cookedTotal: Int = 0,
    val cookedRecipes: Int = 0,
    val isPlus: Boolean = false,
)

class MoreViewModel(
    private val recipes: RecipeRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<MoreUiState> = combine(
        combine(recipes.observeFavorites(), recipes.observeAll(), ::Pair),
        combine(settings.unitSystem, settings.keepScreenOn, ::Pair), settings.weekStart,
        combine(settings.displayName, settings.isPlus, ::Pair), settings.autoGrocerySync,
    ) { (fav, all), (units, screen), week, (name, plus), auto ->
        MoreUiState(
            fav, all.size, all.count { it.isUserCreated }, units, screen, week, name, auto,
            cookedTotal = all.sumOf { it.cookedCount }, cookedRecipes = all.count { it.cookedCount > 0 },
            isPlus = plus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUiState())

    fun setUnitSystem(s: UnitSystem) = settings.setUnitSystem(s)
    fun setKeepScreenOn(b: Boolean) = settings.setKeepScreenOn(b)
    fun setWeekStart(d: DayOfWeek) = settings.setWeekStart(d)
    fun setDisplayName(n: String) = settings.setDisplayName(n)
    fun setAutoGrocerySync(b: Boolean) = settings.setAutoGrocerySync(b)
    fun unpin(id: Long) = viewModelScope.launch { recipes.setFavorite(id, false) }
}
