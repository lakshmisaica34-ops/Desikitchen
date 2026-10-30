package com.desikitchen.app.scraper

import com.desikitchen.app.util.CaptionRecipeParser
import com.desikitchen.app.util.ImportErrors
import com.desikitchen.app.util.ImportParsing
import com.desikitchen.app.util.SafeRegex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.regex.PatternSyntaxException

/** 1.1.6: a pattern the phone rejects must never become ExceptionInInitializerError / NoClassDefFoundError. */
class SafeRegexTest {

    @After fun reset() = SafeRegex.clearForTest()

    @Test fun badPatternDoesNotThrowAndNeverMatches() {
        val r = SafeRegex.of("Test.bad", "(unclosed", RegexOption.IGNORE_CASE) // invalid on every engine
        assertFalse(r.containsMatchIn("(unclosed anything"))
        assertTrue(SafeRegex.failed().containsKey("Test.bad"))
        try { SafeRegex.requireOk("Test"); fail("expected Unavailable") } catch (e: SafeRegex.Unavailable) {
            val msg = ImportErrors.userMessage(e)
            assertTrue(msg, msg.contains("internal error") && msg.contains("Test.bad"))
        }
        SafeRegex.requireOk("Other") // unrelated owners are unaffected
    }

    /** Simulates the 1.1.4 phone: CaptionRecipeParser's pattern failed. The class still loads; callers get words. */
    @Test fun brokenCaptionPatternGivesReadableErrorAndPasteStillWorks() {
        SafeRegex.recordForTest("CaptionRecipeParser.stepNumber", PatternSyntaxException("U_REGEX_PROPERTY_SYNTAX", "[:.)]", 1))
        val e = try { CaptionRecipeParser.parse("Step 1: Heat oil"); null } catch (t: Throwable) { t }
        assertTrue("got $e", e is SafeRegex.Unavailable)
        assertFalse(e is LinkageError)
        val msg = ImportErrors.userMessage(e!!)
        assertTrue(msg, msg.contains("internal error") && !msg.contains("NoClassDefFoundError"))
        // Paste/Scan path falls back to the plain parser instead of failing.
        val parsed = ImportParsing.parse("Paneer bhurji\nIngredients:\n200 g paneer\n1 onion\nMethod:\nHeat oil.\nAdd paneer.")
        assertNotNull(parsed)
        assertEquals(2, parsed!!.steps.size)
    }
}
