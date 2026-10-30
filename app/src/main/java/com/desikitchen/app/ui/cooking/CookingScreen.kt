package com.desikitchen.app.ui.cooking

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import com.desikitchen.app.ui.components.NumberBadge
import com.desikitchen.app.ui.theme.Hairline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desikitchen.app.ui.AppViewModelProvider
import com.desikitchen.app.ui.components.DkScaffold
import com.desikitchen.app.ui.components.EmptyState
import com.desikitchen.app.ui.components.KeepScreenOn
import com.desikitchen.app.ui.components.LocalSnackbarHostState
import com.desikitchen.app.ui.components.PillButton
import com.desikitchen.app.ui.components.SoftOutlinePill
import com.desikitchen.app.ui.components.RecipeArt
import com.desikitchen.app.ui.detail.AskDesiKitchenSheet
import com.desikitchen.app.ui.detail.StepHighlight
import com.desikitchen.app.ui.detail.beep
import com.desikitchen.app.ui.detail.buildStepText
import com.desikitchen.app.ui.detail.describeTemperature
import com.desikitchen.app.ui.detail.rememberKitchenTimers
import com.desikitchen.app.ui.theme.Chip
import com.desikitchen.app.ui.theme.Ink
import com.desikitchen.app.ui.theme.Muted
import com.desikitchen.app.ui.theme.SoftSage
import com.desikitchen.app.ui.theme.SoftSageDeep
import com.desikitchen.app.ui.theme.SoftSageFill
import com.desikitchen.app.ui.theme.SoftSagePale
import com.desikitchen.app.util.Units
import kotlinx.coroutines.launch

