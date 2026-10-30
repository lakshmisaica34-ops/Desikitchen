package com.desikitchen.app.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

enum class UnitSystem(val label: String) { METRIC("Metric"), IMPERIAL("Imperial") }

data class DisplayQuantity(val amount: Double?, val unit: String) {
    fun format(): String {
        val a = amount ?: return unit
        val num = formatAmount(a)
        return if (unit.isBlank()) num else "$num $unit"
    }
}

object Units {
    /**
     * Converts a quantity + unit (as stored, usually metric or spoon/cup measures) to the
     * requested unit system. Spoons, pinches and pieces are left untouched.
     */
    fun convert(amount: Double?, unit: String, system: UnitSystem): DisplayQuantity {
        if (amount == null) return DisplayQuantity(null, unit)
        val u = unit.trim().lowercase(Locale.ROOT)
        return when (system) {
            UnitSystem.METRIC -> when (u) {
                "oz" -> grams(amount * 28.3495)
                "lb", "lbs" -> grams(amount * 453.592)
                "fl oz" -> millis(amount * 29.5735)
                "cup", "cups" -> millis(amount * 240.0)
                else -> DisplayQuantity(amount, unit)
            }
            UnitSystem.IMPERIAL -> when (u) {
                "g" -> ounces(amount / 28.3495)
                "kg" -> ounces(amount * 1000 / 28.3495)
                "ml" -> volumeImperial(amount)
                "l" -> volumeImperial(amount * 1000)
                else -> DisplayQuantity(amount, unit)
            }
        }
    }

    private fun grams(g: Double) =
        if (g >= 1000) DisplayQuantity(g / 1000, "kg") else DisplayQuantity(roundSensible(g), "g")

    private fun millis(ml: Double) =
        if (ml >= 1000) DisplayQuantity(ml / 1000, "l") else DisplayQuantity(roundSensible(ml), "ml")

    private fun ounces(oz: Double) =
        if (oz >= 16) DisplayQuantity(oz / 16, "lb") else DisplayQuantity(oz, "oz")

    private fun volumeImperial(ml: Double): DisplayQuantity {
        val cups = ml / 240.0
        return if (cups >= 0.25) DisplayQuantity(cups, if (cups > 1.01) "cups" else "cup")
        else DisplayQuantity(ml / 29.5735, "fl oz")
    }

    private fun roundSensible(v: Double): Double = when {
        v >= 100 -> (v / 5).roundToInt() * 5.0
        v >= 10 -> v.roundToInt().toDouble()
        else -> v
    }
}

private val fractions = listOf(
    0.125 to "⅛", 0.25 to "¼", 0.333 to "⅓", 0.5 to "½", 0.667 to "⅔", 0.75 to "¾",
)

/** Formats 1.5 as "1½", 0.25 as "¼", 2.0 as "2", 2.37 as "2.4". */
fun formatAmount(value: Double): String {
    if (value <= 0) return "0"
    val whole = floor(value)
    val frac = value - whole
    if (frac < 0.04) return whole.toLong().toString()
    if (frac > 0.96) return (whole + 1).toLong().toString()
    val match = fractions.firstOrNull { abs(it.first - frac) < 0.04 }
    if (match != null && value < 20) {
        return if (whole == 0.0) match.second else "${whole.toLong()}${match.second}"
    }
    return if (value >= 10) value.roundToInt().toString()
    else String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
}

fun formatMinutes(minutes: Int): String = when {
    minutes <= 0 -> "—"
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}
