package com.desikitchen.app.ui.detail

import com.desikitchen.app.util.SafeRegex

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.desikitchen.app.data.model.Ingredient
import com.desikitchen.app.ui.theme.Ink
import java.util.Locale
import kotlin.math.roundToInt

/** Soft UI: step links (ingredients + temperatures) are Ink + underline — no blue / orange hues. */
val StepLinkColor: Color = Ink

sealed interface StepHighlight {
    data class IngredientRef(val ingredient: Ingredient) : StepHighlight
    data class Temperature(val raw: String, val value: Int?, val unit: Char?) : StepHighlight
}

private val stopWords = setOf(
    "fresh", "chopped", "powder", "seeds", "leaves", "paste", "boneless", "thick", "whole", "green", "dried",
    "ground", "small", "large", "medium", "finely", "thighs", "sliced", "grated", "kashmiri", "water", "salt",
    "split", "white", "black", "light", "with", "and", "for", "optional", "soaked", "boiled", "cubes",
)

private val tempRegex = SafeRegex.of("StepText.tempRegex", 
    "(\\d{2,3})\\s*(?:°\\s*([CF])?|degrees?\\s*([CF])?\\b)|\\b(low|medium|medium-high|high|sim)\\s+(?:heat|flame)",
    RegexOption.IGNORE_CASE,
)

fun ingredientKeywords(ing: Ingredient): List<String> {
    val cleaned = ing.name.lowercase(Locale.ROOT).replace(Regex("\\(.*?\\)"), "").trim()
    val words = cleaned.split(Regex("[^a-z]+")).filter { it.length >= 4 && it !in stopWords }
    return (listOf(cleaned) + words).distinct().filter { it.length >= 3 }
}

/** Builds step text with ingredient names and temperatures / heat levels as tappable Ink + underline links. */
fun buildStepText(text: String, ingredients: List<Ingredient>, onClick: (StepHighlight) -> Unit): AnnotatedString {
    data class Hit(val start: Int, val end: Int, val h: StepHighlight)
    val lower = text.lowercase(Locale.ROOT)
    val hits = mutableListOf<Hit>()
    tempRegex.findAll(text).forEach { m ->
        val value = m.groupValues[1].toIntOrNull()
        val unit = (m.groupValues[2].ifBlank { m.groupValues[3] }).firstOrNull()?.uppercaseChar()
        hits += Hit(m.range.first, m.range.last + 1, StepHighlight.Temperature(m.value, value, unit))
    }
    for (ing in ingredients) {
        for (kw in ingredientKeywords(ing)) {
            var idx = lower.indexOf(kw)
            while (idx >= 0) {
                val before = if (idx == 0) ' ' else lower[idx - 1]
                if (!before.isLetter()) hits += Hit(idx, idx + kw.length, StepHighlight.IngredientRef(ing))
                idx = lower.indexOf(kw, idx + kw.length)
            }
        }
    }
    // Resolve overlaps: prefer earlier, then longer.
    val chosen = mutableListOf<Hit>()
    hits.sortedWith(compareBy<Hit> { it.start }.thenByDescending { it.end - it.start }).forEach { h ->
        if (chosen.none { h.start < it.end && it.start < h.end }) chosen += h
    }
    return buildAnnotatedString {
        var cursor = 0
        chosen.sortedBy { it.start }.forEach { hit ->
            if (hit.start > cursor) append(text.substring(cursor, hit.start))
            val color = StepLinkColor
            withLink(
                LinkAnnotation.Clickable(
                    tag = "h${hit.start}",
                    styles = TextLinkStyles(SpanStyle(color = color, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)),
                    linkInteractionListener = { onClick(hit.h) },
                )
            ) { append(text.substring(hit.start, hit.end)) }
            cursor = hit.end
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

fun describeTemperature(t: StepHighlight.Temperature): Pair<String, String> {
    val v = t.value
    if (v == null) {
        val level = t.raw.lowercase(Locale.ROOT)
        val guide = when {
            "medium-high" in level -> "Medium-high: oil shimmers, onions sizzle briskly. Roughly 190–220 °C pan surface."
            "high" in level -> "High heat: for searing and tadka. Oil just starts to smoke. ~220–250 °C pan surface."
            "medium" in level -> "Medium heat: steady sizzle for bhunao/sautéing. ~150–190 °C pan surface."
            else -> "Low heat / sim: gentle bubbles for simmering gravies and dum. ~95–120 °C."
        }
        return "Heat level" to guide
    }
    fun cToF(c: Int) = (c * 9 / 5.0 + 32).roundToInt()
    fun fToC(f: Int) = ((f - 32) * 5 / 9.0).roundToInt()
    return when (t.unit) {
        'C' -> "$v °C" to "$v °C = ${cToF(v)} °F"
        'F' -> "$v °F" to "$v °F = ${fToC(v)} °C"
        else -> "$v°" to "If Celsius: $v °C = ${cToF(v)} °F\nIf Fahrenheit: $v °F = ${fToC(v)} °C"
    }
}
