import Foundation
import Testing
@testable import GameShelf

@MainActor
@Suite("Collection list")
struct GameListViewModelTests {
    private let owner = "user-1"

    /// A list over an activated engine whose first sync used `server`.
    private func makeModel(server: FakeGameServer, initialSyncSucceeds: Bool = true) async throws -> GameListViewModel {
        let repository = GameRepository(store: try GameStore(url: nil), status: SyncStatus())
        let sync = SyncEngine(repository: repository, api: server)
        repository.onLocalChange = nil
        await sync.activate(ownerID: owner)
        if initialSyncSucceeds {
            try await sync.syncNow()
        } else {
            try? await sync.syncNow()
        }
        let model = GameListViewModel(sync: sync)
        await model.updateResults()
        return model
    }

    @Test func showsLoadingUntilTheStoredDataIsLoaded() async throws {
        let repository = GameRepository(store: try GameStore(url: nil), status: SyncStatus())
        let model = GameListViewModel(sync: SyncEngine(repository: repository, api: FakeGameServer()))
        #expect(model.phase == .loading)
    }

    @Test func failedFirstSyncWithNothingStoredShowsTheError() async throws {
        let server = FakeGameServer()
        await server.setUnreachable(true)
        let model = try await makeModel(server: server, initialSyncSucceeds: false)
        #expect(model.phase == .failed("Can't connect to the server. Check your connection."))
        #expect(model.syncStatus.isOffline)

        await server.setUnreachable(false)
        await model.retry()
        await model.updateResults()
        #expect(model.phase == .loaded)
        #expect(model.isCollectionEmpty)
    }

    @Test func resultsFollowTheCollectionAndTheQuery() async throws {
        let server = FakeGameServer()
        await server.seed(Fixtures.game("Super Mario World", .snes))
        await server.seed(Fixtures.game("Banjo-Kazooie", .snes))
        await server.seed(Fixtures.game("Doom", .pc))
        let model = try await makeModel(server: server)
        #expect(model.phase == .loaded)
        #expect(model.games.map(\.title) == ["Banjo-Kazooie", "Doom", "Super Mario World"])

        var filter = GameFilter()
        filter.platforms = [.snes]
        model.applyFilter(filter)
        model.setSort(.title)
        model.setOrder(.desc)
        await model.updateResults()
        #expect(model.games.map(\.title) == ["Super Mario World", "Banjo-Kazooie"])

        // A local change shows up without any reload.
        try await model.repository.create(SaveGameRequest(title: "Zelda", platform: .snes))
        await model.updateResults()
        #expect(model.games.map(\.title) == ["Zelda", "Super Mario World", "Banjo-Kazooie"])
    }

    @Test func groupsByPlatformUntilGroupingIsTurnedOff() async throws {
        let server = FakeGameServer()
        await server.seed(Fixtures.game("Super Mario World", .snes))
        await server.seed(Fixtures.game("Doom", .pc))
        await server.seed(Fixtures.game("Banjo-Kazooie", .snes))
        let model = try await makeModel(server: server)
        #expect(model.sections?.map(\.platform) == [.pc, .snes])
        #expect(model.sections?.last?.games.map(\.title) == ["Banjo-Kazooie", "Super Mario World"])

        model.setGroupByPlatform(false)
        await model.updateResults()
        #expect(model.sections == nil)
        #expect(model.games.map(\.title) == ["Banjo-Kazooie", "Doom", "Super Mario World"])
    }

    @Test func emptyCollectionAndNoResultsAreDistinguished() async throws {
        let model = try await makeModel(server: FakeGameServer())
        #expect(model.isCollectionEmpty)
        #expect(!model.hasNoResults)

        try await model.repository.create(SaveGameRequest(title: "Doom", platform: .pc))
        model.searchText = "zelda"
        model.commitSearch()
        await model.updateResults()
        #expect(model.hasNoResults)
        #expect(!model.isCollectionEmpty)

        model.resetFilters()
        await model.updateResults()
        #expect(model.games.count == 1)
        #expect(model.searchText.isEmpty)

        // Deleting the last game while searching still reports the search, not an empty collection.
        model.searchText = "doom"
        model.commitSearch()
        try await model.repository.delete(id: model.games[0].id)
        await model.updateResults()
        #expect(model.hasNoResults)
        #expect(!model.isCollectionEmpty)
    }

    @Test func failedRefreshKeepsTheListAndReportsTheError() async throws {
        let server = FakeGameServer()
        await server.seed(Fixtures.game("Doom"))
        let model = try await makeModel(server: server)
        await server.fail(.changes, with: .network(.timedOut))

        await model.refresh()

        #expect(model.refreshError == "Can't connect to the server. Check your connection.")
        #expect(model.phase == .loaded)
        #expect(model.games.map(\.title) == ["Doom"])
        #expect(model.syncStatus.indicator == .offline)
    }

    @Test func syncStatusIsShownInTheHeader() async throws {
        let model = try await makeModel(server: FakeGameServer())
        #expect(model.syncStatus.indicator == nil)
        try await model.repository.create(SaveGameRequest(title: "Doom", platform: .pc))
        try await model.repository.create(SaveGameRequest(title: "Quake", platform: .pc))
        #expect(model.syncStatus.indicator == .unsynced(2))
    }
}
