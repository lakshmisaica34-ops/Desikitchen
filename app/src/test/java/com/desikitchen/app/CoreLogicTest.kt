package com.desikitchen.app

import com.desikitchen.app.data.model.Ingredient
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.model.RecipeWithDetails
import com.desikitchen.app.data.model.Step
import com.desikitchen.app.ui.detail.buildStepText
import com.desikitchen.app.util.NutritionEstimator
import com.desikitchen.app.util.RecipeAssistant
import com.desikitchen.app.util.RecipeTextParser
import com.desikitchen.app.util.UnitSystem
import com.desikitchen.app.util.Units
import com.desikitchen.app.util.WebRecipeFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {
    private val chai = """Masala Chai
Serves 2

Ingredients
2 cups water
1 cup milk
2 tsp tea leaves
3 green cardamom, crushed

Method
1. Boil water with cardamom for 2 minutes.
2. Pour in milk, bring to a boil at 100°C, then strain."""

    private fun details(): RecipeWithDetails {
        val p = RecipeTextParser.parse(chai)
        return RecipeWithDetails(
            Recipe(id = 1, title = p.title, servings = p.servings ?: 2),
            p.ingredients.mapIndexed { i, it -> Ingredient(id = i + 1L, recipeId = 1, position = i, name = it.name, quantity = it.quantity, unit = it.unit, aisle = it.aisle) },
            p.steps.mapIndexed { i, s -> Step(id = i + 1L, recipeId = 1, position = i, text = s) },
        )
    }

    @Test fun pasteParserExtractsSections() {
        val p = RecipeTextParser.parse(chai)
        assertEquals("Masala Chai", p.title)
        assertEquals(2, p.servings)
        assertEquals(4, p.ingredients.size)
        assertEquals(2, p.steps.size)
        println("parsed: $p")
    }

    @Test fun unitsConvert() {
        println(Units.convert(500.0, "g", UnitSystem.IMPERIAL).format())
        println(Units.convert(2.0, "cups", UnitSystem.METRIC).format())
    }

    @Test fun urlExtraction() {
        assertEquals("https://example.com/r/dal", WebRecipeFetcher.extractUrl("Try this (https://example.com/r/dal)."))
    }

    @Test fun stepHighlights() {
        val d = details()
        val s = buildStepText(d.sortedSteps[1].text, d.sortedIngredients) {}
        val links = s.getLinkAnnotations(0, s.length)
        println("links: ${links.map { s.substring(it.start, it.end) }}")
        assertTrue(links.size >= 2) // "milk" + "100°C"
    }

    @Test fun assistantAndNutrition() {
        val d = details()
        RecipeAssistant.Prompt.entries.forEach { assertTrue(RecipeAssistant.answer(it, d).isNotBlank()) }
        println(NutritionEstimator.estimate(d.sortedIngredients, 2))
    }
}
