package com.desikitchen.app.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desikitchen.app.data.model.Aisles
import com.desikitchen.app.data.model.Ingredient
import com.desikitchen.app.data.model.ExtractionStatus
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.model.RecipeOrigin
import com.desikitchen.app.data.model.SourcePlatform
import com.desikitchen.app.data.model.Step
import com.desikitchen.app.data.repo.RecipeRepository
import com.desikitchen.app.util.ImportDraft
import com.desikitchen.app.util.ReviewHandoff
import com.desikitchen.app.util.ReviewSession
import com.desikitchen.app.util.RecipeTextParser
import com.desikitchen.app.util.formatAmount
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

private val keys = AtomicLong(1)

data class IngredientRow(
    val key: Long = keys.getAndIncrement(),
    val quantity: String = "",
    val unit: String = "",
    val name: String = "",
    val aisle: String = Aisles.OTHER,
    val note: String = "",
)

data class StepRow(val key: Long = keys.getAndIncrement(), val text: String = "", val timer: String = "")

data class RecipeForm(
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val cuisine: String = "",
    val mealType: String = "",
    val dietTags: Set<String> = emptySet(),
    val prep: String = "",
    val cook: String = "",
    val servings: Int = 4,
    val sourceUrl: String = "",
    val ingredients: List<IngredientRow> = listOf(IngredientRow()),
    val steps: List<StepRow> = listOf(StepRow()),
)

