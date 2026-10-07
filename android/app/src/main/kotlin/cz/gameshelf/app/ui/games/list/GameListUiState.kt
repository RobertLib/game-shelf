package cz.gameshelf.app.ui.games.list

import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.GameFacets
import cz.gameshelf.app.domain.model.GameFilter
import cz.gameshelf.app.domain.model.GameSort
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.games.filter.FilterDraft

data class GameListUiState(
    val searchQuery: String = "",
    val filter: GameFilter = GameFilter(),
    val sort: GameSort = GameSort(),
    val games: List<Game> = emptyList(),
    val totalItems: Int = 0,
    /** First page of the current query is loading. */
    val isLoading: Boolean = true,
    /** Pull-to-refresh is in progress. */
    val isRefreshing: Boolean = false,
    /** The first page failed; shown instead of the list. */
    val loadError: UiText? = null,
    val isLoadingMore: Boolean = false,
    /** The next page failed; automatic loading pauses until the user retries. */
    val loadMoreFailed: Boolean = false,
    val endReached: Boolean = false,
    val facets: GameFacets = GameFacets.Empty,
    /** Non-null while the filter sheet is open. */
    val filterDraft: FilterDraft? = null,
) {
    val activeFilterCount: Int get() = filter.activeFilters.size

    private val isNarrowed: Boolean get() = searchQuery.isNotBlank() || !filter.isEmpty
    private val isEmptyResult: Boolean get() = !isLoading && loadError == null && games.isEmpty()

    val showEmptyCollection: Boolean get() = isEmptyResult && !isNarrowed
    val showNoResults: Boolean get() = isEmptyResult && isNarrowed
}

sealed interface GameListEvent {
    data class ShowMessage(val message: UiText) : GameListEvent

    /** A new query produced its first page; the list should start from the top. */
    data object ScrollToTop : GameListEvent
}
