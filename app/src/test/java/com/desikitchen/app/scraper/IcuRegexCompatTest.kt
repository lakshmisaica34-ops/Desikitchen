package com.desikitchen.app.scraper

import com.desikitchen.app.util.CaptionRecipeParser
import com.desikitchen.app.util.ImportErrors
import com.desikitchen.app.util.WebRecipeFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * 1.1.4 device bug: "Import failed (ExceptionInInitializerError)".
 *
 * Android's java.util.regex is backed by ICU, not the JDK engine. ICU reads a character class that starts with
 * `[:` (or `[^:`) as a POSIX `[:name:]` property, and when a `:]` follows later it throws
 * PatternSyntaxException (U_REGEX_PROPERTY_SYNTAX). CaptionRecipeParser.stepNumber had
 * `[:.)\-]?|\d{1,2}\s*[.)\-:]`, which compiles fine on the JDK (so every JVM test passed) but throws on ART inside
 * the object's static initialiser, so every Instagram/social import (and Paste/Scan via ImportParsing) failed.
 *
 * The house rule is stricter than ICU (which only fails when a `:]` follows): never open a class with `:`, write
 * `[\\:…]`. The JVM can't run ICU's parser, so this test lints every Regex in the app for constructs ICU parses differently
 * from the JDK. For an exact ICU check against the system libicu, run `python3 tools/icu_regex_check.py`.
 */
class IcuRegexCompatTest {

    /** Returns why ICU (Android) would reject or misread [p], or null if it's safe. */
    private fun icuProblem(p: String): String? {
        if (p.contains("(?U)") ) return "(?U) / UNICODE_CHARACTER_CLASS isn't supported on Android"
        if (Regex("""\\p\{(java|Is[A-Z][a-z]+[A-Z])""").containsMatchIn(p)) return "\\p{java…} / JDK-only property names"
        var i = 0
        var depth = 0
        while (i < p.length) {
            val c = p[i]
            when {
                c == '\\' && i + 1 < p.length && p[i + 1] == 'Q' -> {
                    val end = p.indexOf("\\E", i + 2); i = if (end < 0) p.length else end + 2; continue
                }
                c == '\\' -> { i += 2; continue }
                c == '[' -> {
                    var j = i + 1
                    if (j < p.length && p[j] == '^') j++
                    if (j < p.length && p[j] == ':') return "character class opens with '[:' at index $i (ICU reads it as a POSIX [:prop:]); escape it as '\\:' or move it later"
                    depth++
                }
                c == ']' && depth > 0 -> depth--
            }
            i++
        }
        return null
    }

    @Test fun lintCatchesThe114Pattern() {
        val old114 = "^\\s*(step\\s*\\d+\\s*[:.)\\-]?|\\d{1,2}\\s*[.)\\-:]|\\d{1,2}️⃣)\\s*"
        assertTrue("lint must flag the 1.1.4 stepNumber pattern", icuProblem(old114) != null)
        assertEquals(null, icuProblem("[.\\:)\\-]?|[.)\\-\\:]|[^\\s\"'<>]+|[\\p{L}0-9-]"))
    }

    // ---- 1. every Regex literal in the app source ----

