package com.desikitchen.app.feature

import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.ui.detail.sourceCredit
import com.desikitchen.app.ui.home.HomeUiState
import com.desikitchen.app.ui.navigation.sharedImportUrl
import com.desikitchen.app.util.ImportImageStore
import com.desikitchen.app.util.ImportQuota
import com.desikitchen.app.util.ImportUrlValidator
import com.desikitchen.app.util.ImportUrlValidator.UrlCheck
import com.desikitchen.app.util.LibraryAssistant
import com.desikitchen.app.util.SharedPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1 locks (2026-10-29): quota, YT/IG-only gate, greeting, Coco library assistant, provenance credit. */
class Phase1LogicTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    @Test fun quotaAllowsFivePerRollingWeek() {
        var t = emptyList<Long>()
        repeat(5) { assertTrue(ImportQuota.canImport(t, now)); t = ImportQuota.record(t, now - (4 - it)) }
        assertEquals(0, ImportQuota.remaining(t, now))
        assertFalse(ImportQuota.canImport(t, now))
        assertTrue("frees up after 7 days", ImportQuota.canImport(t, now + 7 * day))
        assertEquals(t, ImportQuota.decode(ImportQuota.encode(t)))
        assertEquals(emptyList<Long>(), ImportQuota.decode(null))
        assertEquals(5, ImportQuota.remaining(listOf(now - 8 * day), now))
    }

    @Test fun onlyYoutubeAndInstagramAreSupported() {
        listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://youtu.be/dQw4w9WgXcQ", "https://m.youtube.com/shorts/abc123",
            "https://www.instagram.com/reel/C1abcDEF/", "check this https://www.instagram.com/p/C1abcDEF/ yum",
        ).forEach { assertTrue(it, ImportUrlValidator.validateSupported(it) is UrlCheck.Ok) }
        listOf("https://www.hebbarskitchen.com/paneer-butter-masala/", "https://www.tiktok.com/@x/video/1", "https://pin.it/abc", "https://fb.watch/xyz")
            .forEach { assertEquals(it, UrlCheck.Invalid(ImportUrlValidator.UNSUPPORTED), ImportUrlValidator.validateSupported(it)) }
        assertEquals(UrlCheck.Invalid(ImportUrlValidator.NOT_AN_IG_POST), ImportUrlValidator.validateSupported("https://www.instagram.com/hebbarskitchen/"))
        assertEquals(UrlCheck.Invalid(ImportUrlValidator.EMPTY), ImportUrlValidator.validateSupported("  "))
        assertFalse(ImportUrlValidator.isSupportedHost("https://notyoutube.com/watch"))
        // Generic validator is untouched (parser tests still use websites).
        assertTrue(ImportUrlValidator.validate("https://www.hebbarskitchen.com/x/") is UrlCheck.Ok)
    }

    @Test fun shareSheetRoutesOnlySupportedLinks() {
        assertEquals("https://www.instagram.com/reel/C1abcDEF/", sharedImportUrl(SharedPayload.Text("https://www.instagram.com/reel/C1abcDEF/")))
        assertNull(sharedImportUrl(SharedPayload.Text("https://www.allrecipes.com/recipe/1/")))
        assertNull(sharedImportUrl(SharedPayload.Text("2 cups rice, 1 onion")))
        assertNull(sharedImportUrl(SharedPayload.Images(listOf("content://x"))))
    }

    @Test fun greetingUsesFirstNameOrChef() {
        assertEquals("chef", HomeUiState.firstName(""))
        assertEquals("chef", HomeUiState.firstName("   "))
        assertEquals("Priya", HomeUiState.firstName("  Priya Sharma "))
        assertEquals("chef", HomeUiState().greetingName)
    }

    @Test fun igThumbnailsAreNotPersistedByDefault() {
        assertFalse(ImportImageStore.persistEnabled)
    }

    @Test fun provenanceCredit() {
        assertEquals("By @hebbars · Instagram", sourceCredit(Recipe(title = "x", sourcePlatform = "instagram", creatorHandle = "@hebbars")))
        assertEquals("Saved from YouTube", sourceCredit(Recipe(title = "x", sourcePlatform = "youtube")))
        assertNull(sourceCredit(Recipe(title = "x")))
    }

    private val lib = listOf(
        LibraryAssistant.Entry(1, "Paneer Butter Masala", listOf("paneer", "butter", "tomato", "cream")),
        LibraryAssistant.Entry(2, "Jeera Rice", listOf("basmati rice", "cumin seeds", "ghee")),
        LibraryAssistant.Entry(3, "Aloo Gobi", listOf("potato", "cauliflower", "turmeric")),
    )

    @Test fun cocoMatchesOnlyTheSavedLibrary() {
        assertEquals(listOf(1L), LibraryAssistant.match(lib, "What can I make with paneer?").map { it.id })
        assertEquals(listOf(2L), LibraryAssistant.match(lib, "something with rice").map { it.id })
        assertTrue(LibraryAssistant.match(lib, "what's for dinner").isEmpty())
        val offline = LibraryAssistant(null).ask(lib, "anything with cauliflower?", emptyList())
        assertFalse(offline.ai)
        assertEquals(listOf(3L), offline.recipeIds)
        assertTrue(offline.text.contains("Aloo Gobi"))
        // Never invents: offline text only mentions saved ingredients.
        assertFalse(offline.text.contains("peas"))
        assertTrue(LibraryAssistant.offline(emptyList(), "hi").contains("library is empty"))
    }

    @Test fun cocoPromptCarriesLibraryAndNoInventionRule() {
        val p = LibraryAssistant.systemPrompt(lib)
        assertTrue(p.contains("Never invent ingredients"))
        assertTrue(p.contains("Jeera Rice: basmati rice, cumin seeds, ghee"))
        val msgs = LibraryAssistant.messages(lib, "q2", listOf(LibraryAssistant.Turn("q1", "a1", true)))
        assertEquals(listOf("system", "user", "assistant", "user"), msgs.map { it.role })
    }
}
