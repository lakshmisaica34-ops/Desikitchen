package com.desikitchen.app.ui.more

import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.desikitchen.app.ui.theme.SoftSage
import com.desikitchen.app.ui.theme.SoftSageFill
import com.desikitchen.app.ui.theme.SoftPistachio
import com.desikitchen.app.ui.components.SoftCardStyle
import com.desikitchen.app.ui.components.PlusSheet
import com.desikitchen.app.ui.components.PillButton
import com.desikitchen.app.ui.components.LocalSnackbarHostState
import androidx.compose.foundation.layout.height
import com.desikitchen.app.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desikitchen.app.BuildConfig
import com.desikitchen.app.util.OpenRouterClient
import com.desikitchen.app.ui.AppViewModelProvider
import com.desikitchen.app.ui.components.DkScaffold
import com.desikitchen.app.ui.components.SoftCard
import com.desikitchen.app.ui.components.SoftChip
import com.desikitchen.app.ui.components.TextInputDialog
import com.desikitchen.app.ui.plan.WeekStartDialog
import com.desikitchen.app.ui.theme.Hairline
import com.desikitchen.app.ui.theme.Ink
import com.desikitchen.app.ui.theme.Mustard
import com.desikitchen.app.ui.theme.MustardCream
import com.desikitchen.app.ui.theme.MustardDeep
import com.desikitchen.app.util.UnitSystem
import java.time.format.TextStyle
import java.util.Locale

