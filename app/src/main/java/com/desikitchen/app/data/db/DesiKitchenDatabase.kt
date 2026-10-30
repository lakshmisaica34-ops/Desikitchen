package com.desikitchen.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.desikitchen.app.data.model.CocoTurn
import com.desikitchen.app.data.model.Cookbook
import com.desikitchen.app.data.model.CookbookRecipeCrossRef
import com.desikitchen.app.data.model.GroceryItem
import com.desikitchen.app.data.model.Ingredient
import com.desikitchen.app.data.model.MealPlanEntry
import com.desikitchen.app.data.model.PendingImport
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.model.Step

@Database(
    entities = [
        Recipe::class,
        Ingredient::class,
        Step::class,
        Cookbook::class,
        CookbookRecipeCrossRef::class,
        MealPlanEntry::class,
        GroceryItem::class,
        CocoTurn::class,
        PendingImport::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DesiKitchenDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun cookbookDao(): CookbookDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun groceryDao(): GroceryDao
    abstract fun cocoChatDao(): CocoChatDao
    abstract fun pendingImportDao(): PendingImportDao

    companion object {
        fun build(context: Context): DesiKitchenDatabase =
            Room.databaseBuilder(context, DesiKitchenDatabase::class.java, "desikitchen.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                // Only for unknown future/downgrade paths; 1 → 2 → 3 → 4 → 5 → 6 keep every recipe.
                .fallbackToDestructiveMigration()
                .build()

        /** 1.0.x → 1.1.0: cooked stats, meal-plan ordering, grocery source + have-at-home. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN cookedCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recipes ADD COLUMN lastCookedAt INTEGER")
                db.execSQL("ALTER TABLE meal_plan ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE grocery_items ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'")
                db.execSQL("ALTER TABLE grocery_items ADD COLUMN haveAtHome INTEGER NOT NULL DEFAULT 0")
                // v1 could not tell plan items from recipe items; keep them all as recipe items so a plan sync never deletes them.
                db.execSQL("UPDATE grocery_items SET source = 'recipe' WHERE recipeId IS NOT NULL")
            }
        }

        /** Phase 1: recipe provenance (source link, platform, creator, extraction status, servings). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN sourceUrl TEXT")
                db.execSQL("ALTER TABLE recipes ADD COLUMN sourcePlatform TEXT")
                db.execSQL("ALTER TABLE recipes ADD COLUMN sourceVideoId TEXT")
                db.execSQL("ALTER TABLE recipes ADD COLUMN creatorHandle TEXT")
                db.execSQL("ALTER TABLE recipes ADD COLUMN origin TEXT NOT NULL DEFAULT 'manual'")
                db.execSQL("ALTER TABLE recipes ADD COLUMN extractionStatus TEXT")
                db.execSQL("ALTER TABLE recipes ADD COLUMN sourceServings INTEGER")
                db.execSQL("ALTER TABLE recipes ADD COLUMN userServings INTEGER")
                db.execSQL("ALTER TABLE recipes ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE recipes SET updatedAt = createdAt")
                // 1.x stored the import link as the whole description ("Imported from <url>"); move it into sourceUrl.
                db.execSQL(
                    "UPDATE recipes SET sourceUrl = trim(substr(description, 15)), origin = 'import', description = '' " +
                        "WHERE description LIKE 'Imported from http%' AND instr(description, char(10)) = 0",
                )
                db.execSQL(
                    "UPDATE recipes SET sourcePlatform = CASE " +
                        "WHEN sourceUrl LIKE '%instagram.com%' THEN 'instagram' " +
                        "WHEN sourceUrl LIKE '%youtube.com%' OR sourceUrl LIKE '%youtu.be%' THEN 'youtube' " +
                        "ELSE 'web' END WHERE sourceUrl IS NOT NULL",
                )
            }
        }

        /** Phase 1 Coco: persisted chat history (new table only; nothing existing is touched). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(COCO_TURNS_SQL)
            }
        }

        /** Imports survive app kill: persisted link queue + parked review drafts (new table only). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(PENDING_IMPORTS_SQL)
            }
        }

        /** Creator page: nullable HTTPS avatar hotlink + source-reported profile URL on recipes (existing rows stay null). */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN creatorAvatarUrl TEXT")
                db.execSQL("ALTER TABLE recipes ADD COLUMN creatorProfileUrl TEXT")
            }
        }

        internal const val PENDING_IMPORTS_SQL =
            "CREATE TABLE IF NOT EXISTS `pending_imports` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`kind` TEXT NOT NULL, `position` INTEGER NOT NULL, `url` TEXT NOT NULL, `draftJson` TEXT, " +
                "`failMessage` TEXT, `createdAt` INTEGER NOT NULL)"

        internal const val COCO_TURNS_SQL =
            "CREATE TABLE IF NOT EXISTS `coco_turns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`question` TEXT NOT NULL, `answer` TEXT NOT NULL, `ai` INTEGER NOT NULL, `recipeIds` TEXT NOT NULL, " +
                "`action` TEXT, `actionState` TEXT, `createdAt` INTEGER NOT NULL)"
    }
}
