package com.desikitchen.app.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.desikitchen.app.MainActivity
import com.desikitchen.app.DesiKitchenApp
import com.desikitchen.app.data.model.Ingredient
import com.desikitchen.app.data.model.Recipe
import com.desikitchen.app.data.model.Step
import kotlinx.coroutines.runBlocking
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real app (fresh empty install, real navigation) to PNGs for comparing against mocks/v1.
 * Opt-in: ./gradlew :app:testDebugUnitTest --tests '*UiTourScreenshots*' -Pscreens=1
 * Output: PNGs in app/build/screens
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class UiTourScreenshots {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun gate() = assumeTrue(System.getProperty("dk.screens").orEmpty().isNotBlank())

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(1200)
        Thread.sleep(300)
        rule.onRoot().captureRoboImage("build/screens/$name.png")
    }

    private fun waitText(t: String) {
        repeat(60) {
            if (rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()) return
            rule.mainClock.advanceTimeBy(250)
            Thread.sleep(100)
        }
        error("text not found: $t")
    }

    /** Phase 1 shell on a fresh (empty, unseeded) install: the five tabs + paste-link import. */
    @Test fun tour() {
        waitText("What's the plan,")
        waitText("Import a recipe")
        shot("01_discover_empty")
        rule.onAllNodesWithText("Collections").onFirst().performClick()
        waitText("Weeknight dal")
        shot("02_collections")
        rule.onNodeWithText("Assistant").performClick()
        waitText("I'm Coco")
        shot("03_assistant")
        rule.onNodeWithText("Saved").performClick()
        waitText("Meal plan")
        shot("04_saved_empty")
        rule.onNodeWithText("Profile").performClick()
        shot("05_profile")
        rule.onNodeWithText("Discover").performClick()
        waitText("Import a recipe")
        rule.onNodeWithText("Import").performClick()
        waitText("Import a recipe")
        shot("06_paste_link")
    }

    private fun softShot(name: String) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1200)
        Thread.sleep(300)
        rule.onRoot().captureRoboImage("build/screens/soft-02-05/$name.png")
    }

    private fun waitGone(t: String) {
        repeat(60) {
            if (rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()) return
            rule.mainClock.advanceTimeBy(250)
            Thread.sleep(100)
        }
    }

    /**
     * Soft pass (mocks 02–05) on the real app. Fresh install has no recipes, so one TEST-ONLY fixture recipe is
     * inserted through the real RecipeRepository after the Discover empty shots (nothing ships; no seed in the app).
     */
    @Test fun softPass() {
        // Mock 02: empty Discover, Soft paste hero + SoftSageFill tab indicator on Discover.
        waitText("What's the plan,")
        waitText("Import a recipe")
        softShot("01_discover_soft_paste_hero_empty")

        // Mock 03: real status pill. Typing a YouTube link + Import sets ImportJob.Running synchronously.
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        rule.onNodeWithText("Import").performClick()
        waitText("Importing from YouTube")
        rule.onRoot().captureRoboImage("build/screens/soft-02-05/02_import_status_pill_running.png")
        runCatching { rule.onNodeWithText("Cancel").performClick() }
        waitGone("Importing from YouTube")

        // Test fixture recipe (stated quantities/steps only).
        val app = rule.activity.application as DesiKitchenApp
        runBlocking {
            app.container.recipes.save(
                Recipe(
                    title = "Weeknight Chana Masala", description = "Soft, tangy chickpeas in an onion-tomato masala.",
                    cuisine = "North Indian", prepMinutes = 10, cookMinutes = 25, servings = 4,
                    sourceUrl = "https://www.instagram.com/reel/SoftTour01/", sourcePlatform = "instagram",
                    creatorHandle = "@desikitchen.demo", origin = "import",
                ),
                listOf(
                    Ingredient(position = 0, name = "Chickpeas, cooked", quantity = 2.0, unit = "cups"),
                    Ingredient(position = 1, name = "Onion, finely chopped", quantity = 1.0),
                    Ingredient(position = 2, name = "Tomatoes, pureed", quantity = 2.0),
                    Ingredient(position = 3, name = "Ginger-garlic paste", quantity = 1.0, unit = "tbsp"),
                    Ingredient(position = 4, name = "Chana masala powder", quantity = 2.0, unit = "tsp"),
                    Ingredient(position = 5, name = "Salt", note = "to taste"),
                ),
                listOf(
                    Step(position = 0, text = "Heat oil and fry the onion until golden, about 8 minutes."),
                    Step(position = 1, text = "Add ginger-garlic paste and cook for 1 minute."),
                    Step(position = 2, text = "Stir in tomatoes and masala powder; simmer 10 minutes."),
                    Step(position = 3, text = "Add chickpeas and salt, simmer 5 minutes and serve."),
                ),
            )
        }
        waitText("Weeknight Chana Masala")
        softShot("03_discover_soft_with_recent")

        // Mock 03 again: duplicate status pill (no network) for the fixture's own link.
        rule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("https://www.instagram.com/reel/SoftTour01/")
        rule.onNodeWithText("Import").performClick()
        waitText("Already saved")
        rule.onRoot().captureRoboImage("build/screens/soft-02-05/04_import_status_pill_duplicate.png")
        runCatching { rule.onNodeWithContentDescription("Dismiss").performClick() }
        waitGone("Already saved")

        // Mock 04: recipe detail SoftSheet + SoftChip Ingredients | Steps.
        rule.onAllNodesWithText("Weeknight Chana Masala").onFirst().performClick()
        waitText("Start cooking")
        waitText("Chickpeas, cooked")
        softShot("05_detail_soft_sheet_ingredients")
        rule.onNodeWithText("Steps").performClick()
        waitText("fry the onion")
        softShot("06_detail_soft_sheet_steps")

        // Mock 05: cook mode, one step.
        rule.onNodeWithText("Start cooking").performClick()
        waitText("STEP 1 OF 4")
        softShot("07_cook_soft_one_step")
        rule.onNodeWithText("Next").performClick()
        waitText("STEP 2 OF 4")
        softShot("08_cook_soft_step2_prev_next")
    }
}
