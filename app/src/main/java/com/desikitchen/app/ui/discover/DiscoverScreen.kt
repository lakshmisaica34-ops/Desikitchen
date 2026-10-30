package com.desikitchen.app.ui.discover

import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.desikitchen.app.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.ui.AppViewModelProvider
import com.desikitchen.app.ui.collections.CuratedCollections
import com.desikitchen.app.ui.collections.PlusComingSoonDialog
import com.desikitchen.app.ui.collections.PlusLockBadge
import com.desikitchen.app.ui.components.RecipeArt
import com.desikitchen.app.ui.components.EmptyState
import com.desikitchen.app.ui.components.hasOwnPhoto
import com.desikitchen.app.ui.detail.sourceCredit
import com.desikitchen.app.ui.home.HomeViewModel
import com.desikitchen.app.ui.theme.Hairline
import com.desikitchen.app.ui.theme.Muted
import com.desikitchen.app.ui.theme.SoftPistachioPale
import com.desikitchen.app.util.ImportUrlValidator
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextAlign
import com.desikitchen.app.ui.theme.Ink
import com.desikitchen.app.ui.components.PillButton
import com.desikitchen.app.ui.components.SoftCard
import com.desikitchen.app.ui.components.SoftCardHero
import com.desikitchen.app.ui.components.SoftCardStyle
import com.desikitchen.app.ui.components.SoftField
import com.desikitchen.app.ui.components.SoftCanvas
import com.desikitchen.app.ui.components.SoftFieldBorder
import com.desikitchen.app.ui.components.SoftInk
import com.desikitchen.app.ui.components.SoftPistachio
import com.desikitchen.app.ui.components.SoftRetroGreen
import com.desikitchen.app.ui.components.SoftTangerine
import com.desikitchen.app.ui.components.SoftTangerineWash
import com.desikitchen.app.ui.components.SoftAmbientHero
import com.desikitchen.app.ui.components.SoftSpotHero
import com.desikitchen.app.ui.components.SoftElevList
import com.desikitchen.app.ui.components.SoftCardPressScale
import com.desikitchen.app.ui.components.softPressScale
import com.desikitchen.app.ui.components.softShadow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Discover home (Sai lock #12): tangerine-washed greeting + seamless import block → photo-led Recently Saved row (own photos only,
 * hidden when none) → locked curated collections row. Empty library: quieter paste + honest empty, no stock photos. No Popular Recipes / category bubbles.
 */
