package cz.gameshelf.app.ui.games.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cz.gameshelf.app.R
import cz.gameshelf.app.data.games.GameChange
import cz.gameshelf.app.data.games.GamesRepository
import cz.gameshelf.app.data.sync.SyncController
import cz.gameshelf.app.data.sync.SyncEvent
import cz.gameshelf.app.domain.collection.GameFacetsCalculator
import cz.gameshelf.app.domain.collection.GameQueryEngine
import cz.gameshelf.app.domain.model.ActiveFilter
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.GameFilter
import cz.gameshelf.app.domain.model.GameQuery
import cz.gameshelf.app.domain.model.GameSortField
import cz.gameshelf.app.domain.model.SortOrder
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.appContainer
import cz.gameshelf.app.ui.common.toUiText
import cz.gameshelf.app.ui.games.filter.FilterDraft
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Collection list computed from the games stored on the device: debounced search, filters and sort
 * (kept here so they survive navigating to a detail and back) applied to the local collection, which
 * updates by itself whenever a local change or a sync changes it. Pull-to-refresh runs a sync.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class GameListViewModel(
    private val repository: GamesRepository,
    private val sync: SyncController,
    searchDebounceMillis: Long = SEARCH_DEBOUNCE_MILLIS,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameListUiState(sync = sync.status.value))
    val uiState: StateFlow<GameListUiState> = _uiState.asStateFlow()

    private val _events = Channel<GameListEvent>(Channel.BUFFERED)
    val events: Flow<GameListEvent> = _events.receiveAsFlow()

    private val searchInput = MutableStateFlow("")

    init {
        val debouncedSearch = searchInput
            // Clearing the search applies immediately; typing waits for a pause.
            .debounce { if (it.isBlank()) 0L else searchDebounceMillis }
            .map { it.trim() }
            .distinctUntilChanged()
        val query = combine(
            debouncedSearch,
            _uiState.map { it.filter }.distinctUntilChanged(),
            _uiState.map { it.sort }.distinctUntilChanged(),
            ::GameQuery,
        ).distinctUntilChanged()

        viewModelScope.launch {
            var shownQuery: GameQuery? = null
            combine(repository.games, query, ::Pair)
                .mapLatest { (games, query) -> QueryResult(query, GameQueryEngine.run(games, query), games.size) }
                .flowOn(computeDispatcher)
                .collect { result ->
                    _uiState.update { it.copy(games = result.games, collectionSize = result.collectionSize) }
                    if (shownQuery != null && shownQuery != result.query) _events.send(GameListEvent.ScrollToTop)
                    shownQuery = result.query
                }
        }
        viewModelScope.launch {
            repository.games
                .map { GameFacetsCalculator.calculate(it) }
                .flowOn(computeDispatcher)
                .collect { facets -> _uiState.update { it.copy(facets = facets) } }
        }
        viewModelScope.launch { sync.status.collect { status -> _uiState.update { it.copy(sync = status) } } }
        viewModelScope.launch { repository.changes.collect(::onGameChanged) }
        viewModelScope.launch {
            sync.events.collect { event ->
                when (event) {
                    SyncEvent.ChangesRejected ->
                        _events.send(GameListEvent.ShowMessage(UiText(R.string.error_changes_rejected)))
                }
            }
        }
    }

    fun onSearchQueryChange(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
        searchInput.value = value
    }

    /** Pull-to-refresh: runs a sync; a failure is reported and the list is kept. */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            try {
                val result = sync.syncNow()
                if (result is ApiResult.Failure) _events.send(GameListEvent.ShowMessage(result.error.toUiText()))
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** "Try again" after the first sync failed; the outcome replaces the error state. */
    fun retry() {
        if (_uiState.value.isRetrying) return
        _uiState.update { it.copy(isRetrying = true) }
        viewModelScope.launch {
            try {
                sync.syncNow()
            } finally {
                _uiState.update { it.copy(isRetrying = false) }
            }
        }
    }

    fun setSortField(field: GameSortField) = _uiState.update { it.copy(sort = it.sort.copy(field = field)) }

    fun setSortOrder(order: SortOrder) = _uiState.update { it.copy(sort = it.sort.copy(order = order)) }

    fun removeFilter(filter: ActiveFilter) = _uiState.update { it.copy(filter = it.filter.without(filter)) }

    fun clearFilters() = _uiState.update { it.copy(filter = GameFilter()) }

    fun clearSearchAndFilters() {
        onSearchQueryChange("")
        clearFilters()
    }

    fun openFilters() = _uiState.update { it.copy(filterDraft = FilterDraft.from(it.filter)) }

    fun updateFilterDraft(transform: (FilterDraft) -> FilterDraft) =
        _uiState.update { state -> state.copy(filterDraft = state.filterDraft?.let(transform)) }

    fun resetFilterDraft() = _uiState.update { state -> state.copy(filterDraft = state.filterDraft?.let { FilterDraft() }) }

    fun applyFilterDraft() = _uiState.update { state ->
        val filter = state.filterDraft?.toFilter() ?: return@update state
        state.copy(filter = filter, filterDraft = null)
    }

    fun dismissFilters() = _uiState.update { it.copy(filterDraft = null) }

    /** Confirms the user's own changes; the list itself follows the stored collection. */
    private fun onGameChanged(change: GameChange) {
        val message = when (change) {
            is GameChange.Created -> R.string.game_saved
            is GameChange.Deleted -> R.string.game_deleted
            is GameChange.Updated -> return
        }
        _events.trySend(GameListEvent.ShowMessage(UiText(message)))
    }

    private class QueryResult(val query: GameQuery, val games: List<Game>, val collectionSize: Int)

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 350L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { GameListViewModel(appContainer.gamesRepository, appContainer.syncController) }
        }
    }
}
