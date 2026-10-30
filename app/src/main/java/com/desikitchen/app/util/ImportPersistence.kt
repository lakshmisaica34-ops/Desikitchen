package com.desikitchen.app.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * Where [BackgroundImporter] keeps unfinished link-import work so it survives the app being killed: the links still
 * waiting (including the one that was running) and the drafts parked for review. Production = Room
 * ([com.desikitchen.app.data.repo.RoomImportPersistence]); tests use an in-memory fake.
 */
interface ImportPersistence {
    /** Everything saved last time, or an empty snapshot. */
    suspend fun load(): Snapshot

    /** Replaces everything saved with [snapshot]. */
    suspend fun save(snapshot: Snapshot)

    /** [queue] = links to (re-)import, in order (the running one first). */
    data class Snapshot(
        val queue: List<String> = emptyList(),
        val parked: List<ParkedDraft> = emptyList(),
        /** End-of-batch summary / bare failure the user hasn't dismissed yet. */
        val notice: String? = null,
        /** The draft handed to the Review editor and not yet saved or discarded (survives app kill). */
        val editor: EditorDraft? = null,
    ) {
        val isEmpty: Boolean get() = queue.isEmpty() && parked.isEmpty() && notice == null && editor == null
    }

    /** A draft waiting for review; [failMessage] is set for the partial draft of a failed import. */
    data class ParkedDraft(val draft: ImportDraft.Draft, val failMessage: String? = null)

    /**
     * The editor session: the draft exactly as handed to Review plus [edits] = the user's in-progress form fields
     * (opaque JSON written by the editor; null until the user changes something).
     */
    data class EditorDraft(val draft: ImportDraft.Draft, val failMessage: String? = null, val edits: String? = null)
}

/** Lossless JSON for [ImportDraft.Draft] (every field; nothing is filled in on decode that wasn't stored). */
object ImportDraftCodec {
    private const val VERSION = 1

    fun encode(d: ImportDraft.Draft): String = JSONObject().apply {
        put("v", VERSION)
        put("title", d.parsed.title)
        d.parsed.servings?.let { put("servings", it) }
        put("ingredients", JSONArray().apply {
            d.parsed.ingredients.forEach { i ->
                put(JSONObject().apply {
                    i.quantity?.takeIf { it.isFinite() }?.let { put("quantity", it) }
                    put("unit", i.unit)
                    put("name", i.name)
                    put("aisle", i.aisle)
                })
            }
        })
        put("steps", JSONArray().apply { d.parsed.steps.forEach { put(it) } })
        put("description", d.description)
        d.imageUrl?.let { put("imageUrl", it) }
        d.sourceUrl?.let { put("sourceUrl", it) }
        put("prepMinutes", d.prepMinutes)
        put("cookMinutes", d.cookMinutes)
        put("warnings", JSONArray().apply { d.warnings.forEach { put(it) } })
        put("partial", d.partial)
        d.creatorAvatarUrl?.let { put("creatorAvatarUrl", it) }
        d.creatorProfileUrl?.let { put("creatorProfileUrl", it) }
    }.toString()

    /** Null when [json] is unreadable (the entry is then dropped rather than guessed at). */
    fun decode(json: String?): ImportDraft.Draft? = runCatching {
        val o = JSONObject(json ?: return null)
        val ing = o.optJSONArray("ingredients") ?: JSONArray()
        val steps = o.optJSONArray("steps") ?: JSONArray()
        val warnings = o.optJSONArray("warnings") ?: JSONArray()
        ImportDraft.Draft(
            parsed = RecipeTextParser.ParsedRecipe(
                title = o.optString("title", ""),
                servings = if (o.has("servings") && !o.isNull("servings")) o.getInt("servings") else null,
                ingredients = (0 until ing.length()).map { k ->
                    val i = ing.getJSONObject(k)
                    RecipeTextParser.ParsedIngredient(
                        quantity = if (i.has("quantity") && !i.isNull("quantity")) i.getDouble("quantity") else null,
                        unit = i.optString("unit", ""),
                        name = i.getString("name"),
                        aisle = i.optString("aisle", ""),
                    )
                },
                steps = (0 until steps.length()).map { steps.getString(it) },
            ),
            description = o.optString("description", ""),
            imageUrl = o.optStringOrNull("imageUrl"),
            sourceUrl = o.optStringOrNull("sourceUrl"),
            prepMinutes = o.optInt("prepMinutes", 0),
            cookMinutes = o.optInt("cookMinutes", 0),
            warnings = (0 until warnings.length()).map { warnings.getString(it) },
            partial = o.optBoolean("partial", false),
            creatorAvatarUrl = o.optStringOrNull("creatorAvatarUrl"),
            creatorProfileUrl = o.optStringOrNull("creatorProfileUrl"),
        )
    }.getOrNull()

    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
}
