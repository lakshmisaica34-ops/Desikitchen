package com.desikitchen.app.util

/**
 * Free-tier import allowance (Sai lock #2): 5 link imports per rolling 7 days. Local stub only — no billing,
 * no backend. Pure so it is unit-testable; [com.desikitchen.app.data.repo.SettingsRepository] stores the timestamps.
 */
object ImportQuota {
    const val FREE_PER_WEEK = 5
    const val WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000

    /** Import timestamps still inside the rolling window ending at [now]. */
    fun recent(timestamps: List<Long>, now: Long): List<Long> = timestamps.filter { it in (now - WINDOW_MILLIS + 1)..now }

    fun used(timestamps: List<Long>, now: Long): Int = recent(timestamps, now).size

    fun remaining(timestamps: List<Long>, now: Long, limit: Int = FREE_PER_WEEK): Int = (limit - used(timestamps, now)).coerceAtLeast(0)

    fun canImport(timestamps: List<Long>, now: Long, limit: Int = FREE_PER_WEEK): Boolean = remaining(timestamps, now, limit) > 0

    /** When the oldest counted import drops out of the window (null if not at the limit). */
    fun nextFreeAt(timestamps: List<Long>, now: Long, limit: Int = FREE_PER_WEEK): Long? {
        val r = recent(timestamps, now).sorted()
        if (r.size < limit) return null
        return r[r.size - limit] + WINDOW_MILLIS
    }

    fun record(timestamps: List<Long>, now: Long): List<Long> = recent(timestamps, now) + now

    /**
     * DesiKitchen Plus entitlement on launch: debug builds are always Plus (and persist it); otherwise whatever was
     * stored, defaulting to free. [stored] is null when the pref key has never been written.
     */
    fun resolvePlus(stored: Boolean?, debugBuild: Boolean): Boolean = debugBuild || stored == true

    /** Plus = unlimited; otherwise the free rolling-week allowance. */
    fun canImport(timestamps: List<Long>, now: Long, isPlus: Boolean): Boolean = isPlus || canImport(timestamps, now)

    /** Multi-link queue: also counts [reserved] drafts already extracted but not saved yet (each counts once on save). */
    fun canImport(timestamps: List<Long>, now: Long, isPlus: Boolean, reserved: Int): Boolean =
        isPlus || remaining(timestamps, now) > reserved.coerceAtLeast(0)

    /** Free imports left this week, or null meaning unlimited (Plus). */
    fun remainingOrUnlimited(timestamps: List<Long>, now: Long, isPlus: Boolean): Int? =
        if (isPlus) null else remaining(timestamps, now)

    /** Short status line for import surfaces. */
    fun statusLine(timestamps: List<Long>, now: Long, isPlus: Boolean): String =
        if (isPlus) PLUS_LINE else "${remaining(timestamps, now)} of $FREE_PER_WEEK free imports left this week"

    const val PLUS_LINE = "Plus · unlimited imports"

    fun encode(timestamps: List<Long>): String = timestamps.joinToString(",")
    fun decode(raw: String?): List<Long> = raw.orEmpty().split(",").mapNotNull { it.trim().toLongOrNull() }
}
