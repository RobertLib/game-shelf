package cz.gameshelf.app.ui.games.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cz.gameshelf.app.R
import cz.gameshelf.app.data.games.GameChange
import cz.gameshelf.app.data.games.GameListQuery
import cz.gameshelf.app.data.games.GamesRepository
import cz.gameshelf.app.domain.model.ActiveFilter
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.GameFilter
import cz.gameshelf.app.domain.model.GameQuery
import cz.gameshelf.app.domain.model.GameSortField
import cz.gameshelf.app.domain.model.SortOrder
import cz.gameshelf.app.domain.model.onSuccess
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.appContainer
import cz.gameshelf.app.ui.common.toUiText
import cz.gameshelf.app.ui.games.filter.FilterDraft
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Collection list: debounced search, filters and sort (all kept here so they survive navigating
 * to a detail and back), page-by-page loading and reaction to games changed on other screens.
 */
@OptIn(FlowPreview::class)
class GameListViewModel(
    private val repository: GamesRepository,
    private val pageSize: Int = GameListQuery.DEFAULT_PAGE_SIZE,
    searchDebounceMillis: Long = SEARCH_DEBOUNCE_MILLIS,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameListUiState())
    val uiState: StateFlow<GameListUiState> = _uiState.asStateFlow()

    private val _events = Channel<GameListEvent>(Channel.BUFFERED)
    val events: Flow<GameListEvent> = _events.receiveAsFlow()

    private val searchInput = MutableStateFlow("")

    private var currentQuery = GameQuery()
    private var nextPage = 1
    private var firstPageJob: Job? = null
    private var nextPageJob: Job? = null
    private var facetsJob: Job? = null

    init {
        val debouncedSearch = searchInput
            // Clearing the search applies immediately; typing waits for a pause.
            .debounce { if (it.isBlank()) 0L else searchDebounceMillis }
            .map { it.trim() }
            .distinctUntilChanged()
        viewModelScope.launch {
            combine(
                debouncedSearch,
                _uiState.map { it.filter }.distinctUntilChanged(),
                _uiState.map { it.sort }.distinctUntilChanged(),
                ::GameQuery,
            ).distinctUntilChanged().collect { query -> loadFirstPage(query, LoadMode.NewQuery) }
        }
        viewModelScope.launch { repository.changes.collect(::onGameChanged) }
        loadFacets()
    }

    fun onSearchQueryChange(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
        searchInput.value = value
    }

    fun refresh() {
        loadFirstPage(currentQuery, LoadMode.Refresh)
        loadFacets()
    }

    fun retry() {
        loadFirstPage(currentQuery, LoadMode.NewQuery)
        loadFacets()
    }

    /** Called when the list is scrolled near its end; ignored while a page is already loading. */
    fun loadNextPage() {
        val state = _uiState.value
        val busy = state.isLoading || state.isRefreshing || state.isLoadingMore || nextPageJob?.isActive == true
        if (busy || state.endReached || state.loadMoreFailed || state.loadError != null) return

        val query = currentQuery
        val page = nextPage
        _uiState.update { it.copy(isLoadingMore = true) }
        nextPageJob = viewModelScope.launch {
            when (val result = repository.listGames(query, page, pageSize)) {
                is ApiResult.Success -> {
                    val pageData = result.value
                    nextPage = page + 1
                    _uiState.update { state ->
                        state.copy(
                            games = (state.games + pageData.items).distinctBy { it.id },
                            totalItems = pageData.totalItems,
                            endReached = pageData.items.isEmpty() || pageData.page >= pageData.totalPages,
                            isLoadingMore = false,
                        )
                    }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    fun retryLoadMore() {
        _uiState.update { it.copy(loadMoreFailed = false) }
        loadNextPage()
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

    private fun loadFirstPage(query: GameQuery, mode: LoadMode) {
        firstPageJob?.cancel()
        nextPageJob?.cancel()
        currentQuery = query
        _uiState.update {
            it.copy(
                isLoading = mode == LoadMode.NewQuery,
                isRefreshing = mode == LoadMode.Refresh,
                loadError = null,
                isLoadingMore = false,
                loadMoreFailed = false,
            )
        }
        firstPageJob = viewModelScope.launch {
            when (val result = repository.listGames(query, page = 1, pageSize = pageSize)) {
                is ApiResult.Success -> {
                    val pageData = result.value
                    nextPage = 2
                    _uiState.update {
                        it.copy(
                            games = pageData.items,
                            totalItems = pageData.totalItems,
                            endReached = pageData.page >= pageData.totalPages,
                            isLoading = false,
                            isRefreshing = false,
                        )
                    }
                    if (mode == LoadMode.NewQuery) _events.send(GameListEvent.ScrollToTop)
                }
                is ApiResult.Failure -> {
                    val message = result.error.toUiText()
                    val keepItems = mode != LoadMode.NewQuery && _uiState.value.games.isNotEmpty()
                    _uiState.update {
                        if (keepItems) {
                            it.copy(isLoading = false, isRefreshing = false)
                        } else {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                games = emptyList(),
                                totalItems = 0,
                                loadError = message,
                            )
                        }
                    }
                    if (keepItems) _events.send(GameListEvent.ShowMessage(message))
                }
            }
        }
    }

    private fun loadFacets() {
        facetsJob?.cancel()
        facetsJob = viewModelScope.launch {
            repository.facets().onSuccess { facets -> _uiState.update { it.copy(facets = facets) } }
        }
    }

    private fun onGameChanged(change: GameChange) {
        when (change) {
            is GameChange.Created -> {
                loadFirstPage(currentQuery, LoadMode.Silent)
                _events.trySend(GameListEvent.ShowMessage(UiText(R.string.game_saved)))
            }
            is GameChange.Updated -> _uiState.update { state ->
                state.copy(games = state.games.map { if (it.id == change.game.id) change.game else it })
            }
            is GameChange.Deleted -> {
                _uiState.update { state ->
                    val remaining = state.games.filterNot { it.id == change.id }
                    val removed = state.games.size - remaining.size
                    state.copy(games = remaining, totalItems = (state.totalItems - removed).coerceAtLeast(0))
                }
                _events.trySend(GameListEvent.ShowMessage(UiText(R.string.game_deleted)))
            }
        }
        loadFacets()
    }

    private enum class LoadMode {
        /** Search, filter or sort changed: show a loading state and scroll to the top. */
        NewQuery,

        /** Pull-to-refresh: keep the list, show the refresh indicator. */
        Refresh,

        /** Background reload after a change elsewhere: no indicator at all. */
        Silent,
    }

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 350L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { GameListViewModel(appContainer.gamesRepository) }
        }
    }
}
