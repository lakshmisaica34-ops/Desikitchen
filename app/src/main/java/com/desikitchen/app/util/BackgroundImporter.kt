package com.desikitchen.app.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the background link import queue is doing right now (shown as a small status pill, never a blocking screen). */
sealed interface ImportJob {
    data object Idle : ImportJob
    /** [queued] links still waiting after this one; [ready] drafts already parked for review. */
    data class Running(
        val url: String,
        val stage: ImportStage = ImportStage.READING_LINK,
        val queued: Int = 0,
        val ready: Int = 0,
    ) : ImportJob
    /** A finished draft waiting for review; [more] further drafts are parked behind it. */
    data class Ready(val draft: ImportDraft.Draft, val more: Int = 0) : ImportJob
    data class Failed(val message: String, val partial: ImportDraft.Draft? = null, val more: Int = 0) : ImportJob
    data class Duplicate(val recipeId: Long, val title: String) : ImportJob
    /** End-of-batch summary for a multi-link paste (e.g. "1 already saved · 1 couldn't be read"). Dismiss to continue. */
    data class Notice(val message: String) : ImportJob
}

/**
 * Runs YouTube / Instagram imports in an app-scoped coroutine so the user keeps using the app while recipes are
 * extracted. Links are queued and processed one at a time (keeps the pill simple and doesn't hammer the backend).
 * Duplicate and quota checks run before any network call. Nothing is invented: each draft is exactly what
 * [WebRecipeFetcher] read.
 *
 * Review handoff: there is no auto-save; every finished draft (and every partial draft of a failed import) is parked
 * in a small in-memory review queue so none are lost. The pill shows the first parked draft once the queue is done
 * (or "N ready" while it runs); [takeDraftForReview] hands them out one by one.
 *
 * Quota: the weekly quota is only recorded when a draft is saved from review, so [canImport] is asked with the number
 * of drafts already parked but not yet saved; a free user's queue stops with [QUOTA_USED] once those would use it up.
 *
 * Surviving app kill: with a [persistence], every change to the queue (waiting + running link), the parked drafts
 * or the end-of-batch notice is written as a whole snapshot (in order, latest wins). On creation the last snapshot
 * is reloaded ([restore]): parked drafts come back (minus any whose link is already a saved recipe) and the links
 * that were waiting or running are queued again and re-fetched. Nothing is rebuilt from guesses.
 *
 * Editor session ([ReviewSession]): a draft handed to Review is not forgotten on hand-off. It stays persisted (with the
 * user's in-progress form edits, see [updateReviewEdits]) until the editor saves or discards it ([finishReview]). After
 * an app kill it comes back "detached": the pill shows it first (Open → the editor with the user's edits), or an
 * editor screen the system restored claims it directly ([resumeReview]). Dropped on restore if its link is already a
 * saved recipe (Save finished but the clear didn't reach disk).
 */