    private fun sourceRoot(): File =
        listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }

    private fun unescapeKotlin(s: String): String {
        val sb = StringBuilder(); var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                    'u' -> { sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 6; continue }
                    else -> sb.append(n)
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    /** Regex("…" + "…", …) and Pattern.compile("…") first-argument string literals (regular strings only). */
    private fun regexLiterals(src: String): List<String> {
        val out = mutableListOf<String>()
        val call = Regex("""\b(?:Regex|Pattern\.compile|toRegex)\s*\(|\bSafeRegex\.of\s*\(\s*"[^"]*"\s*,""")
        for (m in call.findAll(src)) {
            var i = m.range.last + 1
            val sb = StringBuilder(); var any = false
            while (true) {
                while (i < src.length && src[i].isWhitespace()) i++
                if (i >= src.length || src[i] != '"' || src.startsWith("\"\"\"", i)) break
                i++
                val start = i
                while (i < src.length && src[i] != '"') { if (src[i] == '\\') i++; i++ }
                sb.append(unescapeKotlin(src.substring(start, i))); any = true; i++
                while (i < src.length && src[i].isWhitespace()) i++
                if (i < src.length && src[i] == '+') { i++; continue }
                break
            }
            if (any) out += sb.toString()
        }
        return out
    }

    @Test fun noIcuIncompatibleRegexLiterals() {
        val problems = mutableListOf<String>()
        var count = 0
        sourceRoot().walk().filter { it.extension == "kt" || it.extension == "java" }.forEach { f ->
            for (p in regexLiterals(f.readText())) {
                count++
                icuProblem(p)?.let { problems += "${f.name}: $it\n    $p" }
            }
        }
        assertTrue("found only $count regex literals; scanner broken?", count > 50)
        if (problems.isNotEmpty()) fail("Regexes that break on Android (ICU):\n" + problems.joinToString("\n"))
    }

    // ---- 2. every compiled Regex/Pattern held by the importer objects (catches string templates) ----

    private val importerClasses = listOf(
        "CaptionRecipeParser", "RecipeTextParser", "RecipeHtmlParser", "ImportParsing", "InstagramImporter",
        "SocialRecipeImporter", "WebRecipeFetcher", "CreatorsApiClient", "VideoRecipeExtractor",
        "TextRecipeExtractor", "StillsRecipeExtractor", "OpenRouterClient", "ImportErrors", "ImportUrlValidator", "SafeRegex",
        "OcrTextAssembler",
    ).map { "com.desikitchen.app.util.$it" }

    @Test fun importerObjectsInitialiseAndUseIcuSafePatterns() {
        val problems = mutableListOf<String>()
        var checked = 0
        for (name in importerClasses) {
            val cls = try { Class.forName(name, true, javaClass.classLoader) } catch (e: ClassNotFoundException) { continue }
            for (f in cls.declaredFields) {
                if (!java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                val v = runCatching { f.isAccessible = true; f.get(null) }.getOrNull()
                val pattern = when (v) { is Regex -> v.pattern; is Pattern -> v.pattern(); else -> continue }
                checked++
                icuProblem(pattern)?.let { problems += "${cls.simpleName}.${f.name}: $it\n    $pattern" }
            }
        }
        assertTrue("checked only $checked patterns", checked > 15)
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    @Test fun stepNumberStillStripsNumbering() {
        val f = CaptionRecipeParser::class.java.getDeclaredField("stepNumber").apply { isAccessible = true }
        val step = f.get(null) as Regex
        for ((input, out) in listOf(
            "Step 2: Add onion" to "Add onion", "step 3. Fry" to "Fry", "STEP 4 - Stir" to "Stir", "Step 5) Boil" to "Boil",
            "1. Heat oil" to "Heat oil", "2) Add jeera" to "Add jeera", "3- Add salt" to "Add salt", "4: Serve" to "Serve",
            "1️⃣ Heat oil" to "Heat oil",
        )) assertEquals(input, out, input.replace(step, "").trim())
    }

    // ---- 3. if a static init ever fails again, the user sees words, not "ExceptionInInitializerError" ----

    @Test fun initialiserFailureBecomesReadableMessage() {
        val eiie = ExceptionInInitializerError(PatternSyntaxException("U_REGEX_PROPERTY_SYNTAX", "[:.)]", 1))
        val msg = ImportErrors.userMessage(eiie)
        assertTrue(msg, msg.contains("internal error") && msg.contains("PatternSyntaxException") && msg.contains("Paste import"))
        assertTrue(msg, !msg.contains("ExceptionInInitializerError"))
        val ncdfe = ImportErrors.userMessage(NoClassDefFoundError("Could not initialize class CaptionRecipeParser"))
        assertTrue(ncdfe, ncdfe.contains("internal error"))
    }

    @Test fun fetchMapsInitialiserFailureFromImporter() {
        val saved = WebRecipeFetcher.instagramImport
        try {
            WebRecipeFetcher.instagramImport = { _ ->
                throw ExceptionInInitializerError(PatternSyntaxException("U_REGEX_PROPERTY_SYNTAX", "[:.)]", 1))
            }
            val r = kotlinx.coroutines.runBlocking { WebRecipeFetcher.fetch("https://www.instagram.com/reel/DF6dMW5vwgw/") }
            val m = (r as WebRecipeFetcher.Result.Failure).message
            assertTrue(m, m.contains("internal error") && m.contains("PatternSyntaxException"))
        } finally { WebRecipeFetcher.instagramImport = saved }
    }
}
