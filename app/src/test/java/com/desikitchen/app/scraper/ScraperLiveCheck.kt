package com.desikitchen.app.scraper

import com.desikitchen.app.util.WebRecipeFetcher
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in live harness (hits the network). Skipped unless SCRAPER_LIVE=1:
 *
 *   SCRAPER_LIVE=1 ./gradlew :app:testDebugUnitTest --tests '*ScraperLiveCheck*' -i
 *
 * URLs come from src/test/resources/scraper/live-urls.txt (or SCRAPER_URLS=/path/file).
 * Results are appended to build/scraper-live.jsonl, one JSON object per URL; URLs already in the
 * file are skipped, so a flaky run can simply be re-run (resumable, de-duplicated by URL).
 * Delete the file (or set SCRAPER_FRESH=1) to start over.
 */
class ScraperLiveCheck {
    @Test fun live() {
        assumeTrue("set SCRAPER_LIVE=1 to run", System.getenv("SCRAPER_LIVE") == "1")
        val list = System.getenv("SCRAPER_URLS")?.let(::File) ?: File("src/test/resources/scraper/live-urls.txt")
        val out = File("build/scraper-live.jsonl").apply { parentFile?.mkdirs() }
        if (System.getenv("SCRAPER_FRESH") == "1") out.delete()
        val done = if (out.exists()) out.readLines().mapNotNull { runCatching { JSONObject(it).getString("url") }.getOrNull() }.toSet() else emptySet()
        val urls = list.readLines().map { it.trim() }.filter { it.startsWith("http") }.distinct()
        for (url in urls) {
            if (url in done) { println("SKIP (already in results) $url"); continue }
            val started = System.currentTimeMillis()
            val r = runBlocking { WebRecipeFetcher.fetch(url) }
            val row = JSONObject().put("url", url).put("ms", System.currentTimeMillis() - started)
            when (r) {
                is WebRecipeFetcher.Result.Success -> row.put("ok", true).put("title", r.draft.parsed.title)
                    .put("ingredients", r.draft.parsed.ingredients.size).put("steps", r.draft.parsed.steps.size)
                    .put("image", r.draft.imageUrl != null).put("servings", r.draft.parsed.servings)
                    .put("prep", r.draft.prepMinutes).put("cook", r.draft.cookMinutes)
                    .put("structured", r.fromStructuredData).put("completeness", r.completeness.name)
                is WebRecipeFetcher.Result.Failure -> row.put("ok", false).put("error", r.message)
            }
            out.appendText(row.toString() + "\n")
            println("LIVE $row")
        }
    }
}
