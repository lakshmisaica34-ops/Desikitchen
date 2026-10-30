package com.desikitchen.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.ui.theme.Ink
import com.desikitchen.app.ui.theme.Nunito
import com.desikitchen.app.ui.theme.SoftSageDeep
import com.desikitchen.app.ui.theme.SoftSagePale
import com.desikitchen.app.ui.theme.SoftSageFill
import com.desikitchen.app.util.formatMinutes

/** App-wide snackbar host provided by the root scaffold. */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

/**
 * Screen scaffold that lets the root scaffold own system-bar insets (avoids double padding).
 */
@Composable
fun DkScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

/**
 * Bundled v1 food photos (assets/food) used when a recipe has no photo of its own. Only matched
 * on clear dish keywords so we never show a misleading picture; anything else gets the monogram tile.
 */
fun bundledPhotoFor(title: String): String? {
    val t = title.lowercase()
    val name = when {
        listOf("paneer butter", "paneer makhani", "butter paneer", "shahi paneer", "kadai paneer", "paneer tikka masala").any { it in t } -> "paneer"
        listOf("biryani", "pulao", "pulav").any { it in t } -> "biryani"
        listOf("dosa", "idli", "uttapam").any { it in t } -> "dosa"
        listOf("dal", "daal", "sambar", "rasam").any { Regex("\\b$it\\b").containsMatchIn(t) } -> "dal"
        listOf("jamun", "ladoo", "laddu", "barfi", "halwa", "kheer", "gulab").any { it in t } -> "sweets"
        listOf("chai", "masala tea").any { it in t } -> "chai"
        else -> return null
    }
    return "file:///android_asset/food/$name.jpg"
}

