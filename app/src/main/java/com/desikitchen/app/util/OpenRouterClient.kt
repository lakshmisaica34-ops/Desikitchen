package com.desikitchen.app.util

import com.desikitchen.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal OpenRouter chat-completions client (text only) shared by Ask DesiKitchen and caption / comment
 * recipe extraction. Key and model come from BuildConfig (local.properties → OPENROUTER_API_KEY / OPENROUTER_MODEL);
 * the key is never logged or shown. [transport] is injectable so request building and parsing are unit-tested.
 */
class OpenRouterClient(
    private val apiKey: String,
    val model: String,
    private val endpoint: String = ENDPOINT,
    private val transport: (url: String, headers: Map<String, String>, body: String) -> Pair<Int, String> = ::httpPost,
    /**
     * Streaming (SSE) transport: calls `onLine` for every response line while it returns true, then returns the HTTP
     * code (and the error body for non-2xx). Injectable like [transport] so the stream contract is unit-tested.
     */
    private val streamTransport: (url: String, headers: Map<String, String>, body: String, onLine: (String) -> Boolean) -> Pair<Int, String> = ::httpStream,
) {
    data class Message(val role: String, val content: String)

    /** One parsed server-sent-events line of a streamed completion. */
    sealed interface StreamEvent {
        data class Delta(val text: String) : StreamEvent
        data object Done : StreamEvent
    }

    /**
     * Blocking streamed completion; call from Dispatchers.IO. [onDelta] receives each text fragment as it arrives
     * (it may throw, e.g. a CancellationException, to stop reading). Returns the whole trimmed answer.
     * Throws on HTTP / format errors or an empty answer, exactly like [chat].
     */
    fun chatStream(
        messages: List<Message>, temperature: Double = 0.2, maxTokens: Int = 900, onDelta: (String) -> Unit,
    ): String {
        val out = StringBuilder()
        val (code, errorBody) = streamTransport(
            endpoint, headers() + ("Accept" to "text/event-stream"), buildBody(messages, false, temperature, maxTokens, stream = true),
        ) { line ->
            when (val ev = parseStreamLine(line)) {
                null -> true
                StreamEvent.Done -> false
                is StreamEvent.Delta -> { out.append(ev.text); onDelta(ev.text); true }
            }
        }
        if (code !in 200..299) error(errorMessage(code, errorBody))
        val text = out.toString().trim()
        if (text.isEmpty()) error("OpenRouter returned an empty answer")
        return text
    }

    /** Blocking; call from Dispatchers.IO. Returns the assistant message text. Throws on HTTP / format errors. */
    fun chat(messages: List<Message>, json: Boolean = false, temperature: Double = 0.2, maxTokens: Int = 900): String {
        val (code, text) = transport(endpoint, headers(), buildBody(messages, json, temperature, maxTokens))
        if (code !in 200..299) error(errorMessage(code, text))
        return parseContent(text)
    }

    internal fun headers(): Map<String, String> = mapOf(
        "Authorization" to "Bearer $apiKey",
        "Content-Type" to "application/json",
        "X-Title" to "DesiKitchen",
        "HTTP-Referer" to "https://github.com/desikitchen/android",
    )

    internal fun buildBody(messages: List<Message>, json: Boolean, temperature: Double, maxTokens: Int, stream: Boolean = false): String {
        val o = JSONObject()
            .put("model", model)
            .put("temperature", temperature)
            .put("max_tokens", maxTokens)
            .put("messages", JSONArray().apply { messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) } })
        if (json) o.put("response_format", JSONObject().put("type", "json_object"))
        if (stream) o.put("stream", true)
        return o.toString()
    }

    companion object {
        const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

        val isConfigured: Boolean get() = BuildConfig.OPENROUTER_API_KEY.isNotBlank()

        fun fromBuildConfig(): OpenRouterClient? =
            BuildConfig.OPENROUTER_API_KEY.takeIf { it.isNotBlank() }?.let { OpenRouterClient(it, BuildConfig.OPENROUTER_MODEL) }

        fun parseContent(response: String): String {
            val root = JSONObject(response)
            root.optJSONObject("error")?.let { error("OpenRouter: ${it.optString("message").take(160)}") }
            val msg = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val content = msg.opt("content")
            val text = when (content) {
                is String -> content
                is JSONArray -> (0 until content.length()).joinToString("") { content.optJSONObject(it)?.optString("text").orEmpty() }
                else -> ""
            }.trim()
            if (text.isEmpty()) error("OpenRouter returned an empty answer")
            return text
        }

        /**
         * Parses one SSE line of an OpenRouter stream: `data: {json}` → [StreamEvent.Delta] (choices[0].delta.content),
         * `data: [DONE]` → [StreamEvent.Done]; blank lines, `: keep-alive` comments and empty deltas → null.
         * An `error` object in a chunk throws (mid-stream provider errors).
         */
        fun parseStreamLine(line: String): StreamEvent? {
            val l = line.trim()
            if (!l.startsWith("data:")) return null
            val data = l.removePrefix("data:").trim()
            if (data.isEmpty()) return null
            if (data == "[DONE]") return StreamEvent.Done
            val root = runCatching { JSONObject(data) }.getOrNull() ?: return null
            root.optJSONObject("error")?.let { error("OpenRouter: ${it.optString("message").take(160)}") }
            val choice = root.optJSONArray("choices")?.optJSONObject(0) ?: return null
            val content = choice.optJSONObject("delta")?.opt("content")
            val text = when (content) {
                is String -> content
                is JSONArray -> (0 until content.length()).joinToString("") { content.optJSONObject(it)?.optString("text").orEmpty() }
                else -> ""
            }
            return if (text.isEmpty()) null else StreamEvent.Delta(text)
        }

        /** Human-readable, never echoes headers or the key. */
        internal fun errorMessage(code: Int, body: String): String {
            val detail = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()?.take(120)
            return when (code) {
                401, 403 -> "AI key was rejected (HTTP $code)"
                402 -> "AI account is out of credits (HTTP 402)"
                429 -> "AI is busy — too many requests (HTTP 429)"
                else -> "AI request failed (HTTP $code${detail?.let { ": $it" } ?: ""})"
            }
        }

        private fun httpPost(url: String, headers: Map<String, String>, body: String): Pair<Int, String> {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                connectTimeout = 15_000; readTimeout = 60_000
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                val text = ((if (code >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.use { it.readText() }).orEmpty()
                return code to text
            } finally { conn.disconnect() }
        }
    }
}

