package com.desikitchen.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desikitchen.app.data.model.Cookbook
import com.desikitchen.app.data.model.Ingredient
import com.desikitchen.app.data.model.MealSlot
import com.desikitchen.app.data.model.RecipeWithDetails
import com.desikitchen.app.data.repo.CookbookRepository
import com.desikitchen.app.data.repo.GroceryRepository
import com.desikitchen.app.data.repo.MealPlanRepository
import com.desikitchen.app.data.repo.RecipeRepository
import com.desikitchen.app.data.repo.SettingsRepository
import com.desikitchen.app.util.CreatorProfile
import com.desikitchen.app.util.RecipePdf
import com.desikitchen.app.util.UnitSystem
import com.desikitchen.app.util.Units
import com.desikitchen.app.util.formatAmount
import com.desikitchen.app.util.formatMinutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class RecipeDetailUiState(
    val loading: Boolean = true,
    val details: RecipeWithDetails? = null,
    val servings: Int = 4,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val keepScreenOn: Boolean = true,
    val cookbooks: List<Cookbook> = emptyList(),
    val memberCookbookIds: Set<Long> = emptySet(),
    /** Ingredients ticked as already in the pantry; they are skipped when adding to groceries. */
    val pantry: Set<Long> = emptySet(),
    /** Other recipes on THIS device by the same creator (normalised handle), excluding this one. */
    val moreFromCreatorCount: Int = 0,
) {
    private val recipe get() = details?.recipe

    /** "Open original" target: the stored source link, or null. */
    val originalUrl: String? get() = recipe?.sourceUrl?.trim()?.takeIf { it.isNotEmpty() }

    /** Stored creator handle (as imported, e.g. "@cook.book"), or null. Never invented. */
    val creatorHandle: String? get() = recipe?.let(CreatorProfile::handleOf)

    /** HTTPS avatar hotlink, or null → monogram. Never persist the bytes. */
    val creatorAvatarUrl: String? get() = CreatorProfile.safeAvatarUrl(recipe?.creatorAvatarUrl)

    /** IG profile (derived from handle) / YT channel (only if known), or null → hide the Follow button. */
    val creatorProfileUrl: String? get() = recipe?.let(CreatorProfile::profileUrl)

    /** "Follow on Instagram" / "Visit YouTube channel" when [creatorProfileUrl] is set. */
    val creatorFollowLabel: String? get() = creatorProfileUrl?.let { CreatorProfile.followLabel(recipe?.sourcePlatform) }

    /** Show "More from this creator" whenever there is a handle (the Creator page itself lists this recipe too). */
    val canOpenCreator: Boolean get() = creatorHandle != null

    val toBuyCount: Int get() = details?.ingredients?.count { it.id !in pantry } ?: 0

    val factor: Double
        get() = details?.let { servings.toDouble() / it.recipe.servings.coerceAtLeast(1) } ?: 1.0

    fun displayQuantity(ing: Ingredient): String {
        val q = ing.quantity?.times(factor)
        return if (q == null) ing.unit else Units.convert(q, ing.unit, unitSystem).format()
    }
}

class RecipeDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val recipes: RecipeRepository,
    private val cookbooks: CookbookRepository,
    private val mealPlan: MealPlanRepository,
    private val grocery: GroceryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val recipeId: Long = savedStateHandle.get<Long>("id") ?: -1L
    private val servingsOverride = MutableStateFlow<Int?>(null)
    private val pantry = MutableStateFlow<Set<Long>>(emptySet())

    private val messages = Channel<String>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    /** One-shot navigation/intent requests; the screen collects and performs them (VM holds no Context). */
    sealed interface Action {
        /** Open in browser / IG / YT app; on failure call [postMessage]("No app can open that link"). */
        data class OpenUrl(val url: String) : Action
        /** Navigate to `creator/{handle}` (Routes.creator(handle)). */
        data class OpenCreator(val handle: String) : Action
    }

    private val actions = Channel<Action>(Channel.BUFFERED)
    val actionFlow = actions.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val moreFromCreator = recipes.observeDetails(recipeId)
        .map { it?.recipe?.creatorHandle?.trim()?.takeIf { h -> h.isNotEmpty() } }
        .distinctUntilChanged()
        .flatMapLatest { h -> if (h == null) flowOf(0) else recipes.observeByCreator(h).map { list -> list.count { it.id != recipeId } } }

    private val base = combine(
        recipes.observeDetails(recipeId),
        cookbooks.observeAll(),
        cookbooks.observeIdsForRecipe(recipeId),
        settings.unitSystem,
        settings.keepScreenOn,
    ) { details, allBooks, ids, units, screenOn ->
        RecipeDetailUiState(
            loading = false,
            details = details,
            servings = details?.recipe?.servings ?: 4,
            unitSystem = units,
            keepScreenOn = screenOn,
            cookbooks = allBooks.map { it.cookbook },
            memberCookbookIds = ids.toSet(),
        )
    }

    val uiState: StateFlow<RecipeDetailUiState> = combine(base, servingsOverride, pantry, moreFromCreator) { s, override, have, more ->
        (if (override != null) s.copy(servings = override) else s).copy(pantry = have, moreFromCreatorCount = more)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeDetailUiState())

    fun setServings(n: Int) { servingsOverride.value = n.coerceIn(1, 50) }
    fun setUnitSystem(system: UnitSystem) = settings.setUnitSystem(system)

    fun togglePinned() = viewModelScope.launch {
        val r = uiState.value.details?.recipe ?: return@launch
        recipes.setFavorite(r.id, !r.isFavorite)
        messages.send(if (r.isFavorite) "Removed from favourites" else "Saved to favourites")
    }

    fun addToPlan(date: LocalDate, slot: MealSlot) = viewModelScope.launch {
        mealPlan.add(date, slot, recipeId, uiState.value.servings)
        messages.send("Added to ${slot.label}, ${date.format(DateTimeFormatter.ofPattern("EEE d MMM"))}")
    }

    fun togglePantry(ingredientId: Long) {
        pantry.value = pantry.value.let { if (ingredientId in it) it - ingredientId else it + ingredientId }
    }

    fun addToGroceries() = viewModelScope.launch {
        val n = grocery.addRecipe(recipeId, uiState.value.servings, pantry.value)
        messages.send(if (n == 0) "Everything is already in your pantry" else "Added $n item${if (n == 1) "" else "s"} to your Grocery List")
    }

    fun setCookbooks(selected: Set<Long>) = viewModelScope.launch {
        val current = uiState.value.memberCookbookIds
        (selected - current).forEach { cookbooks.addRecipe(it, recipeId) }
        (current - selected).forEach { cookbooks.removeRecipe(it, recipeId) }
        messages.send("Cookbooks updated")
    }

    fun createCookbookWithRecipe(name: String, emoji: String) = viewModelScope.launch {
        val id = cookbooks.create(name, emoji)
        cookbooks.addRecipe(id, recipeId)
        messages.send("Added to new cookbook “$name”")
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        recipes.delete(recipeId)
        onDone()
    }

    fun markCooked() = viewModelScope.launch {
        val r = uiState.value.details?.recipe ?: return@launch
        recipes.markCooked(r.id, System.currentTimeMillis())
        val n = r.cookedCount + 1
        messages.send("Shabash! ${r.title} cooked ${if (n == 1) "for the first time" else "$n times"}")
    }

    fun setImage(imageUrl: String?) = viewModelScope.launch {
        recipes.setImage(recipeId, imageUrl)
        messages.send(if (imageUrl == null) "Photo removed" else "Recipe photo updated")
    }

    fun postMessage(text: String) = viewModelScope.launch { messages.send(text) }

    /** "Open original" (sourceUrl). No-op when the recipe has no source link. */
    fun openOriginal() { uiState.value.originalUrl?.let { actions.trySend(Action.OpenUrl(it)) } }

    /** "Follow on Instagram" / YouTube channel. No-op when no profile URL can be derived. */
    fun openCreatorProfile() { uiState.value.creatorProfileUrl?.let { actions.trySend(Action.OpenUrl(it)) } }

    /** "More from this creator" → Creator page. No-op without a stored handle. */
    fun navigateToCreator() { uiState.value.creatorHandle?.let { actions.trySend(Action.OpenCreator(it)) } }

    /** PDF content at the current servings and unit system. */
    fun pdfBlocks(): List<RecipePdf.Block> {
        val s = uiState.value
        val d = s.details ?: return emptyList()
        val r = d.recipe
        return RecipePdf.blocks(
            title = "${r.title}",
            description = r.description,
            meta = listOf(
                "Serves ${s.servings}",
                if (r.prepMinutes > 0) "Prep ${formatMinutes(r.prepMinutes)}" else "",
                if (r.cookMinutes > 0) "Cook ${formatMinutes(r.cookMinutes)}" else "",
                (listOf(r.cuisine) + r.dietTagList).filter { it.isNotBlank() }.joinToString(", "),
            ),
            ingredients = d.sortedIngredients.map { ing ->
                buildString {
                    if (ing.quantity != null) append(s.displayQuantity(ing)).append(' ')
                    append(ing.name)
                    if (ing.note.isNotBlank()) append(", ${ing.note}")
                }
            },
            steps = d.sortedSteps.map { it.text },
            source = r.sourceUrl,
        )
    }

    fun exportText(): String {
        val s = uiState.value
        val d = s.details ?: return ""
        val r = d.recipe
        return buildString {
            appendLine(r.title)
            if (r.description.isNotBlank()) appendLine(r.description)
            r.creatorHandle?.takeIf { it.isNotBlank() }?.let { appendLine("By $it") }
            appendLine()
            appendLine("INGREDIENTS")
            d.sortedIngredients.forEach { ing ->
                append("• ")
                if (ing.quantity != null) append(s.displayQuantity(ing)).append(' ')
                append(ing.name)
                if (ing.note.isNotBlank()) append(", ${ing.note}")
                appendLine()
            }
            appendLine()
            appendLine("INSTRUCTIONS")
            d.sortedSteps.forEachIndexed { i, st -> appendLine("${i + 1}. ${st.text}") }
            appendLine()
            r.sourceUrl?.takeIf { it.isNotBlank() }?.let { appendLine("Original: $it") }
            append("Shared from DesiKitchen")
        }
    }

    @Suppress("unused")
    fun formatScaled(q: Double) = formatAmount(q)
}
