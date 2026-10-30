package com.desikitchen.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.desikitchen.app.data.model.CocoTurn
import com.desikitchen.app.data.model.Cookbook
import com.desikitchen.app.data.model.CookbookRecipeCrossRef
import com.desikitchen.app.data.model.CookbookWithRecipes
import com.desikitchen.app.data.model.GroceryItem
import com.desikitchen.app.data.model.Ingredient
import com.desikitchen.app.data.model.MealPlanEntry
import com.desikitchen.app.data.model.MealPlanWithRecipe
import com.desikitchen.app.data.model.MealSlot
import com.desikitchen.app.data.model.PendingImport
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.model.RecipeWithDetails
import com.desikitchen.app.data.model.Step
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY isUserCreated DESC, createdAt ASC")
    fun observeAll(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE isFavorite = 1 ORDER BY title")
    fun observeFavorites(): Flow<List<Recipe>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeDetails(id: Long): Flow<RecipeWithDetails?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getDetails(id: Long): RecipeWithDetails?

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun count(): Int

    /** Duplicate detection: an existing recipe with this platform + reel shortcode / YouTube video id. */
    @Query("SELECT * FROM recipes WHERE sourcePlatform = :platform AND sourceVideoId = :videoId ORDER BY id LIMIT 1")
    suspend fun findBySourceVideo(platform: String, videoId: String): Recipe?

    /** Every recipe that has a source link (id, title, sourceUrl), for normalised-URL duplicate matching. */
    @Query("SELECT id, title, sourceUrl FROM recipes WHERE sourceUrl IS NOT NULL AND sourceUrl != '' ORDER BY id")
    suspend fun sourceLinks(): List<SourceLink>

    /**
     * Creator page (device-only): every local recipe whose [Recipe.creatorHandle] matches [handle] after normalising
     * both sides (trim, strip leading "@", ASCII case-fold), newest first. "@Cook.Book", "cook.book" and " @cook.book "
     * all match. Pass the handle as stored or as typed; no pre-normalising needed.
     */
    @Query(
        "SELECT * FROM recipes WHERE creatorHandle IS NOT NULL AND trim(creatorHandle) != '' " +
            "AND lower(trim(ltrim(trim(creatorHandle), '@'))) = lower(trim(ltrim(trim(:handle), '@'))) " +
            "ORDER BY createdAt DESC, id DESC",
    )
    fun recipesByCreatorHandle(handle: String): Flow<List<Recipe>>

    @Insert
    suspend fun insert(recipe: Recipe): Long

    @Update
    suspend fun update(recipe: Recipe)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE recipes SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE recipes SET cookedCount = cookedCount + 1, lastCookedAt = :at WHERE id = :id")
    suspend fun markCooked(id: Long, at: Long)

    @Query("UPDATE recipes SET imageUrl = :imageUrl WHERE id = :id")
    suspend fun setImage(id: Long, imageUrl: String?)

    /** recipeId → ingredient name, for the ingredient search filter. */
    @Query("SELECT recipeId, name FROM ingredients")
    fun observeIngredientNames(): Flow<List<IngredientName>>

    @Insert
    suspend fun insertIngredients(items: List<Ingredient>)

    @Insert
    suspend fun insertSteps(items: List<Step>)

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredients(recipeId: Long)

    @Query("DELETE FROM steps WHERE recipeId = :recipeId")
    suspend fun deleteSteps(recipeId: Long)

    @Transaction
    suspend fun insertFull(recipe: Recipe, ingredients: List<Ingredient>, steps: List<Step>): Long {
        val id = insert(recipe)
        insertIngredients(ingredients.mapIndexed { i, it -> it.copy(id = 0, recipeId = id, position = i) })
        insertSteps(steps.mapIndexed { i, it -> it.copy(id = 0, recipeId = id, position = i) })
        return id
    }

    @Transaction
    suspend fun updateFull(recipe: Recipe, ingredients: List<Ingredient>, steps: List<Step>) {
        update(recipe)
        deleteIngredients(recipe.id)
        deleteSteps(recipe.id)
        insertIngredients(ingredients.mapIndexed { i, it -> it.copy(id = 0, recipeId = recipe.id, position = i) })
        insertSteps(steps.mapIndexed { i, it -> it.copy(id = 0, recipeId = recipe.id, position = i) })
    }
}

data class IngredientName(val recipeId: Long, val name: String)

data class SourceLink(val id: Long, val title: String, val sourceUrl: String)

@Dao
interface CookbookDao {
    @Transaction
    @Query("SELECT * FROM cookbooks ORDER BY createdAt ASC")
    fun observeAllWithRecipes(): Flow<List<CookbookWithRecipes>>

    @Transaction
    @Query("SELECT * FROM cookbooks WHERE id = :id")
    fun observeWithRecipes(id: Long): Flow<CookbookWithRecipes?>

