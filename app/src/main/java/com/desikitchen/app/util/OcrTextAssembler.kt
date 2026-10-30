package com.desikitchen.app.util

/**
 * Turns OCR blocks (ML Kit Text Recognition, or any engine) into clean recipe text in reading order.
 * Pure Kotlin so it is unit-tested without a device.
 */
object OcrTextAssembler {
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)
    data class Line(val text: String, val box: Box)
    data class Block(val lines: List<Line>, val box: Box)
    data class Page(val blocks: List<Block>)

    /** Several photos (e.g. two pages of a cookbook) → one text, pages separated by a blank line. */
    fun assemble(pages: List<Page>): String = pages.map { assemble(it) }.filter { it.isNotBlank() }.joinToString("\n\n")

    fun assemble(page: Page): String {
        val blocks = page.blocks.filter { b -> b.lines.any { it.text.isNotBlank() } }
        if (blocks.isEmpty()) return ""
        val ordered = readingOrder(blocks)
        val raw = ordered.joinToString("\n\n") { b -> b.lines.sortedWith(compareBy({ it.box.top }, { it.box.left })).joinToString("\n") { it.text } }
        return clean(raw)
    }

    /** Single column: top-to-bottom. Two columns (common in cookbooks: ingredients left, method right): left column, then right. */
    internal fun readingOrder(blocks: List<Block>): List<Block> {
        val minLeft = blocks.minOf { it.box.left }
        val maxRight = blocks.maxOf { it.box.right }
        val width = (maxRight - minLeft).coerceAtLeast(1)
        val mid = minLeft + width / 2
        val left = blocks.filter { it.box.right <= mid + width * 0.05 }
        val right = blocks.filter { it.box.left >= mid - width * 0.05 }
        val full = blocks - left.toSet() - right.toSet()
        val byTop = compareBy<Block>({ it.box.top }, { it.box.left })
        if (left.size < 2 || right.size < 2) return blocks.sortedWith(byTop)
        // Full-width blocks above the columns (title, "Serves 4") come first, the rest after.
        val columnsTop = (left + right).minOf { it.box.top }
        val (above, below) = full.partition { it.box.bottom <= columnsTop + 10 }
        return above.sortedWith(byTop) + left.sortedWith(byTop) + right.sortedWith(byTop) + below.sortedWith(byTop)
    }

    private val bullet = SafeRegex.of("OcrTextAssembler.bullet", "^\\s*[•◦▪▫●○■□·»›✓✔➤►\\-–—*]+\\s*")
    private val unitWords = "tsp|tbsp|cup|cups|g|kg|ml|l|oz|lb|inch|clove|cloves|pinch"

    internal fun clean(raw: String): String {
        val lines = raw.replace("\r", "").lines().map { it.replace(Regex("[ \\t]+"), " ").trim() }
        val out = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            var line = lines[i]
            // "toma-" + "toes" → "tomatoes" (hyphenated line break inside a word)
            while (line.endsWith("-") && i + 1 < lines.size && lines[i + 1].firstOrNull()?.isLowerCase() == true &&
                line.length > 1 && line[line.length - 2].isLetter()
            ) {
                line = line.dropLast(1) + lines[i + 1]; i++
            }
            line = line.replace(bullet, "- ").let { if (it == "- ") "" else it }
            // Common OCR confusions in quantities: "l tsp" / "I cup" → "1 tsp", "O.5" → "0.5", "½" kept.
            line = line.replace(Regex("^(- )?[lI|](?= ?($unitWords)\\b)"), "$11")
            line = line.replace(Regex("(?<=^|\\s)O(?=[.,]\\d)"), "0")
            out += line
            i++
        }
        return out.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
