package com.desikitchen.app.ui.saved

import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import com.desikitchen.app.ui.theme.Ink
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desikitchen.app.data.model.CookbookWithRecipes
import com.desikitchen.app.data.model.MealPlanWithRecipe
import com.desikitchen.app.ui.AppViewModelProvider
import com.desikitchen.app.ui.components.EmptyState
import com.desikitchen.app.ui.components.RecipeGridCard
import com.desikitchen.app.ui.components.hasOwnPhoto
import com.desikitchen.app.ui.components.RecipeListRow
import com.desikitchen.app.ui.components.SoftCard
import com.desikitchen.app.ui.components.SoftCardStyle
import com.desikitchen.app.ui.components.PillButton
import com.desikitchen.app.ui.components.SoftField
import com.desikitchen.app.ui.components.SoftChip
import com.desikitchen.app.ui.components.TextInputDialog
import com.desikitchen.app.ui.theme.Hairline
import com.desikitchen.app.ui.theme.MustardDeep
import com.desikitchen.app.ui.theme.MustardCream
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Saved tab (Sai locks #5, #13): one personal library replacing Favourites / All recipes / Cookbooks.
 * Sections: Meal plan (next 7 days, opens the planner) · Grocery entry · Collections (text-first, counts) · Recipes.
 */
@Composable
fun SavedScreen(
    onOpenRecipe: (Long) -> Unit,
    onOpenCollection: (Long) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenGrocery: () -> Unit,
    onImport: () -> Unit,
    viewModel: SavedViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<CookbookWithRecipes?>(null) }
    var deleting by remember { mutableStateOf<CookbookWithRecipes?>(null) }

    // Cooler page ground: SoftSagePale under the Canvas SoftCards / photo grid tiles so their lift reads.
    LazyColumn(Modifier.fillMaxSize().background(com.desikitchen.app.ui.components.SoftPageGround), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                "Saved", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
            )
        }

        // Single empty state: an empty library shows ONE message + ONE PillButton, not three stacked "nothing yet" blocks.
        if (!s.loading && s.totalCount == 0 && s.collections.isEmpty() && s.upcoming.isEmpty()) {
            item {
                EmptyState(
                    title = "Your library is empty",
                    message = "Paste a YouTube or Instagram link on Discover to save your first recipe.",
                    action = { PillButton("Paste a link", onClick = onImport, fill = false) },
                )
            }
            return@LazyColumn
        }

        // ---- Meal plan (inside Saved, not its own tab) ----
        item { SectionTitle("Meal plan", action = "Open planner", onAction = onOpenPlan) }
        item { MealPlanCard(s.upcoming, onOpenPlan, onOpenRecipe) }
        item {
            EntryRow(Icons.Outlined.ShoppingCart, "Grocery list", "Built from your plan or any recipe", onOpenGrocery)
        }

        // ---- Collections (text-first) ----
        item { SectionTitle("Collections", action = "New", onAction = { creating = true }) }
        if (s.collections.isEmpty()) {
            item {
                Text(
                    "No collections yet.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        } else {
            items(s.collections, key = { "c${it.cookbook.id}" }) { c ->
                CollectionRow(c, onClick = { onOpenCollection(c.cookbook.id) }, onRename = { renaming = c }, onDelete = { deleting = c })
            }
        }

        // ---- Library ----
        item { SectionTitle("Recipes (${s.totalCount})") }
        if (s.totalCount > 0) {
            item {
                SoftField(
                    value = s.query, onValueChange = viewModel::setQuery,
                    placeholder = "Search by dish or ingredient",
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        if (s.query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Outlined.Clear, "Clear search") }
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SavedFilter.entries.forEach { f ->
                        val label = if (f == SavedFilter.FAVORITES) "${f.label} (${s.favoriteCount})" else f.label
                        SoftChip(selected = s.filter == f, onClick = { viewModel.setFilter(f) }, label = label)
                    }
                }
            }
        }
        when {
            s.loading -> Unit
            s.totalCount == 0 -> item {
                EmptyState(
                    title = "No recipes yet",
                    message = "Imported recipes land here.",
                    action = { PillButton("Paste a link", onClick = onImport, fill = false) },
                )
            }
            s.recipes.isEmpty() -> item {
                EmptyState(
                    title = if (s.filter == SavedFilter.FAVORITES && s.query.isBlank()) "No favourites yet" else "No matches",
                    message = if (s.filter == SavedFilter.FAVORITES && s.query.isBlank()) "Tap the heart on a recipe to keep it here." else "Try another dish or ingredient.",
                )
            }
            // Photo-first 2-up grid of RecipeGridCards (1:1 Crop cover); heart chip toggles favourite in place.
            else -> items(s.recipes.chunked(2), key = { row -> "r" + row.joinToString("-") { it.id.toString() } }) { row ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { r ->
                        RecipeGridCard(
                            r, onClick = { onOpenRecipe(r.id) }, modifier = Modifier.weight(1f),
                            onToggleFavorite = { viewModel.toggleFavorite(r) },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    if (creating) TextInputDialog(
        title = "New collection", label = "Name", confirmText = "Create",
        onDismiss = { creating = false }, onConfirm = { viewModel.createCollection(it); creating = false },
    )
    renaming?.let { c ->
        TextInputDialog(
            title = "Rename collection", label = "Name", initial = c.cookbook.name,
            onDismiss = { renaming = null }, onConfirm = { viewModel.renameCollection(c.cookbook.id, it); renaming = null },
        )
    }
    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${c.cookbook.name}”?") },
            text = { Text("The recipes stay in your library; only the collection is removed.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteCollection(c.cookbook.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 22.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) {
            if (action == "New") { Icon(Icons.Outlined.Add, null, tint = MustardDeep); Spacer(Modifier.width(4.dp)) }
            Text(action, color = Ink, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EntryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    SoftCard(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        contentPadding = PaddingValues(0.dp),
        onClick = onClick,
    ) {
    Row(
        Modifier.fillMaxWidth().padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MustardDeep)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    }
}

@Composable
private fun MealPlanCard(upcoming: List<MealPlanWithRecipe>, onOpenPlan: () -> Unit, onOpenRecipe: (Long) -> Unit) {
    SoftCard(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        if (upcoming.isEmpty()) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpenPlan).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = MustardDeep)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Nothing planned this week", style = MaterialTheme.typography.titleSmall)
                    Text("Plan meals from your saved recipes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val today = LocalDate.now().toEpochDay()
            upcoming.take(5).forEachIndexed { i, m ->
                if (i > 0) HorizontalDivider(color = Hairline.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                val day = LocalDate.ofEpochDay(m.entry.epochDay)
                val dayLabel = when (m.entry.epochDay - today) {
                    0L -> "Today"; 1L -> "Tomorrow"
                    else -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                }
                Row(
                    Modifier.fillMaxWidth().clickable { m.recipe?.let { onOpenRecipe(it.id) } }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$dayLabel · ${m.entry.slot.label}", style = MaterialTheme.typography.labelMedium, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(130.dp))
                    Text(m.recipe?.title.orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
            if (upcoming.size > 5) {
                Text(
                    "+${upcoming.size - 5} more this week", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CollectionRow(c: CookbookWithRecipes, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    SoftCard(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        contentPadding = PaddingValues(0.dp),
        onClick = onClick,
    ) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Photo-first: the first member with a real photo (own imageUrl, then honest bundled); else the quiet folder tile.
        val cover = c.recipes.firstOrNull { it.hasOwnPhoto() }
            ?: c.recipes.firstOrNull { com.desikitchen.app.ui.components.bundledPhotoFor(it.title) != null }
        if (cover != null) {
            com.desikitchen.app.ui.components.RecipeArt(cover, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)), monogramSize = 16.sp)
        } else {
            Box(Modifier.size(52.dp).background(MustardCream, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.FolderOpen, null, tint = MustardDeep)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.cookbook.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val n = c.recipes.size
            Text("$n recipe${if (n == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Outlined.MoreVert, "Collection options")
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
    }
    }
}
