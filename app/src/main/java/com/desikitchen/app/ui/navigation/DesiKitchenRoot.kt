package com.desikitchen.app.ui.navigation

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.desikitchen.app.R
import com.desikitchen.app.ui.assistant.AssistantScreen
import com.desikitchen.app.ui.collections.CollectionsScreen
import com.desikitchen.app.ui.components.LocalSnackbarHostState
import com.desikitchen.app.ui.cookbooks.CookbookDetailScreen
import com.desikitchen.app.ui.cooking.CookingScreen
import com.desikitchen.app.ui.creator.CreatorScreen
import com.desikitchen.app.ui.detail.RecipeDetailScreen
import com.desikitchen.app.ui.discover.DiscoverScreen
import com.desikitchen.app.ui.edit.AddEditRecipeScreen
import com.desikitchen.app.ui.grocery.GroceryScreen
import com.desikitchen.app.ui.importer.WebImportScreen
import com.desikitchen.app.ui.more.MoreScreen
import com.desikitchen.app.ui.plan.PlanScreen
import com.desikitchen.app.ui.saved.SavedScreen
import com.desikitchen.app.ui.search.SearchScreen
import com.desikitchen.app.ui.theme.Hairline
import com.desikitchen.app.ui.theme.Muted
import com.desikitchen.app.ui.theme.SoftSage
import com.desikitchen.app.ui.theme.SoftSageDeep
import com.desikitchen.app.ui.theme.SoftSageFill
import com.desikitchen.app.ui.theme.SoftPistachio
import com.desikitchen.app.ui.theme.RetroGreen
import com.desikitchen.app.ui.components.PillButton
import com.desikitchen.app.util.ImportQuota
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.desikitchen.app.ui.components.importResetLine
import com.desikitchen.app.ui.components.PlusSheet
import com.desikitchen.app.util.BackgroundImporter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import com.desikitchen.app.util.ImportUrlValidator
import com.desikitchen.app.util.SharedPayload
import com.desikitchen.app.ui.AppViewModelProvider
import com.desikitchen.app.ui.importer.ImportStatusViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desikitchen.app.util.ImportJob
import com.desikitchen.app.ui.theme.Ink
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

object Routes {
    const val COLLECTIONS = "collections"
    const val DISCOVER = "discover"
    const val ASSISTANT = "assistant"
    const val SAVED = "saved"
    const val MORE = "more"
    const val COOKBOOK = "cookbook/{id}"
    const val RECIPE = "recipe/{id}"
    const val COOKING = "cooking/{id}"
    const val EDIT = "edit?id={id}&draft={draft}"
    const val PLAN = "plan"
    const val GROCERY = "grocery"
    const val WEB = "import/web?url={url}"
    const val SEARCH = "search?q={q}"
    const val CREATOR = "creator/{handle}"

    fun cookbook(id: Long) = "cookbook/$id"
    fun recipe(id: Long) = "recipe/$id"
    fun cooking(id: Long) = "cooking/$id"
    fun creator(handle: String) = "creator/${Uri.encode(handle.trim())}"
    fun edit(id: Long? = null, draft: Boolean = false) = "edit?id=${id ?: -1}&draft=$draft"
    fun search(q: String? = null) = if (q.isNullOrBlank()) "search" else "search?q=${Uri.encode(q)}"
    fun web(url: String? = null) = if (url.isNullOrBlank()) "import/web" else "import/web?url=${Uri.encode(url)}"

    val topLevel = setOf(COLLECTIONS, DISCOVER, ASSISTANT, SAVED, MORE)
}

private data class Tab(val route: String, val label: String, val icon: ImageVector?, val selectedIcon: ImageVector?)

