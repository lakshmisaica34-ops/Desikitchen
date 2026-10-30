package com.desikitchen.app.util

/**
 * In-memory hand-off between import flows (paste / web / share sheet) and the review editor.
 * Kept deliberately simple: one pending draft at a time.
 */
object ImportDraft {
    data class Draft(
        val parsed: RecipeTextParser.ParsedRecipe,
        val description: String = "",
        val imageUrl: String? = null,
        val sourceUrl: String? = null,
        val prepMinutes: Int = 0,
        val cookMinutes: Int = 0,
        /** Shown on the review screen, e.g. "Caption had no steps; add them from the video". */
        val warnings: List<String> = emptyList(),
        /** True when ingredients or steps are missing, so the review screen asks the user to complete it. */
        val partial: Boolean = false,
        /**
         * Creator avatar URL, only when a scraper already exposed one (HTTPS hotlink; never downloaded). No importer
         * sets this today, so it stays null and the UI shows a monogram. Never invented.
         */
        val creatorAvatarUrl: String? = null,
        /** Creator profile/channel URL as the source reported it (YouTube channel.url / oEmbed author_url), https only. */
        val creatorProfileUrl: String? = null,
    )

    @Volatile var pending: Draft? = null

    /** In-progress Review form fields (editor JSON) that go with [pending] when a saved editor session is reopened. */
    @Volatile var pendingEdits: String? = null

    fun take(): Draft? = pending.also { pending = null; pendingEdits = null }

    /** Puts a hand-off (draft + any saved edits) in place for the editor. */
    @Synchronized
    fun handOff(h: ReviewHandoff) { pending = h.draft; pendingEdits = h.edits }

    /** Takes [pending] together with its [pendingEdits]. */
    @Synchronized
    fun takeHandoff(): ReviewHandoff? {
        val d = pending ?: run { pendingEdits = null; return null }
        val e = pendingEdits
        pending = null; pendingEdits = null
        return ReviewHandoff(d, e)
    }
}

/** A draft handed to the Review editor, with the user's in-progress form fields from before an app kill (or null). */
data class ReviewHandoff(val draft: ImportDraft.Draft, val edits: String? = null)

/**
 * The Review editor's link to the persisted editor session ([BackgroundImporter] in production). The draft stays
 * persisted from hand-off until [finishReview] (Save or discard), so killing the app never loses it.
 */
interface ReviewSession {
    /** Persists the editor's current form fields (opaque JSON) with the session draft; no-op unless [draft] is it. */
    fun updateReviewEdits(draft: ImportDraft.Draft, edits: String?)

    /** [draft] was saved or discarded: forget the session (and its edits); no-op unless [draft] is the session's. */
    fun finishReview(draft: ImportDraft.Draft)

    /**
     * For an editor reopened by the system after process death (nav state restored, in-memory hand-off gone): waits for
     * the persisted state to load and claims the saved session, or null when there is none.
     */
    suspend fun resumeReview(): ReviewHandoff?
}