    @Query("SELECT cookbookId FROM cookbook_recipe WHERE recipeId = :recipeId")
    fun observeCookbookIdsForRecipe(recipeId: Long): Flow<List<Long>>

    @Insert
    suspend fun insert(cookbook: Cookbook): Long

    @Query("UPDATE cookbooks SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM cookbooks WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addRecipe(ref: CookbookRecipeCrossRef)

    @Query("DELETE FROM cookbook_recipe WHERE cookbookId = :cookbookId AND recipeId = :recipeId")
    suspend fun removeRecipe(cookbookId: Long, recipeId: Long)
}

@Dao
interface MealPlanDao {
    @Transaction
    @Query("SELECT * FROM meal_plan WHERE epochDay BETWEEN :startDay AND :endDay ORDER BY epochDay, slot, position, id")
    fun observeRange(startDay: Long, endDay: Long): Flow<List<MealPlanWithRecipe>>

    @Transaction
    @Query("SELECT * FROM meal_plan WHERE epochDay BETWEEN :startDay AND :endDay ORDER BY epochDay, slot, position, id")
    suspend fun getRange(startDay: Long, endDay: Long): List<MealPlanWithRecipe>

    @Insert
    suspend fun insert(entry: MealPlanEntry): Long

    @Query("DELETE FROM meal_plan WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM meal_plan WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("SELECT * FROM meal_plan WHERE id = :id")
    suspend fun get(id: Long): MealPlanEntry?

    @Query("SELECT * FROM meal_plan WHERE epochDay = :epochDay AND slot = :slot ORDER BY position, id")
    suspend fun getSlot(epochDay: Long, slot: MealSlot): List<MealPlanEntry>

    @Query("UPDATE meal_plan SET epochDay = :epochDay, slot = :slot, position = :position WHERE id = :id")
    suspend fun move(id: Long, epochDay: Long, slot: MealSlot, position: Int)

    @Query("UPDATE meal_plan SET servings = :servings WHERE id = :id")
    suspend fun setServings(id: Long, servings: Int)

    @Query("DELETE FROM meal_plan WHERE epochDay BETWEEN :startDay AND :endDay")
    suspend fun clearRange(startDay: Long, endDay: Long)
}

@Dao
interface GroceryDao {
    @Query("SELECT * FROM grocery_items ORDER BY checked ASC, aisle ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<GroceryItem>>

    @Query("SELECT * FROM grocery_items")
    suspend fun getAll(): List<GroceryItem>

    @Query("SELECT * FROM grocery_items WHERE source = 'plan'")
    suspend fun getPlanItems(): List<GroceryItem>

    @Query("DELETE FROM grocery_items WHERE source = 'plan'")
    suspend fun clearPlanItems()

    @Query("UPDATE grocery_items SET haveAtHome = :have WHERE id IN (:ids)")
    suspend fun setHaveAtHome(ids: List<Long>, have: Boolean)

    @Insert
    suspend fun insertAll(items: List<GroceryItem>)

    @Insert
    suspend fun insert(item: GroceryItem): Long

    @Query("UPDATE grocery_items SET checked = :checked WHERE id IN (:ids)")
    suspend fun setChecked(ids: List<Long>, checked: Boolean)

    @Query("DELETE FROM grocery_items WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("DELETE FROM grocery_items WHERE checked = 1")
    suspend fun clearChecked()

    @Query("DELETE FROM grocery_items")
    suspend fun clearAll()

    @Query("DELETE FROM grocery_items WHERE recipeId IS NOT NULL")
    suspend fun clearGenerated()

    @Delete
    suspend fun delete(item: GroceryItem)
}

@Dao
interface CocoChatDao {
    @Query("SELECT * FROM coco_turns ORDER BY id")
    fun observeAll(): Flow<List<CocoTurn>>

    @Query("SELECT * FROM coco_turns ORDER BY id")
    suspend fun getAll(): List<CocoTurn>

    @Insert
    suspend fun insert(turn: CocoTurn): Long

    @Query("UPDATE coco_turns SET actionState = :state WHERE id = :id")
    suspend fun setActionState(id: Long, state: String?)

    @Query("DELETE FROM coco_turns")
    suspend fun clear()
}

/** Persisted link-import queue + parked review drafts ([PendingImport]); written as a whole snapshot. */
@Dao
interface PendingImportDao {
    @Query("SELECT * FROM pending_imports ORDER BY kind, position, id")
    suspend fun getAll(): List<PendingImport>

    @Query("DELETE FROM pending_imports")
    suspend fun clear()

    @Insert
    suspend fun insertAll(items: List<PendingImport>)

    @Transaction
    suspend fun replaceAll(items: List<PendingImport>) {
        clear()
        if (items.isNotEmpty()) insertAll(items)
    }
}