// Sai lock #1: Collections · Discover · Assistant (centre, emphasised) · Saved · Profile.
private val tabs = listOf(
    Tab(Routes.COLLECTIONS, "Collections", Icons.Outlined.GridView, Icons.Filled.GridView),
    Tab(Routes.DISCOVER, "Discover", Icons.Outlined.Explore, Icons.Filled.Explore),
    Tab(Routes.ASSISTANT, "Assistant", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    Tab(Routes.SAVED, "Saved", Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
    Tab(Routes.MORE, "Profile", Icons.Outlined.Person, Icons.Filled.Person),
)

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Share-sheet / VIEW routing (Sai lock #3, #9): only YouTube & Instagram links are accepted. Returns a web-import URL or null. */
internal fun sharedImportUrl(payload: SharedPayload): String? = runCatching {
    val text = (payload as? SharedPayload.Text)?.text?.trim().orEmpty()
    (ImportUrlValidator.validateSupported(text) as? ImportUrlValidator.UrlCheck.Ok)?.url
}.getOrNull()

/** Share sheet with several links in one text: every supported (YT/IG) link, deduped; empty when none. */
internal fun sharedImportUrls(payload: SharedPayload): List<String> = runCatching {
    val text = (payload as? SharedPayload.Text)?.text?.trim().orEmpty()
    ImportUrlValidator.parseMany(text).valid
}.getOrDefault(emptyList())

@Composable
fun DesiKitchenRoot(shared: SharedPayload? = null, onSharedConsumed: () -> Unit = {}) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var lastTab by rememberSaveable { mutableStateOf(Routes.DISCOVER) }
    // App-wide background importer, exposed through the ViewModel factory like every other container dependency.
    val importer: ImportStatusViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val importJob by importer.job.collectAsState()
    val importTimes by importer.quotaTimes.collectAsState()
    val isPlus by importer.isPlus.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(currentRoute) { if (currentRoute in Routes.topLevel) lastTab = currentRoute!! }

    // Crash guard (1.1.1): a bad shared payload must never close the app.
    LaunchedEffect(shared) {
        val payload = shared ?: return@LaunchedEffect
        onSharedConsumed()
        val urls = sharedImportUrls(payload)
        val url = sharedImportUrl(payload)
        if (urls.size > 1) {
            // Multi-link share: queue them all in the background.
            val text = (payload as SharedPayload.Text).text
            importer.startMany(text).message?.let { snackbar.showSnackbar(it) }
        } else if (url != null) {
            // Background import: no blocking screen, the status pill tracks it.
            importer.start(url)?.let { snackbar.showSnackbar(it) }
        } else if (urls.size == 1) {
            importer.start(urls.first())?.let { snackbar.showSnackbar(it) }
        } else snackbar.showSnackbar(ImportUrlValidator.UNSUPPORTED)
    }

    /** After saving a new recipe, land on its detail page above the current tab. */
    fun openSavedRecipe(id: Long) {
        nav.navigate(Routes.recipe(id)) { popUpTo(lastTab) }
    }
    val back: () -> Unit = { nav.popBackStack() }
    val openRecipe: (Long) -> Unit = { nav.navigate(Routes.recipe(it)) }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbar) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = { if (currentRoute in Routes.topLevel) BottomBar(currentRoute) { nav.switchTab(it) } },
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner).imePadding()) {
            NavHost(
                navController = nav,
                startDestination = Routes.DISCOVER,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Routes.COLLECTIONS) { CollectionsScreen(onOpenCollection = { nav.navigate(Routes.cookbook(it)) }) }
                composable(Routes.DISCOVER) {
                    DiscoverScreen(
                        onImportLink = { link ->
                            if (link.isBlank()) { runCatching { nav.navigate(Routes.web()) }; null }
                            else {
                                // One or more links: queue every supported one; summary goes to the snackbar.
                                val r = importer.startMany(link)
                                if (r.queued == 0) r.message
                                else { r.message?.let { m -> scope.launch { snackbar.showSnackbar(m) } }; null }
                            }
                        },
                        onOpenRecipe = openRecipe,
                        onSeeAllSaved = { nav.switchTab(Routes.SAVED) },
                        onSeeCollections = { nav.switchTab(Routes.COLLECTIONS) },
                        onSearch = { nav.navigate(Routes.search()) },
                        importsLeft = ImportQuota.remaining(importTimes, System.currentTimeMillis()),
                        importsPerWeek = ImportQuota.FREE_PER_WEEK,
                        isPlus = isPlus,
                    )
                }
                composable(Routes.ASSISTANT) { AssistantScreen(onOpenRecipe = openRecipe, onOpenDiscover = { nav.switchTab(Routes.DISCOVER) }) }
                composable(Routes.SAVED) {
                    SavedScreen(
                        onOpenRecipe = openRecipe,
                        onOpenCollection = { nav.navigate(Routes.cookbook(it)) },
                        onOpenPlan = { nav.navigate(Routes.PLAN) },
                        onOpenGrocery = { nav.navigate(Routes.GROCERY) },
                        // "Paste a link" → the Discover paste hero (single primary import entry).
                        onImport = { nav.switchTab(Routes.DISCOVER) },
                    )
                }
                composable(Routes.MORE) {
                    MoreScreen(
                        onOpenRecipe = openRecipe,
                        onOpenAllRecipes = { nav.switchTab(Routes.SAVED) },
                        onOpenCookbooks = { nav.switchTab(Routes.SAVED) },
                        onOpenPlan = { nav.navigate(Routes.PLAN) },
                    )
                }
                composable(Routes.COOKBOOK, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    CookbookDetailScreen(onBack = back, onOpenRecipe = openRecipe)
                }
                composable(Routes.RECIPE, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    RecipeDetailScreen(
                        onBack = back,
                        onEdit = { nav.navigate(Routes.edit(it)) },
                        onCook = { nav.navigate(Routes.cooking(it)) },
                        onOpenGroceries = { nav.navigate(Routes.GROCERY) },
                        onOpenCreator = { nav.navigate(Routes.creator(it)) },
                    )
                }
                composable(Routes.CREATOR, arguments = listOf(navArgument("handle") { type = NavType.StringType })) {
                    CreatorScreen(onBack = back, onOpenRecipe = openRecipe)
                }
                composable(Routes.COOKING, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    CookingScreen(onBack = back)
                }
                composable(
                    Routes.EDIT,
                    arguments = listOf(
                        navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("draft") { type = NavType.BoolType; defaultValue = false },
                    ),
                ) {
                    AddEditRecipeScreen(
                        onBack = back,
                        onSaved = { id, isNew -> if (isNew) openSavedRecipe(id) else nav.popBackStack() },
                    )
                }
                composable(Routes.PLAN) {
                    PlanScreen(onOpenRecipe = openRecipe, onOpenGroceries = { nav.navigate(Routes.GROCERY) }, onBack = back)
                }
                composable(Routes.GROCERY) {
                    GroceryScreen(onOpenRecipe = openRecipe, onOpenPlan = { nav.navigate(Routes.PLAN) }, onBack = back)
                }
                composable(
                    Routes.WEB,
                    arguments = listOf(navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { entry ->
                    WebImportScreen(
                        initialUrl = entry.arguments?.getString("url"), onBack = back,
                        onQueued = { m -> scope.launch { snackbar.showSnackbar(m) } },
                    )
                }
                composable(
                    Routes.SEARCH,
                    arguments = listOf(navArgument("q") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) {
                    SearchScreen(onBack = back, onOpenRecipe = openRecipe, onImport = { nav.navigate(Routes.web()) })
                }
            }
            // Soft paywall (Design Lab #11): only when the existing quota gate refuses the 6th import of the week.
            // Quota logic stays in BackgroundImporter / ImportQuota; this only renders the refusal softly.
            val quotaHit = (importJob as? ImportJob.Failed)?.let { it.message == BackgroundImporter.QUOTA_USED && it.partial == null } == true && !isPlus
            if (quotaHit) {
                PlusSheet(
                    title = "You've used ${ImportQuota.FREE_PER_WEEK} imports this week",
                    message = importResetLine(ImportQuota.nextFreeAt(importTimes, System.currentTimeMillis())),
                    onGetPlus = {
                        importer.dismiss()
                        scope.launch { snackbar.showSnackbar("Plus isn't available to buy yet.") }
                    },
                    onDismiss = importer::dismiss,
                )
            } else if (currentRoute != Routes.COOKING && currentRoute != Routes.EDIT) {
                ImportStatusPill(
                    job = importJob,
                    onCancel = importer::cancel,
                    onDismiss = importer::dismiss,
                    onReview = { if (importer.handOffDraft()) runCatching { nav.navigate(Routes.edit(draft = true)) } },
                    onOpenRecipe = { importer.takeDuplicate()?.let(openRecipe) },
                    // On recipe Detail the pill rides 8dp above the floating Start cooking card (76dp card + 16dp gap)
                    // instead of colliding with it; every other route keeps the 12dp placement.
                    modifier = Modifier.align(Alignment.BottomCenter).padding(
                        start = 16.dp, end = 16.dp, top = 12.dp,
                        bottom = if (currentRoute == Routes.RECIPE) DetailCtaClearance else 12.dp,
                    ),
                )
            }
            }
        }
    }
}