/** SSE transport for [OpenRouterClient.chatStream]: reads line by line until the stream ends or [onLine] says stop. */
private fun httpStream(url: String, headers: Map<String, String>, body: String, onLine: (String) -> Boolean): Pair<Int, String> {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"; doOutput = true
        connectTimeout = 15_000; readTimeout = 60_000
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
    }
    try {
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        if (code >= 400) return code to (conn.errorStream?.bufferedReader()?.use { it.readText() }).orEmpty()
        conn.inputStream.bufferedReader().use { r ->
            while (true) {
                val line = r.readLine() ?: break
                if (!onLine(line)) break
            }
        }
        return code to ""
    } finally { conn.disconnect() }
}

/**
 * Reads a recipe out of a caption / creator comment / video description with OpenRouter. Copies only what the
 * text states (the prompt forbids guessing); the importer marks the result PARTIAL with a "check it" warning.
 */
class TextRecipeExtractor(private val client: OpenRouterClient) {
    val model: String get() = client.model

    fun extract(source: String, text: String, system: String = SYSTEM): VideoRecipeExtractor.Extracted? {
        if (text.isBlank()) return null
        val content = client.chat(
            listOf(
                OpenRouterClient.Message("system", system),
                OpenRouterClient.Message("user", "Source: $source\n\n" + text.take(12_000)),
            ),
            json = true, temperature = 0.0, maxTokens = 1500,
        )
        return VideoRecipeExtractor.parseModelJson(content)
    }

    companion object {
        fun fromBuildConfig(): TextRecipeExtractor? = OpenRouterClient.fromBuildConfig()?.let(::TextRecipeExtractor)

        /** Stage 3 (1.1.3): the cheapest OpenRouter text model (OPENROUTER_TEXT_MODEL, default mistralai/mistral-nemo). */
        fun cheapFromBuildConfig(): TextRecipeExtractor? = BuildConfig.OPENROUTER_API_KEY.takeIf { it.isNotBlank() }
            ?.let { TextRecipeExtractor(OpenRouterClient(it, BuildConfig.OPENROUTER_TEXT_MODEL)) }

        internal val COMMENTS_SYSTEM = """
            You read an Instagram cooking reel's caption and its top comments. Some comment or the caption MAY contain the recipe (often pinned by the creator).
            Copy ONLY ingredients and steps that are written in that text, in English. Never guess, never infer from the dish name,
            never use a viewer's suggestion or question as an ingredient. A dish description ("crispy halloumi, hot honey and chickpeas")
            or "full recipe on my site" is NOT a recipe: return empty lists. Never write a step that is not written in the text.
            ${RecipeSanitizer.PROMPT_BUCKET_RULES}
            Reply with JSON only: {"title": string|null, "servings": number|null, "ingredients": [string], "steps": [string]}
        """.trimIndent()

        /**
         * 2026-10-29 (DfEAPhVzLrs): a caption that holds a recipe but whose headings are wrong or missing (method
         * sentences under "Ingredients:", a nutrition block mixed in). The model only re-buckets the caption's own text.
         */
        internal val CAPTION_SPLIT_SYSTEM = """
            You split an Instagram recipe caption into ingredients and steps. The creator's headings may be WRONG
            (for example cooking instructions written under "Ingredients:"), so judge every line by what it is, not by its heading.
            Copy ONLY what is written in the caption, in English. Never guess, never add an ingredient, quantity or step that is not in the text.
            If the caption has no separate ingredient list, list each food item that the written instructions use, once, with the quantity
            exactly as written there (e.g. "1 tbsp yogurt"); write nothing that is not named in the caption.
            Steps are the caption's instruction sentences, in order, copied as written.
            ${RecipeSanitizer.PROMPT_BUCKET_RULES}
            Reply with JSON only: {"title": string|null, "servings": number|null, "ingredients": [string], "steps": [string]}
        """.trimIndent()

        internal val SYSTEM = """
            You extract recipes from social-media captions, creator comments and video descriptions.
            Write down ONLY ingredients and steps that are explicitly stated in the text. Never guess or add ingredients,
            quantities or steps; if a quantity isn't stated, write the ingredient without one. Keep the creator's wording,
            translated to English if needed. Ignore sponsorships, links and hashtags. If the text does not contain a recipe, return empty lists.
            ${RecipeSanitizer.PROMPT_BUCKET_RULES}
            Reply with JSON only: {"title": string|null, "servings": number|null, "ingredients": [string], "steps": [string]}
        """.trimIndent()
    }
}
