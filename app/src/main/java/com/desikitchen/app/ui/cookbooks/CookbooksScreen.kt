package com.desikitchen.app.ui.cookbooks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desikitchen.app.data.model.CookbookWithRecipes
import com.desikitchen.app.ui.AppViewModelProvider
import com.desikitchen.app.ui.components.DkScaffold
import com.desikitchen.app.ui.components.DkTopBar
import com.desikitchen.app.ui.components.EmptyState
import com.desikitchen.app.ui.components.RecipeArt
import com.desikitchen.app.ui.components.recipeMonogram
import com.desikitchen.app.ui.components.PillButton
import com.desikitchen.app.ui.theme.Ink
import com.desikitchen.app.ui.theme.SoftSagePale
import com.desikitchen.app.ui.home.RecipesPane


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookbooksScreen(
    onOpenCookbook: (Long) -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: CookbooksViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val cookbooks by viewModel.cookbookList.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showCreate by rememberSaveable { mutableStateOf(false) }

    DkScaffold(
        topBar = {
            DkTopBar("Cookbooks", onBack = onBack, actions = {
                TextButton(onClick = { showCreate = true }) {
                    Icon(Icons.Filled.Add, null)
                    Text("New Cookbook")
                }
            })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                listOf("Cookbooks", "Recipes").forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = tab == i,
                        onClick = { tab = i },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }
            SearchField(query, { query = it }, if (tab == 0) "Search cookbooks" else "Search titles, tags or ingredients")
            if (tab == 1) {
                RecipesPane(query = query, onOpenRecipe = onOpenRecipe)
            } else {
                val list = cookbooks
                when {
                    list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    list.isEmpty() -> EmptyState(
                        "No cookbooks yet", "Group recipes you cook together.",
                        action = { PillButton("New cookbook", onClick = { showCreate = true }, fill = false) },
                    )
                    else -> {
                        val filtered = list.filter { it.cookbook.name.contains(query.trim(), true) || it.recipes.any { r -> r.title.contains(query.trim(), true) } }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(160.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(filtered, key = { it.cookbook.id }) { cb ->
                                CookbookCard(cb, onClick = { onOpenCookbook(cb.cookbook.id) })
                            }
                            item {
                                Card(
                                    onClick = { showCreate = true },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    modifier = Modifier.aspectRatio(0.95f),
                                ) {
                                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                                        Text("New Cookbook", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        NewCookbookDialog(onDismiss = { showCreate = false }, onCreate = { name, emoji ->
            viewModel.create(name, emoji); showCreate = false
        })
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, "Clear") }
        },
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = CircleShape,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun CookbookCard(cb: CookbookWithRecipes, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        // 2x2 mosaic of the first recipes (photo → bundled → SoftSagePale monogram); no emoji / gradients.
        Box(Modifier.fillMaxWidth().aspectRatio(1.25f).background(SoftSagePale), contentAlignment = Alignment.Center) {
            val covers = cb.recipes.sortedByDescending { !it.imageUrl.isNullOrBlank() }.take(4)
            if (covers.size >= 2) {
                Column(Modifier.fillMaxSize()) {
                    covers.chunked(2).forEach { row ->
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            row.forEach { r -> RecipeArt(r, Modifier.weight(1f).fillMaxSize(), monogramSize = 18.sp) }
                        }
                    }
                }
            } else {
                Text(recipeMonogram(cb.cookbook.name), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(cb.cookbook.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                "${cb.recipes.size} recipe${if (cb.recipes.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun NewCookbookDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
    title: String = "New Cookbook",
    initialName: String = "",
    confirm: String = "Create",
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Cookbook title") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim(), "") }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
