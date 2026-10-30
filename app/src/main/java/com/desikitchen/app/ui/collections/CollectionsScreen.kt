package com.desikitchen.app.ui.collections

import com.desikitchen.app.ui.theme.Ink
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.desikitchen.app.ui.components.SoftCard
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desikitchen.app.ui.AppViewModelProvider
import com.desikitchen.app.ui.components.EmptyState
import com.desikitchen.app.ui.components.LocalSnackbarHostState
import com.desikitchen.app.ui.components.PillButton
import com.desikitchen.app.ui.components.PlusSheet
import com.desikitchen.app.ui.components.RecipeArt
import com.desikitchen.app.ui.components.bundledPhotoFor
import com.desikitchen.app.ui.components.hasOwnPhoto
import com.desikitchen.app.ui.components.SoftCardStyle
import com.desikitchen.app.ui.components.TextInputDialog
import com.desikitchen.app.ui.components.recipeMonogram
import com.desikitchen.app.ui.cookbooks.CookbooksViewModel
import com.desikitchen.app.ui.theme.SoftSagePale
import kotlinx.coroutines.launch
import com.desikitchen.app.ui.theme.SoftSageDeep
import com.desikitchen.app.ui.theme.SoftSageFill
import com.desikitchen.app.ui.theme.SoftPistachio
import com.desikitchen.app.ui.theme.RetroGreenDeep

/** A DesiKitchen-curated catalogue collection. Phase 1: names only (stub), locked for free users. */
data class CuratedCollection(val name: String, val blurb: String)

object CuratedCollections {
    /** Placeholder catalogue names — no recipes ship with them yet (curated content is a later phase). */
    val all = listOf(
        CuratedCollection("Weeknight dal & sabzi", "Quick everyday plates"),
        CuratedCollection("South Indian breakfasts", "Idli, dosa, upma and more"),
        CuratedCollection("Festive sweets", "Diwali, Eid and Onam favourites"),
        CuratedCollection("High-protein desi", "Paneer, chana, egg and chicken"),
        CuratedCollection("One-pot rice", "Biryani, pulao and khichdi"),
        CuratedCollection("Street food at home", "Chaat, pav bhaji, rolls"),
        CuratedCollection("Tiffin box ideas", "Packs well, tastes good cold"),
        CuratedCollection("Chai-time snacks", "Pakoras, mathri, namkeen"),
    )
}

/** "Plus" lock pill: Soft Pistachio chip + Retro Green Deep lock (8:1), Ink label; reads on white and pistachio-pale cards. */
@Composable
fun PlusLockBadge(modifier: Modifier = Modifier) {
    Row(
        modifier.background(SoftPistachio, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(12.dp), tint = RetroGreenDeep)
        Spacer(Modifier.width(4.dp))
        Text("Plus", style = MaterialTheme.typography.labelSmall, color = Ink, fontWeight = FontWeight.Bold)
    }
}

/**
 * Plus upsell for a locked curated collection: the Soft paywall sheet (no OK-dialog). No billing in Phase 1, so
 * "Get Plus" hands back to the caller ([onGetPlus]) which explains billing isn't live yet.
 */
@Composable
fun PlusComingSoonDialog(name: String?, onDismiss: () -> Unit, onGetPlus: () -> Unit = onDismiss) {
    PlusSheet(
        title = name ?: "DesiKitchen Plus",
        message = "Curated collections come with DesiKitchen Plus. Your own recipes and collections stay free.",
        onGetPlus = { onGetPlus() },
        onDismiss = onDismiss,
    )
}

/**
 * Collections tab (Sai lock #14 + Design Lab #10): a 2-column grid. "Your collections" (2×2 photo mosaic, name + count)
 * with ONE PillButton "New collection", then the curated Plus catalogue (locked tiles → Soft Plus sheet).
 * No meal plan / grocery here. Tiles are SoftCards at 24dp / 4dp (Collections-only preset).
 */
