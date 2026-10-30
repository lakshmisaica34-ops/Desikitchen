package com.desikitchen.app.util

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Parses JSON-LD blocks as found in the wild. Real recipe sites ship blocks with raw newlines /
 * tabs inside strings (Sanjeev Kapoor), HTML comment or CDATA wrappers, trailing semicolons and
 * stray trailing commas. We repair those before handing the text to org.json.
 */
object LenientJson {

    fun parse(raw: String): Any? {
        val text = sanitise(raw)
        if (text.isEmpty()) return null
        return runCatching { JSONTokener(text).nextValue() }.getOrNull()?.takeIf { it is JSONObject || it is JSONArray }
            ?: runCatching { JSONTokener(dropTrailingCommas(text)).nextValue() }.getOrNull()?.takeIf { it is JSONObject || it is JSONArray }
    }

    internal fun sanitise(raw: String): String {
        var t = raw.trim()
            .removePrefix("<!--").removeSuffix("-->")
            .replace(Regex("^\\s*(//\\s*)?<!\\[CDATA\\["), "").replace(Regex("(//\\s*)?]]>\\s*$"), "")
            .trim().trimEnd(';').trim()
        // Escape control characters that appear inside string literals.
        val sb = StringBuilder(t.length)
        var inString = false
        var escaped = false
        for (c in t) {
            if (inString) {
                when {
                    escaped -> { sb.append(c); escaped = false }
                    c == '\\' -> { sb.append(c); escaped = true }
                    c == '"' -> { sb.append(c); inString = false }
                    c == '\n' || c == '\r' -> sb.append("\\n")
                    c == '\t' -> sb.append(' ')
                    c < ' ' -> sb.append(' ')
                    else -> sb.append(c)
                }
            } else {
                if (c == '"') inString = true
                sb.append(c)
            }
        }
        t = sb.toString()
        return t
    }

    private fun dropTrailingCommas(t: String): String = t.replace(Regex(",\\s*([}\\]])"), "$1")
}
