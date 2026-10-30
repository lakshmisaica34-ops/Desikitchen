package com.desikitchen.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desikitchen.app.ui.AppViewModelProvider
import com.desikitchen.app.ui.components.DkScaffold
import com.desikitchen.app.ui.components.DkTopBar
import com.desikitchen.app.ui.components.RecipeListRow
import com.desikitchen.app.ui.components.SoftField
import com.desikitchen.app.ui.components.SoftOutlinePill
import com.desikitchen.app.ui.detail.sourceCredit
import com.desikitchen.app.ui.theme.MustardDeep
import com.desikitchen.app.util.SearchState

/** Dedicated Search Results screen (Phase 1): initial hint, loading, results, no results and error states. */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onImport: () -> Unit,
    viewModel: SearchViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (query.isBlank()) runCatching { focus.requestFocus() } }

    DkScaffold(topBar = { DkTopBar("Search", onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SoftField(
                value = query, onValueChange = viewModel::setQuery,
                placeholder = "Search by dish or ingredient",
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Filled.Close, "Clear search") }
                },
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).focusRequester(focus),
            )
            when (val s = state) {
                SearchState.Hint -> Message(
                    "Search your recipes",
                    "Dish, cuisine or ingredient.",
                )
                SearchState.Loading -> Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = MustardDeep, strokeWidth = 3.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("Searching…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is SearchState.NoResults -> Message(
                    "No results for \"${s.query}\"",
                    "Try fewer words.",
                ) { Spacer(Modifier.height(10.dp)); SoftOutlinePill("Import a recipe", onClick = onImport) }
                is SearchState.Error -> Message("Something went wrong", s.message) {
                    Spacer(Modifier.height(10.dp)); SoftOutlinePill("Try again", onClick = viewModel::retry)
                }
                is SearchState.Results -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            "${s.hits.size} ${if (s.hits.size == 1) "recipe" else "recipes"}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    items(s.hits, key = { it.recipe.id }) { hit ->
                        Column {
                            RecipeListRow(hit.recipe, onClick = { onOpenRecipe(hit.recipe.id) })
                            val sub = when {
                                hit.matchedIngredients.isNotEmpty() -> "Has " + hit.matchedIngredients.take(3).joinToString(", ")
                                else -> sourceCredit(hit.recipe)
                            }
                            sub?.let {
                                Text(
                                    it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 116.dp, end = 16.dp, bottom = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}
