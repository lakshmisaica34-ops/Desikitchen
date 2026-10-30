package com.desikitchen.app.data.repo

import com.desikitchen.app.data.db.CocoChatDao
import com.desikitchen.app.data.db.CookbookDao
import com.desikitchen.app.data.db.GroceryDao
import com.desikitchen.app.data.db.MealPlanDao
import com.desikitchen.app.data.db.RecipeDao
import com.desikitchen.app.data.model.CocoTurn
import com.desikitchen.app.data.model.Cookbook
import com.desikitchen.app.data.model.CookbookRecipeCrossRef
import com.desikitchen.app.data.model.CookbookWithRecipes
import com.desikitchen.app.data.model.GroceryItem
import com.desikitchen.app.data.model.GrocerySource
import com.desikitchen.app.data.model.Ingredient
import com.desikitchen.app.data.model.MealPlanEntry
import com.desikitchen.app.data.model.MealPlanWithRecipe
import com.desikitchen.app.data.model.MealSlot
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.model.RecipeWithDetails
import com.desikitchen.app.data.model.Step
import com.desikitchen.app.util.RecipeDedup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RecipeRepository(private val dao: RecipeDao) {
    fun observeAll(): Flow<List<Recipe>> = dao.observeAll()
    fun observeFavorites(): Flow<List<Recipe>> = dao.observeFavorites()
    fun observeDetails(id: Long): Flow<RecipeWithDetails?> = dao.observeDetails(id)
    suspend fun getDetails(id: Long): RecipeWithDetails? = dao.getDetails(id)
    suspend fun count(): Int = dao.count()
    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun markCooked(id: Long, at: Long = System.currentTimeMillis()) = dao.markCooked(id, at)
    suspend fun setImage(id: Long, imageUrl: String?) = dao.setImage(id, imageUrl)

    /** Creator page: local recipes by this creator (normalised handle match, newest first). Device-only. */
    fun observeByCreator(handle: String): Flow<List<Recipe>> = dao.recipesByCreatorHandle(handle)

    /** recipeId → lower-cased ingredient names (for "has ingredient" search). */
    fun observeIngredientIndex(): Flow<Map<Long, List<String>>> = dao.observeIngredientNames()
        .map { rows -> rows.groupBy({ it.recipeId }, { it.name.lowercase() }) }

    /**
     * Phase 1 duplicate detection: the saved recipe that already came from [url] (same reel shortcode / YouTube id,
     * or the same normalised link), or null. Returns (id, title).
     */
    suspend fun findDuplicate(url: String?): Pair<Long, String>? {
        val key = RecipeDedup.key(url) ?: return null
        if (key.platform != null && key.videoId != null) {
            dao.findBySourceVideo(key.platform, key.videoId)?.let { return it.id to it.title }
        }
        return dao.sourceLinks().firstOrNull { link -> RecipeDedup.key(link.sourceUrl)?.let { RecipeDedup.same(key, it) } == true }
            ?.let { it.id to it.title }
    }

    /** Inserts when recipe.id == 0, otherwise replaces recipe and its children. Returns the id. */
    suspend fun save(recipe: Recipe, ingredients: List<Ingredient>, steps: List<Step>): Long =
        if (recipe.id == 0L) {
            dao.insertFull(recipe, ingredients, steps)
        } else {
            dao.updateFull(recipe, ingredients, steps)
            recipe.id
        }
}

class CookbookRepository(private val dao: CookbookDao) {
    fun observeAll(): Flow<List<CookbookWithRecipes>> = dao.observeAllWithRecipes()
    fun observe(id: Long): Flow<CookbookWithRecipes?> = dao.observeWithRecipes(id)
    fun observeIdsForRecipe(recipeId: Long): Flow<List<Long>> = dao.observeCookbookIdsForRecipe(recipeId)
    suspend fun create(name: String, emoji: String = ""): Long = dao.insert(Cookbook(name = name.trim(), emoji = emoji))
    suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim())
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun addRecipe(cookbookId: Long, recipeId: Long) = dao.addRecipe(CookbookRecipeCrossRef(cookbookId, recipeId))
    suspend fun removeRecipe(cookbookId: Long, recipeId: Long) = dao.removeRecipe(cookbookId, recipeId)
}

class MealPlanRepository(private val dao: MealPlanDao) {
    fun observeWeek(weekStart: LocalDate): Flow<List<MealPlanWithRecipe>> =
        dao.observeRange(weekStart.toEpochDay(), weekStart.plusDays(6).toEpochDay())

    suspend fun getWeek(weekStart: LocalDate): List<MealPlanWithRecipe> =
        dao.getRange(weekStart.toEpochDay(), weekStart.plusDays(6).toEpochDay())

    suspend fun getRange(from: LocalDate, toInclusive: LocalDate): List<MealPlanWithRecipe> =
        dao.getRange(from.toEpochDay(), toInclusive.toEpochDay())

    fun observeRange(from: LocalDate, toInclusive: LocalDate): Flow<List<MealPlanWithRecipe>> =
        dao.observeRange(from.toEpochDay(), toInclusive.toEpochDay())

