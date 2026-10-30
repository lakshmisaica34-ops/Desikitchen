package com.desikitchen.app.ui.detail

import com.desikitchen.app.util.SafeRegex

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.desikitchen.app.data.model.Step
import kotlinx.coroutines.delay
import java.util.Locale

/** One countdown (a step timer or a custom one). Lives while the screen is open. */
@Stable
class KitchenTimer(val key: String, val label: String, val totalSeconds: Int) {
    var remaining by mutableIntStateOf(totalSeconds)
    var running by mutableStateOf(false)
    val done: Boolean get() = remaining == 0

    fun toggle() {
        if (remaining == 0) remaining = totalSeconds
        running = !running
    }

    fun display(): String = "%02d:%02d".format(Locale.ROOT, remaining / 60, remaining % 60)
}

/** Timers derived from step timer minutes, plus any custom ones added with "+". */
@Composable
fun rememberKitchenTimers(steps: List<Step>, onFinished: (KitchenTimer) -> Unit): SnapshotStateList<KitchenTimer> {
    val timers = remember(steps.map { it.id to it.timerMinutes }) {
        mutableStateListOf<KitchenTimer>().apply {
            steps.forEachIndexed { i, s ->
                s.timerMinutes?.takeIf { it > 0 }?.let { add(KitchenTimer("step-${s.id}", timerLabel(s.text, i + 1), it * 60)) }
            }
        }
    }
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(timers) {
        while (true) {
            delay(1000)
            timers.forEach { t ->
                if (t.running && t.remaining > 0) {
                    t.remaining--
                    if (t.remaining == 0) { t.running = false; finished(t) }
                }
            }
        }
    }
    return timers
}

fun beep() {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 900)
    }
}

private val labelWords = listOf(
    "gravy", "masala", "tadka", "dal", "rice", "dough", "batter", "marinade", "chai", "milk", "syrup", "curry",
    "chicken", "mutton", "paneer", "potato", "onion", "tomato", "sauce", "pressure", "dum", "soak", "rest",
    "simmer", "boil", "fry", "roast", "bake", "steam",
)

/** Short chip label for a step timer: the first well-known cooking noun in the step, else "Step N". */
fun timerLabel(text: String, number: Int): String {
    val lower = text.lowercase(Locale.ROOT)
    val hit = labelWords.firstOrNull { Regex("\\b$it").containsMatchIn(lower) } ?: return "Step $number"
    return when (hit) {
        "pressure" -> "Cooker"
        "soak" -> "Soak"
        else -> hit.replaceFirstChar { it.uppercase() }
    }
}

data class TempChip(val label: String, val text: String, val highlight: StepHighlight.Temperature)

private val tempChipRegex = SafeRegex.of("KitchenTimers.tempChipRegex", "(\\d{2,3})\\s*(?:°\\s*([CF])|degrees?\\s*([CF])\\b)", RegexOption.IGNORE_CASE)

/** First explicit temperature in the steps (e.g. "220°C"), labelled by the vessel it belongs to. */
fun firstTemperature(steps: List<Step>): TempChip? {
    for (s in steps) {
        val m = tempChipRegex.find(s.text) ?: continue
        val v = m.groupValues[1].toIntOrNull() ?: continue
        val unit = m.groupValues[2].ifBlank { m.groupValues[3] }.uppercase(Locale.ROOT).first()
        val lower = s.text.lowercase(Locale.ROOT)
        val label = listOf("oven", "tawa", "tandoor", "air fryer", "oil", "pan", "grill").firstOrNull { it in lower }
            ?.replaceFirstChar { it.uppercase() } ?: "Heat"
        return TempChip(label, "$v°$unit", StepHighlight.Temperature(m.value, v, unit))
    }
    return null
}
