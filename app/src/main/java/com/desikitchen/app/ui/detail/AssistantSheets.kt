package com.desikitchen.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.desikitchen.app.data.model.RecipeWithDetails
import com.desikitchen.app.util.RecipeAiAssistant
import com.desikitchen.app.util.RecipeAssistant
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coco sheet, scoped to one recipe: suggested prompts + free questions with conversation history.
 * Answers come from OpenRouter AI when OPENROUTER_API_KEY is configured; otherwise (or on error) offline rules.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AskDesiKitchenSheet(details: RecipeWithDetails, onDismiss: () -> Unit, servings: Int = details.recipe.servings, step: Int? = null) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val assistant = remember { RecipeAiAssistant.fromBuildConfig() }
    val turns = remember { mutableStateListOf<RecipeAiAssistant.Turn>() }
    val notes = remember { mutableStateMapOf<Int, String>() }
    var pending by remember { mutableStateOf<String?>(null) }
    var question by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun send(q: String) {
        if (q.isBlank() || pending != null) return
        pending = q
        scope.launch {
            val a = withContext(Dispatchers.IO) { assistant.ask(details, q, turns.toList(), servings, step) }
            a.note?.let { notes[turns.size] = it }
            turns += RecipeAiAssistant.Turn(q, a.text, a.ai)
            pending = null
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Coco", style = MaterialTheme.typography.headlineSmall)
            Text(
                "About ${details.recipe.title}" + (step?.let { " · step ${it + 1}" } ?: "") +
                    ". I'll only use what's in this recipe — never invent ingredients or steps.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecipeAssistant.Prompt.entries.forEach { p ->
                    com.desikitchen.app.ui.components.SoftChip(selected = false, enabled = pending == null, onClick = { send(p.label) }, label = p.label)
                }
            }
            turns.forEachIndexed { i, t ->
                Spacer(Modifier.height(12.dp))
                // In-sheet card preset: flat + hairline (the sheet already floats).
                com.desikitchen.app.ui.components.SoftCardInSheet(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                    Column {
                        Text(t.question, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (t.ai) "From this recipe" else "From this recipe · on your phone",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        notes[i]?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                        Spacer(Modifier.height(6.dp))
                        Text(t.answer.replace("**", ""), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            pending?.let { q ->
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Thinking about “${q.take(40)}”…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = question, onValueChange = { question = it },
                placeholder = { Text(if (turns.isEmpty()) "Ask about this recipe…" else "Ask a follow-up…") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(enabled = question.isNotBlank() && pending == null, onClick = { send(question.trim()); question = "" }) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Ask")
                    }
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
