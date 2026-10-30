package com.desikitchen.app.ui.edit

import com.desikitchen.app.ui.theme.Ink
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desikitchen.app.data.model.Aisles
import com.desikitchen.app.data.model.Tags
import com.desikitchen.app.ui.AppViewModelProvider
import com.desikitchen.app.ui.components.DkScaffold
import com.desikitchen.app.ui.components.DkTopBar
import com.desikitchen.app.ui.components.NumberBadge
import com.desikitchen.app.ui.components.PillButton
import com.desikitchen.app.ui.components.SoftCard
import com.desikitchen.app.ui.components.SoftCardStyle
import com.desikitchen.app.ui.components.SoftField
import com.desikitchen.app.ui.theme.Hairline
import com.desikitchen.app.ui.theme.MustardDeep
import com.desikitchen.app.ui.theme.MustardPale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditRecipeScreen(
    onBack: () -> Unit,
    onSaved: (id: Long, isNew: Boolean) -> Unit,
    viewModel: AddEditRecipeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val f = viewModel.form
    val review = viewModel.fromImport && !viewModel.isEdit
    var showPaste by rememberSaveable { mutableStateOf(false) }
    val save = { viewModel.save { id -> onSaved(id, !viewModel.isEdit) } }
    // Leaving without Save (top-bar or system back) discards the import draft, as before, and clears its persisted
    // editor session. Process death never runs this, so a killed app keeps the draft for the next launch.
    val leave = { viewModel.discardReview(); onBack() }
    androidx.activity.compose.BackHandler(onBack = leave)

    DkScaffold(
        topBar = {
            DkTopBar(
                when { viewModel.isEdit -> "Edit recipe"; review -> "Review recipe"; else -> "New recipe" },
                onBack = leave,
                // One primary action: the bottom "Save" PillButton is the only Save on this screen.
            )
        },
    ) { padding ->
        if (viewModel.loading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@DkScaffold
        }
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Header: photo + title (+ description) ----
            item {
                // The screen's one hero card (28dp / 8dp); every other card here uses the List preset.
                SoftCard(Modifier.fillMaxWidth(), style = SoftCardStyle.Hero) {
                    if (review && f.imageUrl.isNotBlank()) {
                        coil.compose.AsyncImage(
                            model = f.imageUrl, contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp)),
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    if (review) {
                        val n = f.ingredients.count { it.name.isNotBlank() }
                        val m = f.steps.count { it.text.isNotBlank() }
                        Text(
                            (if (viewModel.importPartial) "Partly read · " else "") + "$n ingredients · $m steps",
                            style = MaterialTheme.typography.labelLarge, color = Ink, fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    SoftField(
                        value = f.title, onValueChange = { v -> viewModel.update { it.copy(title = v) } },
                        placeholder = "Recipe title", modifier = Modifier.fillMaxWidth(),
                        isError = viewModel.error != null,
                        textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        container = androidx.compose.ui.graphics.Color.Transparent,
                    )
                    viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (review) viewModel.importWarnings.forEach { w ->
                        Text(w, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!review) {
                        Spacer(Modifier.height(10.dp))
                        SoftField(
                            value = f.description, onValueChange = { v -> viewModel.update { it.copy(description = v) } },
                            placeholder = "Description (optional)", singleLine = false, minLines = 2, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        SoftField(
                            value = f.sourceUrl, onValueChange = { v -> viewModel.update { it.copy(sourceUrl = v.trim()) } },
                            placeholder = "Source link (optional)", modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        PhotoPicker(imageUrl = f.imageUrl, onPicked = { v -> viewModel.update { it.copy(imageUrl = v) } })
                    }
                }
            }
            // Manual create / edit only: times, servings and tags. Import review stays to ingredients + steps.
            if (!review) {
                item {
                    SoftCard(Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SoftField(
                                value = f.prep, onValueChange = { v -> viewModel.update { it.copy(prep = v.filter(Char::isDigit)) } },
                                placeholder = "Prep min", modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            SoftField(
                                value = f.cook, onValueChange = { v -> viewModel.update { it.copy(cook = v.filter(Char::isDigit)) } },
                                placeholder = "Cook min", modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Servings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.update { it.copy(servings = (it.servings - 1).coerceAtLeast(1)) } }) { Icon(Icons.Filled.Remove, "Less") }
                            Text("${f.servings}", Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { viewModel.update { it.copy(servings = it.servings + 1) } }) { Icon(Icons.Filled.Add, "More") }
                        }
                    }
                }
                item {
                    SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 14.dp)) {
                        TagGroup("Cuisine", Tags.cuisines, setOfNotNull(f.cuisine.ifBlank { null })) { t ->
                            viewModel.update { it.copy(cuisine = if (it.cuisine == t) "" else t) }
                        }
                        TagGroup("Meal type", Tags.mealTypes, setOfNotNull(f.mealType.ifBlank { null })) { t ->
                            viewModel.update { it.copy(mealType = if (it.mealType == t) "" else t) }
                        }
                        TagGroup("Diet", Tags.diets, f.dietTags) { t ->
                            viewModel.update { it.copy(dietTags = if (t in it.dietTags) it.dietTags - t else it.dietTags + t) }
                        }
                    }
                }
            }

            // ---- Ingredients: one clean line per item ----
            item {
                SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 18.dp, end = 6.dp, top = 16.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CardTitle("Ingredients", Modifier.weight(1f))
                        TextButton(onClick = { showPaste = true }) { Text("Paste list", color = Ink, fontWeight = FontWeight.Bold) }
                    }
                    f.ingredients.forEachIndexed { i, row ->
                        if (i > 0) HorizontalDivider(Modifier.padding(end = 12.dp), color = Hairline.copy(alpha = 0.6f))
                        IngredientLine(
                            row = row, detailed = !review,
                            onChange = { t -> viewModel.updateIngredient(row.key, t) },
                            onRemove = { viewModel.removeIngredient(row.key) },
                        )
                    }
                    TextButton(onClick = viewModel::addIngredient) {
                        Icon(Icons.Filled.Add, null, tint = MustardDeep); Spacer(Modifier.width(4.dp)); Text("Add ingredient", color = Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ---- Steps: numbered text ----
            item {
                SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 18.dp, end = 6.dp, top = 16.dp, bottom = 8.dp)) {
                    CardTitle("Steps")
                    Spacer(Modifier.height(8.dp))
                    f.steps.forEachIndexed { i, step ->
                        StepLine(
                            index = i, step = step, showTimer = !review,
                            onText = { v -> viewModel.updateStep(step.key) { it.copy(text = v) } },
                            onTimer = { v -> viewModel.updateStep(step.key) { it.copy(timer = v.filter(Char::isDigit)) } },
                            onMove = { d -> viewModel.moveStep(step.key, d) },
                            onRemove = { viewModel.removeStep(step.key) },
                        )
                    }
                    TextButton(onClick = viewModel::addStep) {
                        Icon(Icons.Filled.Add, null, tint = MustardDeep); Spacer(Modifier.width(4.dp)); Text("Add step", color = Ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                PillButton(if (viewModel.isEdit) "Save changes" else "Save recipe", onClick = save)
            }
        }
    }

    if (showPaste) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPaste = false },
            title = { Text("Paste ingredients") },
            text = {
                SoftField(
                    value = text, onValueChange = { text = it }, singleLine = false, minLines = 6,
                    placeholder = "One per line", modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.pasteIngredients(text); showPaste = false }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showPaste = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), modifier = modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroup(label: String, options: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { t ->
                FilterChip(
                    selected = t in selected, onClick = { onToggle(t) }, label = { Text(t) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MustardPale, selectedLabelColor = MustardDeep),
                )
            }
        }
    }
}

/** Borderless inline text (no box, no label): reads like a list line, edits in place. */
@Composable
private fun InlineText(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val color = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = singleLine,
        textStyle = style.copy(color = color),
        cursorBrush = SolidColor(MustardDeep),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.padding(vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), maxLines = 1)
                inner()
            }
        },
    )
}

