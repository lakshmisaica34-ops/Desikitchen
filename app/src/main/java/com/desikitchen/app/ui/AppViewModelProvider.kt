package com.desikitchen.app.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.desikitchen.app.AppContainer
import com.desikitchen.app.DesiKitchenApp
import com.desikitchen.app.ui.assistant.AssistantViewModel
import com.desikitchen.app.ui.cookbooks.CookbookDetailViewModel
import com.desikitchen.app.ui.cookbooks.CookbooksViewModel
import com.desikitchen.app.ui.cooking.CookingViewModel
import com.desikitchen.app.ui.creator.CreatorViewModel
import com.desikitchen.app.ui.detail.RecipeDetailViewModel
import com.desikitchen.app.ui.edit.AddEditRecipeViewModel
import com.desikitchen.app.ui.grocery.GroceryViewModel
import com.desikitchen.app.ui.home.HomeViewModel
import com.desikitchen.app.ui.importer.ImportStatusViewModel
import com.desikitchen.app.ui.more.MoreViewModel
import com.desikitchen.app.ui.plan.PlanViewModel
import com.desikitchen.app.ui.saved.SavedViewModel
import com.desikitchen.app.ui.search.SearchViewModel
import com.desikitchen.app.util.CocoActionExecutor

private fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as DesiKitchenApp).container

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { HomeViewModel(container().recipes, container().settings) }
        initializer {
            val c = container()
            RecipeDetailViewModel(createSavedStateHandle(), c.recipes, c.cookbooks, c.mealPlan, c.grocery, c.settings)
        }
        initializer { CookingViewModel(createSavedStateHandle(), container().recipes, container().settings) }
        initializer {
            val c = container()
            AddEditRecipeViewModel(createSavedStateHandle(), c.recipes, onImportSaved = { c.settings.recordImport() }, reviewSession = c.importer)
        }
        initializer { CookbooksViewModel(container().cookbooks) }
        initializer { CookbookDetailViewModel(createSavedStateHandle(), container().cookbooks, container().recipes) }
        initializer { PlanViewModel(container().mealPlan, container().recipes, container().grocery, container().settings) }
        initializer { GroceryViewModel(container().grocery, container().recipes, container().settings) }
        initializer { MoreViewModel(container().recipes, container().settings) }
        initializer { SavedViewModel(container().recipes, container().cookbooks, container().mealPlan) }
        initializer {
            val c = container()
            AssistantViewModel(c.recipes, c.cocoChat, CocoActionExecutor(c.recipes, c.mealPlan, c.grocery))
        }
        initializer { CreatorViewModel(createSavedStateHandle(), container().recipes) }
        initializer { SearchViewModel(createSavedStateHandle(), container().recipes) }
        initializer { ImportStatusViewModel(container().importer, container().settings.importTimes, isPlus = container().settings.isPlus) }
    }
}
