import Foundation
import Observation

/// State of the collection list: query (search, filters, sort), loaded pages and
/// loading/error states. Lives as long as the main flow, so filters and sort
/// survive navigation to the detail and back.
@Observable
@MainActor
final class GameListViewModel {
    enum Phase: Equatable {
        case idle
        case loading
        case loaded
        case failed(String)
    }

    enum Sheet: String, Identifiable {
        case filters
        case newGame

        var id: Self { self }
    }

    /// Text in the search field; applied to ``query`` after debouncing.
    var searchText = ""
    /// Error of a refresh that kept the previous results on screen.
    var refreshError: String?
    var presentedSheet: Sheet?

    private(set) var query = GameListQuery()
    private(set) var games: [Game] = []
    private(set) var totalItems = 0
    private(set) var phase: Phase = .idle
    private(set) var isReloading = false
    private(set) var isLoadingNextPage = false
    private(set) var nextPageError: String?

    @ObservationIgnored private let service: any GameService
    @ObservationIgnored private var loadedQuery: GameListQuery?
    @ObservationIgnored private var currentPage = 0
    @ObservationIgnored private var totalPages = 0
    @ObservationIgnored private var loadGeneration = 0
    @ObservationIgnored private var nextPageTask: Task<Void, Never>?

    init(service: any GameService) {
        self.service = service
    }

    var hasMorePages: Bool { currentPage < totalPages }

    /// The whole collection is empty (as opposed to nothing matching the filters).
    var isCollectionEmpty: Bool {
        phase == .loaded && games.isEmpty && loadedQuery?.isFiltered == false
    }

    var hasNoResults: Bool {
        phase == .loaded && games.isEmpty && loadedQuery?.isFiltered == true
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

    // MARK: Loading

    /// Loads the first page unless the current query is already on screen.
    func loadIfNeeded() async {
        guard loadedQuery != query else { return }
        await reload()
    }

    /// Loads the first page of the current query (initial load, query change, pull-to-refresh).
    func reload() async {
        let query = query
        loadGeneration += 1
        let generation = loadGeneration
        nextPageTask?.cancel()
        nextPageTask = nil
        isLoadingNextPage = false
        nextPageError = nil

        let showsPlaceholder = games.isEmpty || phase != .loaded
        if showsPlaceholder {
            phase = .loading
        } else {
            isReloading = true
        }

        do {
            let page = try await service.games(matching: query, page: 1)
            guard generation == loadGeneration else { return }
            games = page.items
            totalItems = page.totalItems
            currentPage = page.page
            totalPages = page.totalPages
            loadedQuery = query
            phase = .loaded
        } catch {
            guard generation == loadGeneration else { return }
            if ErrorMessage.isCancellation(error) {
                // The view went away or the query changed; the next task starts over.
                if phase == .loading { phase = .idle }
            } else if loadedQuery == query, !games.isEmpty {
                refreshError = ErrorMessage.message(for: error)
            } else {
                games = []
                loadedQuery = nil
                currentPage = 0
                totalPages = 0
                phase = .failed(ErrorMessage.message(for: error))
            }
        }
        isReloading = false
    }

    /// Infinite scroll: starts loading the next page when one of the last rows appears.
    func loadMoreIfNeeded(after game: Game) {
        guard let index = games.lastIndex(where: { $0.id == game.id }),
              index >= games.count - 5
        else { return }
        loadNextPage()
    }

    func loadNextPage() {
        guard hasMorePages, !isLoadingNextPage, nextPageError == nil,
              let query = loadedQuery, query == self.query
        else { return }

        isLoadingNextPage = true
        let generation = loadGeneration
        let page = currentPage + 1
        nextPageTask = Task {
            do {
                let result = try await service.games(matching: query, page: page)
                guard generation == loadGeneration else { return }
                let known = Set(games.map(\.id))
                games += result.items.filter { !known.contains($0.id) }
                totalItems = result.totalItems
                currentPage = result.page
                totalPages = result.totalPages
            } catch {
                guard generation == loadGeneration, !ErrorMessage.isCancellation(error) else { return }
                nextPageError = ErrorMessage.message(for: error)
            }
            isLoadingNextPage = false
        }
    }

    func retryNextPage() {
        nextPageError = nil
        loadNextPage()
    }

    // MARK: Changes from other screens

    func apply(_ change: GameChange) {
        switch change {
        case .created:
            // The new game's position depends on sort and filters; reload from the start.
            loadedQuery = nil
            Task { await reload() }
        case .updated(let game):
            if let index = games.firstIndex(where: { $0.id == game.id }) {
                games[index] = game
            }
        case .deleted(let id):
            if let index = games.firstIndex(where: { $0.id == id }) {
                games.remove(at: index)
                totalItems = max(0, totalItems - 1)
            }
        }
    }
}
