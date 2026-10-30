package com.desikitchen.app.util

/**
 * 1.1.5: turns anything thrown on an import path into a sentence a person can act on, and logs the real cause
 * (class names and messages only; never headers, keys or page content).
 *
 * Why: 1.1.4 showed "Import failed (ExceptionInInitializerError)" on the phone. That error means a class's static
 * initialiser threw (there, a regex Android's ICU engine rejects in CaptionRecipeParser), and its own message is
 * empty, so the real cause was hidden. [rootCause] unwraps it, and [isAppBug] tells the user it isn't their link.
 */
object ImportErrors {

    /** The underlying exception behind ExceptionInInitializerError / InvocationTargetException wrappers. */
    fun rootCause(e: Throwable): Throwable {
        var c = e
        repeat(8) {
            val next = when (c) {
                is ExceptionInInitializerError -> (c as ExceptionInInitializerError).exception ?: c.cause
                is java.lang.reflect.InvocationTargetException -> (c as java.lang.reflect.InvocationTargetException).targetException
                else -> null
            } ?: return c
            if (next === c) return c
            c = next
        }
        return c
    }

    /** Class-loading / static-init failures are bugs in the app build, not a bad link or a network problem. */
    fun isAppBug(e: Throwable): Boolean =
        e is LinkageError || // ExceptionInInitializerError, NoClassDefFoundError, …
            rootCause(e).let { it is SafeRegex.Unavailable || it is java.util.regex.PatternSyntaxException }

    fun userMessage(e: Throwable): String {
        if (e is OutOfMemoryError) {
            return "This post's video is too large to read on this phone. Copy the caption or recipe text and use Paste import."
        }
        val root = rootCause(e)
        val detail = root.javaClass.simpleName + (root.message?.takeIf { it.isNotBlank() }?.let { ": " + it.lineSequence().first().take(100) } ?: "")
        return if (isAppBug(e)) {
            "DesiKitchen's importer hit an internal error, so this isn't a problem with your link ($detail). " +
                "Copy the recipe text and use Paste import for now."
        } else {
            "Import failed ($detail). Check the link, or copy the recipe text and use Paste import."
        }
    }

    /** Best-effort logcat line; android.util.Log is a stub in JVM unit tests, so failures here are ignored. */
    fun log(what: String, e: Throwable) {
        runCatching { android.util.Log.e("DesiKitchen", "$what: ${e.javaClass.name} (root: ${rootCause(e).javaClass.name}: ${rootCause(e).message?.take(200)})", e) }
    }
}
