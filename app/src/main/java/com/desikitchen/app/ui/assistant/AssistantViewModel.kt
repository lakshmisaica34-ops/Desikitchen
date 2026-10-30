package com.desikitchen.app.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desikitchen.app.data.model.CocoActionState
import com.desikitchen.app.data.model.CocoTurn
import com.desikitchen.app.data.repo.CocoChatRepository
import com.desikitchen.app.data.repo.RecipeRepository
import com.desikitchen.app.util.CocoActionExecutor
import com.desikitchen.app.util.CocoActions
import com.desikitchen.app.util.LibraryAssistant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.coroutines.coroutineContext

/**
 * Coco chat over the saved library. Turns are persisted in Room ([CocoChatRepository]) and restored when the tab
 * opens; "New chat" clears them. OpenRouter answers stream into [streaming]; meal-plan / grocery requests are
 * resolved locally by [CocoActions] and only written after the user confirms.
 */
class AssistantViewModel(
    recipes: RecipeRepository,
    private val chat: CocoChatRepository,
    private val executor: CocoActionExecutor,
    private val assistant: LibraryAssistant = LibraryAssistant.fromBuildConfig(),
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    val library: StateFlow<List<LibraryAssistant.Entry>> =
        combine(recipes.observeAll(), recipes.observeIngredientIndex()) { all, index -> LibraryAssistant.entries(all, index) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Persisted conversation, oldest first. */
    val turns: StateFlow<List<CocoTurn>> = chat.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _pending = MutableStateFlow<String?>(null)
    /** Question being answered right now (shown as the user's bubble until the turn is saved). */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    private val _streaming = MutableStateFlow<String?>(null)
    /** Coco's answer so far while an OpenRouter stream is in flight; null otherwise. */
    val streaming: StateFlow<String?> = _streaming.asStateFlow()

    val aiAvailable: Boolean get() = assistant.aiAvailable

    private var job: Job? = null
    /** Bumped by every new request and by New chat, so a cancelled job can't reset a newer one's state. */
    private var generation = 0

    fun send(question: String) {
        val q = question.trim()
        if (q.isEmpty() || _pending.value != null) return
        _pending.value = q
        val gen = ++generation
        job = viewModelScope.launch {
            try {
                val lib = library.value
                val all = turns.value
                val last = all.lastOrNull()
                val id = when {
                    last?.actionState == CocoActionState.PROPOSED && CocoActions.isYes(q) -> confirmNow(last, q)
                    last?.actionState == CocoActionState.PROPOSED && CocoActions.isNo(q) -> cancelNow(last, q)
                    else -> {
                        val previous = if (last?.actionState == CocoActionState.CLARIFY) CocoActions.decode(last.action) else null
                        val reply = CocoActions.respond(lib, q, today(), previous, last?.recipeIdList.orEmpty())
                        if (reply != null) saveAction(q, reply) else answer(lib, q, all)
                    }
                }
                turns.first { list -> list.any { it.id == id } }
            } finally {
                if (gen == generation) { _streaming.value = null; _pending.value = null }
            }
        }
    }

    /** Quick-reply chips under an open follow-up (recipe titles from the library, days, meals). */
    fun options(turn: CocoTurn): List<String> {
        if (turn.actionState != CocoActionState.CLARIFY) return emptyList()
        val d = CocoActions.decode(turn.action) ?: return emptyList()
        return CocoActions.options(d, library.value, today())
    }

    fun confirm(turn: CocoTurn) = runTurn("Yes, add it") { q -> confirmNow(turn, q) }
    fun cancel(turn: CocoTurn) = runTurn("Cancel") { q -> cancelNow(turn, q) }

    /** New chat: stops any in-flight stream and clears the persisted thread. */
    fun clear() {
        generation++
        job?.cancel()
        job = null
        _streaming.value = null
        _pending.value = null
        viewModelScope.launch { chat.clear() }
    }

    private fun runTurn(label: String, block: suspend (String) -> Long) {
        if (_pending.value != null) return
        _pending.value = label
        val gen = ++generation
        job = viewModelScope.launch {
            try {
                val id = block(label)
                turns.first { list -> list.any { it.id == id } }
            } finally { if (gen == generation) _pending.value = null }
        }
    }

    private suspend fun confirmNow(turn: CocoTurn, q: String): Long {
        val draft = CocoActions.decode(turn.action)
        val text = if (draft == null) "Sorry, I lost track of that request. Could you ask again?" else executor.execute(draft)
        chat.setActionState(turn.id, CocoActionState.DONE)
        return chat.append(q, text, ai = false, recipeIds = listOfNotNull(draft?.recipeId))
    }

    private suspend fun cancelNow(turn: CocoTurn, q: String): Long {
        chat.setActionState(turn.id, CocoActionState.CANCELLED)
        return chat.append(q, "Okay, I won't change anything.", ai = false)
    }

    private suspend fun saveAction(q: String, reply: CocoActions.Reply): Long = when (reply) {
        is CocoActions.Reply.Propose ->
            chat.append(q, reply.text, ai = false, recipeIds = listOfNotNull(reply.draft.recipeId), action = reply.draft.encode(), actionState = CocoActionState.PROPOSED)
        is CocoActions.Reply.Ask ->
            chat.append(q, reply.text, ai = false, action = reply.draft.encode(), actionState = CocoActionState.CLARIFY)
        is CocoActions.Reply.Info -> chat.append(q, reply.text, ai = false)
    }

    private suspend fun answer(lib: List<LibraryAssistant.Entry>, q: String, all: List<CocoTurn>): Long {
        val history = all.filter { it.action == null }.map { LibraryAssistant.Turn(it.question, it.answer, it.ai) }
        if (assistant.aiAvailable) _streaming.value = ""
        val a = withContext(Dispatchers.IO) {
            val ctx = coroutineContext
            assistant.askStreaming(lib, q, history) { partial -> ctx.ensureActive(); _streaming.value = partial }
        }
        coroutineContext.ensureActive()
        return chat.append(q, a.text, a.ai, a.recipeIds)
    }
}
