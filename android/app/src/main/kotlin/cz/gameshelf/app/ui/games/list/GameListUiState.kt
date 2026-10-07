package cz.gameshelf.app.ui.games.list

import cz.gameshelf.app.data.sync.SyncStatus
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.GameFacets
import cz.gameshelf.app.domain.model.GameFilter
import cz.gameshelf.app.domain.model.GameSort
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.toUiText
import cz.gameshelf.app.ui.games.filter.FilterDraft

data class GameListUiState(
    val searchQuery: String = "",
    val filter: GameFilter = GameFilter(),
    val sort: GameSort = GameSort(),
    /** The stored games matching the search and filters, sorted. */
    val games: List<Game> = emptyList(),
    /** Number of stored games; `null` until the local collection has been read. */
    val collectionSize: Int? = null,
    /** Facets of the whole stored collection, for the filter sheet. */
    val facets: GameFacets = GameFacets.Empty,
    val sync: SyncStatus = SyncStatus(),
    /** Pull-to-refresh is in progress. */
    val isRefreshing: Boolean = false,
    /** "Try again" after a failed first sync is in progress. */
    val isRetrying: Boolean = false,
    /** Non-null while the filter sheet is open. */
    val filterDraft: FilterDraft? = null,
) {
    val activeFilterCount: Int get() = filter.activeFilters.size

    val totalItems: Int get() = games.size

    /** Nothing is stored and the first sync has not completed, so "empty" would be a guess. */
    private val awaitsFirstSync: Boolean get() = collectionSize == 0 && !sync.hasCompletedInitialSync

    /** The first sync failed and nothing is stored; shown instead of the list. */
    val loadError: UiText? get() = sync.lastError?.takeIf { awaitsFirstSync && !isRetrying }?.toUiText()

    val isLoading: Boolean get() = collectionSize == null || (awaitsFirstSync && loadError == null)

    private val isNarrowed: Boolean get() = searchQuery.isNotBlank() || !filter.isEmpty
    private val isEmptyResult: Boolean get() = !isLoading && loadError == null && games.isEmpty()

    val showEmptyCollection: Boolean get() = isEmptyResult && !isNarrowed
    val showNoResults: Boolean get() = isEmptyResult && isNarrowed
}

sealed interface GameListEvent {
    data class ShowMessage(val message: UiText) : GameListEvent

    /** Search, filters or sort changed; the list should start from the top. */
    data object ScrollToTop : GameListEvent
}
