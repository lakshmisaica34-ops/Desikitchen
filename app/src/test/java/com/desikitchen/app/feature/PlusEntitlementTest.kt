package com.desikitchen.app.feature

import com.desikitchen.app.util.ImportQuota
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DesiKitchen Plus: unlimited imports; debug builds default to Plus, release stays free unless unlocked. */
class PlusEntitlementTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day
    private val exhausted = List(ImportQuota.FREE_PER_WEEK) { now - it }

    @Test fun debugBuildsDefaultToPlus() {
        assertTrue(ImportQuota.resolvePlus(stored = null, debugBuild = true))
        assertTrue(ImportQuota.resolvePlus(stored = false, debugBuild = true))
    }

    @Test fun releaseDefaultsToFreeUnlessUnlocked() {
        assertFalse(ImportQuota.resolvePlus(stored = null, debugBuild = false))
        assertFalse(ImportQuota.resolvePlus(stored = false, debugBuild = false))
        assertTrue(ImportQuota.resolvePlus(stored = true, debugBuild = false))
    }

    @Test fun plusIgnoresExhaustedQuota() {
        assertFalse(ImportQuota.canImport(exhausted, now, isPlus = false))
        assertTrue(ImportQuota.canImport(exhausted, now, isPlus = true))
        assertNull(ImportQuota.remainingOrUnlimited(exhausted, now, isPlus = true))
        assertEquals(0, ImportQuota.remainingOrUnlimited(exhausted, now, isPlus = false))
    }

    @Test fun statusLineShowsUnlimitedForPlus() {
        assertEquals(ImportQuota.PLUS_LINE, ImportQuota.statusLine(exhausted, now, isPlus = true))
        assertEquals("0 of 5 free imports left this week", ImportQuota.statusLine(exhausted, now, isPlus = false))
    }
}