@Composable
fun DiscoverScreen(
    /** Starts a background import for the pasted link; returns an inline error, or null when it started. */
    onImportLink: (String) -> String?,
    onOpenRecipe: (Long) -> Unit,
    onSeeAllSaved: () -> Unit,
    onSeeCollections: () -> Unit,
    onSearch: () -> Unit = {},
    /** Free imports left this week from the existing [com.desikitchen.app.util.ImportQuota] state; null = don't show. */
    importsLeft: Int? = null,
    importsPerWeek: Int? = null,
    /** DesiKitchen Plus: show "unlimited" instead of the free count. */
    isPlus: Boolean = false,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var locked by rememberSaveable { mutableStateOf<String?>(null) }
    var showLocked by rememberSaveable { mutableStateOf(false) }

    val libraryEmptyNow = !state.loading && state.totalCount == 0
    // Sai mock: Canvas page ground with a barely-there tangerine wash (12% → transparent) behind header + import.
    LazyColumn(Modifier.fillMaxSize().background(SoftCanvas), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
          Column(Modifier.fillMaxWidth().background(SoftTangerineWash)) {
            // Mock: Muted time-of-day line + Ink headline; search is a quiet Retro Green icon so the import block is first.
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 24.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // Brand wordmark (ink on canvas), small so the paste hero stays the focus.
                    Image(
                        painterResource(R.drawable.desikitchen_logo), contentDescription = "DesiKitchen",
                        modifier = Modifier.height(18.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(timeOfDayGreeting(), style = MaterialTheme.typography.bodyMedium, color = Muted)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "What's the plan, ${state.greetingName}?",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = Ink,
                    )
                }
                IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Search your recipes", tint = SoftRetroGreen) }
            }
            // Seamless import block sits directly on the wash (no card chrome), per Sai's mock.
            PasteLinkCard(onImportLink, importsLeft, importsPerWeek, isPlus)
          }
        }
        // Photo-led "Recently saved": only recipes with their own imageUrl (import thumb / user photo) get a tile.
        val withPhotos = state.recent.filter { it.hasOwnPhoto() }
        when {
            state.loading -> Unit
            libraryEmptyNow -> item {
                // Aspirational, honest empty: no stock food photos, no demo recipes. Points back up at the paste card.
                EmptyState(
                    title = "Your kitchen starts with one link",
                    message = "Paste a recipe reel or video above. Its photo and steps land here.",
                )
            }
            withPhotos.isNotEmpty() -> {
                item { SectionRow("Recently saved", "See all", onSeeAllSaved, top = SectionGapAfterHero) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = ScreenPadH, vertical = TileShadowRoom), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(withPhotos, key = { it.id }) { r -> RecentPhotoTile(r) { onOpenRecipe(r.id) } }
                    }
                }
            }
            // Library but no photos yet: skip the photo row (no monogram wall); a quiet link keeps the library one tap away.
            else -> item { SectionRow("Your recipes", "See all", onSeeAllSaved, top = SectionGapAfterHero) }
        }
        // Sai lock #12 keeps the curated Plus row; with an empty library it's demoted to ≤2 tiles so paste stays first.
        val libraryEmpty = !state.loading && state.totalCount == 0
        // Paste hero directly followed by Collections (empty library / still loading) keeps the 16dp hero gap.
        item { SectionRow("Collections", "See all", onSeeCollections, top = if (state.loading || libraryEmptyNow) SectionGapAfterHero else SectionGap) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = ScreenPadH, vertical = TileShadowRoom), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(CuratedCollections.all.take(if (libraryEmpty) 2 else 6), key = { it.name }) { c ->
                    SoftCard(
                        Modifier.width(168.dp),
                        color = SoftPistachioPale, // mock: pale pistachio card so the Soft Pistachio Plus badge reads
                        contentPadding = PaddingValues(16.dp),
                        onClick = { locked = c.name; showLocked = true },
                    ) {
                        PlusLockBadge()
                        Spacer(Modifier.height(10.dp))
                        Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(c.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (showLocked) {
        val snack = com.desikitchen.app.ui.components.LocalSnackbarHostState.current
        val plusScope = androidx.compose.runtime.rememberCoroutineScope()
        PlusComingSoonDialog(
            locked, onDismiss = { showLocked = false },
            onGetPlus = { showLocked = false; plusScope.launch { snack.showSnackbar("Plus isn't available to buy yet.") } },
        )
    }
}

// Discover rhythm (Design Lab soft-depth): screen pad H 20; paste hero → 16 → first section; section → 28 → next title;
// title → content 12 (row bottom 4 + LazyRow top 8, which doubles as shadow room); card gap 12.
private val ScreenPadH = 20.dp
private val SectionGapAfterHero = 16.dp
private val SectionGap = 28.dp
private val TileShadowRoom = 8.dp

@Composable
private fun SectionRow(title: String, action: String?, onAction: () -> Unit, top: androidx.compose.ui.unit.Dp = SectionGap) {
    Row(Modifier.fillMaxWidth().padding(start = ScreenPadH, end = 8.dp, top = top, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = SoftRetroGreen, fontWeight = FontWeight.Bold) }
    }
}

/**
 * Sai mock seamless import block (no card chrome, sits on the tangerine wash): "Import a recipe" headline → rounded
 * Canvas paste bar (light gray border, link icon left, tangerine clipboard right) → big full-width Retro Green "Import"
 * pill with white label → quiet gray quota / Plus line.
 */
@Composable
private fun PasteLinkCard(onImportLink: (String) -> String?, importsLeft: Int?, importsPerWeek: Int?, isPlus: Boolean = false) =
    Column(Modifier.fillMaxWidth().padding(horizontal = ScreenPadH)) {
    val clipboard = LocalClipboardManager.current
    var link by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    // Background import: on start the field clears and the status pill takes over; on error the link stays put.
    fun submit() {
        val problem = onImportLink(link.trim())
        error = problem
        if (problem == null) link = ""
    }
    val barShape = RoundedCornerShape(50)
    Text("Import a recipe", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = SoftInk)
    Spacer(Modifier.height(12.dp))
    SoftField(
        value = link, onValueChange = { link = it; error = null },
        placeholder = "Paste a YouTube or Instagram link",
        modifier = Modifier.fillMaxWidth().border(1.dp, if (error != null) MaterialTheme.colorScheme.error else SoftFieldBorder, barShape),
        shape = barShape,
        container = SoftCanvas,
        isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { submit() }),
        leadingIcon = { Icon(Icons.Outlined.Link, null, tint = Muted, modifier = Modifier.padding(start = 6.dp).size(20.dp)) },
        trailingIcon = {
            IconButton(onClick = {
                val text = clipboard.getText()?.text?.trim().orEmpty()
                if (text.isEmpty()) error = "Nothing to paste. Copy a YouTube or Instagram link first."
                else {
                    // Multi-link paste: keep every link (space-separated in this one-line field); one link as before.
                    val all = ImportUrlValidator.extractAllUrls(text)
                    link = if (all.size > 1) all.joinToString(" ") else ImportUrlValidator.extractUrl(text) ?: text
                    error = null
                }
            }) { Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste link from clipboard", tint = SoftTangerine) }
        },
    )
    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(12.dp))
    // Seamless primary: big full-width Retro Green pill, white label (Theme RetroGreen via Soft bridge).
    PillButton(
        "Import", onClick = { submit() }, minHeight = 56.dp,
        containerColor = SoftRetroGreen, contentColor = SoftCanvas,
    )
    if (isPlus || (importsLeft != null && importsPerWeek != null)) {
        Text(
            if (isPlus) com.desikitchen.app.util.ImportQuota.PLUS_LINE else "$importsLeft of $importsPerWeek imports left this week",
            style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
    Spacer(Modifier.height(8.dp))
}

private fun timeOfDayGreeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

/**
 * Photo-first recent tile (Design Lab): 140×140 Crop photo at 20dp radius, labelLarge Ink title, 1 line. No card chrome;
 * the photo itself floats (List elev + Hero ambient so it reads as lifted on the Canvas ground). 0.98 press, no elev jump.
 */
@Composable
private fun RecentPhotoTile(recipe: Recipe, onClick: () -> Unit) {
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val shape = RoundedCornerShape(SoftCardStyle.List.radius)
    Column(
        // No clip on the column (it would cut the photo's shadow); the ripple is clipped to the photo instead.
        Modifier.width(140.dp).softPressScale(interaction, SoftCardPressScale)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = "Open ${recipe.title}", onClick = onClick),
    ) {
        RecipeArt(
            recipe,
            Modifier.size(140.dp).softShadow(SoftElevList, shape, SoftAmbientHero, SoftSpotHero).clip(shape)
                .indication(interaction, androidx.compose.foundation.LocalIndication.current),
            monogramSize = 22.sp,
        )
        Text(
            recipe.title, style = MaterialTheme.typography.labelLarge, color = Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        )
    }
}