@Composable
fun CollectionsScreen(
    onOpenCollection: (Long) -> Unit = {},
    viewModel: CookbooksViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val mine by viewModel.cookbookList.collectAsStateWithLifecycle()
    var locked by rememberSaveable { mutableStateOf<String?>(null) }
    var show by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        // Cooler page ground under the Canvas CollectionTiles (24dp / List depth).
        modifier = Modifier.fillMaxSize().background(com.desikitchen.app.ui.components.SoftPageGround),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 4.dp)) {
                Text("Collections", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold))
                Spacer(Modifier.height(4.dp))
                Text(
                    "Group your recipes, or browse hand-picked desi sets.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val list = mine
        if (list != null) {
            if (list.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "No collections yet",
                        message = "Group saved recipes into sets like weeknight dinners.",
                        action = { PillButton("New collection", onClick = { creating = true }, fill = false) },
                    )
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) { GridSection("Your collections") }
                items(list, key = { "u${it.cookbook.id}" }) { c ->
                    CollectionTile(
                        name = c.cookbook.name,
                        meta = "${c.recipes.size} recipe${if (c.recipes.size == 1) "" else "s"}",
                        onClick = { onOpenCollection(c.cookbook.id) },
                    ) { Mosaic(c.recipes, c.cookbook.name) }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                        PillButton("New collection", onClick = { creating = true }, fill = false)
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { GridSection("From DesiKitchen Plus") }
        items(CuratedCollections.all, key = { "p${it.name}" }) { c ->
            CollectionTile(name = c.name, meta = c.blurb, onClick = { locked = c.name; show = true }, onClickLabel = "Unlock with Plus") {
                Box(Modifier.fillMaxSize().background(SoftSagePale), contentAlignment = Alignment.Center) {
                    Text(recipeMonogram(c.name), color = Ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                }
                PlusLockBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
    }
    if (show) PlusComingSoonDialog(
        locked, onDismiss = { show = false },
        onGetPlus = {
            show = false
            scope.launch { snackbar.showSnackbar("Plus isn't available to buy yet.") }
        },
    )
    if (creating) TextInputDialog(
        title = "New collection", label = "Name", confirmText = "Create",
        onDismiss = { creating = false },
        onConfirm = { name -> if (name.isNotBlank()) viewModel.create(name.trim(), ""); creating = false },
    )
}

@Composable
private fun GridSection(text: String) = Text(
    text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 2.dp),
)

/** Collections-only SoftCard tile (24dp / 4dp): square art on top, Ink name + Muted meta below. */
@Composable
private fun CollectionTile(
    name: String,
    meta: String,
    onClick: () -> Unit,
    onClickLabel: String? = null,
    art: @Composable BoxScope.() -> Unit,
) {
    SoftCard(
        Modifier.fillMaxWidth(), style = SoftCardStyle.CollectionTile,
        contentPadding = PaddingValues(8.dp), onClick = onClick, onClickLabel = onClickLabel,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)), content = art)
        Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 6.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 40.dp))
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * 2×2 photo mosaic: up to 4 members that have a real photo (own imageUrl first, then honest bundled), Crop per cell;
 * missing cells are plain SoftSagePale. No member photos at all → one full SoftSagePale block with the collection's
 * Ink monogram (no wall of per-recipe monograms).
 */
@Composable
private fun Mosaic(members: List<com.desikitchen.app.data.model.Recipe>, name: String) {
    val covers = members
        .filter { it.hasOwnPhoto() || bundledPhotoFor(it.title) != null }
        .sortedByDescending { it.hasOwnPhoto() }
        .take(4)
    if (covers.isEmpty()) {
        Box(Modifier.fillMaxSize().background(SoftSagePale), contentAlignment = Alignment.Center) {
            Text(recipeMonogram(name), color = Ink, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        }
        return
    }
    if (covers.size == 1) {
        // One photo: let it fill the cover rather than a lonely quarter + three empty cells.
        RecipeArt(covers[0], Modifier.fillMaxSize(), monogramSize = 22.sp)
        return
    }
    Column(Modifier.fillMaxSize().background(SoftSagePale), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        (0 until 2).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (0 until 2).forEach { col ->
                    val r = covers.getOrNull(row * 2 + col)
                    if (r != null) RecipeArt(r, Modifier.weight(1f).fillMaxSize(), monogramSize = 14.sp)
                    else Box(Modifier.weight(1f).fillMaxSize().background(SoftSagePale))
                }
            }
        }
    }
}
