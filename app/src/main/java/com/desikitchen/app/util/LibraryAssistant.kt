package com.desikitchen.app.util

import com.desikitchen.app.data.model.Recipe
import kotlin.coroutines.cancellation.CancellationException

/**
 * Coco — the Assistant tab (Sai lock #15). Talks over the user's saved library only: answers come from OpenRouter
 * when a key is configured (BuildConfig, never in source), otherwise from a small offline matcher.
 * Never invents ingredients: the model is told to use only the library below, and the offline path only quotes it.
 * Unlimited (no quota) in Phase 1.
 */
class LibraryAssistant(private val client: OpenRouterClient?) {
    data class Turn(val question: String, val answer: String, val ai: Boolean)
    data class Answer(val text: String, val ai: Boolean, val recipeIds: List<Long> = emptyList())

    /** One saved recipe as Coco sees it: title + ingredient names as stored (no quantities guessed). */
    data class Entry(val id: Long, val title: String, val ingredients: List<String>, val creator: String? = null, val platform: String? = null, val favorite: Boolean = false)

    val aiAvailable: Boolean get() = client != null

    /** Blocking; call on Dispatchers.IO. */
    fun ask(library: List<Entry>, question: String, history: List<Turn>): Answer {
        val matches = match(library, question)
        val c = client ?: return Answer(offline(library, question, matches), ai = false, recipeIds = matches.map { it.id })
        return runCatching {
            Answer(c.chat(messages(library, question, history), temperature = 0.4, maxTokens = 700), ai = true, recipeIds = matches.map { it.id })
        }.getOrElse {
            Answer(offline(library, question, matches), ai = false, recipeIds = matches.map { it.id })
        }
    }

    /**
     * Like [ask], but streams the OpenRouter answer: [onPartial] gets the answer-so-far after every fragment.
     * Blocking; call on Dispatchers.IO. A CancellationException thrown from [onPartial] (New chat / leaving) stops the
     * stream and is rethrown. Any other stream failure falls back to one non-streamed request, then to the offline
     * matcher. Offline stays non-streaming.
     */
    fun askStreaming(library: List<Entry>, question: String, history: List<Turn>, onPartial: (String) -> Unit): Answer {
        val matches = match(library, question)
        val ids = matches.map { it.id }
        val c = client ?: return Answer(offline(library, question, matches), ai = false, recipeIds = ids)
        val msgs = messages(library, question, history)
        val sb = StringBuilder()
        try {
            return Answer(c.chatStream(msgs, temperature = 0.4, maxTokens = 700) { d -> sb.append(d); onPartial(sb.toString()) }, ai = true, recipeIds = ids)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) { /* fall through to a non-streamed retry */ }
        return try {
            Answer(c.chat(msgs, temperature = 0.4, maxTokens = 700), ai = true, recipeIds = ids)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            Answer(offline(library, question, matches), ai = false, recipeIds = ids)
        }
    }

    companion object {
        const val MAX_CONTEXT_RECIPES = 80
        private val stop = setOf(
            "a", "an", "the", "and", "or", "with", "without", "for", "to", "of", "in", "on", "my", "me", "i", "can", "what",
            "which", "make", "cook", "recipe", "recipes", "something", "some", "have", "got", "is", "are", "do", "any",
            "tonight", "today", "dinner", "lunch", "breakfast", "want", "please", "show", "find", "saved", "using", "use",
        )

        fun fromBuildConfig() = LibraryAssistant(OpenRouterClient.fromBuildConfig())

        fun entries(recipes: List<Recipe>, ingredientIndex: Map<Long, List<String>>): List<Entry> = recipes.map {
            Entry(it.id, it.title, ingredientIndex[it.id].orEmpty(), it.creatorHandle, it.sourcePlatform, it.isFavorite)
        }

        fun keywords(question: String): List<String> =
            question.lowercase().split(Regex("[^\\p{L}0-9]+")).filter { it.length > 1 && it !in stop }.distinct()

        /** Saved recipes whose title or ingredients mention the question's keywords, best first. */
        fun match(library: List<Entry>, question: String, limit: Int = 5): List<Entry> {
            val keys = keywords(question)
            if (keys.isEmpty()) return emptyList()
            return library.map { e ->
                val title = e.title.lowercase()
                val ings = e.ingredients.map { it.lowercase() }
                e to keys.sumOf { k -> (if (k in title) 3 else 0) + ings.count { k in it } }
            }.filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }
        }

        fun offline(library: List<Entry>, question: String, matches: List<Entry> = match(library, question)): String = when {
            library.isEmpty() ->
                "Your library is empty right now. Paste a YouTube or Instagram recipe link on Discover and I'll help you cook from it."
            matches.isNotEmpty() -> buildString {
                append("From your saved recipes, these fit best:\n")
                matches.forEach { e ->
                    append("• ").append(e.title)
                    if (e.ingredients.isNotEmpty()) append(" — ").append(e.ingredients.take(6).joinToString(", "))
                    append('\n')
                }
                append("Open one to see the full ingredients and steps.")
            }
            else -> buildString {
                append("I couldn't find that in your ${library.size} saved recipe${if (library.size == 1) "" else "s"}. ")
                append("Try an ingredient or dish name, for example ")
                append(library.take(2).joinToString(" or ") { "“${it.title}”" }).append('.')
            }
        }

        fun systemPrompt(library: List<Entry>): String = buildString {
            appendLine("You are Coco, the friendly cooking assistant inside the DesiKitchen app. Be warm, brief and practical.")
            appendLine("You can ONLY talk about the user's saved recipes listed below. Never invent ingredients, quantities, recipes or creators.")
            appendLine("If something isn't in the library, say so and suggest the closest saved recipe, or suggest importing a YouTube/Instagram link.")
            appendLine("Plain text, no markdown tables. Mention recipes by their exact title.")
            appendLine("You cannot change the meal plan or grocery list yourself; if asked, tell the user to say e.g. \"add <exact saved title> to Tuesday dinner\" or \"add <exact saved title> to my grocery list\" and the app will confirm it.")
            appendLine()
            if (library.isEmpty()) appendLine("The library is empty.")
            else {
                appendLine("Saved library (${library.size} recipes${if (library.size > MAX_CONTEXT_RECIPES) ", first $MAX_CONTEXT_RECIPES shown" else ""}):")
                library.take(MAX_CONTEXT_RECIPES).forEach { e ->
                    append("- ").append(e.title)
                    if (e.favorite) append(" [favourite]")
                    if (!e.creator.isNullOrBlank()) append(" (by ").append(e.creator).append(')')
                    if (e.ingredients.isNotEmpty()) append(": ").append(e.ingredients.take(20).joinToString(", "))
                    appendLine()
                }
            }
        }

        fun messages(library: List<Entry>, question: String, history: List<Turn>): List<OpenRouterClient.Message> =
            listOf(OpenRouterClient.Message("system", systemPrompt(library))) +
                history.takeLast(8).flatMap { listOf(OpenRouterClient.Message("user", it.question), OpenRouterClient.Message("assistant", it.answer)) } +
                OpenRouterClient.Message("user", question)
    }
}
