package com.desikitchen.app.ui.edit

import com.desikitchen.app.data.model.Aisles
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lossless JSON for the Review editor's in-progress [RecipeForm] (the persisted editor session's edits). Every field
 * is stored exactly as typed (quantities and timers stay text); row keys are not stored and are reissued on decode.
 */
object RecipeFormCodec {
    private const val VERSION = 1

    fun encode(f: RecipeForm): String = JSONObject().apply {
        put("v", VERSION)
        put("title", f.title)
        put("description", f.description)
        put("imageUrl", f.imageUrl)
        put("cuisine", f.cuisine)
        put("mealType", f.mealType)
        put("dietTags", JSONArray().apply { f.dietTags.forEach { put(it) } })
        put("prep", f.prep)
        put("cook", f.cook)
        put("servings", f.servings)
        put("sourceUrl", f.sourceUrl)
        put("ingredients", JSONArray().apply {
            f.ingredients.forEach { r ->
                put(JSONObject().apply {
                    put("quantity", r.quantity); put("unit", r.unit); put("name", r.name)
                    put("aisle", r.aisle); put("note", r.note)
                })
            }
        })
        put("steps", JSONArray().apply { f.steps.forEach { r -> put(JSONObject().apply { put("text", r.text); put("timer", r.timer) }) } })
    }.toString()

    /** Null when [json] is missing / unreadable (the editor then shows the draft as imported). */
    fun decode(json: String?): RecipeForm? = runCatching {
        val o = JSONObject(json ?: return null)
        val tags = o.optJSONArray("dietTags") ?: JSONArray()
        val ing = o.optJSONArray("ingredients") ?: JSONArray()
        val steps = o.optJSONArray("steps") ?: JSONArray()
        RecipeForm(
            title = o.optString("title", ""),
            description = o.optString("description", ""),
            imageUrl = o.optString("imageUrl", ""),
            cuisine = o.optString("cuisine", ""),
            mealType = o.optString("mealType", ""),
            dietTags = (0 until tags.length()).map { tags.getString(it) }.toSet(),
            prep = o.optString("prep", ""),
            cook = o.optString("cook", ""),
            servings = o.optInt("servings", 4),
            sourceUrl = o.optString("sourceUrl", ""),
            ingredients = (0 until ing.length()).map { k ->
                val r = ing.getJSONObject(k)
                IngredientRow(
                    quantity = r.optString("quantity", ""), unit = r.optString("unit", ""), name = r.optString("name", ""),
                    aisle = r.optString("aisle", Aisles.OTHER), note = r.optString("note", ""),
                )
            }.ifEmpty { listOf(IngredientRow()) },
            steps = (0 until steps.length()).map { k ->
                val r = steps.getJSONObject(k)
                StepRow(text = r.optString("text", ""), timer = r.optString("timer", ""))
            }.ifEmpty { listOf(StepRow()) },
        )
    }.getOrNull()
}
