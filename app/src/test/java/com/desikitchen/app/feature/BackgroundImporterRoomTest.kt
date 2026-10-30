package com.desikitchen.app.feature

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.desikitchen.app.data.db.DesiKitchenDatabase
import com.desikitchen.app.data.repo.RoomImportPersistence
import com.desikitchen.app.util.BackgroundImporter
import com.desikitchen.app.util.ImportDraft
import com.desikitchen.app.util.ImportJob
import com.desikitchen.app.util.RecipeTextParser
import com.desikitchen.app.util.WebRecipeFetcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The Room table behind [RoomImportPersistence]: survives closing / reopening the database (= app kill). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundImporterRoomTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val reel = "https://www.instagram.com/reel/DF6dMW5vwgw/"
    private val yt = "https://www.youtube.com/shorts/abc123"

    private fun build(name: String) = Room.databaseBuilder(ctx, DesiKitchenDatabase::class.java, name)
        .addMigrations(DesiKitchenDatabase.MIGRATION_1_2, DesiKitchenDatabase.MIGRATION_2_3, DesiKitchenDatabase.MIGRATION_3_4, DesiKitchenDatabase.MIGRATION_4_5, DesiKitchenDatabase.MIGRATION_5_6)
        .build()

    private fun draft(url: String) = ImportDraft.Draft(
        RecipeTextParser.ParsedRecipe("Rajma", 3, listOf(RecipeTextParser.ParsedIngredient(1.0, "cup", "rajma", "Pantry")), listOf("Soak", "Cook")),
        description = "By @auntyji", imageUrl = "https://cdn.example/r.jpg", sourceUrl = url, warnings = listOf("w"), partial = false,
    )

    @Test fun parkedDraftAndQueueSurviveDatabaseReopen() = runBlocking {
        ctx.getDatabasePath("imports-kill.db").delete()
        val db1 = build("imports-kill.db")
        val killed = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val never = CompletableDeferred<Unit>()
        val a = BackgroundImporter(killed, findDuplicate = { null }, canImport = { true }, persistence = RoomImportPersistence(db1.pendingImportDao()),
            fetch = { url, _ -> if (url == yt) never.await(); WebRecipeFetcher.Result.Success(draft(url), false) })
        a.startAll(listOf(reel, yt))
        withTimeout(5_000) { a.state.first { it is ImportJob.Running && it.url == yt && it.ready == 1 } }
        a.awaitPersisted()
        killed.cancel()
        db1.close()

        val db2 = build("imports-kill.db")
        val fetched = mutableListOf<String>()
        val b = BackgroundImporter(CoroutineScope(SupervisorJob() + Dispatchers.IO), findDuplicate = { null }, canImport = { true },
            persistence = RoomImportPersistence(db2.pendingImportDao()),
            fetch = { url, _ -> synchronized(fetched) { fetched += url }; WebRecipeFetcher.Result.Success(draft(url), false) })
        val ready = withTimeout(5_000) { b.state.first { it is ImportJob.Ready && it.more == 1 } } as ImportJob.Ready
        assertEquals(draft(reel), ready.draft)
        assertEquals("only the link that was running is fetched again", listOf(yt), synchronized(fetched) { fetched.toList() })
        b.takeDraftForReview()?.let(b::finishReview); b.takeDraftForReview()?.let(b::finishReview)
        b.awaitPersisted()
        assertTrue(db2.pendingImportDao().getAll().isEmpty())
        db2.close()
    }

    @Test fun migrate4To5KeepsRecipesAndAddsPendingImports() {
        val schema = JSONObject(File("schemas/com.desikitchen.app.data.db.DesiKitchenDatabase/4.json").readText()).getJSONObject("database")
        val file = ctx.getDatabasePath("mig45.db").apply { parentFile?.mkdirs(); delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).apply {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                e.optJSONArray("indices")?.let { idx -> for (j in 0 until idx.length()) execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName"))) }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) execSQL(setup.getString(i))
            execSQL("INSERT INTO recipes (id, title, description, cuisine, mealType, dietTags, emoji, prepMinutes, cookMinutes, servings, isFavorite, isUserCreated, createdAt, cookedCount, origin, updatedAt) VALUES (7, 'Poha', '', '', '', '', '', 5, 10, 2, 0, 1, 0, 0, 'manual', 0)")
            version = 4
            close()
        }
        val room = build("mig45.db")
        val db = room.openHelper.writableDatabase // runs 4→5→6 and Room's v6 schema validation
        assertEquals(6, db.version)
        db.query("SELECT title FROM recipes WHERE id = 7").use { c -> assertTrue(c.moveToFirst()); assertEquals("Poha", c.getString(0)) }
        runBlocking { assertTrue(room.pendingImportDao().getAll().isEmpty()) }
        room.close()
    }
}