    suspend fun add(date: LocalDate, slot: MealSlot, recipeId: Long, servings: Int): Long {
        val position = dao.getSlot(date.toEpochDay(), slot).size
        return dao.insert(MealPlanEntry(epochDay = date.toEpochDay(), slot = slot, recipeId = recipeId, servings = servings, position = position))
    }

    suspend fun remove(id: Long) = dao.delete(id)
    suspend fun removeAll(ids: Collection<Long>) { if (ids.isNotEmpty()) dao.deleteIds(ids.toList()) }
    suspend fun setServings(id: Long, servings: Int) = dao.setServings(id, servings.coerceAtLeast(1))
    suspend fun clearWeek(weekStart: LocalDate) =
        dao.clearRange(weekStart.toEpochDay(), weekStart.plusDays(6).toEpochDay())

    /**
     * Moves an entry to [date]/[slot] at [index] (clamped; null = end) and renumbers both the old and new slot,
     * so drag-and-drop and "Move up/down" keep a stable order.
     */
    suspend fun move(id: Long, date: LocalDate, slot: MealSlot, index: Int? = null) {
        val entry = dao.get(id) ?: return
        val target = dao.getSlot(date.toEpochDay(), slot).filter { it.id != id }.toMutableList()
        val at = (index ?: target.size).coerceIn(0, target.size)
        target.add(at, entry)
        target.forEachIndexed { i, e -> dao.move(e.id, date.toEpochDay(), slot, i) }
        if (entry.epochDay != date.toEpochDay() || entry.slot != slot) {
            dao.getSlot(entry.epochDay, entry.slot).forEachIndexed { i, e -> dao.move(e.id, e.epochDay, e.slot, i) }
        }
    }

    /** Swap with the neighbour above/below in the same slot. */
    suspend fun nudge(id: Long, delta: Int) {
        val entry = dao.get(id) ?: return
        val list = dao.getSlot(entry.epochDay, entry.slot)
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        move(id, LocalDate.ofEpochDay(entry.epochDay), entry.slot, (i + delta).coerceIn(0, list.size - 1))
    }
}

class GroceryRepository(
    private val dao: GroceryDao,
    private val recipeDao: RecipeDao,
) {
    fun observeAll(): Flow<List<GroceryItem>> = dao.observeAll()

    suspend fun addManual(name: String, quantity: Double?, unit: String, aisle: String) =
        dao.insert(GroceryItem(name = name.trim(), quantity = quantity, unit = unit.trim(), aisle = aisle, source = GrocerySource.MANUAL))

    suspend fun setHaveAtHome(ids: List<Long>, have: Boolean) = dao.setHaveAtHome(ids, have)

    /** Marks everyday pantry staples (salt, oil, haldi…) as "have at home". Returns how many rows changed. */
    suspend fun deselectPantryStaples(): Int {
        val ids = dao.getAll().filter { !it.haveAtHome && !it.checked && PantryStaples.isStaple(it.name) }.map { it.id }
        if (ids.isNotEmpty()) dao.setHaveAtHome(ids, true)
        return ids.size
    }

    /**
     * Auto-sync: makes the plan-sourced items match [entries] exactly. Manual and recipe-page items are never touched;
     * checked / have-at-home state and "added" time carry over for unchanged lines. No-op when nothing changed.
     * Returns true when the list was rewritten.
     */
    suspend fun syncFromPlan(entries: List<MealPlanWithRecipe>, adoptLegacy: Boolean = false): Boolean {
        val desired = entries.flatMap { entry ->
            val details = recipeDao.getDetails(entry.entry.recipeId) ?: return@flatMap emptyList()
            itemsFor(details, entry.entry.servings, source = GrocerySource.PLAN)
        }
        if (adoptLegacy) {
            // First sync after the 1.0.x → 1.1.0 upgrade: items 1.0.x generated from the plan were migrated as
            // "recipe" items. Drop the ones the plan is about to regenerate so they are not doubled.
            val legacy = PlanGrocerySync.legacyDuplicates(dao.getAll().filter { it.source == GrocerySource.RECIPE }, desired)
            if (legacy.isNotEmpty()) dao.deleteIds(legacy)
        }
        val existing = dao.getPlanItems()
        val plan = PlanGrocerySync.reconcile(existing, desired) ?: return false
        dao.clearPlanItems()
        dao.insertAll(plan)
        return true
    }

    suspend fun setChecked(ids: List<Long>, checked: Boolean) = dao.setChecked(ids, checked)
    suspend fun delete(ids: List<Long>) = dao.deleteIds(ids)
    suspend fun clearChecked() = dao.clearChecked()
    suspend fun clearAll() = dao.clearAll()

    /** Adds every ingredient of a recipe, scaled to [servings]. Returns number of items added. */
    /** Adds a recipe's ingredients, skipping [excludeIngredientIds] (ticked as "in pantry"). */
    suspend fun addRecipe(recipeId: Long, servings: Int? = null, excludeIngredientIds: Set<Long> = emptySet()): Int {
        val details = recipeDao.getDetails(recipeId) ?: return 0
        val items = itemsFor(details, servings ?: details.recipe.servings, excludeIngredientIds, GrocerySource.RECIPE)
        dao.insertAll(items)
        return items.size
    }

    /** One-off copy of a plan week (outside the auto-sync window) — stays until the user removes it. */
    suspend fun addPlanSnapshot(entries: List<MealPlanWithRecipe>): Int {
        val items = entries.flatMap { entry ->
            val details = recipeDao.getDetails(entry.entry.recipeId) ?: return@flatMap emptyList()
            itemsFor(details, entry.entry.servings, source = GrocerySource.RECIPE)
        }
        dao.insertAll(items)
        return items.size
    }

    /**
     * "Make grocery list": syncs the plan items to [entries] (manual and recipe-page items are kept).
     * Returns the number of plan items now on the list.
     */
    suspend fun generateFromPlan(entries: List<MealPlanWithRecipe>): Int {
        syncFromPlan(entries)
        return dao.getPlanItems().size
    }

    private fun itemsFor(
        details: RecipeWithDetails, servings: Int, exclude: Set<Long> = emptySet(), source: String = GrocerySource.RECIPE,
    ): List<GroceryItem> {
        val factor = servings.toDouble() / details.recipe.servings.coerceAtLeast(1)
        return details.sortedIngredients.filter { it.id !in exclude }.map { ing ->
            GroceryItem(
                name = ing.name,
                quantity = ing.quantity?.times(factor),
                unit = ing.unit,
                aisle = ing.aisle,
                recipeId = details.recipe.id,
                recipeTitle = details.recipe.title,
                source = source,
            )
        }
    }
}

