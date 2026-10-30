package com.desikitchen.app.util

import com.desikitchen.app.data.model.MealSlot
import com.desikitchen.app.data.repo.GroceryRepository
import com.desikitchen.app.data.repo.MealPlanRepository
import com.desikitchen.app.data.repo.RecipeRepository
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Coco meal-plan / grocery actions (Phase 1). Deterministic and library-only: a request like
 * "add Jeera Rice to Tuesday dinner" or "put Aloo Gobi on my grocery list" is resolved against the saved
 * library, never the model, and Coco **proposes** the write for the user to confirm. If the recipe, day or meal is
 * unclear Coco asks a short follow-up instead of guessing. Servings and quantities are exactly what the recipe stores.
 */
object CocoActions {
    enum class Kind { PLAN, GROCERY }

    /** A (possibly partial) action. [candidates] are saved-recipe ids Coco offered when the recipe was ambiguous. */
    data class Draft(
        val kind: Kind,
        val recipeId: Long? = null,
        val epochDay: Long? = null,
        val slot: MealSlot? = null,
        val candidates: List<Long> = emptyList(),
    ) {
        val complete: Boolean get() = recipeId != null && (kind == Kind.GROCERY || (epochDay != null && slot != null))

        fun encode(): String = JSONObject().apply {
            put("kind", kind.name)
            recipeId?.let { put("recipeId", it) }
            epochDay?.let { put("epochDay", it) }
            slot?.let { put("slot", it.name) }
            if (candidates.isNotEmpty()) put("candidates", JSONArray(candidates))
        }.toString()
    }

    fun decode(s: String?): Draft? = runCatching {
        val o = JSONObject(s ?: return null)
        Draft(
            kind = Kind.valueOf(o.getString("kind")),
            recipeId = if (o.has("recipeId")) o.getLong("recipeId") else null,
            epochDay = if (o.has("epochDay")) o.getLong("epochDay") else null,
            slot = o.optString("slot").takeIf { it.isNotEmpty() }?.let { MealSlot.valueOf(it) },
            candidates = o.optJSONArray("candidates")?.let { a -> (0 until a.length()).map { a.getLong(it) } }.orEmpty(),
        )
    }.getOrNull()

    sealed interface Reply {
        val text: String
        /** A complete draft over a saved recipe: show Confirm / Cancel. */
        data class Propose(val draft: Draft, override val text: String) : Reply
        /** Something is missing or ambiguous: ask, keep the partial draft for the next message. */
        data class Ask(val draft: Draft, override val text: String) : Reply
        /** Nothing to do (e.g. recipe has no saved ingredients); plain answer. */
        data class Info(override val text: String) : Reply
    }

    private val groceryWords = Regex("\\b(grocery|groceries|shopping)\\b")
    private val planWords = Regex("\\b(meal ?plan|plan|planner|schedule)\\b")
    private val addVerbs = Regex("\\b(add|put|include|pop|stick)\\b")
    private val leadingVerb = Regex("^(please |can you |could you |pls )?(plan|schedule)\\b")
    private val yes = setOf("yes", "y", "yep", "yeah", "ok", "okay", "confirm", "sure", "do it", "go ahead", "yes please", "please do")
    private val no = setOf("no", "n", "nope", "cancel", "stop", "never mind", "nevermind", "don't", "dont")

    /** Words that describe the action, day or meal, not the recipe. */
    private val actionStop = setOf(
        "add", "put", "schedule", "include", "pop", "stick", "plan", "planner", "meal", "meals", "mealplan", "grocery",
        "groceries", "shopping", "list", "ingredients", "ingredient", "items", "week", "next", "this", "it", "that", "one",
        "them", "these", "those", "into", "onto", "at", "from", "all", "its", "our", "your", "too", "also", "day", "slot",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "mon", "tue", "tues", "wed", "thu", "thur", "thurs", "fri", "sat", "sun",
        "tomorrow", "snack", "snacks", "supper", "evening", "morning", "afternoon", "be", "us", "we", "could", "would",
        "will", "need", "let", "lets", "get", "you", "now", "just", "then", "pls", "plz", "thanks", "thank",
    )

    fun isYes(q: String) = q.trim().lowercase().trimEnd('!', '.', ' ') in yes
    fun isNo(q: String) = q.trim().lowercase().trimEnd('!', '.', ' ') in no

    /** Action requested by [question], or null when it is an ordinary question for the chat. */
    fun detectKind(question: String): Kind? {
        val q = question.lowercase().trim()
        if (!addVerbs.containsMatchIn(q) && !leadingVerb.containsMatchIn(q)) return null
        if (groceryWords.containsMatchIn(q)) return Kind.GROCERY
        if (planWords.containsMatchIn(q) || parseSlot(q) != null || parseDay(q, LocalDate.now()) != null) return Kind.PLAN
        return null
    }