class BackgroundImporter(
    private val scope: CoroutineScope,
    private val findDuplicate: suspend (String) -> Pair<Long, String>?,
    /** Argument = drafts already extracted in this session but not saved yet (they will each count once on save). */
    private val canImport: (reserved: Int) -> Boolean,
    private val fetch: suspend (String, (ImportStage) -> Unit) -> WebRecipeFetcher.Result = { u, s -> WebRecipeFetcher.fetch(u, s) },
    /** Null = in-memory only (tests / previews). */
    private val persistence: ImportPersistence? = null,
) : ReviewSession {
    private class Parked(val draft: ImportDraft.Draft, val failMessage: String?)

    /** The Review editor's draft; [attached] = an editor in this process has it (false = restored, pill shows it). */
    private class Editing(val draft: ImportDraft.Draft, val failMessage: String?, var edits: String?, var attached: Boolean)

    private val _state = MutableStateFlow<ImportJob>(ImportJob.Idle)
    val state: StateFlow<ImportJob> = _state.asStateFlow()

    private val queue = ArrayDeque<String>()
    private val parked = ArrayList<Parked>()
    private var current: String? = null
    private var stage: ImportStage = ImportStage.READING_LINK
    private var notice: ImportJob? = null
    private var editing: Editing? = null
    private var worker: Job? = null
    private var generation = 0

    // Per-batch tally (a batch = everything queued until the queue drains).
    private var batchSize = 0
    private var batchDuplicates = 0
    private var batchFailures = 0
    private var lastOutcome: ImportJob? = null

    // Persistence: snapshots are numbered; one writer coroutine saves the latest (conflated) after [restore] ran.
    private var persistSeq = 0
    private val toWrite = MutableStateFlow<Pair<Int, ImportPersistence.Snapshot>?>(null)
    private val written = MutableStateFlow(0)
    private val restored = kotlinx.coroutines.CompletableDeferred<Unit>()

    init {
        if (persistence == null) restored.complete(Unit)
        else scope.launch {
            try { restore(persistence) } finally { restored.complete(Unit) }
            toWrite.filterNotNull().collect { (seq, snap) ->
                try { persistence.save(snap) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { ImportErrors.log("save import queue", e) }
                written.value = seq
            }
        }
    }

    /** Drafts parked for review (not yet handed to an editor), including an editor session restored after a kill. */
    @get:Synchronized val readyCount: Int get() = parked.size + (if (editing?.attached == false) 1 else 0)

    /**
     * Starts (or appends) one import. Returns a short error to show next to the field when it can't start, else null.
     * Order (Phase 1 locks): YT/IG gate → duplicate check → weekly quota → network. A duplicate never uses a free
     * import, so when the quota is used up the job still checks for a duplicate first and only then fails with
     * [QUOTA_USED] (shown in the status pill).
     */
    @Synchronized
    fun start(raw: String): String? {
        val check = runCatching { ImportUrlValidator.validateSupported(raw) }
            .getOrElse { ImportUrlValidator.UrlCheck.Invalid(ImportErrors.userMessage(it)) }
        if (check !is ImportUrlValidator.UrlCheck.Ok) return (check as ImportUrlValidator.UrlCheck.Invalid).message
        return if (enqueue(listOf(check.url)) == 0) ALREADY_QUEUED else null
    }

    /**
     * Queues already-validated links (see [ImportUrlValidator.parseMany]); appends while a queue is running.
     * Links that are already importing or queued are skipped. Returns how many were added.
     */
    @Synchronized
    fun startAll(urls: List<String>): Int {
        val supported = urls.filter { (ImportUrlValidator.validateSupported(it) as? ImportUrlValidator.UrlCheck.Ok)?.url == it }
        return enqueue(supported)
    }

    private fun enqueue(urls: List<String>): Int {
        val busy = (listOfNotNull(current) + queue).map(ImportUrlValidator::dedupeKey).toMutableSet()
        val fresh = urls.filter { busy.add(ImportUrlValidator.dedupeKey(it)) }
        if (fresh.isEmpty()) return 0
        if (worker == null) {
            // New batch: clear the last batch's notice so the pill shows this one.
            notice = null
            batchSize = 0; batchDuplicates = 0; batchFailures = 0; lastOutcome = null
        }
        queue.addAll(fresh)
        batchSize += fresh.size
        if (worker == null) {
            val run = ++generation
            current = queue.removeFirst(); stage = ImportStage.READING_LINK
            publish()
            worker = scope.launch { drain(run) }
        } else publish()
        persist()
        return fresh.size
    }

    private suspend fun drain(run: Int) {
        while (true) {
            val target = synchronized(this) { if (generation != run) return; current } ?: return
            val result = importOne(target, run)
            synchronized(this) {
                if (generation != run) return
                val stop = record(result)
                if (stop) queue.clear()
                val next = queue.removeFirstOrNull()
                current = next; stage = ImportStage.READING_LINK
                if (next == null) { finishBatch(); worker = null; publish(); persist(); return }
                publish()
                persist()
            }
        }
    }

    /** Applies one finished import; returns true when the queue must stop (quota used up). */
    private fun record(result: ImportJob): Boolean {
        when (result) {
            is ImportJob.Ready -> parked += Parked(result.draft, null)
            is ImportJob.Failed -> {
                if (result.partial != null) parked += Parked(result.partial, result.message)
                else batchFailures++
                if (result.message == QUOTA_USED) { notice = ImportJob.Failed(QUOTA_USED); return true }
            }
            is ImportJob.Duplicate -> batchDuplicates++
            else -> Unit
        }
        lastOutcome = result
        return false
    }

    private fun finishBatch() {
        if (notice != null) return // quota stop already set it
        val last = lastOutcome
        notice = when {
            batchSize <= 1 -> when (last) {
                is ImportJob.Duplicate -> last
                is ImportJob.Failed -> if (last.partial == null) last else null
                else -> null
            }
            batchDuplicates + batchFailures == 0 -> null
            else -> ImportJob.Notice(
                listOfNotNull(
                    batchDuplicates.takeIf { it > 0 }?.let { "$it already saved" },
                    batchFailures.takeIf { it > 0 }?.let { "$it couldn't be imported" },
                ).joinToString(" \u00b7 "),
            )
        }
    }

    private fun publish() {
        val c = current
        _state.value = when {
            c != null -> ImportJob.Running(c, stage, queued = queue.size, ready = readyCount)
            notice != null -> notice!!
            editing?.attached == false -> editing!!.let { e ->
                if (e.failMessage != null) ImportJob.Failed(e.failMessage, e.draft, more = parked.size)
                else ImportJob.Ready(e.draft, more = parked.size)
            }
            parked.isNotEmpty() -> parked.first().let { p ->
                if (p.failMessage != null) ImportJob.Failed(p.failMessage, p.draft, more = parked.size - 1)
                else ImportJob.Ready(p.draft, more = parked.size - 1)
            }
            else -> ImportJob.Idle
        }
    }

    private suspend fun importOne(target: String, run: Int): ImportJob = try {
        val dup = runCatching { findDuplicate(target) }.getOrNull()
        if (dup != null) ImportJob.Duplicate(dup.first, dup.second)
        else if (!runCatching { canImport(readyCount) }.getOrDefault(false)) ImportJob.Failed(QUOTA_USED)
        else when (val r = fetch(target) { s -> synchronized(this) { if (generation == run && current == target) { stage = s; publish() } } }) {
            is WebRecipeFetcher.Result.Success -> {
                val source = r.draft.sourceUrl ?: target
                val dup2 = runCatching { findDuplicate(source) }.getOrNull()
                if (dup2 != null) ImportJob.Duplicate(dup2.first, dup2.second)
                else ImportJob.Ready(
                    r.draft.copy(
                        sourceUrl = source,
                        warnings = (r.draft.warnings + r.warnings).distinct(),
                        partial = r.draft.partial || r.completeness == RecipeHtmlParser.Completeness.PARTIAL,
                    ),
                )
            }
            is WebRecipeFetcher.Result.Failure -> ImportJob.Failed(r.message, r.partial)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        ImportErrors.log("background import", e)
        ImportJob.Failed(ImportErrors.userMessage(e))
    }

    /** Stops the running import and clears every queued link. Drafts already parked for review are kept. */
    @Synchronized
    fun cancel() {
        generation++
        worker?.cancel(); worker = null
        queue.clear(); current = null; notice = null
        publish()
        persist()
    }

    /**
     * Clears what the pill shows when nothing is running: a notice first, else the first parked draft (the user
     * chose to dismiss it). The next parked draft, if any, shows next.
     */
    @Synchronized
    fun dismiss() {
        if (current != null) return
        if (notice != null) notice = null
        else if (editing?.attached == false) editing = null
        else if (parked.isNotEmpty()) parked.removeAt(0)
        publish()
        persist()
    }

    /**
     * Review handoff: takes the first parked draft (Ready, or the partial draft of a Failed import). Works while
     * the queue is still running, so the user can review one recipe while the next imports. The caller puts it in
     * [ImportDraft.pending] and opens the review editor, so saving still goes through review.
     * Returns null (and changes nothing) when there is no draft to review.
     *
     * The draft becomes the persisted editor session until [finishReview]. A session restored after an app kill is
     * handed out first, with the user's saved edits.
     */
    @Synchronized
    fun takeReviewHandoff(): ReviewHandoff? {
        val e = editing
        if (e != null && !e.attached) {
            e.attached = true
            publish()
            persist()
            return ReviewHandoff(e.draft, e.edits)
        }
        if (parked.isEmpty()) return null
        // One persisted editor session at a time: a newer hand-off replaces one whose editor never reported Save /
        // discard (that editor keeps its draft in memory, as before this session existed, but it is no longer persisted).
        val p = parked.removeAt(0)
        editing = Editing(p.draft, p.failMessage, edits = null, attached = true)
        publish()
        persist()
        return ReviewHandoff(p.draft)
    }

    /** [takeReviewHandoff] without the edits. */
    fun takeDraftForReview(): ImportDraft.Draft? = takeReviewHandoff()?.draft

    @Synchronized
    override fun updateReviewEdits(draft: ImportDraft.Draft, edits: String?) {
        val e = editing?.takeIf { it.draft == draft } ?: return
        if (e.edits == edits) return
        e.edits = edits
        persist()
    }

    @Synchronized
    override fun finishReview(draft: ImportDraft.Draft) {
        if (editing?.draft != draft) return
        editing = null
        publish()
        persist()
    }

    override suspend fun resumeReview(): ReviewHandoff? {
        restored.await()
        return synchronized(this) {
            val e = editing ?: return@synchronized null
            if (!e.attached) { e.attached = true; publish(); persist() }
            ReviewHandoff(e.draft, e.edits)
        }
    }

    /** Duplicate handoff: returns the already-saved recipe id and clears it, or null if the pill isn't a duplicate. */
    @Synchronized
    fun takeDuplicate(): Long? {
        val d = notice as? ImportJob.Duplicate ?: return null
        notice = null
        publish()
        persist()
        return d.recipeId
    }

    /** Queues the latest state for the persistence writer. Call while holding the lock, after a change. */
    private fun persist() {
        if (persistence == null) return
        val n = notice
        val snap = ImportPersistence.Snapshot(
            queue = listOfNotNull(current) + queue,
            parked = parked.map { ImportPersistence.ParkedDraft(it.draft, it.failMessage) },
            editor = editing?.let { ImportPersistence.EditorDraft(it.draft, it.failMessage, it.edits) },
            // Batch summaries and bare failures; a duplicate pill or the quota paywall is not worth restoring.
            notice = when (n) {
                is ImportJob.Notice -> n.message
                is ImportJob.Failed -> n.message.takeIf { n.partial == null && it != QUOTA_USED }
                else -> null
            },
        )
        toWrite.value = ++persistSeq to snap
    }

    /**
     * Reloads the last snapshot: parked drafts go back in front of anything parked since (skipping ones whose link is
     * already a saved recipe, via [findDuplicate]); links that were waiting or running are queued again (re-fetched;
     * the usual duplicate + quota checks run first). Links that already have a parked draft are not fetched again.
     */
    private suspend fun restore(store: ImportPersistence) {
        val snap = try { store.load() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) {
            ImportErrors.log("load import queue", e); return
        }
        if (snap.isEmpty) return
        suspend fun notSaved(d: ImportDraft.Draft): Boolean {
            val url = d.sourceUrl ?: return true
            return runCatching { findDuplicate(url) }.getOrNull() == null
        }
        val keep = snap.parked.filter { notSaved(it.draft) }
        val editor = snap.editor?.takeIf { notSaved(it.draft) }
        synchronized(this) {
            val have = (listOfNotNull(editing?.draft) + parked.map { it.draft })
                .mapNotNull { it.sourceUrl?.let(ImportUrlValidator::dedupeKey) }.toMutableSet()
            if (editor != null && editing == null && editor.draft.sourceUrl?.let { have.add(ImportUrlValidator.dedupeKey(it)) } != false) {
                editing = Editing(editor.draft, editor.failMessage, editor.edits, attached = false)
            }
            val back = keep.filter { p -> p.draft.sourceUrl?.let { have.add(ImportUrlValidator.dedupeKey(it)) } ?: true }
            parked.addAll(0, back.map { Parked(it.draft, it.failMessage) })
            val again = snap.queue.filter { u -> ImportUrlValidator.dedupeKey(u) !in have }
            if (again.isNotEmpty()) enqueue(again)
            else if (current == null && notice == null && snap.notice != null) notice = ImportJob.Notice(snap.notice)
            publish()
            persist()
        }
    }

    /** Test hook: waits until the reload ran and every change so far has been written. */
    internal suspend fun awaitPersisted() {
        restored.await()
        val target = synchronized(this) { persistSeq }
        written.first { it >= target }
    }

    companion object {
        const val QUOTA_USED = "No free imports left this week."
        const val ALREADY_QUEUED = "That link is already importing."
    }
}
