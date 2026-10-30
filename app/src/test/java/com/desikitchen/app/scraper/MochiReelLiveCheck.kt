package com.desikitchen.app.scraper

import com.desikitchen.app.util.CaptionRecipeParser
import com.desikitchen.app.util.CreatorsApiClient
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in (SCRAPER_LIVE=1, 1 Scrape Creators credit): dumps the 2026-10-29 rice-paper reel caption + rule parse to SCRAPER_DUMP. */
class MochiReelLiveCheck {
    private val url = "https://www.instagram.com/reel/Dc07ThlI0EU/"

    @Test fun dumpCaption() {
        assumeTrue(System.getenv("SCRAPER_LIVE") == "1")
        val c = CreatorsApiClient.fromBuildConfig() ?: return
        val m = c.fetchMedia(url)
        val cap = m.caption.orEmpty()
        val p = CaptionRecipeParser.parse(cap).parsed
        val out = buildString {
            appendLine("AUTHOR=${m.author}"); appendLine("CAPTION<<"); appendLine(cap); appendLine(">>")
            appendLine("COMMENTS<<"); m.topComments.forEach { appendLine("- $it") }; appendLine(">>")
            appendLine("ING=${p.ingredients.size}"); p.ingredients.forEach { appendLine("  i: $it") }
            appendLine("STEPS=${p.steps.size}"); p.steps.forEach { appendLine("  s: $it") }
            appendLine("VIDEO=${m.videoUrl != null}")
            if (System.getenv("SCRAPER_AI") == "1") {
                val t = com.desikitchen.app.util.TextRecipeExtractor.cheapFromBuildConfig()
                val input = "Caption:\n$cap\n\nTop comments:\n" + m.topComments.joinToString("\n") { "- $it" }
                appendLine("TEXT_AI=" + runCatching { t?.extract("Instagram", input, com.desikitchen.app.util.TextRecipeExtractor.COMMENTS_SYSTEM) }.fold({ it.toString() }, { "ERR ${it.message}" }))
                val v = com.desikitchen.app.util.VideoRecipeExtractor.fromBuildConfig()
                appendLine("VIDEO_AI=" + runCatching { m.videoUrl?.let { v?.extractRemoteOnly(it, cap) } }.fold({ x ->
                    x?.let { "title=${it.title}\n" + it.ingredients.joinToString("\n") { "  I| $it" } + "\n" + it.steps.joinToString("\n") { "  S| $it" } }.toString() }, { "ERR ${it.message}" }))
            }
        }
        System.getenv("SCRAPER_DUMP")?.let { java.io.File(it).writeText(out) } ?: println(out)
    }

    /** Full 1.1.3 cascade on Sai's 2026-10-29 repro URL; prints the buckets (SCRAPER_LIVE=1 SCRAPER_FULL=1). */
    @Test fun fullCascade() = kotlinx.coroutines.runBlocking {
        assumeTrue(System.getenv("SCRAPER_LIVE") == "1" && System.getenv("SCRAPER_FULL") == "1")
        val r = com.desikitchen.app.util.WebRecipeFetcher.fetch("https://www.instagram.com/reel/Dc07ThlI0EU/?mdxt=MTh4b2FsY2x5Y3Rmdw==")
        val out = when (r) {
            is com.desikitchen.app.util.WebRecipeFetcher.Result.Success -> buildString {
                appendLine("SUCCESS ${r.completeness} winner=${com.desikitchen.app.util.InstagramImporter.lastTrace?.winner} partial=${r.draft.partial}")
                appendLine("notes=${com.desikitchen.app.util.InstagramImporter.lastTrace?.notes}")
                appendLine("warnings=${r.warnings}")
                appendLine("title=${r.draft.parsed.title}")
                r.draft.parsed.ingredients.forEach { appendLine("  i: ${it.quantity} | ${it.unit} | ${it.name}") }
                r.draft.parsed.steps.forEach { appendLine("  s: $it") }
            }
            is com.desikitchen.app.util.WebRecipeFetcher.Result.Failure -> "FAILURE ${r.message}\nnotes=${com.desikitchen.app.util.InstagramImporter.lastTrace?.notes}"
        }
        System.getenv("SCRAPER_DUMP")?.let { java.io.File(it).writeText(out) } ?: println(out)
    }
}