/**
 * One clean ingredient line: qty · unit · name · ✕. Import review shows only that; manual edit adds an aisle chip and an
 * optional note on a second line (no fixed heights, so nothing overlaps).
 */
@Composable
private fun IngredientLine(row: IngredientRow, detailed: Boolean, onChange: ((IngredientRow) -> IngredientRow) -> Unit, onRemove: () -> Unit) {
    var aisleMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InlineText(row.quantity, { v -> onChange { it.copy(quantity = v) } }, "Qty", Modifier.width(48.dp),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            InlineText(row.unit, { v -> onChange { it.copy(unit = v) } }, "unit", Modifier.width(56.dp),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            InlineText(row.name, { v -> onChange { it.copy(name = v) } }, "Ingredient", Modifier.weight(1f))
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Close, "Remove ingredient", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        if (detailed) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp, end = 12.dp)) {
                Box {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { aisleMenu = true }.padding(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text(row.aisle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    DropdownMenu(expanded = aisleMenu, onDismissRequest = { aisleMenu = false }) {
                        Aisles.all.forEach { a ->
                            DropdownMenuItem(text = { Text(a) }, onClick = { onChange { it.copy(aisle = a) }; aisleMenu = false })
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                InlineText(
                    row.note, { v -> onChange { it.copy(note = v) } }, "Add a note", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StepLine(
    index: Int,
    step: StepRow,
    showTimer: Boolean,
    onText: (String) -> Unit,
    onTimer: (String) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        NumberBadge(index + 1, Modifier.padding(top = 10.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            InlineText(step.text, onText, "Describe this step", Modifier.fillMaxWidth(), singleLine = false)
            if (showTimer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Timer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    InlineText(step.timer, onTimer, "– min", Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall, keyboardType = KeyboardType.Number)
                }
            }
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.MoreVert, "Step options", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Move up") }, onClick = { onMove(-1); menu = false })
                DropdownMenuItem(text = { Text("Move down") }, onClick = { onMove(1); menu = false })
                DropdownMenuItem(text = { Text("Remove") }, onClick = { onRemove(); menu = false })
            }
        }
    }
}


/** Recipe photo: preview + Gallery / Camera / Remove. Picked images are copied into app storage (RecipeImageStore). */
@Composable
private fun PhotoPicker(imageUrl: String, onPicked: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun import(uri: android.net.Uri) {
        busy = true; failed = false
        scope.launch {
            val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.desikitchen.app.util.RecipeImageStore.importImage(context, uri) }
            busy = false
            if (saved != null) onPicked(saved) else failed = true
        }
    }
    val gallery = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(::import) }
    val camera = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { ok -> val u = pendingCapture; pendingCapture = null; if (ok && u != null) import(android.net.Uri.parse(u)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(84.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(24.dp))
                imageUrl.isNotBlank() -> coil.compose.AsyncImage(
                    model = imageUrl, contentDescription = "Recipe photo",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
                else -> Icon(Icons.Outlined.PhotoCamera, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Photo", style = MaterialTheme.typography.labelLarge)
            Row {
                TextButton(onClick = {
                    gallery.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Gallery") }
                TextButton(onClick = {
                    val uri = com.desikitchen.app.util.RecipeImageStore.newCaptureUri(context)
                    pendingCapture = uri.toString(); camera.launch(uri)
                }) { Text("Camera") }
                if (imageUrl.isNotBlank()) TextButton(onClick = { onPicked("") }) { Text("Remove") }
            }
            if (failed) Text("Couldn't read that image", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
    }
}
