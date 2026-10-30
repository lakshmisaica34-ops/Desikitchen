package com.desikitchen.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desikitchen.app.ui.AppViewModelProvider
import com.desikitchen.app.ui.components.EmptyState
import com.desikitchen.app.ui.components.RecipeGridCard
import com.desikitchen.app.ui.components.SectionHeader

/**
 * All-recipes grid with tag filters and a pinned row. [query] comes from the shared search
 * field in the Cookbooks tab header.
 */
@Composable
fun RecipesPane(
    query: String,
    onOpenRecipe: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(query) { viewModel.setQuery(query) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.availableTags.take(16)) { tag ->
                    FilterChip(
                        selected = tag in state.selectedTags,
                        onClick = { viewModel.toggleTag(tag) },
                        label = { Text(tag) },
                    )
                }
            }
        }
        if (state.pinned.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Pinned", Modifier.padding(start = 0.dp)) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.pinned, key = { "pin-${it.id}" }) { r ->
                        RecipeGridCard(r, onClick = { onOpenRecipe(r.id) }, modifier = Modifier.width(170.dp))
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth()) {
                SectionHeader(
                    if (state.selectedTags.isEmpty() && query.isBlank()) "All recipes (${state.totalCount})"
                    else "${state.recipes.size} matching",
                    Modifier.weight(1f),
                )
                if (state.selectedTags.isNotEmpty()) TextButton(onClick = viewModel::clearFilters) { Text("Clear filters") }
            }
        }
        if (!state.loading && state.recipes.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth()) {
                    EmptyState("No recipes found", "Try a different search or clear the filters.")
                }
            }
        }
        items(state.recipes, key = { it.id }) { r ->
            RecipeGridCard(r, onClick = { onOpenRecipe(r.id) })
        }
    }
}

@Composable
fun AllRecipesScreen(onBack: () -> Unit, onOpenRecipe: (Long) -> Unit) {
    var q by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    com.desikitchen.app.ui.components.DkScaffold(
        topBar = { com.desikitchen.app.ui.components.DkTopBar("All recipes", onBack = onBack) },
    ) { padding ->
        androidx.compose.foundation.layout.Column(Modifier.padding(padding)) {
            com.desikitchen.app.ui.cookbooks.SearchField(q, { q = it }, "Search titles, tags or ingredients")
            RecipesPane(query = q, onOpenRecipe = onOpenRecipe)
        }
    }
}

