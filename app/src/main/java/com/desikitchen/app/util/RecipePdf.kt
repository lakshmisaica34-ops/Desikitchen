package com.desikitchen.app.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

/**
 * Export / print a recipe as an A4 PDF (android.graphics.pdf.PdfDocument, no third-party library).
 * [blocks] and [paginate] are pure so layout decisions are unit-tested; [write] draws with StaticLayout.
 */
object RecipePdf {
    enum class Style(val sizePt: Float, val bold: Boolean, val spaceBefore: Int, val indent: Int = 0) {
        TITLE(22f, true, 0), META(10.5f, false, 6), HEADING(14f, true, 16), BODY(11f, false, 4),
        BULLET(11f, false, 3, 14), STEP(11f, false, 6, 22), FOOTER(9f, false, 18),
    }
    data class Block(val style: Style, val text: String, val marker: String? = null)

    const val PAGE_W = 595 // A4 at 72 dpi
    const val PAGE_H = 842
    const val MARGIN = 48

    fun blocks(
        title: String, description: String, meta: List<String>,
        ingredients: List<String>, steps: List<String>, source: String?,
    ): List<Block> = buildList {
        add(Block(Style.TITLE, title))
        if (description.isNotBlank()) add(Block(Style.BODY, description.trim()))
        meta.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { add(Block(Style.META, it.joinToString("  ·  "))) }
        add(Block(Style.HEADING, "Ingredients"))
        if (ingredients.isEmpty()) add(Block(Style.BODY, "No ingredients listed."))
        ingredients.forEach { add(Block(Style.BULLET, it, "•")) }
        add(Block(Style.HEADING, "Directions"))
        if (steps.isEmpty()) add(Block(Style.BODY, "No directions listed."))
        steps.forEachIndexed { i, s -> add(Block(Style.STEP, s, "${i + 1}.")) }
        add(Block(Style.FOOTER, listOfNotNull(source?.takeIf { it.isNotBlank() }?.let { "Source: $it" }, "Exported from DesiKitchen").joinToString("  ·  ")))
    }

    /**
     * Splits blocks (given their heights incl. spacing) into pages of [available] height. A heading is never left
     * alone at the bottom of a page (it moves with the next block). A block taller than a page gets its own page.
     */
    fun paginate(heights: List<Int>, available: Int, keepWithNext: Set<Int> = emptySet()): List<List<Int>> {
        val pages = mutableListOf<MutableList<Int>>(mutableListOf())
        var used = 0
        var i = 0
        while (i < heights.size) {
            val group = if (i in keepWithNext && i + 1 < heights.size) listOf(i, i + 1) else listOf(i)
            val h = group.sumOf { heights[it] }
            if (used + h > available && pages.last().isNotEmpty()) { pages += mutableListOf<Int>(); used = 0 }
            pages.last() += group; used += h
            i += group.size
        }
        return pages.filter { it.isNotEmpty() }
    }

    fun fileName(title: String) = title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(Regex("\\s+"), "-").ifBlank { "recipe" }.take(60) + ".pdf"

    /** Draws [blocks] (and an optional local photo on page 1) to [out]. */
    fun write(blocks: List<Block>, out: File, photo: Uri? = null, context: Context? = null): File {
        val width = PAGE_W - 2 * MARGIN
        fun paint(s: Style) = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = s.sizePt
            typeface = if (s.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            color = when (s) { Style.META, Style.FOOTER -> Color.rgb(110, 110, 110); Style.HEADING -> Color.rgb(196, 122, 0); else -> Color.rgb(25, 25, 25) }
        }
        fun layout(b: Block): StaticLayout {
            val w = width - b.style.indent
            return StaticLayout.Builder.obtain(b.text, 0, b.text.length, paint(b.style), w)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()
        }
        val layouts = blocks.map(::layout)
        val bitmap = runCatching {
            if (photo == null || context == null) null
            else context.contentResolver.openInputStream(photo)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        val photoH = bitmap?.let { minOf(220, (width.toFloat() * it.height / it.width).toInt()) } ?: 0
        val heights = layouts.mapIndexed { i, l -> l.height + blocks[i].style.spaceBefore }
        val keep = blocks.indices.filter { blocks[it].style == Style.HEADING || blocks[it].style == Style.TITLE }.toSet()
        // First page loses room to the photo: model it as a tall first "block".
        val all = listOf(if (photoH > 0) photoH + 12 else 0) + heights
        val pages = paginate(all, PAGE_H - 2 * MARGIN, keep.map { it + 1 }.toSet() + 0)
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { pi, idx ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pi + 1).create())
                val c = page.canvas
                var y = MARGIN.toFloat()
                idx.forEach { k ->
                    if (k == 0) {
                        if (bitmap != null && photoH > 0) {
                            val dstW = minOf(width, (photoH.toFloat() * bitmap.width / bitmap.height).toInt())
                            c.drawBitmap(bitmap, null, Rect(MARGIN, y.toInt(), MARGIN + dstW, y.toInt() + photoH), null)
                            y += photoH + 12
                        }
                        return@forEach
                    }
                    val b = blocks[k - 1]; val l = layouts[k - 1]
                    y += b.style.spaceBefore
                    b.marker?.let { m -> c.drawText(m, MARGIN.toFloat(), y - paint(b.style).ascent(), paint(b.style).apply { isFakeBoldText = b.style == Style.STEP }) }
                    c.save(); c.translate((MARGIN + b.style.indent).toFloat(), y); l.draw(c); c.restore()
                    y += l.height
                }
                val fp = paint(Style.FOOTER)
                c.drawText("${pi + 1} / ${pages.size}", (PAGE_W - MARGIN - fp.measureText("${pi + 1} / ${pages.size}")), (PAGE_H - MARGIN / 2).toFloat(), fp)
                doc.finishPage(page)
            }
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { doc.writeTo(it) }
        } finally { doc.close() }
        return out
    }

    fun exportDir(context: Context) = File(context.cacheDir, "exports").apply { mkdirs() }

    /** Opens the system print dialog for an already-written PDF (Save as PDF is offered there too). */
    fun print(context: Context, pdf: File, jobName: String) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
                if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
                cb.onLayoutFinished(PrintDocumentInfo.Builder(pdf.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
            }
            override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
                runCatching {
                    pdf.inputStream().use { input -> FileOutputStream(dest.fileDescriptor).use { input.copyTo(it) } }
                }.onSuccess { cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }.onFailure { cb.onWriteFailed(it.message) }
            }
        }, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }
}
