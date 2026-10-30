package com.desikitchen.app.ui.grocery

import com.desikitchen.app.ui.theme.SoftSageDeep
import com.desikitchen.app.ui.components.SoftOutlinePill
import com.desikitchen.app.ui.components.SoftChip
import com.desikitchen.app.ui.components.PillButton
import androidx.compose.foundation.border

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.ui.AppViewModelProvider
import com.desikitchen.app.ui.components.DkScaffold
import com.desikitchen.app.ui.components.DkTopBar
import com.desikitchen.app.ui.components.EmptyState
import com.desikitchen.app.ui.components.LocalSnackbarHostState
import com.desikitchen.app.ui.components.RecipeArt
import com.desikitchen.app.ui.components.RecipePickerDialog
import com.desikitchen.app.ui.components.SectionHeader
import com.desikitchen.app.ui.components.recipeMonogram
import com.desikitchen.app.ui.theme.Ink
import com.desikitchen.app.ui.theme.SoftSagePale
import androidx.compose.material.icons.outlined.Storefront
import com.desikitchen.app.ui.theme.Mustard
import com.desikitchen.app.ui.theme.MustardCream
import com.desikitchen.app.ui.theme.Sage
import com.desikitchen.app.ui.theme.SageDeep

/** Generic store front pages for the "Order Online" stub — no checkout API, nothing is sent anywhere by DesiKitchen. */
data class GroceryStore(val name: String, val url: String)

val OrderOnlineStores = listOf(
    GroceryStore("BigBasket", "https://www.bigbasket.com/"),
    GroceryStore("Blinkit", "https://blinkit.com/"),
    GroceryStore("Zepto", "https://www.zeptonow.com/"),
    GroceryStore("Swiggy Instamart", "https://www.swiggy.com/instamart"),
    GroceryStore("Amazon Fresh", "https://www.amazon.in/alm/storefront?almBrandId=ctnow"),
    GroceryStore("JioMart", "https://www.jiomart.com/"),
)

@Composable
fun GroceryScreen(
    onOpenRecipe: (Long) -> Unit,
    onOpenPlan: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: GroceryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    LaunchedEffect(Unit) { viewModel.messageFlow.collect { snackbar.showSnackbar(it) } }
    var input by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    var pickRecipe by remember { mutableStateOf(false) }
    var orderOnline by remember { mutableStateOf(false) }
    var showHome by rememberSaveable { mutableStateOf(false) }
    val submit = { if (input.isNotBlank()) { viewModel.addFromText(input); input = "" } }

    DkScaffold(
        topBar = {
            DkTopBar(if (state.remaining > 0) "Groceries · ${state.remaining}" else "Groceries", onBack = onBack, actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Add from Meal Plan") }, onClick = { menu = false; onOpenPlan() })
                        DropdownMenuItem(text = { Text("Add a recipe's ingredients…") }, onClick = { menu = false; pickRecipe = true })
                        DropdownMenuItem(text = { Text("Deselect pantry staples") }, onClick = { menu = false; viewModel.deselectPantry() })
                        DropdownMenuItem(text = { Text("Order online…") }, enabled = state.remaining > 0, onClick = { menu = false; orderOnline = true })
                        DropdownMenuItem(text = { Text("Share list") }, enabled = state.remaining > 0, onClick = { menu = false; shareList(context, state.shareText) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Clear checked") }, enabled = state.checked.isNotEmpty(), onClick = { menu = false; viewModel.clearChecked() })
                        DropdownMenuItem(text = { Text("Clear entire list") }, enabled = !state.isEmpty, onClick = { menu = false; confirmAll = true })
                    }
                }
            })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("Add item, e.g. 1 kg onions") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = { IconButton(onClick = submit) { Icon(Icons.Filled.Add, "Add item") } },
                shape = RoundedCornerShape(24.dp),
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.isEmpty -> EmptyState(
                    "Your list is empty",
                    if (state.autoSync) "Add items above. Your meal plan syncs here." else "Add items above or from your meal plan.",
                    // One PillButton (EmptyState spec); "Add a recipe's ingredients…" stays in the overflow menu.
                    action = { PillButton("Add from Meal Plan", onClick = onOpenPlan, fill = false) },
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    if (state.recipes.isNotEmpty()) {
                        item(key = "carousel") {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.recipes, key = { it.id }) { r ->
                                    RecipeChipCard(r, state.allRecipes.firstOrNull { it.id == r.id }, onClick = { onOpenRecipe(r.id) }, onRemove = { viewModel.removeRecipe(r.id) })
                                }
                            }
                        }
                    }
                    item(key = "controls") {
                        // Soft chrome: SoftChips (Ink selected text on SoftSageFill) instead of M3 segmented buttons.
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(GrocerySort.entries) { s -> SoftChip(selected = state.sort == s, onClick = { viewModel.setSort(s) }, label = s.label) }
                        }
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(GroceryFilter.entries) { f ->
                                SoftChip(selected = state.filter == f, onClick = { viewModel.setFilter(f) }, label = f.label)
                            }
                        }
                        if (state.remaining > 0) {
                            SoftOutlinePill(
                                "Order online", onClick = { orderOnline = true }, icon = Icons.Outlined.ShoppingCart, fill = true,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                    if (state.sections.isEmpty() && state.filter != GroceryFilter.ALL) {
                        item(key = "nofilter") {
                            Text(
                                "Nothing to buy from “${state.filter.label}”.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                    state.sections.forEach { section ->
                        item(key = "h-${section.title}") { SectionHeader(section.title) }
                        items(section.rows, key = { it.key }) { row ->
                            GroceryRowItem(
                                row, showRecipe = state.sort != GrocerySort.RECIPE,
                                onToggle = { viewModel.toggle(row) }, onDelete = { viewModel.delete(row) },
                                onHome = { viewModel.setHaveAtHome(row, true) },
                            )
                        }
                    }
                    if (state.haveAtHome.isNotEmpty()) {
                        item(key = "h-home") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionHeader("Have at home (${state.haveAtHome.size})", Modifier.weight(1f))
                                TextButton(onClick = { showHome = !showHome }, modifier = Modifier.padding(end = 8.dp)) { Text(if (showHome) "Hide" else "Show") }
                            }
                        }
                        if (showHome) items(state.haveAtHome, key = { "h" + it.key }) { row ->
                            GroceryRowItem(
                                row, showRecipe = false,
                                onToggle = { viewModel.setHaveAtHome(row, false) }, onDelete = { viewModel.delete(row) },
                                onHome = { viewModel.setHaveAtHome(row, false) },
                            )
                        }
                    }
                    if (state.checked.isNotEmpty()) {
                        item(key = "h-checked") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SectionHeader("Checked (${state.checked.size})", Modifier.weight(1f))
                                TextButton(onClick = viewModel::clearChecked, modifier = Modifier.padding(end = 8.dp)) { Text("Clear checked") }
                            }
                        }
                        items(state.checked, key = { "c" + it.key }) { row ->
                            GroceryRowItem(row, showRecipe = false, onToggle = { viewModel.toggle(row) }, onDelete = { viewModel.delete(row) }, onHome = null)
                        }
                    }
                }
            }
        }
    }

    if (pickRecipe) RecipePickerDialog(
        title = "Add ingredients from…",
        recipes = state.allRecipes,
        onDismiss = { pickRecipe = false },
        onPick = { viewModel.addRecipe(it); pickRecipe = false },
    )
    if (orderOnline) OrderOnlineSheet(state.shareText, state.remaining, onDismiss = { orderOnline = false })
    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false },
        title = { Text("Clear grocery list?") },
        text = { Text("Every item, checked or not, will be removed. Meal-plan items come back when the plan changes.") },
        confirmButton = { TextButton(onClick = { viewModel.clearAll(); confirmAll = false }) { Text("Clear all") } },
        dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancel") } },
    )
}

