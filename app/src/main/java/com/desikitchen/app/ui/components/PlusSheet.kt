package com.desikitchen.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.desikitchen.app.ui.theme.Hairline
import com.desikitchen.app.ui.theme.Ink
import com.desikitchen.app.ui.theme.SoftSage
import com.desikitchen.app.ui.theme.SoftSageFill
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** DesiKitchen Plus price points shown on the Soft paywall (display only — no billing in Phase 1). */
enum class PlusPlan(val label: String, val price: String, val note: String) {
    MONTHLY("Monthly", "₹69/mo", "Cancel anytime"),
    YEARLY("Yearly", "₹699/yr", "About ₹58 a month"),
}

/** "Resets Tue, 4 Nov at 9:30 PM" for the moment the oldest counted import leaves the rolling window. */
fun importResetLine(resetAtMillis: Long?, zone: ZoneId = ZoneId.systemDefault()): String? = resetAtMillis?.let {
    "Your next free import unlocks " +
        DateTimeFormatter.ofPattern("EEE, d MMM 'at' h:mm a", Locale.getDefault()).format(Instant.ofEpochMilli(it).atZone(zone)) + "."
}

/**
 * Soft paywall (Design Lab #11): a calm bottom sheet, no quiz / countdown / strike-through / confetti.
 * Title + ≤2 Muted lines, two plan SoftCards (selected = SoftSageFill + 1.5dp SoftSage border), one PillButton
 * "Get Plus" and a quiet "Not now". [onGetPlus] receives the chosen plan; billing is wired by the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlusSheet(
    title: String,
    message: String?,
    onGetPlus: (PlusPlan) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var plan by rememberSaveable { mutableStateOf(PlusPlan.YEARLY) }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { SoftGrabHandle(Modifier.padding(top = 12.dp, bottom = 6.dp)) },
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = Ink, textAlign = TextAlign.Center,
            )
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, maxLines = 2,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlusPlan.entries.forEach { p ->
                    val selected = p == plan
                    val shape = RoundedCornerShape(SoftCardStyle.InSheet.radius)
                    Column(
                        Modifier.weight(1f).clip(shape)
                            .background(if (selected) SoftSageFill else MaterialTheme.colorScheme.surface, shape)
                            .border(if (selected) 1.5.dp else 1.dp, if (selected) SoftSage else Hairline, shape)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { plan = p })
                            .padding(16.dp),
                    ) {
                        Text(p.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(p.price, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Ink)
                        Spacer(Modifier.height(2.dp))
                        Text(p.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Plus: unlimited imports and curated collections.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            PillButton("Get Plus", onClick = { onGetPlus(plan) })
            TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
                Text("Not now", color = Ink, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Grab handle for sheets that really drag (ModalBottomSheet). Never on static SoftSheets (no fake affordances). */
@Composable
fun SoftGrabHandle(modifier: Modifier = Modifier) {
    Box(modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline))
}
