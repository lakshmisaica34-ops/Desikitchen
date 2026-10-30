package com.desikitchen.app.ui.creator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.repo.RecipeRepository
import com.desikitchen.app.ui.AppViewModelProvider
import com.desikitchen.app.ui.components.EmptyState
import com.desikitchen.app.ui.components.RecipeGridCard
import com.desikitchen.app.ui.components.SoftCard
import com.desikitchen.app.ui.components.SoftChip
import com.desikitchen.app.ui.components.SoftPageGround
import com.desikitchen.app.ui.components.recipeMonogram
import com.desikitchen.app.ui.theme.Ink
import com.desikitchen.app.ui.theme.Nunito
import com.desikitchen.app.ui.theme.SoftSage
import com.desikitchen.app.ui.theme.SoftSageFill
import com.desikitchen.app.util.CreatorProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI-side creator helpers. Identity / URL rules live in Grok's [CreatorProfile] (data lane); this only adds display
 * formatting. Device-only: the Creator page reads the LOCAL library by handle. Nothing is invented.
 */
object CreatorLinks {
    const val CLAIM_EMAIL = CreatorProfile.CLAIM_EMAIL
    private val IgUsername = Regex("^[A-Za-z0-9._]{1,30}$")

    fun handleOf(r: Recipe): String? = CreatorProfile.handleOf(r)

    /** IG profile or YT channel via [CreatorProfile.profileUrl]; null → hide Follow. */
    fun profileUrl(r: Recipe): String? = CreatorProfile.profileUrl(r)

    /** "Follow on Instagram" / "Visit YouTube channel" for a recipe whose [profileUrl] is non-null; else null. */
    fun followLabel(r: Recipe): String? = profileUrl(r)?.let { CreatorProfile.followLabel(r.sourcePlatform) }

    /** HTTPS-only avatar for hotlinking (never persisted as bytes); null → monogram. */
    fun avatarUrl(r: Recipe): String? = CreatorProfile.safeAvatarUrl(r.creatorAvatarUrl)

    /** "@user" for IG-shaped handles, else as stored (YouTube channel names stay readable). */
    fun display(handle: String): String {
        val h = handle.trim()
        return if (!h.startsWith("@") && IgUsername.matches(h)) "@$h" else h
    }
}

fun openExternal(context: android.content.Context, url: String): Boolean =
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess

/**
 * Creator avatar: Coil hotlink of an HTTPS URL only (memory/HTTP cache; never written to our own storage — Meta-safe),
 * else (or on error) a SoftSageFill circle + Ink monogram.
 */
@Composable
fun CreatorAvatar(handle: String, avatarUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    val https = CreatorProfile.safeAvatarUrl(avatarUrl)
    var failed by remember(https) { mutableStateOf(false) }
    Box(modifier.size(size).clip(CircleShape).background(SoftSageFill), contentAlignment = Alignment.Center) {
        if (https != null && !failed) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(https)
                    // Hotlink only: no disk cache, so IG CDN bytes are never persisted on device.
                    .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                    .build(),
                contentDescription = "$handle avatar",
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                recipeMonogram(handle.removePrefix("@")), color = Ink, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
                fontSize = (size.value * 0.38f).sp,
            )
        }
    }
}

data class CreatorUiState(
    val loading: Boolean = true,
    val handle: String = "",
    val recipes: List<Recipe> = emptyList(),
    /** First derivable IG profile / YT channel among this creator's recipes; null → no Follow chip. */
    val profileUrl: String? = null,
    /** Label for [profileUrl] from [CreatorProfile.followLabel] of the recipe that produced it. */
    val followLabel: String? = null,
    /** HTTPS avatar to hotlink from [Recipe.creatorAvatarUrl] (first recipe that has one); null → monogram. */
    val avatarUrl: String? = null,
    /** [CreatorProfile.isClaimed] (always false until a claim registry exists) → unclaimed banner shows. */
    val claimed: Boolean = false,
)

/** Device-only creator page: the local library filtered by [Recipe.creatorHandle]. No cloud catalog. */
class CreatorViewModel(savedState: SavedStateHandle, private val repo: RecipeRepository) : ViewModel() {
    private val handle: String = savedState.get<String>("handle").orEmpty()

    val uiState: StateFlow<CreatorUiState> = repo.observeByCreator(handle).map { mine ->
        val shownHandle = mine.firstNotNullOfOrNull { CreatorLinks.handleOf(it) } ?: handle
        val follow = mine.firstNotNullOfOrNull { r ->
            CreatorLinks.profileUrl(r)?.let { url -> CreatorLinks.followLabel(r)?.let { url to it } }
        }
        CreatorUiState(
            loading = false,
            handle = shownHandle,
            recipes = mine,
            profileUrl = follow?.first,
            followLabel = follow?.second,
            avatarUrl = mine.firstNotNullOfOrNull { CreatorLinks.avatarUrl(it) },
            claimed = CreatorProfile.isClaimed(shownHandle),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreatorUiState(handle = handle))

    fun toggleFavorite(r: Recipe) = viewModelScope.launch { repo.setFavorite(r.id, !r.isFavorite) }
}

@Composable
fun CreatorScreen(
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    viewModel: CreatorViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shown = CreatorLinks.display(s.handle)

    LazyColumn(
        Modifier.fillMaxSize().background(SoftPageGround),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = onBack, shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", tint = Ink) }
                }
            }
        }
        item(key = "header") {
            SoftCard(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CreatorAvatar(s.handle.ifBlank { "?" }, s.avatarUrl, 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            shown, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (!s.loading) {
                            val n = s.recipes.size
                            Text(
                                "$n recipe${if (n == 1) "" else "s"} in your DesiKitchen",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                val follow = s.profileUrl
                val followLabel = s.followLabel
                if (follow != null && followLabel != null) {
                    Spacer(Modifier.height(14.dp))
                    SoftChip(
                        selected = false, onClick = { openExternal(context, follow) },
                        label = followLabel, icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    )
                }
            }
        }
        if (!s.claimed) item(key = "unclaimed") {
            SoftCard(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                color = SoftSageFill, contentPadding = PaddingValues(16.dp),
            ) {
                Text(
                    CreatorProfile.UNCLAIMED_COPY,
                    style = MaterialTheme.typography.bodyMedium, color = Ink,
                )
            }
        }
        item(key = "title") {
            Text(
                "Recipes", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Ink,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp),
            )
        }
        when {
            s.loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SoftSage) }
            }
            s.recipes.isEmpty() -> item(key = "empty") {
                EmptyState(title = "No recipes yet", message = "Recipes you save from $shown show up here.")
            }
            // Same 2-up Soft photo tiles as Saved.
            else -> items(s.recipes.chunked(2), key = { row -> "r" + row.joinToString("-") { it.id.toString() } }) { row ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { r ->
                        RecipeGridCard(r, onClick = { onOpenRecipe(r.id) }, modifier = Modifier.weight(1f), onToggleFavorite = { viewModel.toggleFavorite(r) })
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