    fun parseSlot(question: String): MealSlot? {
        val q = question.lowercase()
        return when {
            Regex("\\bbreakfast\\b").containsMatchIn(q) -> MealSlot.BREAKFAST
            Regex("\\blunch\\b").containsMatchIn(q) -> MealSlot.LUNCH
            Regex("\\b(dinner|supper|tonight)\\b").containsMatchIn(q) -> MealSlot.DINNER
            Regex("\\bsnacks?\\b").containsMatchIn(q) -> MealSlot.SNACK
            else -> null
        }
    }

    private val dayNames = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )

    /** "today"/"tonight", "tomorrow", or a weekday (next occurrence, today included; "next X" skips today). */
    fun parseDay(question: String, today: LocalDate): LocalDate? {
        val words = question.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        if ("today" in words || "tonight" in words) return today
        if ("tomorrow" in words) return today.plusDays(1)
        words.forEachIndexed { i, w ->
            val dow = dayNames[w] ?: return@forEachIndexed
            val d = today.with(TemporalAdjusters.nextOrSame(dow))
            return if (d == today && words.getOrNull(i - 1) == "next") d.plusWeeks(1) else d
        }
        return null
    }

    sealed interface Resolution {
        data class One(val entry: LibraryAssistant.Entry) : Resolution
        data class Many(val entries: List<LibraryAssistant.Entry>) : Resolution
        data object None : Resolution
    }

    /**
     * Which saved recipe the message means. An exact saved title in the message wins; otherwise title keywords
     * pick a single best match, ties are [Resolution.Many]. Only titles in [library] can ever be returned.
     */
    fun resolveRecipe(library: List<LibraryAssistant.Entry>, question: String): Resolution {
        if (library.isEmpty()) return Resolution.None
        val q = question.lowercase()
        val exact = library.filter { it.title.isNotBlank() && it.title.lowercase() in q }
        if (exact.isNotEmpty()) {
            val longest = exact.maxOf { it.title.length }
            val best = exact.filter { it.title.length == longest }.distinctBy { it.title.lowercase() }
            return if (best.size == 1) Resolution.One(exact.first { it.title.length == longest }) else Resolution.Many(exact.filter { it.title.length == longest })
        }
        val keys = LibraryAssistant.keywords(question).filter { it !in actionStop }
        if (keys.isEmpty()) return Resolution.None
        val scored = library.map { e ->
            val titleWords = e.title.lowercase().split(Regex("[^\\p{L}0-9]+")).filter { it.isNotEmpty() }
            e to keys.count { k -> titleWords.any { w -> w == k || (k.length >= 4 && w.startsWith(k)) } }
        }.filter { it.second > 0 }.sortedByDescending { it.second }
        if (scored.isEmpty()) return Resolution.None
        val top = scored.first().second
        val best = scored.filter { it.second == top }.map { it.first }
        return if (best.size == 1) Resolution.One(best.first()) else Resolution.Many(best.take(4))
    }

    /**
     * Coco's reply to [question] when it is (or continues) a meal-plan / grocery request; null for ordinary chat.
     * [previous] is the open follow-up draft from Coco's last turn; [contextRecipeIds] are the recipes Coco just
     * pointed at, so "add it to my grocery list" works.
     */
    fun respond(
        library: List<LibraryAssistant.Entry>,
        question: String,
        today: LocalDate,
        previous: Draft? = null,
        contextRecipeIds: List<Long> = emptyList(),
    ): Reply? {
        val byId = library.associateBy { it.id }
        val kind = detectKind(question)
        val day = parseDay(question, today)
        val slot = parseSlot(question)

        if (previous != null && kind == null && isNo(question)) return Reply.Info("Okay, I won't change anything.")
        // "Should I add ghee for dinner?" names a meal but no saved recipe and no plan word: that's a cooking question.
        if (kind == Kind.PLAN && !planWords.containsMatchIn(question.lowercase()) && !leadingVerb.containsMatchIn(question.lowercase().trim()) &&
            resolveRecipe(library, question) == Resolution.None && contextRecipeIds.none { id -> library.any { it.id == id } }
        ) return null

        // Start a new draft, or keep filling the open follow-up.
        val base: Draft = when {
            kind != null && (previous == null || previous.kind != kind) -> Draft(kind)
            previous != null -> previous
            else -> return null
        }
        var recipeId = base.recipeId?.takeIf { it in byId }
        var candidates = base.candidates.filter { it in byId }
        var resolvedNow = false
        if (recipeId == null || kind != null) {
            // Prefer the recipes Coco offered last time, then the whole library.
            val pool = candidates.mapNotNull { byId[it] }
            val r = pool.takeIf { it.isNotEmpty() }?.let { resolveRecipe(it, question) }?.takeIf { it !is Resolution.None }
                ?: resolveRecipe(library, question)
            when (r) {
                is Resolution.One -> { recipeId = r.entry.id; candidates = emptyList(); resolvedNow = true }
                is Resolution.Many -> { recipeId = null; candidates = r.entries.map { it.id }; resolvedNow = true }
                Resolution.None -> if (recipeId == null) {
                    val ctx = contextRecipeIds.filter { it in byId }.distinct()
                    if (kind != null && ctx.size == 1) { recipeId = ctx.first(); resolvedNow = true }
                    else if (kind != null && ctx.size > 1) { candidates = ctx.take(4); resolvedNow = true }
                }
            }
        }
        val draft = base.copy(
            recipeId = recipeId,
            candidates = if (recipeId != null) emptyList() else candidates,
            epochDay = day?.toEpochDay() ?: base.epochDay,
            slot = slot ?: base.slot,
        )
        // A follow-up message that adds nothing to the open draft is just a normal question.
        if (kind == null && !resolvedNow && day == null && slot == null) return null

        if (library.isEmpty()) return Reply.Info("Your library is empty, so there's nothing I can add yet. Save a recipe first and I'll plan it for you.")

        val entry = draft.recipeId?.let { byId[it] }
        if (entry == null) {
            val offered = draft.candidates.mapNotNull { byId[it] }
            val where = if (draft.kind == Kind.GROCERY) "to your grocery list" else "to your meal plan"
            return Reply.Ask(
                draft,
                if (offered.size > 1) "Which one should I add $where: ${offered.joinToString(" or ") { "“${it.title}”" }}?"
                else "Which saved recipe should I add $where? Tell me its name.",
            )
        }
        if (draft.kind == Kind.GROCERY) {
            if (entry.ingredients.isEmpty()) return Reply.Info("“${entry.title}” has no saved ingredients yet, so there's nothing to add to your grocery list.")
            val n = entry.ingredients.size
            return Reply.Propose(draft, "Add the $n saved ingredient${if (n == 1) "" else "s"} of “${entry.title}” to your grocery list? Quantities stay exactly as saved.")
        }
        val d = draft.epochDay?.let(LocalDate::ofEpochDay)
        return when {
            d == null && draft.slot == null -> Reply.Ask(draft, "Which day and meal should “${entry.title}” go in?")
            d == null -> Reply.Ask(draft, "Which day should “${entry.title}” be ${draft.slot!!.label.lowercase()}?")
            draft.slot == null -> Reply.Ask(draft, "Which meal on ${dayLabel(d, today)}: breakfast, lunch, dinner or snack?")
            d.isBefore(today) -> Reply.Ask(draft.copy(epochDay = null), "That day has passed. Which day should I use?")
            else -> Reply.Propose(draft, "Add “${entry.title}” to ${dayLabel(d, today)} · ${draft.slot.label} in your meal plan?")
        }
    }

    /** Quick-reply chips for an open follow-up: offered recipe titles, then days, then meals. Library titles only. */
    fun options(draft: Draft, library: List<LibraryAssistant.Entry>, today: LocalDate): List<String> {
        val byId = library.associateBy { it.id }
        if (draft.recipeId == null || draft.recipeId !in byId) {
            val offered = draft.candidates.mapNotNull { byId[it]?.title }
            return offered.ifEmpty { (library.filter { it.favorite } + library).distinctBy { it.id }.take(4).map { it.title } }
        }
        if (draft.kind == Kind.PLAN && draft.epochDay == null) {
            return listOf("Today", "Tomorrow") + (2L..4L).map { today.plusDays(it).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) }
        }
        if (draft.kind == Kind.PLAN && draft.slot == null) return MealSlot.entries.map { it.label }
        return emptyList()
    }

    fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
    }
}

