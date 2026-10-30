package com.desikitchen.app.ui.assistant

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desikitchen.app.R
import com.desikitchen.app.data.model.CocoActionState
import com.desikitchen.app.ui.AppViewModelProvider
import com.desikitchen.app.ui.components.SoftField
import com.desikitchen.app.ui.components.PillButton
import com.desikitchen.app.ui.components.SoftChip
import com.desikitchen.app.ui.components.SoftOutlinePill
import com.desikitchen.app.ui.theme.Hairline
import com.desikitchen.app.ui.theme.Ink
import com.desikitchen.app.ui.theme.SoftSageDeep
import com.desikitchen.app.ui.theme.SoftSageFill
import com.desikitchen.app.ui.theme.Mustard
import com.desikitchen.app.ui.theme.MustardCream
import com.desikitchen.app.ui.theme.MustardDeep

/** Small circular Coco avatar: an AI sparkle on a SoftSageFill chip (replaces the old house+hat mark). */
@Composable
fun CocoAvatar(size: Int = 32) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(SoftSageFill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = "Coco", tint = SoftSageDeep, modifier = Modifier.size((size * 0.55f).dp))
    }
}

private val starters = listOf(
    "What can I cook tonight?",
    "What uses paneer?",
    "What's vegetarian?",
)

/**
 * Assistant tab (Sai lock #15): text-first Coco chat over the saved library. Unlimited in Phase 1. History is
 * persisted, OpenRouter answers stream in, and meal-plan / grocery requests show Confirm / Cancel before any write.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    onOpenRecipe: (Long) -> Unit,
    /** Empty-library CTA ("Paste a link") → Discover paste card. */
    onOpenDiscover: () -> Unit = {},
    viewModel: AssistantViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val turns by viewModel.turns.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val titles = library.associate { it.id to it.title }
    val itemCount = turns.size + (if (pending != null) 1 else 0) + (if (!streaming.isNullOrEmpty()) 1 else 0)

    LaunchedEffect(itemCount) { if (itemCount > 0) listState.animateScrollToItem(itemCount) }
    // Follow the streamed answer as it grows (no animation, so fast token bursts stay smooth).
    LaunchedEffect(streaming?.length) { if (!streaming.isNullOrEmpty()) listState.scrollToItem(itemCount) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            CocoAvatar(44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Coco", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
                Text(
                    "Knows your ${library.size} saved recipe${if (library.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (turns.isNotEmpty() || pending != null) TextButton(onClick = viewModel::clear) { Text("New chat", color = Ink, fontWeight = FontWeight.Bold) }
        }
        HorizontalDivider(color = Hairline.copy(alpha = 0.5f))
        if (pending != null && streaming.isNullOrEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Mustard)

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val emptyThread = turns.isEmpty() && pending == null
            if (library.isEmpty() && emptyThread) item(key = "empty") {
                // Soft empty state: titleMedium "Coco" + ≤2 Muted lines + starter SoftChips + ONE PillButton.
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Coco", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Ink)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "Paste a YouTube or Instagram recipe on Discover and I'll help you cook from your library.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.size(16.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        starters.forEach { s -> SoftChip(selected = false, onClick = { viewModel.send(s) }, label = s) }
                    }
                    Spacer(Modifier.size(20.dp))
                    PillButton("Paste a link", onClick = onOpenDiscover, fill = false)
                }
            } else item(key = "greeting") {
                Column {
                    Bubble(
                        if (library.isEmpty()) "Hi — I'm Coco. Paste a YouTube or Instagram recipe on Discover and I'll help you cook from your library."
                        else "Hi — I'm Coco. Ask about your saved recipes, or add one to your meal plan or grocery list.",
                        mine = false,
                    )
                    if (emptyThread) {
                        Spacer(Modifier.size(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            starters.forEach { s -> SoftChip(selected = false, onClick = { viewModel.send(s) }, label = s) }
                        }
                    }
                }
            }
            itemsIndexed(turns, key = { _, t -> t.id }) { i, t ->
                val isLast = i == turns.lastIndex && pending == null
                Column {
                    Bubble(t.question, mine = true)
                    Spacer(Modifier.size(8.dp))
                    Bubble(t.answer, mine = false)
                    val ids = if (t.actionState == CocoActionState.CLARIFY) emptyList() else t.recipeIdList.filter { it in titles }
                    if (ids.isNotEmpty()) {
                        Spacer(Modifier.size(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ids.forEach { id -> SoftChip(selected = true, onClick = { onOpenRecipe(id) }, label = titles.getValue(id)) }
                        }
                    }
                    if (isLast && t.actionState == CocoActionState.PROPOSED) {
                        Spacer(Modifier.size(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("Confirm", onClick = { viewModel.confirm(t) }, modifier = Modifier.weight(1f))
                            SoftOutlinePill("Cancel", onClick = { viewModel.cancel(t) }, modifier = Modifier.weight(1f), fill = true)
                        }
                    }
                    if (isLast && t.actionState == CocoActionState.CLARIFY) {
                        val options = viewModel.options(t)
                        if (options.isNotEmpty()) {
                            Spacer(Modifier.size(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                options.forEach { o -> SoftChip(selected = false, onClick = { viewModel.send(o) }, label = o) }
                            }
                        }
                    }
                }
            }
            pending?.let { q -> item(key = "pending") { Bubble(q, mine = true) } }
            streaming?.takeIf { it.isNotEmpty() }?.let { partial -> item(key = "streaming") { Bubble(partial, mine = false) } }
        }

        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SoftField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = "Ask about your recipes", singleLine = false,
                shape = RoundedCornerShape(24.dp),
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                enabled = input.isNotBlank() && pending == null,
                onClick = { viewModel.send(input); input = "" },
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MustardDeep) }
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.Top) {
            if (!mine) { CocoAvatar(24); Spacer(Modifier.width(8.dp)) }
            Text(
                text, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.widthIn(max = 300.dp)
                    .then(if (mine) Modifier else Modifier.shadow(4.dp, RoundedCornerShape(24.dp), ambientColor = androidx.compose.ui.graphics.Color(0x1F000000), spotColor = androidx.compose.ui.graphics.Color(0x14000000)))
                    .background(if (mine) MustardCream else MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            )
        }
    }
}
