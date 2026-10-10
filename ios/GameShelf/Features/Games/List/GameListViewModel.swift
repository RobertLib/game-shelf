import Foundation
import Observation

/// State of the collection list: the query (search, filters, sort, grouping) and its results,
/// computed from the local collection. Lives as long as the main flow, so filters and sort survive
/// navigation to the detail and back.
@Observable
@MainActor
final class GameListViewModel {
    enum Phase: Equatable {
        case loading
        case loaded
        case failed(String)
    }

    enum Sheet: Hashable, Identifiable {
        case filters
        case newGame
        /// A new game prefilled from a scanned barcode.
        case scannedGame(barcode: String)

        var id: Self { self }
    }

    /// What the results are computed from; when it changes, the results are recomputed.
    struct ResultsKey: Hashable {
        var query: GameListQuery
        var collectionVersion: Int
    }

    /// Text in the search field; applied to ``query`` after debouncing.
    var searchText = ""
    /// Error of a pull-to-refresh sync; the list stays on screen.
    var refreshError: String?
    var presentedSheet: Sheet?

    private(set) var query = GameListQuery()
    /// Games matching ``query``, sorted.
    private(set) var games: [Game] = []
    /// ``games`` split by platform as they are shown; `nil` when the list is not grouped.
    private(set) var sections: [PlatformSection]?
    /// The user asked to retry the failed first sync.
    private(set) var isRetrying = false
    private var resultsComputedFor: ResultsKey?

    let sync: SyncEngine

    init(sync: SyncEngine) {
        self.sync = sync
    }

    var repository: GameRepository { sync.repository }
    var syncStatus: SyncStatus { sync.status }

    var resultsKey: ResultsKey {
        ResultsKey(query: query, collectionVersion: repository.version)
    }

    /// Until the first complete sync, an empty collection shows loading (or the failure of that
    /// sync) rather than the empty state. Local data that couldn't be opened shows its failure.
    var phase: Phase {
        guard repository.isLoaded else {
            if !isRetrying, let message = repository.loadError {
                return .failed(message)
            }
            return .loading
        }
        if repository.isEmpty, !syncStatus.hasCompletedInitialSync {
            if !isRetrying, let message = syncStatus.lastErrorMessage {
                return .failed(message)
            }
            return .loading
        }
        return resultsComputedFor == nil ? .loading : .loaded
    }

    /// Nothing to show without a search or filter: the collection is empty.
    var isCollectionEmpty: Bool {
        phase == .loaded && games.isEmpty && !shownQueryIsFiltered
    }

    /// Nothing matches the search or filters (even if the collection itself is empty).
    var hasNoResults: Bool {
        phase == .loaded && games.isEmpty && shownQueryIsFiltered
    }

    private var shownQueryIsFiltered: Bool {
        resultsComputedFor?.query.isFiltered ?? false
    }

    // MARK: Query

    func commitSearch() {
        let trimmed = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed != query.search {
            query.search = trimmed
        }
    }

    func applyFilter(_ filter: GameFilter) {
        query.filter = filter
    }

    func removeFilter(_ key: FilterChip.Key) {
        query.filter.remove(key)
    }

    /// Clears filters and search (the "reset" action of the no-results state).
    func resetFilters() {
        query.filter = GameFilter()
        searchText = ""
        query.search = ""
    }

    func setSort(_ field: GameSortField) {
        guard field != query.sort else { return }
        query.sort = field
        query.order = field.defaultOrder
    }

    func setOrder(_ order: SortOrder) {
        query.order = order
    }

    func setGroupByPlatform(_ enabled: Bool) {
        query.groupByPlatform = enabled
    }

    // MARK: Results

    /// Recomputes the results for the current query and collection. Filtering and sorting run off
    /// the main actor; a result that is outdated by the time it is ready is dropped.
    func updateResults() async {
        let key = resultsKey
        guard key != resultsComputedFor else { return }
        guard let results = await Self.results(of: key.query, in: Array(repository.games)),
              key == resultsKey
        else { return }
        games = results.games
        sections = results.sections
        resultsComputedFor = key
    }

    private struct Results: Sendable {
        var games: [Game]
        var sections: [PlatformSection]?
    }

    /// `nil` when the calling task was cancelled (a newer query or collection replaced it).
    @concurrent
    private nonisolated static func results(of query: GameListQuery, in games: [Game]) async -> Results? {
        guard !Task.isCancelled else { return nil }
        let results = query.results(in: games)
        let sections = query.groupByPlatform ? query.sections(of: results) : nil
        return Task.isCancelled ? nil : Results(games: results, sections: sections)
    }

    // MARK: Sync

    /// Pull-to-refresh: syncs and reports a failure; the list stays as it is.
    func refresh() async {
        do {
            try await sync.syncNow()
        } catch {
            if !ErrorMessage.isCancellation(error) {
                refreshError = ErrorMessage.message(for: error)
            }
        }
    }

    /// "Try again" after the local data couldn't be opened or the first sync failed.
    func retry() async {
        isRetrying = true
        defer { isRetrying = false }
        if repository.isLoaded {
            try? await sync.syncNow()
        } else {
            await sync.retryActivation()
        }
    }
}
