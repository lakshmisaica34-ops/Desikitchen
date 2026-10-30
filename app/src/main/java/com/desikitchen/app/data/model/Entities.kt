package com.desikitchen.app.data.model

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val cuisine: String = "",
    val mealType: String = "",
    /** Comma separated diet tags, e.g. "Vegetarian,Gluten-Free". */
    val dietTags: String = "",
    val prepMinutes: Int = 0,
    val cookMinutes: Int = 0,
    val servings: Int = 4,
    /** Legacy (DB v1) column; no longer rendered anywhere in the UI. */
    val emoji: String = "",
    val imageUrl: String? = null,
    val isFavorite: Boolean = false,
    val isUserCreated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Times "I cooked this" was tapped (DB v2). */
    @ColumnInfo(defaultValue = "0") val cookedCount: Int = 0,
    /** Epoch millis of the last "I cooked this" (DB v2). */
    val lastCookedAt: Long? = null,
    // --- Provenance (DB v3) ---
    /** Original page / reel / video the recipe was imported from. */
    val sourceUrl: String? = null,
    /** [SourcePlatform] value: "instagram", "youtube", "web". Null for manual recipes. */
    val sourcePlatform: String? = null,
    /** Reel shortcode / YouTube video id, when known. */
    val sourceVideoId: String? = null,
    /** "@handle" or channel name of the creator, only when the source states it. */
    val creatorHandle: String? = null,
    /** [RecipeOrigin] value: "manual", "import", "scan", "paste". */
    @ColumnInfo(defaultValue = "'manual'") val origin: String = RecipeOrigin.MANUAL,
    /** [ExtractionStatus] value for imported recipes; null for manual ones. */
    val extractionStatus: String? = null,
    /** Servings stated by the source (null = not stated; never guessed). */
    val sourceServings: Int? = null,
    /** Servings the user chose to cook for, if they changed it. */
    val userServings: Int? = null,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    /**
     * DB v6: creator avatar as an HTTPS hotlink, only when the importer already exposed one. Never downloaded or
     * persisted as bytes (Meta-safe); null → UI shows a monogram. See [com.desikitchen.app.util.CreatorProfile].
     */
    val creatorAvatarUrl: String? = null,
    /**
     * DB v6: creator profile / channel URL exactly as the source reported it (e.g. YouTube `channel.url`), https only.
     * Null → [com.desikitchen.app.util.CreatorProfile.profileUrl] derives IG from the handle; YouTube stays null.
     */
    val creatorProfileUrl: String? = null,
) {
    val totalMinutes: Int get() = prepMinutes + cookMinutes
    val dietTagList: List<String>
        get() = dietTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(
    tableName = "ingredients",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recipeId")],
)
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long = 0,
    val position: Int = 0,
    val name: String,
    /** Null means unquantified — never invent "to taste". */
    val quantity: Double? = null,
    val unit: String = "",
    val aisle: String = Aisles.OTHER,
    val note: String = "",
)

@Entity(
    tableName = "steps",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recipeId")],
)
data class Step(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long = 0,
    val position: Int = 0,
    val text: String,
    /** Optional timer for cooking mode. */
    val timerMinutes: Int? = null,
)

