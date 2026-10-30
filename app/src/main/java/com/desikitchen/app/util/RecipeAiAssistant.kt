package com.desikitchen.app.util

import com.desikitchen.app.data.model.RecipeWithDetails

/**
 * Ask DesiKitchen with AI: OpenRouter (BuildConfig OPENROUTER_API_KEY) answers questions scoped to ONE recipe, with
 * the conversation so far. When no key is configured, or the request fails, [RecipeAssistant] answers offline.
 */
class RecipeAiAssistant(private val client: OpenRouterClient?) {
    data class Turn(val question: String, val answer: String, val ai: Boolean)
    data class Answer(val text: String, val ai: Boolean, val note: String? = null)

    val aiAvailable: Boolean get() = client != null

    /** Blocking; call on Dispatchers.IO. */
    fun ask(details: RecipeWithDetails, question: String, history: List<Turn>, servings: Int, step: Int? = null): Answer {
        val c = client ?: return Answer(offline(details, question), ai = false)
        return runCatching {
            Answer(c.chat(messages(details, question, history, servings, step), temperature = 0.4, maxTokens = 700), ai = true)
        }.getOrElse { e ->
            Answer(offline(details, question), ai = false, note = "AI unavailable (${e.message?.take(80)}). Offline answer:")
        }
    }

    private fun offline(details: RecipeWithDetails, question: String): String =
        RecipeAssistant.Prompt.entries.firstOrNull { it.label.equals(question, true) }?.let { RecipeAssistant.answer(it, details) }
            ?: RecipeAssistant.ask(question, details)

    companion object {
        fun fromBuildConfig() = RecipeAiAssistant(OpenRouterClient.fromBuildConfig())

        fun messages(details: RecipeWithDetails, question: String, history: List<Turn>, servings: Int, step: Int?): List<OpenRouterClient.Message> =
            buildList {
                add(OpenRouterClient.Message("system", systemPrompt(details, servings, step)))
                history.takeLast(6).forEach {
                    add(OpenRouterClient.Message("user", it.question))
                    add(OpenRouterClient.Message("assistant", it.answer))
                }
                add(OpenRouterClient.Message("user", question.take(2000)))
            }

        fun systemPrompt(details: RecipeWithDetails, servings: Int, step: Int?): String {
            val r = details.recipe
            val factor = servings.toDouble() / r.servings.coerceAtLeast(1)
            val ings = details.sortedIngredients.joinToString("\n") { i ->
                "- " + listOfNotNull(i.quantity?.let { formatAmount(it * factor) }, i.unit.ifBlank { null }, i.name).joinToString(" ") +
                    (if (i.note.isNotBlank()) ", ${i.note}" else "")
            }
            val steps = details.sortedSteps.mapIndexed { n, s -> "${n + 1}. ${s.text}" }.joinToString("\n")
            return """
                You are DesiKitchen's cooking assistant, helping a home cook with ONE recipe. Stay on this recipe and cooking in general;
                politely decline unrelated requests. Be concise (under 150 words), practical and friendly; use Indian ingredient names where helpful.
                Never claim the recipe says something it doesn't. Substitutions and tips are suggestions; mark food-safety advice clearly.
                Not medical advice: for allergies or health conditions, suggest checking with a professional.

                Recipe: ${r.title}${if (r.cuisine.isNotBlank()) " (${r.cuisine})" else ""}
                Original servings: ${r.servings}; the cook is making $servings (quantities below are scaled).
                Prep ${r.prepMinutes} min, cook ${r.cookMinutes} min.${if (r.dietTagList.isNotEmpty()) " Tags: ${r.dietTagList.joinToString()}." else ""}
                Ingredients:
                ${ings.ifBlank { "(none listed)" }}
                Steps:
                ${steps.ifBlank { "(none listed)" }}
                ${step?.let { "The cook is currently on step ${it + 1}." } ?: ""}
            """.lines().joinToString("\n") { it.trim() }.trim()
        }
    }
}
