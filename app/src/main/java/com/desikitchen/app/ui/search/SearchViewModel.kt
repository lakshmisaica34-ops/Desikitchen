package com.desikitchen.app.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desikitchen.app.data.repo.RecipeRepository
import com.desikitchen.app.util.ImportErrors
import com.desikitchen.app.util.SearchResults
import com.desikitchen.app.util.SearchState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Search Results screen: hint → loading → results / no results / error, over the saved library via RecipeSearch. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(savedState: SavedStateHandle, private val recipes: RecipeRepository) : ViewModel() {
    private val _query = MutableStateFlow(savedState.get<String>("q").orEmpty())
    val query: StateFlow<String> = _query.asStateFlow()
    private val retry = MutableStateFlow(0)

    val state: StateFlow<SearchState> = combine(_query, retry) { q, _ -> q }
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf<SearchState>(SearchState.Hint)
            else flow<SearchState> {
                emit(SearchState.Loading)
                delay(DEBOUNCE_MS)
                emitAll(
                    combine(recipes.observeAll(), recipes.observeIngredientIndex()) { all, index -> SearchResults.compute(q, all, index) as SearchState }
                        .catch { e -> ImportErrors.log("search", e); emit(SearchState.Error(SearchResults.ERROR)) },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), if (_query.value.isBlank()) SearchState.Hint else SearchState.Loading)

    fun setQuery(q: String) { _query.value = q }
    fun retry() { retry.value++ }

    companion object { const val DEBOUNCE_MS = 250L }
}
