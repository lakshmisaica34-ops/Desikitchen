package com.desikitchen.app.ui.grocery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desikitchen.app.data.model.Aisles
import com.desikitchen.app.data.model.GroceryItem
import com.desikitchen.app.data.model.GrocerySource
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.repo.GroceryRepository
import com.desikitchen.app.data.repo.RecipeRepository
import com.desikitchen.app.data.repo.SettingsRepository
import com.desikitchen.app.util.RecipeTextParser
import com.desikitchen.app.util.UnitSystem
import com.desikitchen.app.util.Units
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class GrocerySort(val label: String) { AISLE("Aisle"), RECIPE("Recipe"), AZ("A–Z"), RECENT("Recent") }

/** Source filter chips: where an item came from. */
enum class GroceryFilter(val label: String) { ALL("All"), PLAN("Meal plan"), RECIPE("Recipes"), MANUAL("Added by you") }

/** One displayed line; identical name+unit items are merged and act together. */
data class GroceryRow(
    val ids: List<Long>,
    val name: String,
    val amount: String,
    val aisle: String,
    val recipes: List<String>,
    val checked: Boolean,
    val haveAtHome: Boolean = false,
    val sources: Set<String> = emptySet(),
    val addedAt: Long = 0,
) { val key: String get() = ids.joinToString("-") }

data class GrocerySection(val title: String, val rows: List<GroceryRow>)
data class GroceryRecipe(val id: Long, val title: String, val itemCount: Int, val imageUrl: String? = null, val fromPlan: Boolean = false)

data class GroceryUiState(
    val loading: Boolean = true,
    val sort: GrocerySort = GrocerySort.AISLE,
    val filter: GroceryFilter = GroceryFilter.ALL,
    val sections: List<GrocerySection> = emptyList(),
    val checked: List<GroceryRow> = emptyList(),
    val haveAtHome: List<GroceryRow> = emptyList(),
    val recipes: List<GroceryRecipe> = emptyList(),
    val remaining: Int = 0,
    val total: Int = 0,
    val allRecipes: List<Recipe> = emptyList(),
    val autoSync: Boolean = true,
) {
    val isEmpty: Boolean get() = total == 0
    /** Plain-text list for Share / Copy / Order Online: unchecked items you still need (not "have at home"). */
    val shareText: String get() = GroceryListBuilder.shareText(sections)
}

object GroceryListBuilder {
    const val ADDED_BY_YOU = "Added by you"

    fun build(
        items: List<GroceryItem>,
        sort: GrocerySort,
        filter: GroceryFilter,
        units: UnitSystem,
        images: Map<Long, String?> = emptyMap(),
    ): GroceryUiState {
        val visible = items.filter { matches(it, filter) }
        val (done, open) = visible.partition { it.checked }
        val (home, todo) = open.partition { it.haveAtHome }
        val rows = merge(todo, units)
        val sections = when (sort) {
            GrocerySort.AISLE -> rows.groupBy { it.aisle }.toList()
                .sortedBy { (a, _) -> Aisles.all.indexOf(a).let { if (it < 0) Int.MAX_VALUE else it } }
                .map { (a, r) -> GrocerySection(a, r.sortedBy { it.name.lowercase() }) }
            GrocerySort.RECIPE -> rows.groupBy { it.recipes.firstOrNull() ?: ADDED_BY_YOU }.toList()
                .sortedBy { (t, _) -> if (t == ADDED_BY_YOU) "\uFFFF" else t.lowercase() }
                .map { (t, r) -> GrocerySection(t, r.sortedBy { it.name.lowercase() }) }
            GrocerySort.AZ -> if (rows.isEmpty()) emptyList() else listOf(GrocerySection("All items", rows.sortedBy { it.name.lowercase() }))
            GrocerySort.RECENT -> if (rows.isEmpty()) emptyList() else listOf(GrocerySection("Recently added", rows.sortedWith(compareByDescending<GroceryRow> { it.addedAt }.thenBy { it.name.lowercase() })))
        }
        val recipes = items.filter { it.recipeId != null }.groupBy { it.recipeId!! }
            .map { (id, list) -> GroceryRecipe(id, list.first().recipeTitle ?: "Recipe", list.size, images[id], list.any { it.source == GrocerySource.PLAN }) }
            .sortedBy { it.title.lowercase() }
        return GroceryUiState(
            loading = false, sort = sort, filter = filter, sections = sections,
            checked = merge(done, units).sortedBy { it.name.lowercase() },
            haveAtHome = merge(home, units).sortedBy { it.name.lowercase() },
            recipes = recipes, remaining = rows.size, total = items.size,
        )
    }

