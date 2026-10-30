package com.desikitchen.app.ui.importer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.desikitchen.app.ui.components.DkScaffold
import com.desikitchen.app.ui.components.DkTopBar
import com.desikitchen.app.ui.components.PillButton
import com.desikitchen.app.ui.components.SoftCard
import com.desikitchen.app.ui.components.SoftField
import com.desikitchen.app.util.ImportJob
import com.desikitchen.app.util.ImportQuota

/**
 * Paste-link import: field, Import, quota line. Nothing else.
 * The field takes one or more YouTube / Instagram links (one per line, or separated by spaces). Import queues every
 * supported link in the app-wide [com.desikitchen.app.util.BackgroundImporter] (appending if a queue is already
 * running) and closes this screen at once, so the user keeps browsing while the status pill shows progress.
 * [onQueued] gets a short summary such as "3 links queued; skipped 1 unsupported" (shown as a snackbar).
 */
@Composable
fun WebImportScreen(
    initialUrl: String?,
    onBack: () -> Unit,
    onQueued: (String) -> Unit = {},
    viewModel: ImportStatusViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = com.desikitchen.app.ui.AppViewModelProvider.Factory),
) {
    val clipboard = LocalClipboardManager.current
    var url by rememberSaveable { mutableStateOf(initialUrl.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val importTimes by viewModel.quotaTimes.collectAsState()
    val job by viewModel.job.collectAsState()
    val isPlus by viewModel.isPlus.collectAsState()
    val running = job as? ImportJob.Running

    fun go() {
        val text = url.trim()
        if (text.isBlank()) return
        val result = viewModel.startMany(text)
        if (result.queued == 0) { error = result.message; return }
        result.message?.let(onQueued)
        onBack()
    }

    DkScaffold(topBar = { DkTopBar("Import a recipe", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 12.dp)) {
            SoftCard(Modifier.fillMaxWidth()) {
                SoftField(
                    value = url, onValueChange = { url = it; error = null },
                    placeholder = "Paste one or more YouTube / Instagram links",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false, minLines = 3,
                    isError = error != null,
                    // Multiline: Enter adds a new line for the next link; the Import button submits.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Default),
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { pasted ->
                                // Append to what's already there so several copied links can be collected.
                                url = if (url.isBlank()) pasted else url.trimEnd() + "\n" + pasted
                                error = null
                            }
                        }) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = "Paste")
                        }
                    },
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(14.dp))
                PillButton(
                    // Appending to a running queue is allowed; the label just says so.
                    text = if (running != null) "Add to import queue" else "Import",
                    onClick = ::go, enabled = url.isNotBlank(),
                )
                running?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (it.queued > 0) "Importing now \u00b7 ${it.queued} more in the queue" else "One recipe is importing now",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    ImportQuota.statusLine(importTimes, System.currentTimeMillis(), isPlus),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
