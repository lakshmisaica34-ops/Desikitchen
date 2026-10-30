package com.desikitchen.app.feature

import com.desikitchen.app.ui.importer.ImportStatusViewModel
import com.desikitchen.app.util.BackgroundImporter
import com.desikitchen.app.util.ImportDraft
import com.desikitchen.app.util.ImportJob
import com.desikitchen.app.util.ImportStage
import com.desikitchen.app.util.RecipeTextParser
import com.desikitchen.app.util.WebRecipeFetcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rebuild 1.2: link import runs in the background; the UI only watches [BackgroundImporter.state]. */
class BackgroundImporterTest {
    private val reel = "https://www.instagram.com/reel/DF6dMW5vwgw/"
    private fun draft() = ImportDraft.Draft(RecipeTextParser.ParsedRecipe("Dal", null, emptyList(), listOf("Boil")), sourceUrl = reel)
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun importer(
        dup: Pair<Long, String>? = null,
        quota: Boolean = true,
        fetch: suspend (String, (ImportStage) -> Unit) -> WebRecipeFetcher.Result,
    ) = BackgroundImporter(scope(), findDuplicate = { dup }, canImport = { quota }, fetch = fetch)

    @Test fun successEndsReady() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val imp = importer { _, stage -> stage(ImportStage.CAPTION); gate.await(); WebRecipeFetcher.Result.Success(draft(), false) }
        assertNull(imp.start(reel))
        withTimeout(5_000) { imp.state.first { it is ImportJob.Running && it.stage == ImportStage.CAPTION } }
        assertNotNull("the same link is not queued twice", imp.start(reel))
        gate.complete(Unit)
        val done = withTimeout(5_000) { imp.state.first { it is ImportJob.Ready } } as ImportJob.Ready
        assertEquals("Dal", done.draft.parsed.title)
        imp.dismiss()
        assertEquals(ImportJob.Idle, imp.state.value)
    }

    @Test fun cancelReturnsToIdleAndIgnoresLateResult() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val imp = importer { _, _ -> gate.await(); WebRecipeFetcher.Result.Success(draft(), false) }
        assertNull(imp.start(reel))
        imp.cancel()
        gate.complete(Unit)
        Thread.sleep(200)
        assertEquals(ImportJob.Idle, imp.state.value)
    }

    @Test fun duplicateAndQuotaAndBadLink() = runBlocking {
        var fetched = false
        val dup = importer(dup = 7L to "Dal") { _, _ -> fetched = true; WebRecipeFetcher.Result.Failure("x") }
        assertNull(dup.start(reel))
        val d = withTimeout(5_000) { dup.state.first { it is ImportJob.Duplicate } } as ImportJob.Duplicate
        assertEquals(7L, d.recipeId)
        assertTrue("no network call for a duplicate", !fetched)

        var noQuotaFetched = false
        val noQuota = importer(quota = false) { _, _ -> noQuotaFetched = true; WebRecipeFetcher.Result.Failure("x") }
        assertNull("starts so the duplicate check can still run", noQuota.start(reel))
        val q = withTimeout(5_000) { noQuota.state.first { it is ImportJob.Failed } } as ImportJob.Failed
        assertEquals(BackgroundImporter.QUOTA_USED, q.message)
        assertTrue("no network call when the quota is used up", !noQuotaFetched)

        val bad = importer { _, _ -> WebRecipeFetcher.Result.Failure("x") }
        assertNotNull(bad.start("paneer butter masala"))
    }

    @Test fun failureKeepsPartialDraft() = runBlocking {
        val imp = importer { _, _ -> WebRecipeFetcher.Result.Failure("No recipe in caption", partial = draft()) }
        assertNull(imp.start(reel))
        val f = withTimeout(5_000) { imp.state.first { it is ImportJob.Failed } } as ImportJob.Failed
        assertEquals("No recipe in caption", f.message)
        assertNotNull(f.partial)
    }

    @Test fun duplicateIsDetectedEvenWhenQuotaIsUsedUp() = runBlocking {
        var quotaAsked = false
        var fetched = false
        val imp = BackgroundImporter(
            scope(), findDuplicate = { 3L to "Rajma" }, canImport = { quotaAsked = true; false },
            fetch = { _, _ -> fetched = true; WebRecipeFetcher.Result.Failure("x") },
        )
        assertNull(imp.start(reel))
        val d = withTimeout(5_000) { imp.state.first { it is ImportJob.Duplicate } } as ImportJob.Duplicate
        assertEquals("Rajma", d.title)
        assertTrue("duplicate is checked before the quota", !quotaAsked)
        assertTrue(!fetched)
    }

    @Test fun unsupportedLinkNeverStartsOrTouchesQuota() {
        var quotaAsked = false
        val imp = BackgroundImporter(scope(), findDuplicate = { null }, canImport = { quotaAsked = true; true }, fetch = { _, _ -> error("no") })
        assertNotNull(imp.start("https://www.allrecipes.com/recipe/1/dal/"))
        assertNotNull(imp.start("https://www.tiktok.com/@a/video/1"))
        assertEquals(ImportJob.Idle, imp.state.value)
        assertTrue(!quotaAsked)
    }

    @Test fun readyHandOffGivesDraftOnceAndClears() = runBlocking {
        val imp = importer { _, _ -> WebRecipeFetcher.Result.Success(draft(), false) }
        assertNull("nothing to review while idle", imp.takeDraftForReview())
        assertNull(imp.start(reel))
        withTimeout(5_000) { imp.state.first { it is ImportJob.Ready } }
        assertNull("not a duplicate", imp.takeDuplicate())
        val d = imp.takeDraftForReview()
        assertEquals("Dal", d?.parsed?.title)
        assertEquals(reel, d?.sourceUrl)
        assertEquals(ImportJob.Idle, imp.state.value)
        assertNull("handed off only once", imp.takeDraftForReview())
    }

    @Test fun handOffWhileRunningDoesNothing() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val imp = importer { _, _ -> gate.await(); WebRecipeFetcher.Result.Success(draft(), false) }
        assertNull(imp.start(reel))
        assertNull(imp.takeDraftForReview())
        assertTrue(imp.state.value is ImportJob.Running)
        imp.dismiss()
        assertTrue("dismiss doesn't stop a running import", imp.state.value is ImportJob.Running)
        imp.cancel()
        gate.complete(Unit)
        assertEquals(ImportJob.Idle, imp.state.value)
    }

    @Test fun failedPartialAndDuplicateHandOffs() = runBlocking {
        val failed = importer { _, _ -> WebRecipeFetcher.Result.Failure("No recipe in caption", partial = draft()) }
        assertNull(failed.start(reel))
        withTimeout(5_000) { failed.state.first { it is ImportJob.Failed } }
        assertNotNull("partial draft opens in review", failed.takeDraftForReview())
        assertEquals(ImportJob.Idle, failed.state.value)

        val bare = importer { _, _ -> WebRecipeFetcher.Result.Failure("Blocked") }
        assertNull(bare.start(reel))
        withTimeout(5_000) { bare.state.first { it is ImportJob.Failed } }
        assertNull("no invented draft for a failure without one", bare.takeDraftForReview())
        assertTrue(bare.state.value is ImportJob.Failed)

        val dup = importer(dup = 9L to "Dal") { _, _ -> WebRecipeFetcher.Result.Failure("x") }
        assertNull(dup.start(reel))
        withTimeout(5_000) { dup.state.first { it is ImportJob.Duplicate } }
        assertNull(dup.takeDraftForReview())
        assertEquals(9L, dup.takeDuplicate())
        assertEquals(ImportJob.Idle, dup.state.value)
    }

    @Test fun statusViewModelParksDraftForReviewEditor() = runBlocking {
        val imp = importer { _, _ -> WebRecipeFetcher.Result.Success(draft(), false) }
        val vm = ImportStatusViewModel(imp, MutableStateFlow(listOf(1_000L)), now = { 2_000L })
        assertEquals(4, vm.remaining())
        ImportDraft.pending = null
        assertTrue(!vm.handOffDraft())
        assertNull(vm.start(reel))
        withTimeout(5_000) { vm.job.first { it is ImportJob.Ready } }
        assertTrue(vm.handOffDraft())
        assertEquals("Dal", ImportDraft.take()?.parsed?.title)
        assertEquals(ImportJob.Idle, vm.job.value)
    }

    @Test fun plusGateLetsImportRunWithExhaustedFreeQuota() = runBlocking {
        val now = 1_000_000_000L
        val full = List(com.desikitchen.app.util.ImportQuota.FREE_PER_WEEK) { now - it }
        fun gated(isPlus: Boolean) = importer(quota = com.desikitchen.app.util.ImportQuota.canImport(full, now, isPlus)) { _, _ ->
            WebRecipeFetcher.Result.Success(draft(), false)
        }
        val free = gated(isPlus = false)
        assertNull(free.start(reel))
        val blocked = withTimeout(5_000) { free.state.first { it is ImportJob.Failed } } as ImportJob.Failed
        assertEquals(BackgroundImporter.QUOTA_USED, blocked.message)

        val plus = gated(isPlus = true)
        assertNull(plus.start(reel))
        withTimeout(5_000) { plus.state.first { it is ImportJob.Ready } }
        Unit
    }
    // ---- Multi-link paste queue ----
    private val yt = "https://www.youtube.com/shorts/abc123"
    private val reel2 = "https://www.instagram.com/reel/C1abcDEF/"
    private fun draftFor(url: String) = ImportDraft.Draft(RecipeTextParser.ParsedRecipe("R $url", null, emptyList(), listOf("Boil")), sourceUrl = url)

    @Test fun extractAllUrlsFindsEveryLinkAndDedupes() {
        val paste = """
            Try these!
            https://www.instagram.com/reel/DF6dMW5vwgw/?igsh=abc
            https://youtu.be/xyz987 https://www.allrecipes.com/recipe/1/dal/
            https://www.instagram.com/reel/DF6dMW5vwgw/?igsh=other,https://www.youtube.com/shorts/abc123.
            youtube.com/watch?v=q1w2e3
        """.trimIndent()
        val all = com.desikitchen.app.util.ImportUrlValidator.extractAllUrls(paste)
        assertEquals(
            listOf(
                "https://www.instagram.com/reel/DF6dMW5vwgw/?igsh=abc", "https://youtu.be/xyz987",
                "https://www.allrecipes.com/recipe/1/dal/", "https://www.instagram.com/reel/DF6dMW5vwgw/?igsh=other",
                "https://www.youtube.com/shorts/abc123", "youtube.com/watch?v=q1w2e3",
            ),
            all,
        )
        val b = com.desikitchen.app.util.ImportUrlValidator.parseMany(paste)
        assertEquals(4, b.valid.size) // reel (deduped across igsh), youtu.be, short, bare youtube
        assertEquals(1, b.skipped) // allrecipes
        assertEquals(1, b.duplicatesInPaste)
        assertEquals(com.desikitchen.app.util.ImportUrlValidator.UNSUPPORTED, b.firstError)
        assertEquals("https://youtube.com/watch?v=q1w2e3", b.valid.last())

        val none = com.desikitchen.app.util.ImportUrlValidator.parseMany("paneer butter masala")
        assertTrue(none.valid.isEmpty())
        assertEquals(com.desikitchen.app.util.ImportUrlValidator.NOT_A_LINK, none.firstError)
        assertEquals(listOf(reel), com.desikitchen.app.util.ImportUrlValidator.parseMany(reel).valid)
    }

    @Test fun pasteSummaryCountsQueuedAndSkipped() {
        val batch = com.desikitchen.app.util.ImportUrlValidator.parseMany("$reel $yt $reel2 https://www.tiktok.com/@a/video/1")
        val r = ImportStatusViewModel.pasteResult(batch) { it.size }
        assertEquals(3, r.queued)
        assertEquals("3 links queued; skipped 1 unsupported", r.message)
        val single = ImportStatusViewModel.pasteResult(com.desikitchen.app.util.ImportUrlValidator.parseMany(reel)) { it.size }
        assertEquals(ImportStatusViewModel.PasteResult(1, null), single)
        val bad = ImportStatusViewModel.pasteResult(com.desikitchen.app.util.ImportUrlValidator.parseMany("https://www.tiktok.com/@a/video/1")) { it.size }
        assertEquals(0, bad.queued)
        assertEquals(com.desikitchen.app.util.ImportUrlValidator.UNSUPPORTED, bad.message)
    }

    @Test fun queueRunsSequentiallyAndParksEveryDraft() = runBlocking {
        val order = java.util.Collections.synchronizedList(mutableListOf<String>())
        var inFlight = 0
        var maxInFlight = 0
        val gate = CompletableDeferred<Unit>()
        val imp = importer { url, _ ->
            synchronized(order) { inFlight++; maxInFlight = maxOf(maxInFlight, inFlight) }
            if (url == reel) gate.await()
            order += url
            synchronized(order) { inFlight-- }
            WebRecipeFetcher.Result.Success(draftFor(url), false)
        }
        assertEquals(2, imp.startAll(listOf(reel, yt)))
        val running = withTimeout(5_000) { imp.state.first { it is ImportJob.Running } } as ImportJob.Running
        assertEquals(reel, running.url)
        assertEquals(1, running.queued)
        // Appending while running is allowed; an already-queued link is skipped.
        assertEquals(1, imp.startAll(listOf(yt, reel2)))
        assertEquals(2, (imp.state.value as ImportJob.Running).queued)
        gate.complete(Unit)
        val ready = withTimeout(5_000) { imp.state.first { it is ImportJob.Ready } } as ImportJob.Ready
        assertEquals(listOf(reel, yt, reel2), order)
        assertEquals(1, maxInFlight)
        assertEquals(2, ready.more)
        // Hand-off one by one; none dropped.
        assertEquals(reel, imp.takeDraftForReview()?.sourceUrl)
        assertEquals(yt, imp.takeDraftForReview()?.sourceUrl)
        assertEquals(0, (imp.state.value as ImportJob.Ready).more)
        assertEquals(reel2, imp.takeDraftForReview()?.sourceUrl)
        assertEquals(ImportJob.Idle, imp.state.value)
    }

    @Test fun reviewWhileQueueStillRunning() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val imp = importer { url, _ -> if (url == yt) gate.await(); WebRecipeFetcher.Result.Success(draftFor(url), false) }
        imp.startAll(listOf(reel, yt))
        val r = withTimeout(5_000) { imp.state.first { it is ImportJob.Running && it.ready == 1 } } as ImportJob.Running
        assertEquals(yt, r.url)
        assertEquals(reel, imp.takeDraftForReview()?.sourceUrl)
        assertEquals(0, (imp.state.value as ImportJob.Running).ready)
        gate.complete(Unit)
        withTimeout(5_000) { imp.state.first { it is ImportJob.Ready } }
        assertEquals(yt, imp.takeDraftForReview()?.sourceUrl)
    }

    @Test fun freeQuotaStopsQueueMidWayAndKeepsDrafts() = runBlocking {
        val fetched = java.util.Collections.synchronizedList(mutableListOf<String>())
        // 2 free imports left; parked (unsaved) drafts count against it.
        val imp = BackgroundImporter(scope(), findDuplicate = { null }, canImport = { reserved -> reserved < 2 }, fetch = { url, _ ->
            fetched += url; WebRecipeFetcher.Result.Success(draftFor(url), false)
        })
        imp.startAll(listOf(reel, yt, reel2, "https://youtu.be/zzz111"))
        val stop = withTimeout(5_000) { imp.state.first { it is ImportJob.Failed } } as ImportJob.Failed
        assertEquals(BackgroundImporter.QUOTA_USED, stop.message)
        assertEquals(listOf(reel, yt), fetched.toList())
        assertEquals(2, imp.readyCount)
        Thread.sleep(100)
        assertEquals("rest of the queue was dropped", listOf(reel, yt), fetched.toList())
        imp.dismiss() // close the paywall → the parked drafts show
        assertEquals(1, (imp.state.value as ImportJob.Ready).more)

        val plusFetched = java.util.Collections.synchronizedList(mutableListOf<String>())
        val plus = BackgroundImporter(scope(), findDuplicate = { null }, canImport = { true }, fetch = { url, _ ->
            plusFetched += url; WebRecipeFetcher.Result.Success(draftFor(url), false)
        })
        plus.startAll(listOf(reel, yt, reel2))
        val all = withTimeout(5_000) { plus.state.first { it is ImportJob.Ready } } as ImportJob.Ready
        assertEquals(2, all.more)
        assertEquals(3, plusFetched.size)
    }

    @Test fun quotaHelperCountsReservedDrafts() {
        val now = 1_000_000_000L
        val used3 = List(3) { now - it }
        assertTrue(com.desikitchen.app.util.ImportQuota.canImport(used3, now, isPlus = false, reserved = 1))
        assertTrue(!com.desikitchen.app.util.ImportQuota.canImport(used3, now, isPlus = false, reserved = 2))
        assertTrue(com.desikitchen.app.util.ImportQuota.canImport(used3, now, isPlus = true, reserved = 99))
    }

    @Test fun cancelClearsQueueButKeepsParkedDrafts() = runBlocking {
        val fetched = java.util.Collections.synchronizedList(mutableListOf<String>())
        val gate = CompletableDeferred<Unit>()
        val imp = importer { url, _ -> fetched += url; if (url == yt) gate.await(); WebRecipeFetcher.Result.Success(draftFor(url), false) }
        imp.startAll(listOf(reel, yt, reel2))
        withTimeout(5_000) { imp.state.first { it is ImportJob.Running && it.url == yt } }
        imp.cancel()
        gate.complete(Unit)
        Thread.sleep(200)
        assertEquals(listOf(reel, yt), fetched.toList())
        val r = imp.state.value as ImportJob.Ready
        assertEquals(reel, r.draft.sourceUrl)
        assertEquals(0, r.more)
        // A fresh paste after cancel starts a new queue.
        assertEquals(1, imp.startAll(listOf(reel2)))
        withTimeout(5_000) { imp.state.first { it is ImportJob.Ready && it.more == 1 } }
        Unit
    }

    @Test fun multiBatchSummarisesDuplicatesAndFailures() = runBlocking {
        val imp = BackgroundImporter(
            scope(), findDuplicate = { if (it == yt) 5L to "Poha" else null }, canImport = { true },
            fetch = { url, _ -> if (url == reel2) WebRecipeFetcher.Result.Failure("Blocked") else WebRecipeFetcher.Result.Success(draftFor(url), false) },
        )
        imp.startAll(listOf(reel, yt, reel2))
        val n = withTimeout(5_000) { imp.state.first { it is ImportJob.Notice } } as ImportJob.Notice
        assertEquals("1 already saved \u00b7 1 couldn't be imported", n.message)
        imp.dismiss()
        assertEquals(reel, (imp.state.value as ImportJob.Ready).draft.sourceUrl)
    }
}