class AddEditRecipeViewModel(
    savedStateHandle: SavedStateHandle,
    private val recipes: RecipeRepository,
    /** Called once when a NEW recipe from a link import is saved (counts toward the free weekly quota). */
    private val onImportSaved: () -> Unit = {},
    /**
     * Persisted editor session for an imported draft (the app-wide importer): edits are saved as the user types and
     * the session is cleared on Save / discard, so killing the app never loses the draft. Null = not persisted.
     */
    private val reviewSession: ReviewSession? = null,
) : ViewModel() {
    val recipeId: Long = savedStateHandle.get<Long>("id") ?: -1L
    val isEdit: Boolean get() = recipeId > 0
    private var original: Recipe? = null

    var form by mutableStateOf(RecipeForm())
        private set
    var loading by mutableStateOf(recipeId > 0)
        private set
    var fromImport by mutableStateOf(false)
        private set
    var sourceUrl by mutableStateOf<String?>(null)
        private set
    var importWarnings by mutableStateOf<List<String>>(emptyList())
        private set
    var importPartial by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Creator byline from the import ("@handle"), stored in [Recipe.creatorHandle], never guessed. */
    private var creatorHandle: String? = null
    /** HTTPS avatar hotlink from the import, only if a scraper exposed one (none do today). Never invented. */
    private var creatorAvatarUrl: String? = null
    /** Source-reported creator profile/channel URL (validated https + platform host). */
    private var creatorProfileUrl: String? = null
    private var sourceServings: Int? = null
    /** The import draft this editor reviews, while its persisted session is open (cleared on Save / discard). */
    private var sessionDraft: ImportDraft.Draft? = null

    init {
        if (recipeId > 0) {
            viewModelScope.launch {
                recipes.getDetails(recipeId)?.let { d ->
                    original = d.recipe
                    val r = d.recipe
                    form = RecipeForm(
                        title = r.title, description = r.description, sourceUrl = r.sourceUrl.orEmpty(), imageUrl = r.imageUrl.orEmpty(),
                        cuisine = r.cuisine, mealType = r.mealType, dietTags = r.dietTagList.toSet(),
                        prep = r.prepMinutes.takeIf { it > 0 }?.toString().orEmpty(),
                        cook = r.cookMinutes.takeIf { it > 0 }?.toString().orEmpty(),
                        servings = r.servings,
                        ingredients = d.sortedIngredients.map {
                            IngredientRow(quantity = it.quantity?.let(::formatPlain).orEmpty(), unit = it.unit, name = it.name, aisle = it.aisle, note = it.note)
                        }.ifEmpty { listOf(IngredientRow()) },
                        steps = d.sortedSteps.map { StepRow(text = it.text, timer = it.timerMinutes?.toString().orEmpty()) }.ifEmpty { listOf(StepRow()) },
                    )
                }
                loading = false
            }
        } else if (savedStateHandle.get<Boolean>("draft") == true) {
            val handoff = ImportDraft.takeHandoff()
            if (handoff != null) applyDraft(handoff)
            else if (reviewSession != null) {
                // The system restored this screen after killing the app: the in-memory hand-off is gone, so reopen
                // the persisted editor session (draft + the user's edits) instead of showing an empty form.
                loading = true
                viewModelScope.launch {
                    try { reviewSession.resumeReview()?.let(::applyDraft) } finally { loading = false }
                }
            }
        }
    }

    private fun applyDraft(h: ReviewHandoff) {
        val d = h.draft
        sessionDraft = d.takeIf { reviewSession != null }
        fromImport = true
        sourceUrl = d.sourceUrl
        importWarnings = d.warnings
        importPartial = d.partial
        val p = d.parsed
        val (byline, rest) = splitByline(d.description)
        creatorHandle = byline
        creatorAvatarUrl = byline?.let { com.desikitchen.app.util.CreatorProfile.safeAvatarUrl(d.creatorAvatarUrl) }
        creatorProfileUrl = byline?.let { com.desikitchen.app.util.CreatorProfile.safeProfileUrl(d.creatorProfileUrl) }
        sourceServings = p.servings
        form = RecipeForm(
            title = p.title,
            description = rest,
            sourceUrl = d.sourceUrl.orEmpty(),
            imageUrl = d.imageUrl.orEmpty(),
            prep = d.prepMinutes.takeIf { it > 0 }?.toString().orEmpty(),
            cook = d.cookMinutes.takeIf { it > 0 }?.toString().orEmpty(),
            servings = p.servings ?: 4,
            ingredients = p.ingredients.map {
                IngredientRow(quantity = it.quantity?.let(::formatPlain).orEmpty(), unit = it.unit, name = it.name, aisle = it.aisle)
            }.ifEmpty { listOf(IngredientRow()) },
            steps = p.steps.map { StepRow(text = it) }.ifEmpty { listOf(StepRow()) },
        )
        // The user's in-progress fields from before the app was killed win over the imported values.
        RecipeFormCodec.decode(h.edits)?.let { form = it }
    }

    fun update(transform: (RecipeForm) -> RecipeForm) {
        form = transform(form); error = null
        sessionDraft?.let { d -> reviewSession?.updateReviewEdits(d, RecipeFormCodec.encode(form)) }
    }

    /**
     * Leaving Review without saving (top-bar / system back): the draft is discarded, as before; its persisted editor
     * session is cleared so it doesn't come back on the next launch.
     */
    fun discardReview() {
        val d = sessionDraft ?: return
        sessionDraft = null
        reviewSession?.finishReview(d)
    }

    fun updateIngredient(key: Long, t: (IngredientRow) -> IngredientRow) =
        update { f -> f.copy(ingredients = f.ingredients.map { if (it.key == key) t(it) else it }) }

    fun updateStep(key: Long, t: (StepRow) -> StepRow) =
        update { f -> f.copy(steps = f.steps.map { if (it.key == key) t(it) else it }) }

    fun addIngredient() = update { it.copy(ingredients = it.ingredients + IngredientRow()) }
    fun removeIngredient(key: Long) = update { it.copy(ingredients = it.ingredients.filterNot { r -> r.key == key }) }
    fun addStep() = update { it.copy(steps = it.steps + StepRow()) }
    fun removeStep(key: Long) = update { it.copy(steps = it.steps.filterNot { r -> r.key == key }) }
    fun moveStep(key: Long, delta: Int) = update { f ->
        val list = f.steps.toMutableList()
        val i = list.indexOfFirst { it.key == key }
        val j = i + delta
        if (i < 0 || j !in list.indices) f else { list.add(j, list.removeAt(i)); f.copy(steps = list) }
    }

    /** Bulk-adds ingredient lines ("2 tbsp ghee") parsed with the paste parser. */
    fun pasteIngredients(text: String) = update { f ->
        val parsed = text.lines().map { it.trim().trimStart('-', '•', '*').trim() }.filter { it.isNotBlank() }
            .map { RecipeTextParser.parseIngredient(it) }
            .map { IngredientRow(quantity = it.quantity?.let(::formatPlain).orEmpty(), unit = it.unit, name = it.name, aisle = it.aisle) }
        f.copy(ingredients = f.ingredients.filter { it.name.isNotBlank() } + parsed)
    }

    fun guessAisle(key: Long) = updateIngredient(key) { it.copy(aisle = RecipeTextParser.guessAisle(it.name)) }

    fun save(onSaved: (Long) -> Unit) {
        val f = form
        if (f.title.isBlank()) { error = "Give your recipe a title"; return }
        val ingredients = f.ingredients.filter { it.name.isNotBlank() }.map {
            Ingredient(
                name = it.name.trim(), quantity = parseQty(it.quantity), unit = it.unit.trim(),
                aisle = if (it.aisle == Aisles.OTHER) RecipeTextParser.guessAisle(it.name) else it.aisle, note = it.note.trim(),
            )
        }
        val steps = f.steps.filter { it.text.isNotBlank() }.map { Step(text = it.text.trim(), timerMinutes = it.timer.toIntOrNull()?.takeIf { m -> m > 0 }) }
        val base = original ?: Recipe(
            title = f.title, isUserCreated = true,
            origin = if (fromImport) RecipeOrigin.IMPORT else RecipeOrigin.MANUAL,
            creatorHandle = creatorHandle,
            creatorAvatarUrl = creatorAvatarUrl,
            creatorProfileUrl = creatorProfileUrl,
            sourceServings = sourceServings,
            extractionStatus = when {
                !fromImport -> null
                importPartial -> ExtractionStatus.PARTIAL
                else -> ExtractionStatus.COMPLETE
            },
        )
        val link = f.sourceUrl.trim().ifBlank { null }
        val recipe = base.copy(
            sourceUrl = link,
            sourcePlatform = SourcePlatform.fromUrl(link),
            sourceVideoId = SourcePlatform.videoId(link),
            updatedAt = System.currentTimeMillis(),
            title = f.title.trim(), description = f.description.trim(),
            imageUrl = f.imageUrl.trim().ifBlank { null }, cuisine = f.cuisine, mealType = f.mealType,
            dietTags = f.dietTags.joinToString(","), prepMinutes = f.prep.toIntOrNull() ?: 0,
            cookMinutes = f.cook.toIntOrNull() ?: 0, servings = f.servings.coerceAtLeast(1),
        )
        val countsAsImport = original == null && fromImport
        val session = sessionDraft
        viewModelScope.launch {
            val id = recipes.save(recipe, ingredients, steps)
            if (session != null) { sessionDraft = null; reviewSession?.finishReview(session) }
            if (countsAsImport) onImportSaved()
            onSaved(id)
        }
    }

    companion object {
        fun parseQty(s: String): Double? {
            val t = s.trim()
            if (t.isEmpty()) return null
            return RecipeTextParser.parseIngredient("$t x").quantity ?: t.replace(',', '.').toDoubleOrNull()
        }

        private fun formatPlain(v: Double): String = formatAmount(v)

        private val bylineRegex = Regex("^By (@?[\\w.\\- ]{1,60}) on [\\w ]{2,20}$", RegexOption.IGNORE_CASE)

        /**
         * Importers put "By @handle on Instagram" first in the description. Returns the handle (as written by the
         * source) and the remaining description, so the byline lands in [Recipe.creatorHandle] instead of free text.
         */
        fun splitByline(description: String): Pair<String?, String> {
            val lines = description.lines()
            val first = lines.firstOrNull()?.trim().orEmpty()
            val m = bylineRegex.find(first) ?: return null to description.trim()
            return m.groupValues[1].trim() to lines.drop(1).joinToString("\n").trim()
        }
    }
}
