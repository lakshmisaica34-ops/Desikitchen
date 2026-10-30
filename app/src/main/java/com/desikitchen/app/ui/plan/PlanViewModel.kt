package com.desikitchen.app.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desikitchen.app.data.model.MealPlanWithRecipe
import com.desikitchen.app.data.model.MealSlot
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.repo.GroceryRepository
import com.desikitchen.app.data.repo.MealPlanRepository
import com.desikitchen.app.data.repo.RecipeRepository
import com.desikitchen.app.data.repo.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class PlanDay(val date: LocalDate, val slots: Map<MealSlot, List<MealPlanWithRecipe>>)

data class PlanUiState(
    val loading: Boolean = true,
    val weekStart: LocalDate = LocalDate.now(),
    val weekStartDay: DayOfWeek = DayOfWeek.MONDAY,
    val weekOffset: Int = 0,
    val days: List<PlanDay> = emptyList(),
    val allRecipes: List<Recipe> = emptyList(),
    val autoGrocerySync: Boolean = true,
) {
    val mealCount: Int get() = days.sumOf { d -> d.slots.values.sumOf { it.size } }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(
    private val mealPlan: MealPlanRepository,
    recipes: RecipeRepository,
    private val grocery: GroceryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val offset = MutableStateFlow(0)
    private val messages = Channel<String>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    private val weekStart = combine(settings.weekStart, offset) { day, off ->
        Triple(LocalDate.now().with(TemporalAdjusters.previousOrSame(day)).plusWeeks(off.toLong()), day, off)
    }

    val uiState: StateFlow<PlanUiState> = weekStart.flatMapLatest { (start, day, off) ->
        mealPlan.observeWeek(start).map { entries -> Triple(start, day, off) to entries }
    }.combine(combine(recipes.observeAll(), settings.autoGrocerySync, ::Pair)) { (meta, entries), (all, auto) ->
        val (start, day, off) = meta
        val byDay = entries.groupBy { it.entry.epochDay }
        PlanUiState(
            loading = false, weekStart = start, weekStartDay = day, weekOffset = off, allRecipes = all, autoGrocerySync = auto,
            days = (0..6).map { i ->
                val date = start.plusDays(i.toLong())
                val dayEntries = byDay[date.toEpochDay()].orEmpty()
                PlanDay(date, MealSlot.entries.associateWith { s -> dayEntries.filter { it.entry.slot == s } })
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    fun previousWeek() { offset.value-- }
    fun nextWeek() { offset.value++ }
    fun thisWeek() { offset.value = 0 }
    fun goToWeek(weekOffset: Int) { offset.value = weekOffset }

    fun add(date: LocalDate, slot: MealSlot, recipe: Recipe) = viewModelScope.launch {
        mealPlan.add(date, slot, recipe.id, recipe.servings)
    }

    fun remove(entryId: Long) = viewModelScope.launch { mealPlan.remove(entryId) }

    fun removeMany(ids: Set<Long>) = viewModelScope.launch {
        mealPlan.removeAll(ids)
        messages.send("Removed ${ids.size} ${if (ids.size == 1) "recipe" else "recipes"}")
    }

    /** Drag-and-drop target / "Move to…" picker. */
    fun move(entryId: Long, date: LocalDate, slot: MealSlot, index: Int? = null) = viewModelScope.launch {
        mealPlan.move(entryId, date, slot, index)
    }

    fun nudge(entryId: Long, delta: Int) = viewModelScope.launch { mealPlan.nudge(entryId, delta) }
    fun setServings(entryId: Long, servings: Int) = viewModelScope.launch { mealPlan.setServings(entryId, servings) }

    fun clearWeek() = viewModelScope.launch {
        mealPlan.clearWeek(uiState.value.weekStart)
        messages.send("Week cleared")
    }

    fun setWeekStart(day: DayOfWeek) = settings.setWeekStart(day)

    fun generateGroceries(onDone: () -> Unit = {}) = viewModelScope.launch {
        val entries = mealPlan.getWeek(uiState.value.weekStart)
        if (entries.isEmpty()) { messages.send("Plan some meals first"); return@launch }
        val off = uiState.value.weekOffset
        if (settings.autoGrocerySync.value && off in 0..1) {
            val n = grocery.generateFromPlan(
                mealPlan.getRange(uiState.value.weekStart.minusWeeks(off.toLong()), uiState.value.weekStart.minusWeeks(off.toLong()).plusDays(13)),
            )
            messages.send("Groceries synced — $n plan items for this week and next")
        } else if (settings.autoGrocerySync.value) {
            val n = grocery.addPlanSnapshot(entries)
            messages.send("Added $n items from ${entries.size} meals (one-off copy; auto-sync covers this week and next)")
        } else {
            val n = grocery.generateFromPlan(entries)
            messages.send("Added $n items from ${entries.size} meals to Groceries")
        }
        onDone()
    }
}