/** Profile tab (v1): who you are, your kitchen, settings and about. */
@Composable
fun MoreScreen(
    onOpenRecipe: (Long) -> Unit,
    onOpenAllRecipes: () -> Unit,
    onOpenCookbooks: () -> Unit = {},
    onOpenPlan: () -> Unit = {},
    viewModel: MoreViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var weekDialog by remember { mutableStateOf(false) }
    var nameDialog by remember { mutableStateOf(false) }
    var plusSheet by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    DkScaffold { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text(
                    "Profile", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
                )
            }
            item {
                SoftGroup(Modifier.padding(top = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { nameDialog = true }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(56.dp).background(SoftPistachio, CircleShape), contentAlignment = Alignment.Center) {
                            Text(state.displayName.firstOrNull()?.uppercase() ?: "?", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(state.displayName.ifBlank { "Add your name" }, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                            Text(
                                "${state.recipeCount} recipes · ${state.favorites.size} favourites" +
                                    if (state.cookedTotal > 0) " · ${state.cookedTotal} cooked" else "",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Outlined.Edit, "Edit name", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // Plus card: the plan lives here once (it used to repeat in About).
            item {
                SoftCard(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    style = SoftCardStyle.List, color = SoftSageFill,
                ) {
                    Text(
                        if (state.isPlus) "DesiKitchen Plus" else "Free plan",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = Ink,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (state.isPlus) "Unlimited imports and curated collections."
                        else "5 imports a week. Plus adds unlimited imports and curated collections.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!state.isPlus) {
                        Spacer(Modifier.height(12.dp))
                        PillButton("See Plus", onClick = { plusSheet = true }, fill = false)
                    }
                }
            }
            item { GroupTitle("Your kitchen") }
            item {
                SoftGroup {
                    // One row into Saved (recipes + your collections live there); the old separate
                    // "Collections" and "All recipes" rows both landed on Saved.
                    NavRow(
                        Icons.AutoMirrored.Outlined.ViewList, "Saved recipes",
                        "${state.myRecipeCount} created or imported by you · your collections", onOpenAllRecipes,
                    )
                    GroupDivider()
                    NavRow(Icons.Outlined.CalendarMonth, "Meal plan", "Plan the week, then fill the grocery list", onOpenPlan)
                }
            }
            item { GroupTitle("Settings") }
            item {
                SoftGroup {
                    ListItem(
                        headlineContent = { Text("Measurement units") },
                        supportingContent = {
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                UnitSystem.entries.forEach { u ->
                                    SoftChip(selected = state.unitSystem == u, onClick = { viewModel.setUnitSystem(u) }, label = u.label)
                                }
                            }
                        },
                        colors = clearItem(),
                    )
                    GroupDivider()
                    ListItem(
                        headlineContent = { Text("Keep screen on") },
                        supportingContent = { Text("While viewing a recipe. Cook mode always stays awake.") },
                        trailingContent = {
                            Switch(
                                checked = state.keepScreenOn, onCheckedChange = viewModel::setKeepScreenOn,
                                colors = SwitchDefaults.colors(checkedTrackColor = SoftSage, checkedThumbColor = MaterialTheme.colorScheme.surface),
                            )
                        },
                        modifier = Modifier.clickable { viewModel.setKeepScreenOn(!state.keepScreenOn) },
                        colors = clearItem(),
                    )
                    GroupDivider()
                    ListItem(
                        headlineContent = { Text("Auto-sync grocery list") },
                        supportingContent = { Text("Adds this and next week's meal plan to Grocery.") },
                        trailingContent = {
                            Switch(
                                checked = state.autoGrocerySync, onCheckedChange = viewModel::setAutoGrocerySync,
                                colors = SwitchDefaults.colors(checkedTrackColor = SoftSage, checkedThumbColor = MaterialTheme.colorScheme.surface),
                            )
                        },
                        modifier = Modifier.clickable { viewModel.setAutoGrocerySync(!state.autoGrocerySync) },
                        colors = clearItem(),
                    )
                    GroupDivider()
                    ListItem(
                        headlineContent = { Text("Meal plan week starts on") },
                        supportingContent = { Text(state.weekStart.getDisplayName(TextStyle.FULL, Locale.getDefault())) },
                        modifier = Modifier.clickable { weekDialog = true },
                        colors = clearItem(),
                    )
                }
            }
            item { GroupTitle("About") }
            item {
                SoftGroup {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Image(
                            painterResource(R.drawable.desikitchen_logo), contentDescription = "DesiKitchen",
                            modifier = Modifier.padding(vertical = 4.dp).height(18.dp),
                        )
                        Text(
                            "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Your recipes are stored on this phone.", style = MaterialTheme.typography.bodyMedium)
                        if (state.cookedTotal > 0) Text(
                            "You've cooked ${state.cookedRecipes} different dishes ${state.cookedTotal} times.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    GroupDivider()
                    ListItem(
                        headlineContent = { Text("Coco") },
                        supportingContent = {
                            Text(
                                if (OpenRouterClient.isConfigured) "On. Only recipe titles and ingredients leave your phone."
                                else "Coco is offline on this phone.",
                            )
                        },
                        colors = clearItem(),
                    )
                }
            }
        }
    }
    if (weekDialog) WeekStartDialog(state.weekStart, onDismiss = { weekDialog = false }) { viewModel.setWeekStart(it); weekDialog = false }
    if (plusSheet) PlusSheet(
        title = "DesiKitchen Plus",
        message = "Unlimited imports and hand-picked desi collections.",
        onGetPlus = { plusSheet = false; scope.launch { snackbar.showSnackbar("Plus isn't available to buy yet.") } },
        onDismiss = { plusSheet = false },
    )
    if (nameDialog) TextInputDialog(
        title = "Your name", label = "Shown in the home greeting", initial = state.displayName,
        onDismiss = { nameDialog = false }, onConfirm = { viewModel.setDisplayName(it); nameDialog = false },
    )
}

/** White soft card that groups Profile rows (replaces flat full-bleed ListItems). */
@Composable
private fun SoftGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    SoftCard(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        contentPadding = PaddingValues(vertical = 8.dp), content = content,
    )
}

@Composable
private fun GroupTitle(text: String) = Text(
    text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp),
)

@Composable
private fun GroupDivider() = HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Hairline.copy(alpha = 0.6f))

@Composable
private fun clearItem() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(MustardCream, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MustardDeep)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
