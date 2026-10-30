package com.desikitchen.app.data.repo

import android.content.Context
import com.desikitchen.app.BuildConfig
import com.desikitchen.app.util.ImportQuota
import com.desikitchen.app.util.UnitSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek

/** Tiny SharedPreferences-backed settings store exposed as StateFlows. */
class SettingsRepository(context: Context, debugBuild: Boolean = BuildConfig.DEBUG) {
    private val prefs = context.getSharedPreferences("desikitchen_settings", Context.MODE_PRIVATE)

    private val _unitSystem = MutableStateFlow(
        runCatching { UnitSystem.valueOf(prefs.getString(KEY_UNITS, UnitSystem.METRIC.name)!!) }
            .getOrDefault(UnitSystem.METRIC)
    )
    val unitSystem: StateFlow<UnitSystem> = _unitSystem.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_SCREEN_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _weekStart = MutableStateFlow(
        runCatching { DayOfWeek.valueOf(prefs.getString(KEY_WEEK_START, DayOfWeek.MONDAY.name)!!) }
            .getOrDefault(DayOfWeek.MONDAY)
    )
    val weekStart: StateFlow<DayOfWeek> = _weekStart.asStateFlow()

    private val _displayName = MutableStateFlow(prefs.getString(KEY_NAME, "") ?: "")
    /** Name used in the home greeting ("What's the plan, <first name>?"); empty until the user sets it. Editable in Profile. */
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _autoGrocerySync = MutableStateFlow(prefs.getBoolean(KEY_AUTO_GROCERY, true))
    /** When on, the grocery list follows meal-plan edits for this week and next (plan-sourced items only). */
    val autoGrocerySync: StateFlow<Boolean> = _autoGrocerySync.asStateFlow()

    fun setAutoGrocerySync(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_GROCERY, enabled).apply()
        _autoGrocerySync.value = enabled
    }

    /** True until the first plan → grocery sync after upgrading from 1.0.x has run. */
    fun needsLegacyGroceryAdoption(): Boolean = !prefs.getBoolean(KEY_LEGACY_ADOPTED, false)
    fun markLegacyGroceryAdopted() = prefs.edit().putBoolean(KEY_LEGACY_ADOPTED, true).apply()

    private val _importTimes = MutableStateFlow(ImportQuota.decode(prefs.getString(KEY_IMPORT_TIMES, null)))
    /** Timestamps of successful link imports (local free-tier stub: [ImportQuota.FREE_PER_WEEK] per rolling week). */
    val importTimes: StateFlow<List<Long>> = _importTimes.asStateFlow()

    fun importsRemaining(now: Long = System.currentTimeMillis()): Int = ImportQuota.remaining(_importTimes.value, now)

    private val _isPlus = MutableStateFlow(
        ImportQuota.resolvePlus(
            stored = if (prefs.contains(KEY_PLUS)) prefs.getBoolean(KEY_PLUS, false) else null,
            debugBuild = debugBuild,
        ).also { plus ->
            // Debug installs (including updates over an old install) unlock once and persist it.
            if (debugBuild && !prefs.contains(KEY_PLUS)) prefs.edit().putBoolean(KEY_PLUS, plus).apply()
        }
    )
    /** DesiKitchen Plus (unlimited link imports). Always on in debug builds; local flag only, no billing yet. */
    val isPlus: StateFlow<Boolean> = _isPlus.asStateFlow()

    /** For a future Profile toggle / billing hook. Debug builds stay Plus on next launch regardless. */
    fun setPlusUnlocked(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PLUS, enabled).apply()
        _isPlus.value = enabled
    }

    /** Records a free-tier import. Skipped while Plus so free counters stay clean if Plus is turned off later. */
    fun recordImport(now: Long = System.currentTimeMillis()) {
        if (_isPlus.value) return
        val next = ImportQuota.record(_importTimes.value, now)
        prefs.edit().putString(KEY_IMPORT_TIMES, ImportQuota.encode(next)).apply()
        _importTimes.value = next
    }

    fun setDisplayName(name: String) {
        _displayName.value = name.trim()
        prefs.edit().putString(KEY_NAME, name.trim()).apply()
    }

    fun setWeekStart(day: DayOfWeek) {
        prefs.edit().putString(KEY_WEEK_START, day.name).apply()
        _weekStart.value = day
    }

    fun setUnitSystem(system: UnitSystem) {
        prefs.edit().putString(KEY_UNITS, system.name).apply()
        _unitSystem.value = system
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCREEN_ON, enabled).apply()
        _keepScreenOn.value = enabled
    }

    private companion object {
        const val KEY_UNITS = "unit_system"
        const val KEY_SCREEN_ON = "keep_screen_on"
        const val KEY_WEEK_START = "week_start"
        const val KEY_NAME = "display_name"
        const val KEY_AUTO_GROCERY = "auto_grocery_sync"
        const val KEY_LEGACY_ADOPTED = "legacy_grocery_adopted"
        const val KEY_IMPORT_TIMES = "import_times"
        const val KEY_PLUS = "plus_unlocked"
    }
}