/**
 * Writes a confirmed [CocoActions.Draft] through the existing repositories. Re-reads the recipe first, so a recipe
 * deleted since the proposal is never written; servings / quantities are exactly the recipe's stored values.
 */
class CocoActionExecutor(
    private val recipes: RecipeRepository,
    private val mealPlan: MealPlanRepository,
    private val grocery: GroceryRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    suspend fun execute(draft: CocoActions.Draft): String {
        if (!draft.complete) return "I need a bit more detail before I can add that."
        val details = recipes.getDetails(draft.recipeId!!)
            ?: return "I can't find that recipe in your library any more, so I didn't change anything."
        val title = details.recipe.title
        return when (draft.kind) {
            CocoActions.Kind.GROCERY -> {
                val n = grocery.addRecipe(details.recipe.id)
                if (n == 0) "“$title” has no saved ingredients, so nothing was added."
                else "Added $n ingredient${if (n == 1) "" else "s"} from “$title” to your grocery list."
            }
            CocoActions.Kind.PLAN -> {
                val date = LocalDate.ofEpochDay(draft.epochDay!!)
                mealPlan.add(date, draft.slot!!, details.recipe.id, details.recipe.servings)
                "Added “$title” to ${CocoActions.dayLabel(date, today())} · ${draft.slot.label} (serves ${details.recipe.servings}, as saved)."
            }
        }
    }
}