/** 1–2 Ink initials from a recipe title ("Paneer Butter Masala" → "PB"). */
fun recipeMonogram(title: String): String {
    val words = title.trim().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "D"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/** True when the recipe carries its own photo (import thumb / user photo), not a bundled stand-in. */
fun Recipe.hasOwnPhoto(): Boolean = !imageUrl.isNullOrBlank()

/**
 * Photo cascade state for one recipe: own imageUrl (remote import thumb may expire) → bundled keyword photo →
 * none. A failed load drops to the next step, so callers can collapse a hero instead of showing a pale void.
 */
@Stable
class RecipePhotoState(private val own: String?, private val bundled: String?) {
    private var ownFailed by mutableStateOf(false)
    private var bundledFailed by mutableStateOf(false)
    val model: String?
        get() = when {
            own != null && !ownFailed -> own
            bundled != null && !bundledFailed -> bundled
            else -> null
        }
    val hasPhoto: Boolean get() = model != null
    fun onError(failed: String) {
        if (failed == own) ownFailed = true else if (failed == bundled) bundledFailed = true
    }
}

@Composable
fun rememberRecipePhoto(recipe: Recipe): RecipePhotoState {
    val own = recipe.imageUrl?.takeIf { it.isNotBlank() }
    return remember(own, recipe.title) { RecipePhotoState(own, bundledPhotoFor(recipe.title)) }
}

/**
 * Recipe artwork cascade: own imageUrl → bundled keyword photo → SoftSagePale tile with an Ink monogram.
 * No emoji, no gradients (Soft UI pass). The monogram is a quiet fallback, never a hero: [monogramSize] defaults
 * small and [compactFallback] caps it further for big slots. While a photo loads the tile is plain SoftSagePale.
 */
@Composable
fun RecipeArt(
    recipe: Recipe,
    modifier: Modifier = Modifier,
    monogramSize: TextUnit = 22.sp,
    compactFallback: Boolean = false,
    photo: RecipePhotoState = rememberRecipePhoto(recipe),
    alignment: Alignment = Alignment.Center,
) {
    val model = photo.model
    Box(modifier.background(SoftSagePale), contentAlignment = Alignment.Center) {
        if (model == null) {
            Text(
                recipeMonogram(recipe.title), color = Ink,
                fontSize = if (compactFallback && monogramSize.value > 28f) 28.sp else monogramSize,
                fontWeight = FontWeight.ExtraBold, fontFamily = Nunito,
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = recipe.title,
                contentScale = ContentScale.Crop,
                alignment = alignment,
                onError = { photo.onError(model) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun RecipeGridCard(
    recipe: Recipe,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** When set, the heart chip is always shown and toggles favourite (Saved grid); otherwise it's a badge on favourites only. */
    onToggleFavorite: (() -> Unit)? = null,
) {
    // Photo-first Canvas SoftCard (List: radius 20 / elev 4): 1:1 Crop cover is the loud plane, Ink bold title, muted meta.
    // Lifted paper on the SoftSagePale ground: Hero ambient/spot at List elev (deeper, not higher). 0.98 press, no elev jump.
    val interaction = remember { MutableInteractionSource() }
    val style = SoftCardStyle.List
    Column(
        modifier
            .softPressScale(interaction, SoftCardPressScale)
            .softShadow(style.elevation, RoundedCornerShape(style.radius), SoftAmbientHero, SoftSpotHero)
            .clip(RoundedCornerShape(style.radius))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
    ) {
        Box(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)) {
            RecipeArt(recipe, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)), monogramSize = 26.sp)
            if (recipe.isFavorite || onToggleFavorite != null) {
                val heart = rememberHeartScale(recipe.isFavorite)
                Surface(
                    shape = CircleShape,
                    color = SoftSageFill,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        .then(if (onToggleFavorite != null) Modifier.clip(CircleShape).clickable(onClick = onToggleFavorite) else Modifier),
                ) {
                    Icon(
                        if (recipe.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (onToggleFavorite == null) "Favourite"
                            else if (recipe.isFavorite) "Remove from favourites" else "Add to favourites",
                        tint = SoftSageDeep,
                        modifier = Modifier.padding(6.dp).size(16.dp).graphicsLayer { scaleX = heart; scaleY = heart },
                    )
                }
            }
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)) {
            Text(
                recipe.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface, // Ink
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(min = 44.dp),
            )
            Spacer(Modifier.height(4.dp))
            MetaLine(recipe)
        }
    }
}

@Composable
fun RecipeListRow(
    recipe: Recipe,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    // White card row, clear photo, Ink title, muted meta — no sage/mustard block behind the row.
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .softPressScale(interaction, SoftCardPressScale)
            .softShadow(SoftCardStyle.List.elevation, RoundedCornerShape(SoftCardStyle.List.radius), SoftCardStyle.List.ambient, SoftCardStyle.List.spot)
            .clip(RoundedCornerShape(SoftCardStyle.List.radius))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipeArt(recipe, Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)), monogramSize = 20.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                recipe.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            MetaLine(recipe)
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun MetaLine(recipe: Recipe) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Schedule, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(
            buildString {
                append(formatMinutes(recipe.totalMinutes))
                if (recipe.cuisine.isNotBlank()) append(" · ${recipe.cuisine}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Soft empty state: titleMedium Ink + ≤2 lines Muted + optional single action (one PillButton). No emoji. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center,
        )
        if (message.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Single text-field dialog used for create / rename flows. */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String = "",
    confirmText: String = "Save",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true)
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Searchable recipe picker dialog (used by meal planner and cookbooks). */
@Composable
fun RecipePickerDialog(
    title: String,
    recipes: List<Recipe>,
    onDismiss: () -> Unit,
    onPick: (Recipe) -> Unit,
    excludeIds: Set<Long> = emptySet(),
) {
    var query by remember { mutableStateOf("") }
    val filtered = recipes.filter { it.id !in excludeIds && it.title.contains(query, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text("Search recipes") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Text("No recipes match.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(filtered, key = { it.id }) { recipe ->
                            ListItem(
                                headlineContent = { Text(recipe.title) },
                                supportingContent = { Text(formatMinutes(recipe.totalMinutes) + " · serves ${recipe.servings}") },
                                leadingContent = { RecipeArt(recipe, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)), monogramSize = 14.sp) },
                                modifier = Modifier.clickable { onPick(recipe) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Multi-select recipe picker: tick several recipes and add them in one go (cookbooks). */
@Composable
fun RecipeMultiPickerDialog(
    title: String,
    recipes: List<Recipe>,
    onDismiss: () -> Unit,
    onAdd: (List<Recipe>) -> Unit,
    excludeIds: Set<Long> = emptySet(),
) {
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<Long>()) }
    val filtered = recipes.filter { it.id !in excludeIds && com.desikitchen.app.util.RecipeSearch.matches(it, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text("Search recipes or tags") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Text(
                        if (recipes.all { it.id in excludeIds }) "Every recipe is already here." else "No recipes match.",
                        modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(filtered, key = { it.id }) { recipe ->
                            val on = recipe.id in picked
                            ListItem(
                                headlineContent = { Text(recipe.title) },
                                supportingContent = { Text(formatMinutes(recipe.totalMinutes) + " · serves ${recipe.servings}") },
                                leadingContent = { RecipeArt(recipe, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)), monogramSize = 14.sp) },
                                trailingContent = { androidx.compose.material3.Checkbox(checked = on, onCheckedChange = { picked = if (on) picked - recipe.id else picked + recipe.id }) },
                                modifier = Modifier.clickable { picked = if (on) picked - recipe.id else picked + recipe.id },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = picked.isNotEmpty(), onClick = { onAdd(recipes.filter { it.id in picked }) }) {
                Text(if (picked.isEmpty()) "Add" else "Add ${picked.size}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
