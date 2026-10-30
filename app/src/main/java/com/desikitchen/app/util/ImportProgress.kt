package com.desikitchen.app.util

/**
 * Real cascade stages reported by [InstagramImporter] / [SocialRecipeImporter] / [WebRecipeFetcher] while an
 * import runs. Only stages the cascade actually enters are reported; nothing is timed or faked.
 */
enum class ImportStage(val label: String) {
    READING_LINK("Reading link"),
    CAPTION("Reading the caption"),
    POST_DETAILS("Fetching post details"),
    LINKED_PAGE("Opening the linked recipe page"),
    COMMENTS("Reading top comments"),
    VIDEO_FRAMES("Reading video frames"),
    VIDEO("Reading the video"),
    PHOTO("Reading the photo"),
    FINISHING("Almost done"),
}

/**
 * Thread-local listener so the blocking cascade (which runs entirely on one IO thread inside
 * [WebRecipeFetcher.fetch]) can report stages without changing the importer lambdas used by tests.
 * A listener may throw CancellationException to stop the cascade at the next stage boundary.
 */
object ImportProgress {
    private val listener = ThreadLocal<((ImportStage) -> Unit)?>()

    fun <T> withListener(l: ((ImportStage) -> Unit)?, block: () -> T): T {
        val prev = listener.get()
        listener.set(l)
        try { return block() } finally { listener.set(prev) }
    }

    fun report(stage: ImportStage) { listener.get()?.invoke(stage) }
}
