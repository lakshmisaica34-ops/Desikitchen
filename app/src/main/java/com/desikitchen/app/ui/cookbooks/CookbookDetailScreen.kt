package com.desikitchen.app.ui.cookbooks

import com.desikitchen.app.ui.components.PillButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.desikitchen.app.util.RecipeSearch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desikitchen.app.ui.AppViewModelProvider
import com.desikitchen.app.ui.components.DkScaffold
import com.desikitchen.app.ui.components.DkTopBar
import com.desikitchen.app.ui.components.EmptyState
import com.desikitchen.app.ui.components.RecipeGridCard
import com.desikitchen.app.ui.components.RecipeMultiPickerDialog

@Composable
fun CookbookDetailScreen(
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    viewModel: CookbookDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var menu by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val cb = state.cookbook

    DkScaffold(
        topBar = {
            DkTopBar(cb?.cookbook?.name ?: "Cookbook", onBack = onBack, actions = {
                if (editing) TextButton(onClick = { editing = false }) { Text("Done") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Add recipes") }, onClick = { menu = false; showPicker = true })
                        DropdownMenuItem(text = { Text("Remove recipes") }, onClick = { menu = false; editing = true })
                        DropdownMenuItem(text = { Text("Rename cookbook") }, onClick = { menu = false; showRename = true })
                        DropdownMenuItem(text = { Text("Delete cookbook") }, onClick = { menu = false; showDelete = true })
                    }
                }
            })
        },
        floatingActionButton = {
            // Empty collection: the empty state's PillButton is the one primary, so the FAB hides.
            if (cb != null && cb.recipes.isNotEmpty()) {
                ExtendedFloatingActionButton(onClick = { showPicker = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add recipes") })
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
        if (cb != null && cb.recipes.size > 3) SearchField(query, { query = it }, "Search this cookbook")
        Box(Modifier.fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                cb == null -> EmptyState("Collection not found", "It may have been deleted.")
                cb.recipes.isEmpty() -> EmptyState(
                    "This collection is empty", "Add a few recipes to start.",
                    action = { PillButton("Add recipes", onClick = { showPicker = true }, fill = false) },
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(cb.recipes.filter { RecipeSearch.matches(it, query) }.sortedBy { it.title }, key = { it.id }) { r ->
                        Box {
                            RecipeGridCard(r, onClick = { if (!editing) onOpenRecipe(r.id) })
                            if (editing) {
                                FilledTonalIconButton(
                                    onClick = { viewModel.removeRecipe(r.id) },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(36.dp),
                                ) { Icon(Icons.Filled.Close, "Remove ${r.title}") }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showPicker && cb != null) {
        RecipeMultiPickerDialog(
            title = "Add to ${cb.cookbook.name}",
            recipes = state.allRecipes,
            excludeIds = cb.recipes.map { it.id }.toSet(),
            onDismiss = { showPicker = false },
            onAdd = { picked -> picked.forEach { viewModel.addRecipe(it.id) }; showPicker = false },
        )
    }
    if (showRename && cb != null) {
        com.desikitchen.app.ui.components.TextInputDialog(
            title = "Rename cookbook", label = "Cookbook title", initial = cb.cookbook.name,
            onDismiss = { showRename = false }, onConfirm = { viewModel.rename(it); showRename = false },
        )
    }
    if (showDelete && cb != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete “${cb.cookbook.name}”?") },
            text = { Text("The recipes stay in your collection; only the cookbook is removed.") },
            confirmButton = { TextButton(onClick = { showDelete = false; viewModel.delete(onBack) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
}
