package com.desikitchen.app

import android.content.Context
import com.desikitchen.app.data.db.DesiKitchenDatabase
import com.desikitchen.app.data.repo.CocoChatRepository
import com.desikitchen.app.data.repo.CookbookRepository
import com.desikitchen.app.data.repo.GroceryRepository
import com.desikitchen.app.data.repo.MealPlanRepository
import com.desikitchen.app.data.repo.RecipeRepository
import com.desikitchen.app.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Simple manual dependency container owned by [DesiKitchenApp]. */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = DesiKitchenDatabase.build(context)

    val recipes = RecipeRepository(db.recipeDao())
    val cookbooks = CookbookRepository(db.cookbookDao())
    val mealPlan = MealPlanRepository(db.mealPlanDao())
    val grocery = GroceryRepository(db.groceryDao(), db.recipeDao())
    /** Persisted Coco chat turns (Assistant tab). */
    val cocoChat = CocoChatRepository(db.cocoChatDao())
    val settings = SettingsRepository(context)

    /**
     * Link imports run here, off-screen, so the user can keep browsing while a recipe is extracted. Created with the
     * app process, so unfinished work from a killed process is restored as soon as the app starts.
     */
    val importer = com.desikitchen.app.util.BackgroundImporter(
        scope = appScope,
        findDuplicate = { recipes.findDuplicate(it) },
        // reserved = drafts already extracted but not saved yet; each counts once when saved from review.
        canImport = { reserved ->
            com.desikitchen.app.util.ImportQuota.canImport(settings.importTimes.value, System.currentTimeMillis(), isPlus = settings.isPlus.value, reserved = reserved)
        },
        // Queue + parked review drafts survive the app being killed; reloaded (and re-queued) on start.
        persistence = com.desikitchen.app.data.repo.RoomImportPersistence(db.pendingImportDao()),
    )

    init {
        appScope.launch { autoSyncGroceries() }
    }

    /** First day of the current plan week for the user's week-start setting. */
    fun currentWeekStart(day: DayOfWeek = settings.weekStart.value): LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(day))

    /**
     * Grocery auto-sync: whenever the plan for this week or next changes (or the setting / week start changes),
     * plan-sourced grocery items are reconciled. Debounced so drag-and-drop bursts write once.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private suspend fun autoSyncGroceries() {
        combine(settings.autoGrocerySync, settings.weekStart, ::Pair)
            .flatMapLatest { (enabled, day) ->
                if (!enabled) emptyFlow() else {
                    val start = currentWeekStart(day)
                    mealPlan.observeRange(start, start.plusDays(13))
                }
            }
            .debounce(400)
            .collect { entries ->
                runCatching {
                    grocery.syncFromPlan(entries, adoptLegacy = settings.needsLegacyGroceryAdoption())
                    settings.markLegacyGroceryAdopted()
                }
            }
    }

    // No demo/seed recipes or cookbooks: a fresh install starts empty.
}
