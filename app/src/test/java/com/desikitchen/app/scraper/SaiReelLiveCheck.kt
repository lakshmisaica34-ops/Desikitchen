package com.desikitchen.app.scraper

import com.desikitchen.app.util.WebRecipeFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in (SCRAPER_LIVE=1): the reel that closed the app in 1.1.0, through the real 1.1.3 cascade (1 SC /instagram/post; no SC transcript). Prints counts only. */
class SaiReelLiveCheck {
    private val url = "https://www.instagram.com/reel/Ddk4k1PoM-k/?xtok=MWtndzhiaWoxcWhzaQ=="

    @Test fun saiReel() = runBlocking {
        assumeTrue(System.getenv("SCRAPER_LIVE") == "1")
        val t0 = System.currentTimeMillis()
        val r = WebRecipeFetcher.fetch(url)
        println("LIVE seconds=${(System.currentTimeMillis() - t0) / 1000.0}")
        when (r) {
            is WebRecipeFetcher.Result.Success -> println("LIVE fetch SUCCESS ${r.completeness} warn=${r.warnings.firstOrNull()?.take(60)} title='${r.draft.parsed.title.take(60)}' ing=${r.draft.parsed.ingredients.size} steps=${r.draft.parsed.steps.size}")
            is WebRecipeFetcher.Result.Failure -> println("LIVE fetch FAILURE ${r.message.take(200)}")
        }
    }
}
