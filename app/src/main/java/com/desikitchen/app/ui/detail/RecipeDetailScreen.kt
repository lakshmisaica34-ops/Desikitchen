package com.desikitchen.app.ui.detail

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.PlayArrow
import com.desikitchen.app.ui.theme.SoftSagePale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.outlined.ShoppingCart
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desikitchen.app.data.model.MealSlot
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.model.SourcePlatform
import com.desikitchen.app.ui.AppViewModelProvider
import com.desikitchen.app.ui.components.DkScaffold
import com.desikitchen.app.ui.components.EmptyState
import com.desikitchen.app.ui.components.KeepScreenOn
import com.desikitchen.app.ui.components.LocalSnackbarHostState
import com.desikitchen.app.ui.components.NumberBadge
import com.desikitchen.app.ui.components.RecipeArt
import com.desikitchen.app.ui.components.PillButton
import com.desikitchen.app.ui.components.SoftChip
import com.desikitchen.app.ui.components.SoftSheet
import com.desikitchen.app.ui.components.SoftPageGround
import com.desikitchen.app.ui.components.SoftAmbientFloat
import com.desikitchen.app.ui.components.SoftSpotFloat
import com.desikitchen.app.ui.components.SoftElevFloat
import com.desikitchen.app.ui.components.softShadow
import com.desikitchen.app.ui.components.SoftCardStyle
import com.desikitchen.app.ui.components.recipeMonogram
import com.desikitchen.app.ui.theme.SoftSageFill
import com.desikitchen.app.ui.theme.Nunito
import androidx.compose.ui.platform.LocalConfiguration
import com.desikitchen.app.ui.theme.Hairline
import com.desikitchen.app.ui.theme.Ink
import com.desikitchen.app.ui.theme.SoftSage
import com.desikitchen.app.ui.theme.SoftSageDeep
import com.desikitchen.app.ui.creator.CreatorAvatar
import com.desikitchen.app.ui.creator.CreatorLinks
import com.desikitchen.app.util.RecipeImageStore
import com.desikitchen.app.util.RecipePdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Phase 1 recipe detail (Sai lock #4): photo, title, source credit, ingredients, steps and "Open original",
 * laid out as a ReciMe-style white sheet over a tall photo with a sticky "Add to grocery" / "Start cooking" bar.
 * No stats row (time / servings / calories / diet), no servings stepper, no ingredient checkboxes, no nutrition. Edit / favourite / collections / share / delete stay; cook mode and the rest live in the
 * overflow menu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onCook: (Long) -> Unit,
    onOpenGroceries: () -> Unit = {},
    onOpenCreator: (String) -> Unit = {},
    viewModel: RecipeDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.messageFlow.collect { launch { snackbar.showSnackbar(it) } } }
    KeepScreenOn(state.keepScreenOn)

    var menu by remember { mutableStateOf(false) }
    var showPlan by rememberSaveable { mutableStateOf(false) }
    var showCollections by rememberSaveable { mutableStateOf(false) }
    var showAsk by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showPhoto by rememberSaveable { mutableStateOf(false) }
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    val details = state.details

    fun share() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, details?.recipe?.title)
            putExtra(Intent.EXTRA_TEXT, viewModel.exportText())
        }
        context.startActivity(Intent.createChooser(send, "Share recipe"))
    }

    fun openOriginal(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { viewModel.postMessage("No app can open that link") }
    }

    // VM one-shot actions (openOriginal / openCreatorProfile / navigateToCreator) → intent or nav.
    LaunchedEffect(Unit) {
        viewModel.actionFlow.collect { a ->
            when (a) {
                is RecipeDetailViewModel.Action.OpenUrl -> openOriginal(a.url)
                is RecipeDetailViewModel.Action.OpenCreator -> onOpenCreator(a.handle)
            }
        }
    }

    fun sharePdf() {
        val r = details?.recipe ?: return
        val f = runCatching {
            val photo = r.imageUrl?.takeIf { it.startsWith("file:") || it.startsWith("content:") }?.let(Uri::parse)
            RecipePdf.write(viewModel.pdfBlocks(), java.io.File(RecipePdf.exportDir(context), RecipePdf.fileName(r.title)), photo, context)
        }.onFailure { viewModel.postMessage("Couldn't create the PDF: ${it.message}") }.getOrNull() ?: return
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, r.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Export recipe PDF"))
    }

    fun importPhoto(uri: Uri) {
        scope.launch {
            val stored = withContext(Dispatchers.IO) { runCatching { RecipeImageStore.importImage(context, uri) }.getOrNull() }
            if (stored != null) viewModel.setImage(stored) else viewModel.postMessage("Couldn't read that photo")
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(::importPhoto) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pendingCapture?.let(Uri::parse)
        pendingCapture = null
        if (ok && u != null) importPhoto(u)
    }

    DkScaffold { padding ->
        // Detail root ground = SoftSagePale; the floating photo card, SoftSheet and floating CTA card stay Canvas on top of it.
        Box(Modifier.padding(padding).fillMaxSize().background(SoftPageGround)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = SoftSage)
                details == null -> EmptyState(title = "Recipe not found", message = "It may have been deleted.")
                else -> {
                    val r = details.recipe
                    val steps = details.sortedSteps
                    val sheetBg = MaterialTheme.colorScheme.surface
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        // Stack bottom clears the floating CTA card (FloatBarHeight + 16dp gap) + 24dp air. The root Scaffold
                        // already pads + consumes the system nav inset, so it is NOT added again here (B2 fix).
                        contentPadding = PaddingValues(bottom = FloatBarHeight + FloatBarGap + SheetBottomAir),
                    ) {
                        item(key = "hero") {
                            // Soft depth B (Choco-style): the hero is an inset floating card on the SoftSagePale ground — 16dp side
                            // margins, 24dp radius, Hero elev 8 — not full-bleed. Photo-led A content rules stay: imageUrl → bundled
                            // cascade, 4:3, top-biased Crop. No photo (or a dead CDN thumb) = compact 120dp Canvas card with a
                            // SoftSageFill inner + small Ink monogram at List elev 4 (never a pale-on-pale void, never tall).
                            val heroPhoto = com.desikitchen.app.ui.components.rememberRecipePhoto(r)
                            val shortPhone = LocalConfiguration.current.screenHeightDp < ShortPhoneHeightDp
                            BoxWithConstraints(
                                Modifier.fillMaxWidth()
                                    .padding(start = HeroInsetH, end = HeroInsetH, top = HeroInsetTop),
                            ) {
                                val photoHeight = (maxWidth * 0.75f).let { if (shortPhone) minOf(it, HeroMaxShort) else it }
                                val cardStyle = if (heroPhoto.hasPhoto) SoftCardStyle.Hero else SoftCardStyle.List
                                Box(
                                    Modifier.fillMaxWidth()
                                        .height(if (heroPhoto.hasPhoto) photoHeight else HeroFallbackHeight)
                                        .softShadow(cardStyle.elevation, HeroShape, cardStyle.ambient, cardStyle.spot)
                                        .background(sheetBg, HeroShape)
                                        .clip(HeroShape),
                                ) {
                                    if (heroPhoto.hasPhoto) {
                                        RecipeArt(
                                            r, Modifier.fillMaxSize(), monogramSize = 28.sp, compactFallback = true,
                                            photo = heroPhoto, alignment = Alignment.TopCenter,
                                        )
                                        // Light top scrim so the white round buttons sit cleanly on bright photos.
                                        Box(
                                            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(72.dp)
                                                .background(Brush.verticalGradient(listOf(Ink.copy(alpha = 0.14f), Color.Transparent))),
                                        )
                                    } else {
                                        Box(
                                            Modifier.fillMaxSize().padding(HeroFallbackInnerPad)
                                                .background(SoftSageFill, RoundedCornerShape(HeroRadius - HeroFallbackInnerPad)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                recipeMonogram(r.title), color = Ink, fontSize = 28.sp,
                                                fontWeight = FontWeight.ExtraBold, fontFamily = Nunito,
                                            )
                                        }
                                    }
                                    Row(
                                        Modifier.fillMaxWidth().padding(HeroButtonPad),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        HeroButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", onBack)
                                        Spacer(Modifier.weight(1f))
                                        HeroButton(
                                            if (r.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            if (r.isFavorite) "Remove from favourites" else "Add to favourites",
                                            viewModel::togglePinned,
                                            tint = if (r.isFavorite) SoftSageDeep else Ink,
                                            iconScale = com.desikitchen.app.ui.components.rememberHeartScale(r.isFavorite),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Box {
                                            HeroButton(Icons.Filled.MoreHoriz, "More actions", { menu = true })
                                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                                DropdownMenuItem(text = { Text("Edit recipe") }, onClick = { menu = false; onEdit(r.id) })
                                                DropdownMenuItem(text = { Text("Add to collection") }, onClick = { menu = false; showCollections = true })
                                                DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; share() })
                                                DropdownMenuItem(text = { Text("Coco") }, onClick = { menu = false; showAsk = true })
                                                DropdownMenuItem(text = { Text("Add to meal plan") }, onClick = { menu = false; showPlan = true })
                                                DropdownMenuItem(text = { Text("Add to grocery list") }, onClick = { menu = false; viewModel.addToGroceries() })
                                                DropdownMenuItem(text = { Text("Change photo") }, onClick = { menu = false; showPhoto = true })
                                                DropdownMenuItem(text = { Text("Export PDF") }, onClick = { menu = false; sharePdf() })
                                                DropdownMenuItem(text = { Text("Delete recipe") }, onClick = { menu = false; showDelete = true })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "sheet") {
                            // Depth B: the Canvas SoftSheet no longer overlaps the hero — 12dp gap, same 16dp side insets, 24dp
                            // corners, SoftSheet elev 10, so photo card + sheet float as one stack on SoftSagePale. The whole
                            // sheet is one item so its shadow wraps all four sides (recipes are tens of rows, not thousands).
                            SoftSheet(
                                Modifier.padding(start = HeroInsetH, end = HeroInsetH, top = HeroToSheetGap),
                                radius = SheetRadius, bottomRadius = SheetRadius,
                                contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 14.dp, bottom = SheetBottomAir),
                            ) {
                                Column(Modifier.padding(horizontal = SheetPadH)) {
                                    // Soft stack (ReciMe-airy): title/credit → compact creator row → quiet link chips →
                                    // Ingredients → Steps. No grab handle (static sheet), no stats / nutrition / checkboxes.
                                    Spacer(Modifier.height(10.dp))
                                    Text(r.title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp), color = Ink)
                                    val handle = state.creatorHandle
                                    val origin = state.originalUrl
                                    val followLabel = state.creatorFollowLabel?.takeIf { state.creatorProfileUrl != null }
                                    if (handle != null) {
                                        // Compact creator row: HTTPS-hotlinked avatar (else monogram) + handle → Creator page.
                                        Spacer(Modifier.height(TitleToSource))
                                        Row(
                                            Modifier.clip(CircleShape).clickable(onClickLabel = "Open creator page") { viewModel.navigateToCreator() }
                                                .padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            CreatorAvatar(handle, avatarUrl = state.creatorAvatarUrl, size = CreatorAvatarSize)
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(CreatorLinks.display(handle), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                SourcePlatform.label(r.sourcePlatform)?.takeIf { r.sourcePlatform != SourcePlatform.WEB }?.let {
                                                    Text("From $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    } else sourceCredit(r)?.let {
                                        Spacer(Modifier.height(TitleToSource))
                                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    // Quiet unselected SoftChips — Start cooking stays the only solid sage on the screen.
                                    if (origin != null || followLabel != null || state.canOpenCreator) {
                                        Spacer(Modifier.height(CreatorToLinks))
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (origin != null) {
                                                SoftChip(selected = false, onClick = { viewModel.openOriginal() }, label = "Open original", icon = Icons.AutoMirrored.Outlined.OpenInNew)
                                            }
                                            followLabel?.let { label ->
                                                SoftChip(selected = false, onClick = { viewModel.openCreatorProfile() }, label = label)
                                            }
                                            if (state.canOpenCreator) {
                                                val more = state.moreFromCreatorCount
                                                SoftChip(
                                                    selected = false, onClick = { viewModel.navigateToCreator() },
                                                    label = if (more > 0) "More from creator ($more)" else "More from creator",
                                                )
                                            }
                                        }
                                    }
                                    if (r.description.isNotBlank()) {
                                        Spacer(Modifier.height(14.dp))
                                        Text(r.description, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp), color = Ink)
                                    }
                                    val books = state.cookbooks.filter { it.id in state.memberCookbookIds }
                                    if (books.isNotEmpty()) {
                                        Text(
                                            "In " + books.joinToString { it.name },
                                            style = MaterialTheme.typography.bodySmall, color = Ink, fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clickable { showCollections = true }.padding(vertical = 8.dp),
                                        )
                                    }
                                }
                                // Stacked sections (no tab switch): Ingredients then Steps, one scroll, quiet headings.
                                SectionTitle("Ingredients", Modifier.padding(top = LinksToSection))
                                // Plain lines (Phase 1 lock): quantity column + Ink name. No checkboxes / time / servings / nutrition.
                                details.sortedIngredients.forEach { ing ->
                                    androidx.compose.runtime.key("i${ing.id}") {
                                        Row(Modifier.fillMaxWidth().padding(horizontal = SheetPadH, vertical = DetailRowPadV), verticalAlignment = Alignment.Top) {
                                            // Only what the source stated: no quantity → blank (never a guessed amount).
                                            val qty = state.displayQuantity(ing).takeIf { ing.quantity != null || ing.unit.isNotBlank() }
                                            Text(
                                                qty.orEmpty(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Ink,
                                                modifier = Modifier.width(96.dp).padding(end = 12.dp),
                                            )
                                            Column(Modifier.weight(1f)) {
                                                Text(ing.name, style = MaterialTheme.typography.bodyLarge, color = Ink)
                                                if (ing.note.isNotBlank()) {
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(ing.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                        HorizontalDivider(Modifier.padding(horizontal = SheetPadH), color = Hairline)
                                    }
                                }
                                if (details.ingredients.isEmpty()) MissingNote("No ingredients found. Edit the recipe to add them.")
                                SectionTitle("Steps", Modifier.padding(top = SectionGap))
                                steps.forEachIndexed { i, step ->
                                    androidx.compose.runtime.key("s${step.id}") {
                                        Row(Modifier.fillMaxWidth().padding(horizontal = SheetPadH, vertical = DetailRowPadV), verticalAlignment = Alignment.Top) {
                                            NumberBadge(i + 1)
                                            Spacer(Modifier.width(16.dp))
                                            Text(step.text, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp), color = Ink, modifier = Modifier.weight(1f).padding(top = 2.dp))
                                        }
                                    }
                                }
                                if (steps.isEmpty()) MissingNote("No steps found. Edit the recipe to add them.")
                            }
                        }
                    }
                    // Depth B floating Start cooking (mock 04): an inset Canvas card — 16dp sides, 16dp above the system nav
                    // inset (Detail has no NavigationBar), 28dp radius, 12dp padding, Float elev 12 + Hairline. The lift lives
                    // on the card; the PillButton fill stays flat (elev 0, 0.97 press). The screen's only solid sage.
                    // "Add to grocery" stays in the ⋯ menu.
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = FloatBarInsetH, end = FloatBarInsetH, bottom = FloatBarGap)
                            .softShadow(SoftElevFloat, FloatBarShape, SoftAmbientFloat, SoftSpotFloat)
                            .background(sheetBg, FloatBarShape)
                            .border(1.dp, Hairline, FloatBarShape)
                            .padding(FloatBarPad),
                    ) {
                        PillButton(
                            "Start cooking", onClick = { onCook(r.id) }, enabled = steps.isNotEmpty(),
                            leadingIcon = Icons.Outlined.PlayArrow,
                        )
                    }
                }
            }
        }
    }

    if (details != null) {
        if (showAsk) AskDesiKitchenSheet(details, onDismiss = { showAsk = false }, servings = state.servings)
        if (showPhoto) AlertDialog(
            onDismissRequest = { showPhoto = false },
            title = { Text("Recipe photo") },
            text = { Text("Pick a photo from your gallery or take a new one.") },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        showPhoto = false
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Gallery") }
                    TextButton(onClick = {
                        showPhoto = false
                        runCatching { RecipeImageStore.newCaptureUri(context) }.onSuccess { u -> pendingCapture = u.toString(); takePhoto.launch(u) }
                            .onFailure { viewModel.postMessage("Camera isn't available") }
                    }) { Text("Camera") }
                }
            },
            dismissButton = {
                if (details.recipe.imageUrl != null) TextButton(onClick = { showPhoto = false; viewModel.setImage(null) }) { Text("Remove") }
                else TextButton(onClick = { showPhoto = false }) { Text("Cancel") }
            },
        )
        if (showPlan) AddToPlanDialog(onDismiss = { showPlan = false }, onAdd = { d, s -> viewModel.addToPlan(d, s); showPlan = false })
        if (showCollections) CollectionMembershipDialog(
            collections = state.cookbooks.map { it.id to it.name },
            selected = state.memberCookbookIds,
            onDismiss = { showCollections = false },
            onSave = { viewModel.setCookbooks(it); showCollections = false },
            onCreate = { name -> viewModel.createCookbookWithRecipe(name, "") },
        )
        if (showDelete) AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete ${details.recipe.title}?") },
            text = { Text("It will be removed from your library, collections, meal plan and grocery list.") },
            confirmButton = { TextButton(onClick = { showDelete = false; viewModel.delete(onBack) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
}

/** "By @handle · Instagram" from stored provenance only; null when the source didn't say. Never invented. */
internal fun sourceCredit(r: Recipe): String? {
    val platform = SourcePlatform.label(r.sourcePlatform)?.takeIf { r.sourcePlatform != SourcePlatform.WEB }
    val handle = r.creatorHandle?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        handle != null && platform != null -> "By $handle · $platform"
        handle != null -> "By $handle"
        platform != null -> "Saved from $platform"
        else -> null
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Ink,
        modifier = modifier.fillMaxWidth().padding(start = SheetPadH, end = SheetPadH, top = 4.dp, bottom = 2.dp),
    )
}

/** No-photo hero: a compact 120dp Canvas card (SoftSageFill inner), not the old 336dp empty block. */
private val HeroFallbackHeight = 120.dp
private val HeroFallbackInnerPad = 8.dp

// Soft depth B floating stack (Design Lab): hero card + sheet inset 16dp, hero 8dp under the status bar — the root Scaffold
// already clears it, so no extra statusBars inset here (no TopAppBar —
// round buttons float on the card), 24dp radius, 12dp hero → sheet gap (no overlap). 4:3 photo, capped on short phones.
private val HeroInsetH = 16.dp
private val HeroInsetTop = 8.dp
private val HeroRadius = 24.dp
private val HeroShape = RoundedCornerShape(HeroRadius)
private val HeroButtonPad = 12.dp
private val HeroMaxShort = 220.dp
private const val ShortPhoneHeightDp = 700
private val HeroToSheetGap = 12.dp
private val SheetRadius = 24.dp
private val SheetPadH = 24.dp

// Detail rhythm (Soft stack): title → creator 8; creator → link chips 12; chips → Ingredients 20; Ingredients → Steps 24;
// rows 10; sheet bottom 24.
private val TitleToSource = 8.dp
private val CreatorToLinks = 12.dp
private val LinksToSection = 20.dp
private val SectionGap = 24.dp
private val CreatorAvatarSize = 36.dp
private val DetailRowPadV = 10.dp
private val SheetBottomAir = 24.dp
/** Floating CTA card: 16dp side inset, 16dp above the nav inset, 28dp radius, 12dp padding around a 52dp PillButton. */
private val FloatBarInsetH = 16.dp
private val FloatBarGap = 16.dp
private val FloatBarPad = 12.dp
private val FloatBarShape = RoundedCornerShape(28.dp)
private val FloatBarHeight = FloatBarPad * 2 + 52.dp

@Composable
private fun MissingNote(text: String, bg: Color = Color.Transparent) {
    Text(
        text, Modifier.fillMaxWidth().background(bg).padding(horizontal = SheetPadH, vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun HeroButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = Ink, iconScale: Float = 1f) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = tint, modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale })
        }
    }
}

@Composable
fun AddToPlanDialog(onDismiss: () -> Unit, onAdd: (LocalDate, MealSlot) -> Unit) {
    val today = remember { LocalDate.now() }
    var dayOffset by rememberSaveable { mutableStateOf(0) }
    var slot by rememberSaveable { mutableStateOf(MealSlot.DINNER) }
    val fmtShort = remember { DateTimeFormatter.ofPattern("EEE d MMM") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to meal plan") },
        text = {
            Column {
                Text("Day", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((0 until 21).toList()) { off ->
                        SoftChip(
                            selected = off == dayOffset, onClick = { dayOffset = off },
                            label = if (off == 0) "Today" else today.plusDays(off.toLong()).format(fmtShort),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Meal", style = MaterialTheme.typography.labelLarge)
                SlotChips(slot) { slot = it }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(today.plusDays(dayOffset.toLong()), slot) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotChips(selected: MealSlot, onSelect: (MealSlot) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MealSlot.entries.forEach { s ->
            SoftChip(selected = s == selected, onClick = { onSelect(s) }, label = s.label)
        }
    }
}

/** Text-first "Add to collection" picker (personal collections are stored as cookbooks). */
@Composable
private fun CollectionMembershipDialog(
    collections: List<Pair<Long, String>>,
    selected: Set<Long>,
    onDismiss: () -> Unit,
    onSave: (Set<Long>) -> Unit,
    onCreate: (String) -> Unit,
) {
    var chosen by remember(selected) { mutableStateOf(selected) }
    var showNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to collection") },
        text = {
            LazyColumn {
                items(collections, key = { it.first }) { (id, name) ->
                    ListItem(
                        headlineContent = { Text(name) },
                        leadingContent = {
                            Checkbox(checked = id in chosen, onCheckedChange = { chosen = if (it) chosen + id else chosen - id })
                        },
                        modifier = Modifier.clickable { chosen = if (id in chosen) chosen - id else chosen + id },
                    )
                }
                item {
                    if (showNew) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("Collection name") }, modifier = Modifier.weight(1f))
                            TextButton(enabled = newName.isNotBlank(), onClick = { onCreate(newName.trim()); newName = ""; showNew = false }) { Text("Add") }
                        }
                    } else {
                        ListItem(
                            headlineContent = { Text("New collection", color = Ink, fontWeight = FontWeight.Bold) },
                            leadingContent = { Icon(Icons.Filled.Add, null, tint = SoftSageDeep) },
                            modifier = Modifier.clickable { showNew = true },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosen) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
