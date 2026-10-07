import Foundation
import Testing
@testable import GameShelf

/// Serves `totalItems` generated games in pages of 25 after a short delay.
private actor PagedGameService: GameService {
    private let totalItems: Int
    private(set) var requests: [(search: String, page: Int)] = []

    init(totalItems: Int) {
        self.totalItems = totalItems
    }

    func games(matching query: GameListQuery, page: Int) async throws -> GamePage {
        requests.append((query.search, page))
        try await Task.sleep(for: .milliseconds(50))
        let pageSize = GameListQuery.defaultPageSize
        let start = (page - 1) * pageSize
        let items = (start..<min(start + pageSize, totalItems)).map { Self.game(index: $0, search: query.search) }
        let totalPages = Int((Double(totalItems) / Double(pageSize)).rounded(.up))
        return GamePage(items: items, page: page, pageSize: pageSize, totalItems: totalItems, totalPages: totalPages)
    }

    func facets() async throws -> GameFacets { .empty }
    func game(id: Game.ID) async throws -> Game { throw APIError.invalidResponse }
    func create(_ request: SaveGameRequest) async throws -> Game { throw APIError.invalidResponse }
    func update(id: Game.ID, with request: SaveGameRequest) async throws -> Game { throw APIError.invalidResponse }
    func delete(id: Game.ID) async throws {}

    private static let template = try! JSONDecoder.api().decode(Game.self, from: Data(Fixtures.gameJSON.utf8))

    static func game(index: Int, search: String) -> Game {
        var game = template
        game.id = "\(search)-\(index)"
        game.title = "Game \(index)"
        return game
    }
}

@MainActor
@Suite("Collection list paging")
struct GameListViewModelTests {
    private func waitUntilIdle(_ model: GameListViewModel) async throws {
        for _ in 0..<200 where model.isLoadingNextPage {
            try await Task.sleep(for: .milliseconds(10))
        }
    }

    @Test func nextPageIsRequestedOnlyOnceWhileLoading() async throws {
        let service = PagedGameService(totalItems: 60)
        let model = GameListViewModel(service: service)
        await model.reload()
        #expect(model.games.count == 25)
        #expect(model.totalItems == 60)
        #expect(model.hasMorePages)

        let last = try #require(model.games.last)
        model.loadMoreIfNeeded(after: last)
        model.loadMoreIfNeeded(after: last)
        model.loadNextPage()
        try await waitUntilIdle(model)

        #expect(model.games.count == 50)
        #expect(await service.requests.map(\.page) == [1, 2])
    }

    @Test func rowsFarFromTheEndDoNotTriggerPaging() async throws {
        let service = PagedGameService(totalItems: 60)
        let model = GameListViewModel(service: service)
        await model.reload()
        model.loadMoreIfNeeded(after: model.games[3])
        #expect(!model.isLoadingNextPage)
    }

    @Test func lastPageStopsPaging() async throws {
        let service = PagedGameService(totalItems: 30)
        let model = GameListViewModel(service: service)
        await model.reload()
        model.loadNextPage()
        try await waitUntilIdle(model)
        #expect(model.games.count == 30)
        #expect(!model.hasMorePages)
        model.loadNextPage()
        #expect(await service.requests.count == 2)
    }

    @Test func queryChangeDiscardsInFlightPage() async throws {
        let service = PagedGameService(totalItems: 60)
        let model = GameListViewModel(service: service)
        await model.reload()
        model.loadNextPage()

        model.searchText = "zelda"
        model.commitSearch()
        await model.loadIfNeeded()
        try await Task.sleep(for: .milliseconds(100))

        #expect(model.games.count == 25)
        #expect(model.games.allSatisfy { $0.id.hasPrefix("zelda-") })
    }

    @Test func deletionUpdatesRowsAndCount() async throws {
        let model = GameListViewModel(service: PagedGameService(totalItems: 3))
        await model.reload()
        let first = try #require(model.games.first)
        model.apply(.deleted(first.id))
        #expect(model.games.count == 2)
        #expect(model.totalItems == 2)
    }

    @Test func emptyCollectionAndNoResultsAreDistinguished() async throws {
        let model = GameListViewModel(service: PagedGameService(totalItems: 0))
        await model.reload()
        #expect(model.isCollectionEmpty)
        #expect(!model.hasNoResults)

        var filter = GameFilter()
        filter.favoritesOnly = true
        model.applyFilter(filter)
        await model.loadIfNeeded()
        #expect(model.hasNoResults)
        #expect(!model.isCollectionEmpty)
    }
}
