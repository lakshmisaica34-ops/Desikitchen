package com.desikitchen.app.scraper

import com.desikitchen.app.util.CaptionRecipeParser
import com.desikitchen.app.util.InstagramImporter
import com.desikitchen.app.util.WebRecipeFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in (SCRAPER_LIVE=1): Sai's 2026-10-29 second bad scrape, reel DfEAPhVzLrs (ingredients and method jumbled).
 * Dumps the public caption + rule parse, then runs the full cascade and prints winner / AI call count / buckets.
 * Output goes to SCRAPER_DUMP (or stdout). No keys are printed.
 */
class RacSplitReelLiveCheck {
    private val url = "https://www.instagram.com/reel/DfEAPhVzLrs/?dlrf=Zm04d2lnNTJrYm1i"

    @Test fun cascade() = runBlocking {
        assumeTrue(System.getenv("SCRAPER_LIVE") == "1")
        val out = StringBuilder()
        val embed = runCatching { InstagramImporter.parseEmbed(WebRecipeFetcher.download(InstagramImporter.embedUrl("reel", "DfEAPhVzLrs")).html) }
        val cap = embed.getOrNull()?.caption
        out.appendLine("EMBED caption=${cap != null} err=${embed.exceptionOrNull()?.message}")
        out.appendLine("CAPTION<<\n$cap\n>>")
        cap?.let { c ->
            val r = CaptionRecipeParser.parse(c)
            out.appendLine("RULE hasRecipe=${r.hasRecipe} complete=${r.isComplete} sections=${r.hadSections} ing=${r.parsed.ingredients.size} steps=${r.parsed.steps.size}")
            r.parsed.ingredients.forEach { out.appendLine("  ri: ${it.quantity} | ${it.unit} | ${it.name}") }
            r.parsed.steps.forEach { out.appendLine("  rs: $it") }
        }
        val t0 = System.currentTimeMillis()
        val r = WebRecipeFetcher.fetch(url)
        val tr = InstagramImporter.lastTrace
        out.appendLine("SECONDS=${(System.currentTimeMillis() - t0) / 1000.0} winner=${tr?.winner} scCalls=${tr?.scPostCalls} openRouterCalls=${tr?.openRouterCalls} stages=${tr?.stageSeconds?.keys}")
        out.appendLine("notes=${tr?.notes}")
        when (r) {
            is WebRecipeFetcher.Result.Success -> {
                out.appendLine("SUCCESS ${r.completeness} partial=${r.draft.partial} title=${r.draft.parsed.title} ing=${r.draft.parsed.ingredients.size} steps=${r.draft.parsed.steps.size}")
                out.appendLine("warnings=${r.warnings}")
                r.draft.parsed.ingredients.forEach { out.appendLine("  i: ${it.quantity} | ${it.unit} | ${it.name}") }
                r.draft.parsed.steps.forEach { out.appendLine("  s: $it") }
            }
            is WebRecipeFetcher.Result.Failure -> out.appendLine("FAILURE ${r.message}")
        }
        System.getenv("SCRAPER_DUMP")?.let { java.io.File(it).writeText(out.toString()) } ?: println(out)
    }
}
