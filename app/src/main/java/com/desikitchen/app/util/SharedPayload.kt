package com.desikitchen.app.util

/**
 * What arrived from the Android share sheet / "Open with".
 * Text (links, captions, .txt/.md files) goes to web or paste import; images go to the scanner (OCR).
 */
sealed interface SharedPayload {
    data class Text(val text: String) : SharedPayload
    data class Images(val uris: List<String>, val caption: String? = null) : SharedPayload

    companion object {
        /**
         * Combines the pieces of a share intent. Nothing is dropped: subject, EXTRA_TEXT and every text file are
         * joined (deduplicated) so the paste screen receives the full content. Markdown files are flattened.
         */
        fun from(
            subject: String?,
            text: String?,
            files: List<Pair<String /*name or mime*/, String /*content*/>> = emptyList(),
            imageUris: List<String> = emptyList(),
        ): SharedPayload? {
            val fileTexts = files.map { (name, body) -> if (MarkdownText.looksLikeMarkdown(name, body)) MarkdownText.strip(body) else body }
            val parts = (listOfNotNull(subject, text) + fileTexts)
                .map { it.replace("\r\n", "\n").trim() }.filter { it.isNotEmpty() }
            // Drop a part fully contained in another (subject repeated inside the text, etc.).
            val joined = parts.filterIndexed { i, p -> parts.withIndex().none { (j, o) -> j != i && o.length > p.length && o.contains(p) } }
                .distinct().joinToString("\n\n")
            if (imageUris.isNotEmpty()) return Images(imageUris.take(MAX_IMAGES), joined.ifBlank { null })
            return joined.takeIf { it.isNotBlank() }?.let { Text(it.take(MAX_TEXT)) }
        }

        const val MAX_IMAGES = 5
        const val MAX_TEXT = 100_000
    }
}

/** Minimal Markdown → plain recipe text (headings, emphasis, list/checkbox markers, links, code fences). */
object MarkdownText {
    fun looksLikeMarkdown(nameOrMime: String, body: String): Boolean {
        val n = nameOrMime.lowercase()
        if (n.endsWith(".md") || n.endsWith(".markdown") || n.contains("markdown")) return true
        return Regex("(?m)^#{1,6} \\S").containsMatchIn(body) && Regex("(?m)^\\s*([-*+]|\\d+\\.) ").containsMatchIn(body)
    }

    fun strip(md: String): String = md.replace("\r\n", "\n").lines()
        .filterNot { it.trim().startsWith("```") || it.trim().matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) }
        .joinToString("\n") { line ->
            var l = line
            l = l.replace(Regex("^\\s{0,3}#{1,6}\\s+"), "")              // headings
            l = l.replace(Regex("^\\s*>\\s?"), "")                       // block quotes
            l = l.replace(Regex("^(\\s*)[-*+]\\s+\\[[ xX]]\\s+"), "$1- ") // task-list checkboxes
            l = l.replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")           // images → alt text
            l = l.replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"), "$1")          // links → text
            l = l.replace(Regex("(\\*\\*|__)(.+?)\\1"), "$2")              // bold
            l = l.replace(Regex("(?<![\\w*])[*_](\\S(?:.*?\\S)?)[*_](?![\\w*])"), "$1") // italics
            l = l.replace(Regex("`([^`]+)`"), "$1")
            l.trimEnd()
        }
        .replace(Regex("\n{3,}"), "\n\n").trim()
}