private fun shareList(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, "Grocery list")
    context.startActivity(Intent.createChooser(send, "Share grocery list"))
}

private fun copyList(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Grocery list", text))
}

/**
 * Order Online — honest stub. DesiKitchen has no supermarket checkout integration; this copies/shares the list and
 * opens a store's public site so you can search and add items yourself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderOnlineSheet(text: String, count: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Order online", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                "DesiKitchen can't place orders or fill a store cart. Copy your $count items, then open a store and search them there.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { copyList(context, text); copied = true }) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(if (copied) "Copied" else "Copy list")
                }
                OutlinedButton(onClick = { shareList(context, text) }) {
                    Icon(Icons.Filled.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Share list")
                }
            }
            Spacer(Modifier.height(8.dp))
            OrderOnlineStores.forEach { store ->
                ListItem(
                    headlineContent = { Text(store.name) },
                    supportingContent = { Text(Uri.parse(store.url).host.orEmpty().removePrefix("www."), style = MaterialTheme.typography.labelSmall) },
                    leadingContent = { Icon(Icons.Outlined.Storefront, null, tint = SageDeep) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open ${store.name}") },
                    modifier = Modifier.clickable {
                        copyList(context, text); copied = true
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(store.url))) } catch (_: ActivityNotFoundException) {}
                    },
                )
            }
        }
    }
}

/** Soft white recipe chip: monogram/photo thumb + Ink title + Muted count. No gradient scrim, no white-on-sage. */
@Composable
private fun RecipeChipCard(r: GroceryRecipe, recipe: Recipe?, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.width(200.dp).height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val art = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
        if (recipe != null) RecipeArt(recipe, art, monogramSize = 18.sp)
        else Box(art.background(SoftSagePale), contentAlignment = Alignment.Center) {
            Text(recipeMonogram(r.title), color = Ink, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (r.fromPlan) "Meal plan · ${r.itemCount} items" else "${r.itemCount} items",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1,
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, "Remove recipe items", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun GroceryRowItem(row: GroceryRow, showRecipe: Boolean, onToggle: () -> Unit, onDelete: () -> Unit, onHome: (() -> Unit)?) {
    val muted = row.checked || row.haveAtHome
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = row.checked, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = Sage, checkmarkColor = androidx.compose.ui.graphics.Color.White))
        Column(Modifier.weight(1f)) {
            Text(
                row.name, style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (row.checked) TextDecoration.LineThrough else null,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (showRecipe && row.recipes.isNotEmpty()) {
                Text(row.recipes.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (row.amount.isNotBlank()) Text(row.amount, style = MaterialTheme.typography.bodyMedium, color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else SageDeep)
        if (onHome != null) IconButton(onClick = onHome) {
            Icon(
                Icons.Outlined.Home, if (row.haveAtHome) "Need to buy" else "Have at home", Modifier.size(18.dp),
                tint = if (row.haveAtHome) SoftSageDeep else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, "Delete", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