/** Detail's floating CTA card (12dp pad ×2 + 52dp PillButton = 76dp) + its 16dp gap + 8dp air above it. */
private val DetailCtaClearance = 100.dp

/**
 * Mock 03: floating SoftSageFill status card above the tabs (never a blocking screen). Same [ImportJob] state machine;
 * Soft chrome only. Running = link badge + real stage label + thin indeterminate SoftSage bar (no fake percent) + Cancel.
 * Ready = SoftSage check + "Recipe ready" + draft title · platform + a quiet "Open" text action (no second PillButton:
 * the screen underneath already owns its one primary action).
 */
@Composable
private fun ImportStatusPill(
    job: ImportJob,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onReview: () -> Unit,
    onOpenRecipe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = job !is ImportJob.Idle, modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        // Keep the last non-idle job on screen while the exit animation runs.
        var shown by remember { mutableStateOf(job) }
        if (job !is ImportJob.Idle) shown = job
        val shape = RoundedCornerShape(24.dp)
        Surface(
            shape = shape, color = SoftSageFill, contentColor = Ink, shadowElevation = 6.dp,
            border = BorderStroke(1.dp, Hairline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (val j = shown) {
                    is ImportJob.Running -> {
                        StatusBadge(Icons.Filled.Link, solid = false)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            val from = platformOf(j.url)
                            Text(
                                if (from != null) "Importing from $from\u2026" else "Importing recipe\u2026",
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            // Multi-link queue: quiet counts next to the real stage label.
                            val extra = listOfNotNull(
                                j.queued.takeIf { it > 0 }?.let { "$it queued" },
                                j.ready.takeIf { it > 0 }?.let { "$it ready to review" },
                            )
                            Text((listOf(j.stage.label) + extra).joinToString(" \u00b7 "), style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(5.dp))
                            LinearProgressIndicator(
                                Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = SoftSage, trackColor = MaterialTheme.colorScheme.surface,
                            )
                        }
                        // Review a finished draft while the rest of the queue keeps importing.
                        if (j.ready > 0) TextAction("Review") { onReview() }
                        TextButton(onClick = onCancel) { Text("Cancel", color = Ink, fontWeight = FontWeight.Bold) }
                    }
                    is ImportJob.Ready -> {
                        StatusBadge(Icons.Filled.Check, solid = true)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (j.more > 0) "${j.more + 1} recipes ready to review" else "Recipe ready",
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1,
                            )
                            val title = j.draft.parsed.title.trim().takeIf { it.isNotEmpty() }
                            val from = j.draft.sourceUrl?.let(::platformOf)
                            val sub = listOfNotNull(title, from?.let { "from $it" }).joinToString(" \u00b7 ")
                            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        TextAction("Open") { onReview() }
                        DismissButton(onDismiss)
                    }
                    is ImportJob.Failed -> {
                        Spacer(Modifier.width(6.dp))
                        Text(j.message, style = MaterialTheme.typography.labelLarge, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (j.partial != null) { Spacer(Modifier.width(8.dp)); TextAction("Open draft") { onReview() } }
                        DismissButton(onDismiss)
                    }
                    is ImportJob.Duplicate -> {
                        StatusBadge(Icons.Filled.Check, solid = false)
                        Spacer(Modifier.width(10.dp))
                        Text("Already saved: \u201c${j.title}\u201d", style = MaterialTheme.typography.labelLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        TextAction("Open") { onOpenRecipe() }
                        DismissButton(onDismiss)
                    }
                    is ImportJob.Notice -> {
                        Spacer(Modifier.width(6.dp))
                        Text(j.message, style = MaterialTheme.typography.labelLarge, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        DismissButton(onDismiss)
                    }
                    ImportJob.Idle -> Unit
                }
            }
        }
    }
}

/** Platform name from the link host only (what the user pasted); null when it's neither. */
private fun platformOf(url: String): String? {
    val u = url.lowercase()
    return when {
        "instagram.com" in u || "instagr.am" in u -> "Instagram"
        "youtube.com" in u || "youtu.be" in u -> "YouTube"
        else -> null
    }
}

/** Running: surface circle + Retro Green Deep icon. Ready: solid Retro Green circle + white check (7.0:1; never Ink on Retro Green). */
@Composable
private fun StatusBadge(icon: ImageVector, solid: Boolean) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(if (solid) SoftSage else MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(18.dp), tint = if (solid) Color.White else SoftSageDeep) }
}

@Composable
private fun DismissButton(onDismiss: () -> Unit) {
    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, "Dismiss", tint = Muted, modifier = Modifier.size(18.dp)) }
}

/** Quiet inline action on the status card (Open / Open draft): Ink bold text, never a second PillButton. */
@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(text, color = Ink, fontWeight = FontWeight.Bold) }
}

/**
 * Nav bar: surface, Hairline top divider, Soft Pistachio wash behind the selected icon (Sai mock). Selected = Retro Green
 * (5.6:1 on Pistachio, 7.0:1 on white), unselected = Muted #6E6E73 (5.1:1). Each tab is a Role.Tab with a
 * contentDescription and selected state for TalkBack.
 */
@Composable
private fun BottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(thickness = 1.dp, color = Hairline)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    val tint = if (selected) RetroGreen else Muted
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab.route) })
                            .semantics { contentDescription = "${tab.label} tab" }
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(width = 56.dp, height = 32.dp).clip(RoundedCornerShape(50))
                                .background(if (selected) SoftPistachio else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (selected) tab.selectedIcon!! else tab.icon!!, null, tint = tint)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tab.label, style = MaterialTheme.typography.labelSmall, color = tint,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1, modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                }
            }
        }
    }
}
