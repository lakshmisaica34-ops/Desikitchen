package com.desikitchen.app.ui.importer

import androidx.lifecycle.ViewModel
import com.desikitchen.app.util.BackgroundImporter
import com.desikitchen.app.util.ImportDraft
import com.desikitchen.app.util.ImportJob
import com.desikitchen.app.util.ImportQuota
import com.desikitchen.app.util.ImportUrlValidator
import kotlinx.coroutines.flow.StateFlow

/**
 * UI-facing handle on the app-wide [BackgroundImporter] (from [com.desikitchen.app.AppContainer.importer]).
 * Paste-link (Discover / import screen) and the share sheet start imports here; the status pill watches [job].
 * The job itself lives in the app scope, so leaving a screen never cancels it; only [cancel] does.
 */
class ImportStatusViewModel(
    private val importer: BackgroundImporter,
    private val importTimes: StateFlow<List<Long>>,
    private val now: () -> Long = System::currentTimeMillis,
    /** DesiKitchen Plus (unlimited imports); defaults to free tier. */
    val isPlus: StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false),
) : ViewModel() {
    val job: StateFlow<ImportJob> = importer.state
    val quotaTimes: StateFlow<List<Long>> = importTimes

    fun remaining(): Int = ImportQuota.remaining(importTimes.value, now())

    /** Free imports left, or null when Plus (unlimited). */
    fun remainingOrUnlimited(): Int? = ImportQuota.remainingOrUnlimited(importTimes.value, now(), isPlus.value)

    /** Starts a background import. Returns an inline error when it can't start, else null (the pill takes over). */
    fun start(link: String): String? = importer.start(link)

    /** Outcome of a multi-link paste: [queued] links added; [message] is an error (queued == 0) or a short summary. */
    data class PasteResult(val queued: Int, val message: String?)

    /**
     * Paste with one or more YouTube / Instagram links: every supported link is queued (appending to a running
     * queue), unsupported ones are skipped and counted. A single link behaves exactly like [start].
     */
    fun startMany(text: String): PasteResult = pasteResult(ImportUrlValidator.parseMany(text)) { importer.startAll(it) }

    companion object {
        internal fun pasteResult(batch: ImportUrlValidator.Batch, enqueue: (List<String>) -> Int): PasteResult {
            if (batch.valid.isEmpty()) return PasteResult(0, batch.firstError ?: ImportUrlValidator.NOT_A_LINK)
            val added = enqueue(batch.valid)
            val already = batch.valid.size - added
            if (added == 0) return PasteResult(0, if (batch.valid.size == 1) BackgroundImporter.ALREADY_QUEUED else "Those links are already importing.")
            val notes = listOfNotNull(
                batch.skipped.takeIf { it > 0 }?.let { "skipped $it unsupported" },
                already.takeIf { it > 0 }?.let { "$it already importing" },
            )
            val head = if (added == 1) "1 link queued" else "$added links queued"
            return PasteResult(added, if (notes.isEmpty() && added == 1) null else (listOf(head) + notes).joinToString("; "))
        }
    }

    fun cancel() = importer.cancel()
    fun dismiss() = importer.dismiss()

    /**
     * Ready / partial-draft handoff to the existing review editor: parks the draft in [ImportDraft.pending] for
     * `edit?draft=true`. Returns false when there was nothing to review. Saving (and the quota record) still happens
     * only from the review screen. The draft (and the user's edits) stay persisted as the editor session until the
     * editor saves or discards it; a session restored after an app kill comes back here with its edits.
     */
    fun handOffDraft(): Boolean {
        val handoff = importer.takeReviewHandoff() ?: return false
        ImportDraft.handOff(handoff)
        return true
    }

    /** Duplicate handoff: the saved recipe's id to open, or null. */
    fun takeDuplicate(): Long? = importer.takeDuplicate()
}
