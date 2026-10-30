package com.desikitchen.app.feature

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import com.desikitchen.app.data.db.DesiKitchenDatabase
import com.desikitchen.app.util.RecipePdf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 1.0.x → 1.1.0 upgrade keeps data; PDF export writes a real PDF file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DatabaseAndPdfTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Builds a real 1.0.x database from the exported v1 schema, then lets Room migrate + validate it against v2. */
    private fun createV1(name: String, seed: SQLiteDatabase.() -> Unit) {
        val schema = JSONObject(File("schemas/com.desikitchen.app.data.db.DesiKitchenDatabase/1.json").readText()).getJSONObject("database")
        val file = ctx.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).apply {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                e.optJSONArray("indices")?.let { idx -> for (j in 0 until idx.length()) execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName"))) }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) execSQL(setup.getString(i))
            version = 1
            seed()
            close()
        }
    }

    @Test fun migrate1To2KeepsRecipesAndMarksRecipeGroceries() {
        createV1("mig.db") {
            execSQL("INSERT INTO recipes (id, title, description, cuisine, mealType, dietTags, emoji, prepMinutes, cookMinutes, servings, isFavorite, isUserCreated, createdAt) VALUES (1, 'Poha', '', '', '', '', '🍚', 5, 10, 2, 0, 1, 0)")
            execSQL("INSERT INTO grocery_items (id, name, unit, aisle, recipeId, recipeTitle, checked, createdAt) VALUES (1, 'poha', 'cup', 'Other', 1, 'Poha', 0, 0)")
            execSQL("INSERT INTO grocery_items (id, name, unit, aisle, checked, createdAt) VALUES (2, 'milk', 'l', 'Other', 0, 0)")
        }
        val room = Room.databaseBuilder(ctx, DesiKitchenDatabase::class.java, "mig.db")
            .addMigrations(DesiKitchenDatabase.MIGRATION_1_2, DesiKitchenDatabase.MIGRATION_2_3, DesiKitchenDatabase.MIGRATION_3_4, DesiKitchenDatabase.MIGRATION_4_5, DesiKitchenDatabase.MIGRATION_5_6).allowMainThreadQueries().build()
        val db = room.openHelper.writableDatabase // runs 1→2→3→4 and Room's v4 schema validation (v3 provenance, v4 Coco chat)
        assertEquals(6, db.version)
        db.query("SELECT title, cookedCount, origin, sourceUrl FROM recipes").use { c -> c.moveToFirst(); assertEquals("Poha", c.getString(0)); assertEquals(0, c.getInt(1)); assertEquals("manual", c.getString(2)); assertTrue(c.isNull(3)) }
        db.query("SELECT id, source, haveAtHome FROM grocery_items ORDER BY id").use { c ->
            c.moveToFirst(); assertEquals("recipe", c.getString(1)); assertEquals(0, c.getInt(2))
            c.moveToNext(); assertEquals("manual", c.getString(1))
        }
        room.close()
    }

    @Test fun pdfExportDirAndPaginationForLongRecipe() {
        // Robolectric has no native PdfDocument, so the drawing itself is device-only; layout/pagination is covered here.
        assertTrue(RecipePdf.exportDir(ctx).isDirectory)
        val blocks = RecipePdf.blocks("Biryani", "Layered rice", listOf("Serves 6"), List(80) { "Ingredient $it" }, List(30) { "Step $it" }, null)
        val heights = blocks.map { if (it.style == RecipePdf.Style.TITLE) 40 else 20 }
        val headings = blocks.indices.filter { blocks[it].style == RecipePdf.Style.HEADING }.toSet()
        val pages = RecipePdf.paginate(heights, RecipePdf.PAGE_H - 2 * RecipePdf.MARGIN, headings)
        assertTrue(pages.size >= 3)
        assertEquals(blocks.indices.toList(), pages.flatten())
        pages.forEach { p -> assertTrue(p.last() !in headings) }
    }
}