@Entity(tableName = "cookbooks")
data class Cookbook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Legacy column; not rendered. */
    val emoji: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "cookbook_recipe",
    primaryKeys = ["cookbookId", "recipeId"],
    foreignKeys = [
        ForeignKey(entity = Cookbook::class, parentColumns = ["id"], childColumns = ["cookbookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Recipe::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("recipeId")],
)
data class CookbookRecipeCrossRef(
    val cookbookId: Long,
    val recipeId: Long,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "meal_plan",
    foreignKeys = [ForeignKey(
        entity = Recipe::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recipeId"), Index("epochDay")],
)
data class MealPlanEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** LocalDate.toEpochDay() */
    val epochDay: Long,
    val slot: MealSlot,
    val recipeId: Long,
    val servings: Int,
    /** Order inside a day + slot; drag-and-drop / move rewrites it (DB v2). */
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)

enum class MealSlot(val label: String, val emoji: String) {
    BREAKFAST("Breakfast", "🌅"),
    LUNCH("Lunch", "☀️"),
    DINNER("Dinner", "🌙"),
    SNACK("Snack", "🍵"),
}

@Entity(tableName = "grocery_items")
data class GroceryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Double? = null,
    val unit: String = "",
    val aisle: String = Aisles.OTHER,
    val recipeId: Long? = null,
    val recipeTitle: String? = null,
    val checked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** "manual", "recipe" or "plan" — plan items are re-synced when the meal plan changes (DB v2). */
    @ColumnInfo(defaultValue = "'manual'") val source: String = GrocerySource.MANUAL,
    /** Deselected as "have at home" / pantry: kept on the list but not counted or shared (DB v2). */
    @ColumnInfo(defaultValue = "0") val haveAtHome: Boolean = false,
)

/**
 * One Coco chat turn (DB v4): the user's question and Coco's answer, persisted so the Assistant tab restores the
 * conversation. Holds no keys or secrets — only chat text, the saved-recipe ids Coco pointed at, and (for
 * meal-plan / grocery proposals) the encoded [com.desikitchen.app.util.CocoActions.Draft] plus its state.
 */
@Entity(tableName = "coco_turns")
data class CocoTurn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val question: String,
    val answer: String,
    val ai: Boolean = false,
    /** Comma separated saved-recipe ids shown as chips under the answer. */
    val recipeIds: String = "",
    /** Encoded meal-plan / grocery draft, or null for a plain answer. */
    val action: String? = null,
    /** [CocoActionState] value, or null for a plain answer. */
    val actionState: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val recipeIdList: List<Long> get() = recipeIds.split(',').mapNotNull { it.trim().toLongOrNull() }
}

object CocoActionState {
    /** Coco proposed a write and is waiting for Confirm / Cancel. */
    const val PROPOSED = "proposed"
    /** Coco asked a short follow-up (which recipe / day / meal); the next message fills the draft in. */
    const val CLARIFY = "clarify"
    const val DONE = "done"
    const val CANCELLED = "cancelled"
}

object RecipeOrigin {
    const val MANUAL = "manual"
    const val IMPORT = "import"
    const val PASTE = "paste"
    const val SCAN = "scan"
}

object SourcePlatform {
    const val INSTAGRAM = "instagram"
    const val YOUTUBE = "youtube"
    const val WEB = "web"

    fun fromUrl(url: String?): String? {
        val u = url?.lowercase() ?: return null
        return when {
            "instagram.com" in u || "instagr.am" in u -> INSTAGRAM
            "youtube.com" in u || "youtu.be" in u -> YOUTUBE
            u.startsWith("http") -> WEB
            else -> null
        }
    }

    /** Reel/post shortcode or YouTube video id from a URL, when it has one. */
    fun videoId(url: String?): String? {
        val u = url ?: return null
        Regex("instagram\\.com/(?:[^/]+/)?(?:reel|reels|p|tv)/([A-Za-z0-9_-]+)").find(u)?.let { return it.groupValues[1] }
        Regex("(?:youtu\\.be/|[?&]v=|/shorts/|/embed/)([A-Za-z0-9_-]{11})").find(u)?.let { return it.groupValues[1] }
        return null
    }

    fun label(platform: String?): String? = when (platform) {
        INSTAGRAM -> "Instagram"
        YOUTUBE -> "YouTube"
        WEB -> "the web"
        else -> null
    }
}

object ExtractionStatus {
    const val COMPLETE = "complete"
    const val PARTIAL = "partial"
    const val FAILED = "failed"
}

object GrocerySource {
    const val MANUAL = "manual"
    const val RECIPE = "recipe"
    const val PLAN = "plan"
}

object Aisles {
    const val PRODUCE = "Fresh Produce"
    const val SPICES = "Spices & Masalas"
    const val GRAINS = "Rice, Atta & Grains"
    const val LENTILS = "Dals & Pulses"
    const val DAIRY = "Dairy & Paneer"
    const val MEAT = "Meat & Eggs"
    const val PANTRY = "Oils, Ghee & Pantry"
    const val SWEETS = "Sugar & Baking"
    const val OTHER = "Other"

    val all = listOf(PRODUCE, SPICES, GRAINS, LENTILS, DAIRY, MEAT, PANTRY, SWEETS, OTHER)
}

object Tags {
    val cuisines = listOf("North Indian", "South Indian", "Punjabi", "Mughlai", "Hyderabadi", "Gujarati", "Bengali", "Indo-Chinese", "Street Food")
    val mealTypes = listOf("Breakfast", "Lunch", "Dinner", "Snack", "Dessert")
    val diets = listOf("Vegetarian", "Vegan", "Non-Veg", "Gluten-Free", "Jain", "High-Protein")
}

data class RecipeWithDetails(
    @Embedded val recipe: Recipe,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<Ingredient>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val steps: List<Step>,
) {
    val sortedIngredients get() = ingredients.sortedBy { it.position }
    val sortedSteps get() = steps.sortedBy { it.position }
}

data class CookbookWithRecipes(
    @Embedded val cookbook: Cookbook,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = CookbookRecipeCrossRef::class,
            parentColumn = "cookbookId",
            entityColumn = "recipeId",
        ),
    )
    val recipes: List<Recipe>,
)

data class MealPlanWithRecipe(
    @Embedded val entry: MealPlanEntry,
    @Relation(parentColumn = "recipeId", entityColumn = "id")
    val recipe: Recipe?,
)

/**
 * Link-import work that must survive the app being killed (see [com.desikitchen.app.util.BackgroundImporter]):
 * [kind] = [PendingImport.QUEUE] (a link waiting or running; re-fetched on reopen), [PendingImport.PARKED] (a finished
 * or partial draft waiting for review, [draftJson] = encoded ImportDraft.Draft, [failMessage] for a partial draft of a
 * failed import), [PendingImport.NOTICE] (end-of-batch summary text in [failMessage]) or the Review editor session
 * ([PendingImport.EDITOR] draft + optional [PendingImport.EDITOR_EDITS] form JSON). New kinds reuse the v6 columns, so
 * no schema change. Rows are rewritten as a whole snapshot; [position] keeps the order.
 */
@Entity(tableName = "pending_imports")
data class PendingImport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val position: Int,
    val url: String = "",
    val draftJson: String? = null,
    val failMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val QUEUE = "queue"
        const val PARKED = "parked"
        const val NOTICE = "notice"
        /** The draft open in the Review editor (handed off, not yet saved / discarded); at most one. */
        const val EDITOR = "editor"
        /** The Review editor's in-progress form fields ([draftJson] = editor form JSON); only with an [EDITOR] row. */
        const val EDITOR_EDITS = "editor_edits"
    }
}