    fun matches(item: GroceryItem, filter: GroceryFilter): Boolean = when (filter) {
        GroceryFilter.ALL -> true
        GroceryFilter.PLAN -> item.source == GrocerySource.PLAN
        GroceryFilter.RECIPE -> item.source == GrocerySource.RECIPE
        GroceryFilter.MANUAL -> item.source == GrocerySource.MANUAL && item.recipeId == null
    }

    fun merge(list: List<GroceryItem>, units: UnitSystem): List<GroceryRow> =
        list.groupBy { it.name.trim().lowercase() to it.unit.trim().lowercase() }.values.map { group ->
            val first = group.first()
            val qty = group.mapNotNull { it.quantity }.takeIf { it.isNotEmpty() }?.sum()
            GroceryRow(
                ids = group.map { it.id },
                name = first.name.replaceFirstChar { it.uppercase() },
                amount = qty?.let { Units.convert(it, first.unit, units).format() } ?: first.unit,
                aisle = first.aisle,
                recipes = group.mapNotNull { it.recipeTitle }.distinct(),
                checked = group.all { it.checked },
                haveAtHome = group.all { it.haveAtHome },
                sources = group.map { it.source }.toSet(),
                addedAt = group.maxOf { it.createdAt },
            )
        }

    fun shareText(sections: List<GrocerySection>): String = buildString {
        append("DesiKitchen grocery list\n")
        sections.forEach { s ->
            append("\n").append(s.title).append("\n")
            s.rows.forEach { r -> append("☐ ").append(r.name); if (r.amount.isNotBlank()) append(" — ").append(r.amount); append("\n") }
        }
    }.trimEnd()
}

class GroceryViewModel(
    private val grocery: GroceryRepository,
    recipes: RecipeRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val sort = MutableStateFlow(GrocerySort.AISLE)
    private val filter = MutableStateFlow(GroceryFilter.ALL)
    private val messages = Channel<String>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    val uiState: StateFlow<GroceryUiState> = combine(
        grocery.observeAll(),
        combine(sort, filter, ::Pair),
        settings.unitSystem,
        recipes.observeAll(),
        settings.autoGrocerySync,
    ) { items, (s, f), units, all, auto ->
        lastItems = items
        GroceryListBuilder.build(items, s, f, units, all.associate { it.id to it.imageUrl }).copy(allRecipes = all, autoSync = auto)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroceryUiState())

    fun setSort(s: GrocerySort) { sort.value = s }
    fun setFilter(f: GroceryFilter) { filter.value = f }

    fun addFromText(text: String) = viewModelScope.launch {
        text.lines().map { it.trim().trimStart('-', '•', '*', '☐').trim() }.filter { it.isNotBlank() }.forEach { line ->
            val p = RecipeTextParser.parseIngredient(line)
            val name = p.name.ifBlank { line }
            grocery.addManual(name, p.quantity, p.unit, RecipeTextParser.guessAisle(name))
        }
    }

    fun addRecipe(recipe: Recipe) = viewModelScope.launch {
        val n = grocery.addRecipe(recipe.id)
        messages.send("Added $n items from ${recipe.title}")
    }

    fun toggle(row: GroceryRow) = viewModelScope.launch { grocery.setChecked(row.ids, !row.checked) }
    fun setHaveAtHome(row: GroceryRow, have: Boolean) = viewModelScope.launch { grocery.setHaveAtHome(row.ids, have) }
    fun deselectPantry() = viewModelScope.launch {
        val n = grocery.deselectPantryStaples()
        messages.send(if (n == 0) "No pantry staples left to deselect" else "Moved $n pantry staples to “Have at home”")
    }
    fun delete(row: GroceryRow) = viewModelScope.launch { grocery.delete(row.ids) }
    fun clearChecked() = viewModelScope.launch { grocery.clearChecked() }
    fun clearAll() = viewModelScope.launch { grocery.clearAll() }
    fun removeRecipe(recipeId: Long) = viewModelScope.launch {
        grocery.delete(lastItems.filter { it.recipeId == recipeId }.map { it.id })
    }

    private var lastItems: List<GroceryItem> = emptyList()
}
