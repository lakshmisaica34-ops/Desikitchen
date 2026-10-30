package com.desikitchen.app.ui.plan

import com.desikitchen.app.ui.theme.Ink
import com.desikitchen.app.ui.theme.SoftSageFill
import com.desikitchen.app.ui.components.SoftCardStyle
import com.desikitchen.app.ui.components.SoftCard
import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desikitchen.app.data.model.MealPlanWithRecipe
import com.desikitchen.app.data.model.MealSlot
import com.desikitchen.app.ui.AppViewModelProvider
import com.desikitchen.app.ui.components.DkScaffold
import com.desikitchen.app.ui.components.DkTopBar
import com.desikitchen.app.ui.components.LocalSnackbarHostState
import com.desikitchen.app.ui.components.RecipeArt
import com.desikitchen.app.ui.components.RecipePickerDialog
import com.desikitchen.app.ui.theme.Mustard
import com.desikitchen.app.ui.theme.MustardPale
import com.desikitchen.app.ui.theme.Sage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val rangeFmt = DateTimeFormatter.ofPattern("d MMM")
private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d MMM")
private val chipDayFmt = DateTimeFormatter.ofPattern("EEE d")
private const val DRAG_LABEL = "desikitchen-plan-entry"

/** Week view: Breakfast / Lunch / Dinner / Snack per day; long-press-drag a meal to another slot, or use its ⋮ menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlanScreen(
    onOpenRecipe: (Long) -> Unit,
    onOpenGroceries: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: PlanViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    LaunchedEffect(Unit) { viewModel.messageFlow.collect { snackbar.showSnackbar(it) } }

    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var pickStartDay by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Pair<LocalDate, MealSlot>?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var moving by remember { mutableStateOf<MealPlanWithRecipe?>(null) }
    var servingsFor by remember { mutableStateOf<MealPlanWithRecipe?>(null) }
    val weekChips = rememberLazyListState()
    LaunchedEffect(state.weekOffset) { weekChips.animateScrollToItem((state.weekOffset + WEEKS_BACK - 1).coerceAtLeast(0)) }

    DkScaffold(
        topBar = {
            DkTopBar("Meal Plan", onBack = onBack, actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Add week to Groceries") }, onClick = { menu = false; viewModel.generateGroceries() })
                        DropdownMenuItem(
                            text = { Text("Remove recipes…") }, enabled = state.mealCount > 0,
                            onClick = { menu = false; selecting = true; selected = emptySet() },
                        )
                        DropdownMenuItem(text = { Text("Clear this week") }, enabled = state.mealCount > 0, onClick = { menu = false; confirmClear = true })
                        DropdownMenuItem(text = { Text("Change start day…") }, onClick = { menu = false; pickStartDay = true })
                    }
                }
            })
        },
        floatingActionButton = {
            if (state.mealCount > 0 && !selecting) ExtendedFloatingActionButton(
                onClick = { viewModel.generateGroceries(onOpenGroceries) },
                icon = { Icon(Icons.Filled.ShoppingCart, null) },
                text = { Text(if (state.autoGrocerySync && state.weekOffset in 0..1) "Open synced groceries" else "Make grocery list") },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@DkScaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::previousWeek) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous week") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${state.weekStart.format(rangeFmt)} – ${state.weekStart.plusDays(6).format(rangeFmt)}",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            weekLabel(state.weekOffset) + " · ${state.mealCount} meal${if (state.mealCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = viewModel::nextWeek) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next week") }
                }
                // Future weeks scroll: quick jump chips from last week to 8 weeks ahead.
                LazyRow(state = weekChips, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((-WEEKS_BACK..WEEKS_AHEAD).toList(), key = { it }) { off ->
                        val start = state.weekStart.plusWeeks((off - state.weekOffset).toLong())
                        FilterChip(
                            selected = off == state.weekOffset, onClick = { viewModel.goToWeek(off) },
                            label = { Text(if (off in -1..1) weekLabel(off) else start.format(rangeFmt), fontSize = 13.sp) },
                        )
                    }
                }
                val synced = state.autoGrocerySync && state.weekOffset in 0..1
                Text(
                    when {
                        synced -> "Grocery List stays in sync with this week and next."
                        state.autoGrocerySync -> "Grocery auto-sync covers this week and next; use “Make grocery list” for this week."
                        else -> "Grocery auto-sync is off (Profile → Settings)."
                    },
                    style = MaterialTheme.typography.bodySmall, color = if (synced) Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            if (selecting) {
                item(key = "select") {
                    Surface(color = SoftSageFill, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${selected.size} selected", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            val all = state.days.flatMap { d -> d.slots.values.flatten().map { it.entry.id } }.toSet()
                            TextButton(onClick = { selected = if (selected == all) emptySet() else all }) { Text(if (selected == all) "None" else "All") }
                            TextButton(onClick = { selecting = false; selected = emptySet() }) { Text("Cancel") }
                            TextButton(enabled = selected.isNotEmpty(), onClick = { viewModel.removeMany(selected); selecting = false; selected = emptySet() }) {
                                Text("Remove", color = if (selected.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            items(state.days, key = { it.date.toEpochDay() }) { day ->
                val today = day.date == LocalDate.now()
                // Soft chrome: List SoftCard (20/4); today gets the SoftSageFill wash instead of an M3 tinted Card.
                SoftCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    style = SoftCardStyle.List,
                    color = if (today) SoftSageFill else MaterialTheme.colorScheme.surface,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            (if (today) "Today · " else "") + day.date.format(dayFmt),
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        MealSlot.entries.forEach { slot ->
                            val entries = day.slots[slot].orEmpty()
                            SlotRow(
                                slot = slot,
                                entries = entries,
                                selecting = selecting,
                                selected = selected,
                                onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
                                onAdd = { picking = day.date to slot },
                                onOpen = onOpenRecipe,
                                onRemove = viewModel::remove,
                                onMove = { moving = it },
                                onNudge = viewModel::nudge,
                                onServings = { servingsFor = it },
                                onDrop = { id, index -> viewModel.move(id, day.date, slot, index) },
                            )
                        }
                    }
                }
            }
        }
    }

    picking?.let { (date, slot) ->
        RecipePickerDialog(
            title = "${slot.label} · ${date.format(rangeFmt)}",
            recipes = state.allRecipes,
            onDismiss = { picking = null },
            onPick = { viewModel.add(date, slot, it); picking = null },
        )
    }
    moving?.let { e ->
        MoveMealDialog(
            title = e.recipe?.title ?: "Meal",
            weekStart = state.weekStart,
            current = LocalDate.ofEpochDay(e.entry.epochDay) to e.entry.slot,
            onDismiss = { moving = null },
            onMove = { d, s -> viewModel.move(e.entry.id, d, s); moving = null },
        )
    }
    servingsFor?.let { e ->
        var n by remember(e.entry.id) { mutableIntStateOf(e.entry.servings) }
        AlertDialog(
            onDismissRequest = { servingsFor = null },
            title = { Text("Servings for ${e.recipe?.title ?: "this meal"}") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { n = (n - 1).coerceAtLeast(1) }) { Text("−", fontSize = 22.sp) }
                    Text("$n", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
                    TextButton(onClick = { n = (n + 1).coerceAtMost(50) }) { Text("+", fontSize = 22.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.setServings(e.entry.id, n); servingsFor = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { servingsFor = null }) { Text("Cancel") } },
        )
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear this week?") },
        text = { Text("All ${state.mealCount} planned meals for this week will be removed.") },
        confirmButton = { TextButton(onClick = { viewModel.clearWeek(); confirmClear = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
    if (pickStartDay) WeekStartDialog(state.weekStartDay, onDismiss = { pickStartDay = false }) {
        viewModel.setWeekStart(it); pickStartDay = false
    }
}

private const val WEEKS_BACK = 1
private const val WEEKS_AHEAD = 8

private fun weekLabel(off: Int) = when (off) { 0 -> "This week"; 1 -> "Next week"; -1 -> "Last week"; else -> if (off > 0) "In $off weeks" else "${-off} weeks ago" }

private fun dragId(event: DragAndDropEvent): Long? = runCatching {
    val clip = event.toAndroidDragEvent().clipData ?: return null
    if (clip.description?.label != DRAG_LABEL) return null
    clip.getItemAt(0).text?.toString()?.toLongOrNull()
}.getOrNull()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SlotRow(
    slot: MealSlot,
    entries: List<MealPlanWithRecipe>,
    selecting: Boolean,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onMove: (MealPlanWithRecipe) -> Unit,
    onNudge: (Long, Int) -> Unit,
    onServings: (MealPlanWithRecipe) -> Unit,
    onDrop: (entryId: Long, index: Int?) -> Unit,
) {
    var hovering by remember { mutableStateOf(false) }
    val drop by rememberUpdatedState(onDrop)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { hovering = true }
            override fun onExited(event: DragAndDropEvent) { hovering = false }
            override fun onEnded(event: DragAndDropEvent) { hovering = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering = false
                val id = dragId(event) ?: return false
                drop(id, null) // append to this slot
                return true
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(
        Modifier.fillMaxWidth()
            .dragAndDropTarget(shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) }, target = target)
            .then(if (hovering) Modifier.background(MustardPale).border(1.dp, Mustard, RoundedCornerShape(12.dp)) else Modifier)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(slot.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(104.dp))
        Column(Modifier.weight(1f)) {
            if (entries.isEmpty()) {
                Text(if (hovering) "Drop here" else "—", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            entries.forEachIndexed { index, e ->
                val r = e.recipe
                var entryMenu by remember { mutableStateOf(false) }
                val myIndex by rememberUpdatedState(index)
                val itemTarget = remember(e.entry.id) {
                    object : DragAndDropTarget {
                        override fun onDrop(event: DragAndDropEvent): Boolean {
                            val id = dragId(event) ?: return false
                            if (id == e.entry.id) return true
                            drop(id, myIndex) // insert before this meal
                            return true
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .then(
                            if (selecting) Modifier.clickable { onToggle(e.entry.id) }
                            else Modifier
                                .dragAndDropTarget(shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) }, target = itemTarget)
                                .dragAndDropSource {
                                    detectTapGestures(
                                        onTap = { r?.let { onOpen(it.id) } },
                                        onLongPress = {
                                            startTransfer(DragAndDropTransferData(ClipData.newPlainText(DRAG_LABEL, e.entry.id.toString())))
                                        },
                                    )
                                },
                        )
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selecting) {
                        Checkbox(checked = e.entry.id in selected, onCheckedChange = { onToggle(e.entry.id) }, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    if (r != null) RecipeArt(r, Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)), monogramSize = 12.sp)
                    else Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r?.title ?: "Deleted recipe", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        if (r != null && e.entry.servings != r.servings) {
                            Text("${e.entry.servings} servings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!selecting) {
                        Icon(Icons.Filled.DragIndicator, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                        Box {
                            IconButton(onClick = { entryMenu = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.MoreVert, "Meal options", Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = entryMenu, onDismissRequest = { entryMenu = false }) {
                                if (r != null) DropdownMenuItem(text = { Text("Open recipe") }, onClick = { entryMenu = false; onOpen(r.id) })
                                DropdownMenuItem(text = { Text("Move to…") }, onClick = { entryMenu = false; onMove(e) })
                                if (index > 0) DropdownMenuItem(text = { Text("Move up") }, onClick = { entryMenu = false; onNudge(e.entry.id, -1) })
                                if (index < entries.lastIndex) DropdownMenuItem(text = { Text("Move down") }, onClick = { entryMenu = false; onNudge(e.entry.id, 1) })
                                DropdownMenuItem(text = { Text("Servings…") }, onClick = { entryMenu = false; onServings(e) })
                                DropdownMenuItem(text = { Text("Remove") }, onClick = { entryMenu = false; onRemove(e.entry.id) })
                            }
                        }
                    }
                }
            }
        }
        if (!selecting) IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add ${slot.label}") }
    }
}

/** Fallback for drag-and-drop: pick a day (this or next 2 weeks) and a meal slot. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoveMealDialog(
    title: String,
    weekStart: LocalDate,
    current: Pair<LocalDate, MealSlot>,
    onDismiss: () -> Unit,
    onMove: (LocalDate, MealSlot) -> Unit,
) {
    var day by remember { mutableStateOf(current.first) }
    var slot by remember { mutableStateOf(current.second) }
    val days = remember(weekStart) { (0 until 21).map { weekStart.plusDays(it.toLong()) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move $title") },
        text = {
            Column {
                Text("Day", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), state = rememberLazyListState((days.indexOf(current.first)).coerceAtLeast(0))) {
                    items(days, key = { it.toEpochDay() }) { d ->
                        FilterChip(selected = d == day, onClick = { day = d }, label = { Text(d.format(chipDayFmt), fontSize = 12.sp) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Meal", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealSlot.entries.forEach { s -> FilterChip(selected = s == slot, onClick = { slot = s }, label = { Text(s.label) }) }
                }
            }
        },
        confirmButton = { TextButton(enabled = day to slot != current, onClick = { onMove(day, slot) }) { Text("Move") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun WeekStartDialog(current: DayOfWeek, onDismiss: () -> Unit, onPick: (DayOfWeek) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Week starts on") },
        text = {
            Column {
                DayOfWeek.entries.forEach { d ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(d) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = d == current, onClick = { onPick(d) })
                        Text(d.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {},
    )
}