/**
 * Cook mode (Soft pass): one step at a time. Close · title · quiet icons; ONE progress (SoftSage segments + a single
 * "n / N"); optional real photo only (no monogram/gradient hero); the step text is the hero; ONE timer control, shown
 * only when the step states a time. Previous = SoftOutlinePill, Next/Finish = PillButton (the only solid sage).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingScreen(
    onBack: () -> Unit,
    viewModel: CookingViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    // Cooking mode always keeps the screen awake, regardless of the recipe-page setting.
    KeepScreenOn(true)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val details = state.details
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var showAsk by rememberSaveable { mutableStateOf(false) }
    var showIngredients by rememberSaveable { mutableStateOf(false) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var current by rememberSaveable { mutableIntStateOf(0) }
    var highlight by remember { mutableStateOf<StepHighlight?>(null) }
    val steps = details?.sortedSteps.orEmpty()
    val timers = rememberKitchenTimers(steps) { t ->
        beep()
        scope.launch { snackbar.showSnackbar("${t.label} timer is done") }
    }
    // Guard a stale saved index if the recipe's steps shrank.
    LaunchedEffect(steps.size) { if (steps.isNotEmpty() && current > steps.lastIndex) current = steps.lastIndex }

    DkScaffold(
        bottomBar = {
            if (steps.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = Color(0x22000000), spotColor = Color(0x1A000000))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Mock 05: Previous = SoftOutlinePill (Pistachio + Retro Green hairline), Next/Finish = PillButton (white on Retro Green).
                    SoftOutlinePill(
                        "Previous", onClick = { if (current > 0) current-- }, enabled = current > 0,
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, fill = true,
                        contentColor = Ink, border = BorderStroke(1.5.dp, SoftSage),
                        modifier = Modifier.weight(1f),
                    )
                    val last = current >= steps.lastIndex
                    PillButton(
                        text = if (last) "Finish" else "Next",
                        onClick = { if (!last) current++ else finished = true },
                        modifier = Modifier.weight(1f),
                        trailingIcon = if (last) Icons.Filled.Check else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Top bar: close · title; Ingredients + Coco stay as quiet icons. Step count lives with the progress bar only.
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onBack, shape = CircleShape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, Hairline),
                    modifier = Modifier.size(40.dp),
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Close, "Close cook mode", tint = Ink, modifier = Modifier.size(20.dp)) } }
                Text(
                    details?.recipe?.title ?: "Cook mode",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton(onClick = { showIngredients = true }, enabled = details != null) { Icon(Icons.Outlined.Checklist, "Ingredients", tint = Ink) }
                IconButton(onClick = { showAsk = true }, enabled = details != null) { Icon(Icons.Filled.AutoAwesome, "Coco", tint = SoftSageDeep) }
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center), color = SoftSage) }
                details == null || steps.isEmpty() -> EmptyState("No steps to cook", "Add instructions to this recipe first.")
                else -> {
                    val idx = current.coerceIn(0, steps.lastIndex)
                    // ONE progress: SoftSage segments (done + current) / SoftSageFill (upcoming) + a single "n / N".
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            steps.indices.forEach { i ->
                                Box(
                                    Modifier.weight(1f).height(5.dp).clip(CircleShape)
                                        .background(if (i <= idx) SoftSage else SoftSageFill),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${idx + 1} / ${steps.size}", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val step = steps[idx]
                    val timer = timers.firstOrNull { it.key == "step-${step.id}" }
                    // ONE context photo (own imageUrl → honest bundled), the same on every step; max 140dp, 20dp radius, Crop.
                    // No photo — or a dead remote thumb — omits it entirely: the step text is the hero, never a monogram void.
                    val photo = com.desikitchen.app.ui.components.rememberRecipePhoto(details.recipe)
                    if (photo.hasPhoto) {
                        RecipeArt(
                            details.recipe,
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp).height(140.dp).clip(RoundedCornerShape(20.dp)),
                            photo = photo,
                        )
                    }
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                    ) {
                        val text = remember(step.text, details.ingredients) { buildStepText(step.text, details.sortedIngredients) { highlight = it } }
                        // The step text is the hero: big Ink type, readable at arm's length from the stove.
                        Text(text, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 36.sp, color = Ink))
                        if (timer != null) {
                            // ONE timer control — only for a time the step text itself states (rememberKitchenTimers).
                            Spacer(Modifier.height(20.dp))
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).background(SoftSageFill).clickable { timer.toggle() }
                                    .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(if (timer.running) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = SoftSageDeep) }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    when { timer.running -> "Pause"; timer.done -> "Restart"; timer.remaining < timer.totalSeconds -> "Resume"; else -> "Start" } + " ${timer.label} timer",
                                    style = MaterialTheme.typography.titleSmall, color = Ink,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(timer.display(), color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (details != null && showAsk) AskDesiKitchenSheet(details, onDismiss = { showAsk = false }, step = current)
    if (details != null && showIngredients) {
        ModalBottomSheet(onDismissRequest = { showIngredients = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text("Ingredients", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
                Spacer(Modifier.height(8.dp))
                details.sortedIngredients.forEach { ing ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(ing.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            if (ing.quantity == null) ing.unit.ifBlank { "" } else Units.convert(ing.quantity, ing.unit, state.unitSystem).format(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (finished && details != null) {
        AlertDialog(
            onDismissRequest = { finished = false },
            title = { Text("All done") },
            text = {
                Text(
                    "Enjoy your ${details.recipe.title}." +
                        if (details.recipe.cookedCount > 0) " You've cooked it ${details.recipe.cookedCount} time${if (details.recipe.cookedCount == 1) "" else "s"} before." else "",
                )
            },
            confirmButton = { TextButton(onClick = { finished = false; viewModel.markCooked(onBack) }) { Text("Mark cooked & finish") } },
            dismissButton = { TextButton(onClick = { finished = false }) { Text("Keep cooking") } },
        )
    }
    highlight?.let { h ->
        val (title, body) = when (h) {
            is StepHighlight.IngredientRef -> h.ingredient.name to
                (if (h.ingredient.quantity == null) h.ingredient.unit.ifBlank { "No amount listed" } else Units.convert(h.ingredient.quantity, h.ingredient.unit, state.unitSystem).format())
            is StepHighlight.Temperature -> describeTemperature(h)
        }
        AlertDialog(
            onDismissRequest = { highlight = null },
            title = { Text(title) }, text = { Text(body) },
            confirmButton = { TextButton(onClick = { highlight = null }) { Text("OK") } },
        )
    }
}