/** Pure reconcile step of the plan → grocery auto-sync (unit-tested). */
object PlanGrocerySync {
    private data class Key(val recipeId: Long?, val name: String, val unit: String)
    private fun keyOf(i: GroceryItem) = Key(i.recipeId, i.name.trim().lowercase(), i.unit.trim().lowercase())

    /** Ids of migrated 1.0.x items that the plan sync would re-create (same recipe, name and unit). */
    fun legacyDuplicates(recipeItems: List<GroceryItem>, desired: List<GroceryItem>): List<Long> {
        val keys = desired.map(::keyOf).toSet()
        return recipeItems.filter { it.recipeId != null && keyOf(it) in keys }.map { it.id }
    }

    /** Returns the new plan-item rows, or null when [existing] already matches [desired]. */
    fun reconcile(existing: List<GroceryItem>, desired: List<GroceryItem>, now: Long = System.currentTimeMillis()): List<GroceryItem>? {
        fun totals(list: List<GroceryItem>) = list.groupBy(::keyOf).mapValues { (_, g) ->
            g.mapNotNull { it.quantity }.takeIf { it.isNotEmpty() }?.sum()?.let { Math.round(it * 1000) / 1000.0 }
        }
        if (totals(existing) == totals(desired) && existing.size == desired.groupBy(::keyOf).size) return null
        val old = existing.groupBy(::keyOf)
        return desired.groupBy(::keyOf).map { (k, g) ->
            val first = g.first()
            val prev = old[k]?.firstOrNull()
            first.copy(
                id = 0,
                quantity = g.mapNotNull { it.quantity }.takeIf { it.isNotEmpty() }?.sum(),
                source = GrocerySource.PLAN,
                checked = prev?.checked ?: false,
                haveAtHome = prev?.haveAtHome ?: false,
                createdAt = prev?.createdAt ?: now,
            )
        }
    }
}

/** Everyday Indian-kitchen staples most people already have; used by "Deselect pantry staples". */
object PantryStaples {
    val words = listOf(
        "salt", "water", "oil", "ghee", "sugar", "turmeric", "haldi", "cumin", "jeera", "mustard seed", "rai",
        "red chilli powder", "chilli powder", "coriander powder", "dhania powder", "garam masala", "hing", "asafoetida",
        "black pepper", "pepper", "curry leaves", "bay leaf", "tej patta",
    )
    fun isStaple(name: String): Boolean {
        val n = name.lowercase()
        return words.any { w -> Regex("\\b" + Regex.escape(w) + "s?\\b").containsMatchIn(n) } &&
            listOf("paneer", "chicken", "potato", "onion", "tomato").none { it in n }
    }
}

/** Persisted Coco chat (Assistant tab). Unlimited; "New chat" clears it. Stores chat text only, never keys. */
class CocoChatRepository(private val dao: CocoChatDao) {
    fun observe(): Flow<List<CocoTurn>> = dao.observeAll()
    suspend fun getAll(): List<CocoTurn> = dao.getAll()

    suspend fun append(
        question: String, answer: String, ai: Boolean, recipeIds: List<Long> = emptyList(),
        action: String? = null, actionState: String? = null,
    ): Long = dao.insert(
        CocoTurn(
            question = question, answer = answer, ai = ai, recipeIds = recipeIds.distinct().joinToString(","),
            action = action, actionState = actionState,
        ),
    )

    suspend fun setActionState(id: Long, state: String?) = dao.setActionState(id, state)
    suspend fun clear() = dao.clear()
}
