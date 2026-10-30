package com.desikitchen.app.util

import java.util.concurrent.ConcurrentHashMap

/**
 * 1.1.6: builds the app's long-lived regexes so that a pattern the phone's regex engine (ICU on Android) rejects can
 * NEVER fail a class's static initialiser.
 *
 * Background: in 1.1.0–1.1.4 a single bad pattern in CaptionRecipeParser threw PatternSyntaxException inside
 * `<clinit>`. The first use failed with ExceptionInInitializerError, and every later use in that process failed with
 * NoClassDefFoundError ("class stays broken"). Here, a failed compile is recorded under [name], the field gets a
 * regex that never matches, the class still loads, and parsers call [requireOk] to fail with a readable
 * [Unavailable] (a normal RuntimeException that ImportErrors turns into words, and that ImportParsing can fall back from).
 */
object SafeRegex {

    class Unavailable(val names: List<String>, cause: Throwable?) :
        IllegalStateException("Text pattern ${names.joinToString()} isn't supported on this phone (${cause?.javaClass?.simpleName}: ${cause?.message?.lineSequence()?.firstOrNull()?.take(120)})", cause)

    private val failures = ConcurrentHashMap<String, Throwable>()

    /** A pattern that never matches and that every engine accepts. */
    private val NEVER: Regex = runCatching { Regex("[^\\s\\S]") }.getOrElse { Regex("a") }

    fun of(name: String, pattern: String): Regex = compile(name) { Regex(pattern) }
    fun of(name: String, pattern: String, option: RegexOption): Regex = compile(name) { Regex(pattern, option) }
    fun of(name: String, pattern: String, options: Set<RegexOption>): Regex = compile(name) { Regex(pattern, options) }

    private inline fun compile(name: String, make: () -> Regex): Regex = try {
        make()
    } catch (e: Throwable) {
        if (e is VirtualMachineError) throw e
        failures.putIfAbsent(name, e)
        ImportErrors.log("regex $name failed to compile", e)
        NEVER
    }

    /** Names of patterns that failed to compile in this process (empty when all is well). */
    fun failed(): Map<String, Throwable> = HashMap(failures)

    /** Throws a readable [Unavailable] if any pattern whose name starts with [owner] failed to compile. */
    fun requireOk(owner: String) {
        if (failures.isEmpty()) return
        val bad = failures.filterKeys { it == owner || it.startsWith("$owner.") }
        if (bad.isNotEmpty()) throw Unavailable(bad.keys.sorted(), bad.values.first())
    }

    /** Test hook. */
    internal fun recordForTest(name: String, e: Throwable) { failures[name] = e }
    internal fun clearForTest() = failures.clear()
}
