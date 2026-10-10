import Foundation
import Testing
@testable import GameShelf

/// A signed-in sync engine over an in-memory store and a ``FakeGameServer``.
///
/// Local changes don't start runs on their own here (`onLocalChange` is cleared), so every test
/// decides when a run happens.
@MainActor
private struct SyncHarness {
    static let owner = "01a1163a-395c-7270-9196-b4eda9d89b80"

    let server: FakeGameServer
    let store: GameStore
    let repository: GameRepository
    let engine: SyncEngine

    init(server: FakeGameServer = FakeGameServer(), store: GameStore? = nil, coverCache: URLCache = .shared) async throws {
        self.server = server
        self.store = try store ?? GameStore(url: nil)
        repository = GameRepository(store: self.store, status: SyncStatus())
        engine = SyncEngine(repository: repository, api: server, coverCache: coverCache)
        repository.onLocalChange = nil
        await engine.activate(ownerID: Self.owner)
        try await engine.syncNow()
    }

    var status: SyncStatus { repository.status }

    func pending() async throws -> [PendingChange] {
        try await store.pendingChanges()
    }

    /// A game that exists on the server and has been pulled.
    func syncedGame(_ title: String = "Chrono Trigger", configure: (inout Game) -> Void = { _ in }) async throws -> Game {
        let game = Fixtures.game(title, .snes, configure: configure)
        await server.seed(game)
        try await engine.syncNow()
        return try #require(repository.game(id: game.id))
    }

    func edit(_ game: Game, _ change: (inout SaveGameRequest) -> Void) async throws {
        var request = SaveGameRequest(game: try #require(repository.game(id: game.id)))
        change(&request)
        try await repository.update(id: game.id, with: request)
    }
}

/// A database file in a fresh temporary directory, removed by `cleanUp`.
private func temporaryDatabase() throws -> (url: URL, cleanUp: () -> Void) {
    let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    return (directory.appending(path: "games.sqlite"), { try? FileManager.default.removeItem(at: directory) })
}

@MainActor
@Suite("Sync engine")
struct SyncEngineTests {
    // MARK: Push

    @Test func createIsPushedAndThePendingChangeRemoved() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.repository.create(SaveGameRequest(title: "Doom", platform: .pc))
        #expect(harness.status.pendingCount == 1)

        try await harness.engine.syncNow()

        let server = try #require(await harness.server.games[game.id])
        #expect(server.title == "Doom")
        #expect(try await harness.pending().isEmpty)
        #expect(harness.status.pendingCount == 0)
        // The server's copy (with its timestamps) is stored.
        #expect(harness.repository.game(id: game.id) == server)
        #expect(await harness.server.requests(.create).count == 1)
    }

    @Test func replayedCreatePushesFieldsEditedAfterTheFirstAttempt() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.repository.create(SaveGameRequest(title: "Doom", platform: .pc))
        // The first POST reaches the server, but its response is lost.
        await harness.server.fail(.create, with: .network(.timedOut), afterApplying: true)
        await #expect(throws: APIError.network(.timedOut)) {
            try await harness.engine.syncNow()
        }
        try await harness.edit(game) { $0.notes = "Big box" }
        let pending = try #require(try await harness.pending().first)
        #expect(pending.kind == .create)
        #expect(pending.attempted)
        #expect(pending.fields == [.notes])

        try await harness.engine.syncNow()

        // POST answers 200 without changing anything; the edit follows as PATCH in the same run.
        let requests = await harness.server.requests.filter { $0.operation != .changes }
        #expect(requests == [
            .init(operation: .create, gameID: game.id),
            .init(operation: .create, gameID: game.id),
            .init(operation: .update, gameID: game.id, fields: [.notes]),
        ])
        #expect(await harness.server.games[game.id]?.notes == "Big box")
        #expect(harness.repository.game(id: game.id)?.notes == "Big box")
        #expect(try await harness.pending().isEmpty)
    }

    @Test func replayedCreatePushesOnlyTheFieldsEditedWhileItWasInFlight() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.repository.create(SaveGameRequest(title: "Doom", platform: .pc))
        // The first POST reaches the server, but its response is lost.
        await harness.server.fail(.create, with: .network(.timedOut), afterApplying: true)
        await #expect(throws: APIError.network(.timedOut)) {
            try await harness.engine.syncNow()
        }
        await harness.server.editOnAnotherDevice(game.id) { $0.title = "Doom (other device)" }

        // The edit happens while the second POST is in flight.
        await harness.server.hold(.create)
        let run = Task { try await harness.engine.syncNow() }
        await harness.server.waitUntilHeld()
        try await harness.edit(game) { $0.rating = 9 }
        await harness.server.release()
        try await run.value

        #expect(await harness.server.requests(.update) == [.init(operation: .update, gameID: game.id, fields: [.rating])])
        let server = try #require(await harness.server.games[game.id])
        #expect(server.rating == 9)
        #expect(server.title == "Doom (other device)", "fields not edited are never sent")
        #expect(try await harness.pending().isEmpty)
    }

    @Test func editsOfOneGameAreCoalescedIntoOneUpdate() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        try await harness.edit(game) { $0.title = "Chrono Trigger (US)" }
        try await harness.edit(game) { $0.notes = "Cartridge only" }

        let pending = try #require(try await harness.pending().first)
        #expect(pending.kind == .update)
        #expect(pending.fields == [.title, .notes])
        #expect(pending.revision == 2)

        try await harness.engine.syncNow()
        #expect(await harness.server.requests(.update) == [.init(operation: .update, gameID: game.id, fields: [.title, .notes])])
        let server = try #require(await harness.server.games[game.id])
        #expect(server.title == "Chrono Trigger (US)")
        #expect(server.notes == "Cartridge only")
        #expect(try await harness.pending().isEmpty)
    }

    @Test func deletingAGameThatWasNeverSentLeavesNothingToPush() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.repository.create(SaveGameRequest(title: "Doom", platform: .pc))
        try await harness.repository.delete(id: game.id)

        #expect(harness.repository.game(id: game.id) == nil)
        #expect(try await harness.pending().isEmpty)
        #expect(harness.status.pendingCount == 0)

        try await harness.engine.syncNow()
        #expect(await harness.server.requests.allSatisfy { $0.operation == .changes })
    }

    @Test func deleteIsPushedAndAlreadyDeletedCountsAsSuccess() async throws {
        let harness = try await SyncHarness()
        let first = try await harness.syncedGame("Doom")
        let second = try await harness.syncedGame("Quake")
        try await harness.repository.delete(id: first.id)
        try await harness.repository.delete(id: second.id)
        await harness.server.deleteOnAnotherDevice(second.id)

        try await harness.engine.syncNow()

        #expect(await harness.server.games.isEmpty)
        #expect(await harness.server.requests(.delete).count == 2)
        #expect(try await harness.pending().isEmpty)
        #expect(!harness.status.hasUndoneRejectedChanges)
    }

    @Test func localEditDuringAnInFlightPushKeepsThePendingChange() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        try await harness.edit(game) { $0.notes = "First" }

        await harness.server.hold(.update)
        let run = Task { try await harness.engine.syncNow() }
        await harness.server.waitUntilHeld()
        try await harness.edit(game) { $0.rating = 9 }
        await harness.server.release()
        try await run.value

        let pending = try #require(try await harness.pending().first)
        #expect(pending.kind == .update)
        #expect(pending.fields == [.notes, .rating])
        let local = try #require(harness.repository.game(id: game.id))
        #expect(local.notes == "First")
        #expect(local.rating == 9)
        #expect(await harness.server.games[game.id]?.rating == nil)

        try await harness.engine.syncNow()
        #expect(await harness.server.games[game.id]?.rating == 9)
        #expect(try await harness.pending().isEmpty)
    }

    @Test func aLocalDeleteWhileACreateIsInFlightIsPushedAfterIt() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.repository.create(SaveGameRequest(title: "Doom", platform: .pc))

        await harness.server.hold(.create)
        let run = Task { try await harness.engine.syncNow() }
        await harness.server.waitUntilHeld()
        // The CREATE was sent, so the server may have the game: its deletion is queued.
        try await harness.repository.delete(id: game.id)
        await harness.server.release()
        try await run.value

        // Neither the server's answer nor the pull brings it back.
        #expect(harness.repository.game(id: game.id) == nil)
        #expect(try await harness.pending().map(\.kind) == [.delete])
        #expect(await harness.server.games[game.id] != nil)

        try await harness.engine.syncNow()
        #expect(await harness.server.games[game.id] == nil)
        #expect(harness.repository.game(id: game.id) == nil)
        #expect(try await harness.pending().isEmpty)
    }

    @Test func aLocalDeleteWhileAnUpdateIsInFlightIsPushedAfterIt() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        try await harness.edit(game) { $0.notes = "In flight" }

        await harness.server.hold(.update)
        let run = Task { try await harness.engine.syncNow() }
        await harness.server.waitUntilHeld()
        try await harness.repository.delete(id: game.id)
        await harness.server.release()
        try await run.value

        #expect(harness.repository.game(id: game.id) == nil)
        #expect(try await harness.pending().map(\.kind) == [.delete])
        #expect(await harness.server.games[game.id]?.notes == "In flight")

        try await harness.engine.syncNow()
        #expect(await harness.server.games[game.id] == nil)
        #expect(harness.repository.game(id: game.id) == nil)
        #expect(try await harness.pending().isEmpty)
    }

    // MARK: Pull

    @Test func pullStoresNewGamesCursorAndSyncTime() async throws {
        let server = FakeGameServer()
        let games = (1...3).map { Fixtures.game("Game \($0)") }
        for game in games {
            await server.seed(game)
        }

        let harness = try await SyncHarness(server: server)

        #expect(Set(harness.repository.games.map(\.id)) == Set(games.map(\.id)))
        #expect(harness.status.hasCompletedInitialSync)
        let access = try #require(harness.repository.access)
        #expect(try await harness.store.cursor(access: access) == "3")
    }

    @Test func pullFollowsPagesUntilTheEnd() async throws {
        let server = FakeGameServer()
        let games = (1...(SyncEngine.pageSize + 20)).map { Fixtures.game("Game \($0)") }
        for game in games {
            await server.seed(game)
        }
        let harness = try await SyncHarness(server: server)
        #expect(harness.repository.gamesByID.count == games.count)
        #expect(await server.requests(.changes).count >= 2)
    }

    @Test func pullMergesServerChangesWithAPendingUpdate() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame { $0.notes = "Original notes" }

        await harness.server.hold(.changes)
        let run = Task { try await harness.engine.syncNow() }
        await harness.server.waitUntilHeld()
        await harness.server.editOnAnotherDevice(game.id) {
            $0.title = "Title from the other device"
            $0.notes = "Notes from the other device"
        }
        try await harness.edit(game) { $0.title = "Local title" }
        await harness.server.release()
        try await run.value

        let local = try #require(harness.repository.game(id: game.id))
        #expect(local.title == "Local title")
        #expect(local.notes == "Notes from the other device")
        #expect(try await harness.pending().map(\.fields) == [[.title]])
    }

    @Test func anEditOfAGameDeletedMeanwhileIsNotSavedAndKeepsTheInput() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        let form = GameFormViewModel(mode: .edit(game), repository: harness.repository)
        form.draft.notes = "Typed while it was deleted elsewhere"
        await harness.server.deleteOnAnotherDevice(game.id)
        try await harness.engine.syncNow()

        #expect(await form.save() == false)
        #expect(form.saveError == "Game not found.")
        #expect(form.draft.notes == "Typed while it was deleted elsewhere")
        #expect(harness.repository.game(id: game.id) == nil)
        #expect(try await harness.pending().isEmpty)
    }

    @Test func pulledDeletionWinsOverAPendingUpdate() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()

        await harness.server.hold(.changes)
        let run = Task { try await harness.engine.syncNow() }
        await harness.server.waitUntilHeld()
        await harness.server.deleteOnAnotherDevice(game.id)
        try await harness.edit(game) { $0.title = "Edited meanwhile" }
        await harness.server.release()
        try await run.value

        #expect(harness.repository.game(id: game.id) == nil)
        #expect(try await harness.pending().isEmpty)
        #expect(harness.status.pendingCount == 0)
    }

    @Test func anInterruptedResetContinuesInTheNextRun() async throws {
        let harness = try await SyncHarness()
        let kept = try await harness.syncedGame("Kept")
        let other = try await harness.syncedGame("Other")
        try await harness.edit(kept) { $0.title = "Kept (edited)" }
        let lastSyncedAt = harness.status.lastSyncedAt
        let access = try #require(harness.repository.access)
        let cursor = try await harness.store.cursor(access: access)

        // The cursor can't be continued, and the first page of the full pull fails.
        await harness.server.fail(.changes, with: .server(statusCode: 410, code: .syncResetRequired, details: []))
        await harness.server.fail(.changes, with: .network(.timedOut))
        await #expect(throws: APIError.network(.timedOut)) {
            try await harness.engine.syncNow()
        }
        // Nothing was removed: the collection is shown as it was.
        #expect(Set(harness.repository.games.map(\.id)) == [kept.id, other.id])
        #expect(harness.status.lastSyncedAt == lastSyncedAt)
        #expect(try await harness.store.cursor(access: access) == nil)

        // Restored from a backup without the other game: the next run finishes the reset and pushes it back.
        await harness.server.loseWithoutTrace(other.id)
        try await harness.engine.syncNow()
        try await harness.engine.syncNow()
        #expect(Set(harness.repository.games.map(\.id)) == [kept.id, other.id])
        #expect(await harness.server.games[other.id]?.title == "Other")
        #expect(await harness.server.games[kept.id]?.title == "Kept (edited)")
        #expect(try await harness.pending().isEmpty)
        let changesRequests = await harness.server.requests(.changes)
        #expect(changesRequests.map(\.cursor).filter { $0 == cursor }.count == 1)
    }

    @Test func aResetKeepsEveryGameAndPushesBackTheOnesTheServerLost() async throws {
        let harness = try await SyncHarness()
        let kept = try await harness.syncedGame("Kept")
        let lost = try await harness.syncedGame("Lost")
        let lostAndEdited = try await harness.syncedGame("Lost and edited")
        // The server was restored from a backup: two games are gone without a tombstone, and the stored
        // cursor can't be continued.
        await harness.server.loseWithoutTrace(lost.id)
        await harness.server.loseWithoutTrace(lostAndEdited.id)
        let added = Fixtures.game("Added after the restore")
        await harness.server.seed(added)
        await harness.server.hold(.changes)
        await harness.server.fail(.changes, with: .server(statusCode: 410, code: .syncResetRequired, details: []))

        let run = Task { try await harness.engine.syncNow() }
        await harness.server.waitUntilHeld()
        try await harness.edit(kept) { $0.title = "Kept (edited)" }
        // Edited before the reset: its UPDATE gets GAME_UNKNOWN and becomes a CREATE.
        try await harness.edit(lostAndEdited) { $0.rating = 7 }
        await harness.server.release()
        try await run.value

        // Nothing was removed; the game the server lost is pushed back by another run.
        #expect(Set(harness.repository.games.map(\.id)) == [kept.id, lost.id, lostAndEdited.id, added.id])
        #expect(harness.repository.game(id: kept.id)?.title == "Kept (edited)")
        try await harness.engine.syncNow()
        #expect(try await harness.pending().isEmpty)
        let server = await harness.server.games
        #expect(server[lost.id]?.title == "Lost")
        #expect(server[lostAndEdited.id]?.rating == 7)
        #expect(server[kept.id]?.title == "Kept (edited)")
        let lostAndEditedRequests = await harness.server.requests.filter { $0.gameID == lostAndEdited.id }.map(\.operation)
        #expect(lostAndEditedRequests == [.update, .create])
        #expect(Set(harness.repository.games.map(\.id)) == [kept.id, lost.id, lostAndEdited.id, added.id])
    }

    @Test func atMostOneResetPerRun() async throws {
        let server = FakeGameServer()
        for index in 0...SyncEngine.pageSize {
            await server.seed(Fixtures.game("Game \(index)"))
        }
        let harness = try await SyncHarness(server: server)
        // The full pull after the reset is told to reset again on its second page.
        let reset = APIError.server(statusCode: 410, code: .syncResetRequired, details: [])
        await server.fail(.changes, with: reset)
        await server.pass(.changes)
        await server.fail(.changes, with: reset)

        await #expect(throws: reset) {
            try await harness.engine.syncNow()
        }
        #expect(harness.repository.gamesByID.count == SyncEngine.pageSize + 1)

        // The next run goes on from the first page; every game turns out to be on the server.
        try await harness.engine.syncNow()
        #expect(try await harness.pending().isEmpty)
        #expect(harness.repository.gamesByID.count == SyncEngine.pageSize + 1)
    }

    @Test func aFullPullAfterAnAppUpdatePushesBackTheGamesTheServerLost() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        let server = FakeGameServer()
        let kept = Fixtures.game("Kept", .snes)
        let lost = Fixtures.game("Lost", .snes) { $0.notes = "Only on this device now" }
        await server.seed(kept)
        await server.seed(lost)
        let before = try await SyncHarness(server: server, store: GameStore(url: url, appVersion: "1.0 (1)"))
        #expect(before.repository.gamesByID.count == 2)
        await before.engine.deactivate(erasingData: false)
        // Restored from a backup without the game meanwhile; no 410 tells the updated app, its pull starts
        // without a cursor.
        await server.loseWithoutTrace(lost.id)
        let pulls = await server.requests(.changes).count

        let after = try await SyncHarness(server: server, store: GameStore(url: url, appVersion: "1.1 (2)"))
        try await after.engine.syncNow()

        #expect(await server.requests(.changes).dropFirst(pulls).first?.cursor == nil)
        #expect(await server.games[lost.id]?.notes == "Only on this device now")
        #expect(await server.requests(.create).map(\.gameID) == [lost.id])
        #expect(Set(after.repository.games.map(\.id)) == [kept.id, lost.id])
        #expect(try await after.pending().isEmpty)
    }

    @Test func atMostOneResetPerRunAlsoInAFullPullAfterAnAppUpdate() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        let server = FakeGameServer()
        for index in 0...SyncEngine.pageSize {
            await server.seed(Fixtures.game("Game \(index)"))
        }
        let before = try await SyncHarness(server: server, store: GameStore(url: url, appVersion: "1.0 (1)"))
        await before.engine.deactivate(erasingData: false)

        // The updated app's first run can't reach the server; its pull starts without a cursor later.
        await server.setUnreachable(true)
        let store = try GameStore(url: url, appVersion: "1.1 (2)")
        let repository = GameRepository(store: store, status: SyncStatus())
        let engine = SyncEngine(repository: repository, api: server)
        repository.onLocalChange = nil
        await engine.activate(ownerID: SyncHarness.owner)
        await #expect(throws: APIError.network(.notConnectedToInternet)) {
            try await engine.syncNow()
        }
        await server.setUnreachable(false)

        // The full pull is told to reset on its second page, and again after the reset.
        let reset = APIError.server(statusCode: 410, code: .syncResetRequired, details: [])
        await server.pass(.changes)
        await server.fail(.changes, with: reset)
        await server.pass(.changes)
        await server.fail(.changes, with: reset)
        let pulls = await server.requests(.changes).count
        await #expect(throws: reset) {
            try await engine.syncNow()
        }
        let cursors = await server.requests(.changes).dropFirst(pulls).map(\.cursor)
        #expect(cursors == [nil, "500", nil, "500"])

        // The next run goes on; every game turns out to be on the server.
        try await engine.syncNow()
        #expect(try await store.pendingChanges().isEmpty)
        #expect(repository.gamesByID.count == SyncEngine.pageSize + 1)
        #expect(await server.requests(.create).isEmpty)
    }

    // MARK: Failures

    @Test func updateOfAGameDeletedElsewhereRemovesIt() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        await harness.server.deleteOnAnotherDevice(game.id)
        try await harness.edit(game) { $0.rating = 8 }

        try await harness.engine.syncNow()

        #expect(await harness.server.requests(.update).count == 1)
        #expect(harness.repository.game(id: game.id) == nil)
        #expect(try await harness.pending().isEmpty)
        #expect(!harness.status.hasUndoneRejectedChanges)
    }

    @Test func rejectedUpdateIsUndoneWithTheServersVersion() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        try await harness.edit(game) { $0.title = "Rejected title" }
        await harness.server.fail(.update, with: .server(statusCode: 400, code: .validationFailed, details: ["title is invalid"]))

        try await harness.engine.syncNow()

        #expect(await harness.server.requests(.get) == [.init(operation: .get, gameID: game.id)])
        #expect(harness.repository.game(id: game.id)?.title == "Chrono Trigger")
        #expect(try await harness.pending().isEmpty)
        #expect(harness.status.hasUndoneRejectedChanges)
    }

    @Test func rejectedDeleteRestoresTheServersVersion() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        try await harness.repository.delete(id: game.id)
        await harness.server.fail(.delete, with: .server(statusCode: 409, code: .conflict, details: []))

        try await harness.engine.syncNow()

        #expect(harness.repository.game(id: game.id)?.title == game.title)
        #expect(try await harness.pending().isEmpty)
        #expect(harness.status.hasUndoneRejectedChanges)
    }

    @Test func aRejectedCreateIsKeptAndSentAgainLater() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.repository.create(SaveGameRequest(title: "Doom", platform: .pc))
        let other = try await harness.repository.create(SaveGameRequest(title: "Quake", platform: .pc))
        let rejection = APIError.server(statusCode: 400, code: .validationFailed, details: [])
        await harness.server.fail(.create, with: rejection)
        let fromAnotherDevice = Fixtures.game("From another device")
        await harness.server.seed(fromAnotherDevice)

        await #expect(throws: rejection) {
            try await harness.engine.syncNow()
        }
        // Nothing the user typed is lost, and the rest of the run went on.
        #expect(harness.repository.game(id: game.id)?.title == "Doom")
        #expect(try await harness.pending().map(\.gameID) == [game.id])
        #expect(!harness.status.hasUndoneRejectedChanges)
        #expect(await harness.server.games[other.id] != nil)
        #expect(harness.repository.game(id: fromAnotherDevice.id) != nil)

        try await harness.engine.syncNow()
        #expect(await harness.server.games[game.id]?.title == "Doom")
        #expect(try await harness.pending().isEmpty)
    }

    @Test func networkFailureKeepsTheChangeAndShowsOffline() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.repository.create(SaveGameRequest(title: "Doom", platform: .pc))
        await harness.server.fail(.create, with: .network(.notConnectedToInternet))

        await #expect(throws: APIError.network(.notConnectedToInternet)) {
            try await harness.engine.syncNow()
        }
        #expect(harness.status.isOffline)
        #expect(harness.status.lastErrorMessage == "Can't connect to the server. Check your connection.")
        #expect(try await harness.pending().map(\.kind) == [.create])
        #expect(harness.status.indicator == .offline)

        try await harness.engine.syncNow()
        #expect(!harness.status.isOffline)
        #expect(harness.status.lastErrorMessage == nil)
        #expect(await harness.server.games[game.id] != nil)
        #expect(try await harness.pending().isEmpty)
    }

    @Test func aChangeThatFailsDoesNotHoldUpTheOthersOrThePull() async throws {
        let harness = try await SyncHarness()
        let failing = try await harness.repository.create(SaveGameRequest(title: "Doom", platform: .pc))
        let next = try await harness.repository.create(SaveGameRequest(title: "Quake", platform: .pc))
        let failure = APIError.server(statusCode: 500, code: .internalError, details: [])
        await harness.server.fail(.create, with: failure)
        let fromAnotherDevice = Fixtures.game("From another device")
        await harness.server.seed(fromAnotherDevice)

        await #expect(throws: failure) {
            try await harness.engine.syncNow()
        }
        #expect(!harness.status.isOffline)
        #expect(harness.status.lastErrorMessage == "Something went wrong. Please try again.")
        #expect(try await harness.pending().map(\.gameID) == [failing.id])
        #expect(await harness.server.games[next.id] != nil)
        #expect(harness.repository.game(id: fromAnotherDevice.id) != nil)
        #expect(!harness.status.hasUndoneRejectedChanges)

        try await harness.engine.syncNow()
        #expect(await harness.server.games[failing.id] != nil)
        #expect(try await harness.pending().isEmpty)
    }

    @Test func aPushWhoseAnswerCantBeDecodedDoesNotStopThePushOrThePull() async throws {
        let harness = try await SyncHarness()
        let first = try await harness.syncedGame("Doom")
        let second = try await harness.syncedGame("Quake")
        try await harness.edit(first) { $0.notes = "First" }
        try await harness.edit(second) { $0.notes = "Second" }
        // The server stores the edit, but its answer is a game this app version can't read.
        let failure = APIError.undecodable(statusCode: 200)
        await harness.server.fail(.update, with: failure, afterApplying: true)
        let fromAnotherDevice = Fixtures.game("From another device")
        await harness.server.seed(fromAnotherDevice)

        await #expect(throws: failure) {
            try await harness.engine.syncNow()
        }
        #expect(try await harness.pending().map(\.gameID) == [first.id])
        #expect(await harness.server.games[second.id]?.notes == "Second")
        #expect(harness.repository.game(id: fromAnotherDevice.id) != nil, "the pull ran")
        #expect(!harness.status.isOffline)

        try await harness.engine.syncNow()
        #expect(await harness.server.games[first.id]?.notes == "First")
        #expect(try await harness.pending().isEmpty)
    }

    @Test func aRunEndedByTheServerReportsThatErrorRatherThanTheFailedChange() async throws {
        let harness = try await SyncHarness()
        _ = try await harness.repository.create(SaveGameRequest(title: "Doom", platform: .pc))
        await harness.server.fail(.create, with: .server(statusCode: 500, code: .internalError, details: []))
        await harness.server.fail(.changes, with: .network(.notConnectedToInternet))

        await #expect(throws: APIError.network(.notConnectedToInternet)) {
            try await harness.engine.syncNow()
        }
        #expect(harness.status.indicator == .offline)
    }

    @Test(arguments: [APIError.network(.timedOut), .http(statusCode: 502), .server(statusCode: 429, code: .tooManyRequests, details: [])])
    func whenTheServerCantBeUsedTheRunEndsWithoutPulling(error: APIError) async throws {
        let harness = try await SyncHarness()
        let first = try await harness.repository.create(SaveGameRequest(title: "Doom", platform: .pc))
        _ = try await harness.repository.create(SaveGameRequest(title: "Quake", platform: .pc))
        await harness.server.fail(.create, with: error)
        let pulls = await harness.server.requests(.changes).count

        await #expect(throws: error) {
            try await harness.engine.syncNow()
        }
        #expect(await harness.server.requests(.create).map(\.gameID) == [first.id])
        #expect(await harness.server.requests(.changes).count == pulls)
        #expect(try await harness.pending().count == 2)
    }

    @Test func anUpdateOfAGameTheServerDoesntKnowIsCreatedAgainInTheSameRun() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame { $0.notes = "Local notes" }
        // Restored from a backup older than the game.
        await harness.server.loseWithoutTrace(game.id)
        try await harness.edit(game) { $0.rating = 8 }

        try await harness.engine.syncNow()

        let requests = await harness.server.requests.filter { $0.operation != .changes }
        #expect(requests == [
            .init(operation: .update, gameID: game.id, fields: [.rating]),
            .init(operation: .create, gameID: game.id),
        ])
        let server = try #require(await harness.server.games[game.id])
        #expect(server.rating == 8)
        #expect(server.notes == "Local notes")
        #expect(try await harness.pending().isEmpty)
        #expect(!harness.status.hasUndoneRejectedChanges)
    }

    nonisolated private static let classificationCases: [(error: APIError, kind: SyncEngine.FailureKind)] = [
        (.server(statusCode: 404, code: .gameNotFound, details: []), .gameGone),
        (.server(statusCode: 404, code: .gameUnknown, details: []), .gameUnknown),
        (.server(statusCode: 400, code: .validationFailed, details: []), .rejected),
        (.server(statusCode: 409, code: .conflict, details: []), .rejected),
        // Any other code with an API error body: only this change failed.
        (.server(statusCode: 400, code: .badRequest, details: []), .changeFailed),
        (.server(statusCode: 404, code: .notFound, details: []), .changeFailed),
        (.server(statusCode: 403, code: .unknown, details: []), .changeFailed),
        (.server(statusCode: 409, code: .unknown, details: []), .changeFailed),
        (.server(statusCode: 500, code: .internalError, details: []), .changeFailed),
        (.server(statusCode: 503, code: .unknown, details: []), .changeFailed),
        (.server(statusCode: 401, code: .unauthorized, details: []), .serverUnavailable),
        (.server(statusCode: 408, code: .unknown, details: []), .serverUnavailable),
        (.server(statusCode: 429, code: .tooManyRequests, details: []), .serverUnavailable),
        // Responses without an API error body never undo a change or delete a game.
        (.http(statusCode: 400), .serverUnavailable),
        (.http(statusCode: 403), .serverUnavailable),
        (.http(statusCode: 404), .serverUnavailable),
        (.http(statusCode: 409), .serverUnavailable),
        (.http(statusCode: 502), .serverUnavailable),
        (.network(.timedOut), .serverUnavailable),
        (.invalidResponse, .serverUnavailable),
        // A success whose game this app version can't decode: only this change failed.
        (.undecodable(statusCode: 200), .changeFailed),
        (.undecodable(statusCode: 201), .changeFailed),
        (.sessionExpired, .serverUnavailable),
    ]

    @Test(arguments: classificationCases)
    func failureClassification(error: APIError, expected: SyncEngine.FailureKind) {
        #expect(SyncEngine.classify(error) == expected)
    }

    @Test func aDeleteAnswered404WithoutAnAPIErrorBodyIsKept() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        try await harness.repository.delete(id: game.id)
        await harness.server.fail(.delete, with: .http(statusCode: 404))

        await #expect(throws: APIError.http(statusCode: 404)) {
            try await harness.engine.syncNow()
        }
        #expect(try await harness.pending().map(\.kind) == [.delete])
        #expect(await harness.server.games[game.id] != nil)

        try await harness.engine.syncNow()
        #expect(await harness.server.games[game.id] == nil)
        #expect(try await harness.pending().isEmpty)
    }

    @Test(arguments: [APIError.http(statusCode: 403), .http(statusCode: 404), .http(statusCode: 400), .server(statusCode: 408, code: .unknown, details: [])])
    func anEditAnsweredWithoutAnAPIErrorBodyIsNeitherUndoneNorDeleted(error: APIError) async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        try await harness.edit(game) { $0.notes = "Kept" }
        await harness.server.fail(.update, with: error)

        await #expect(throws: error) {
            try await harness.engine.syncNow()
        }
        #expect(harness.repository.game(id: game.id)?.notes == "Kept")
        #expect(try await harness.pending().map(\.fields) == [[.notes]])
        #expect(!harness.status.hasUndoneRejectedChanges)
    }

    @Test func a404WithAnotherCodeIsAFailureOfThatChangeNotADeletion() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        try await harness.edit(game) { $0.notes = "Kept" }
        let failure = APIError.server(statusCode: 404, code: .notFound, details: [])
        await harness.server.fail(.update, with: failure)

        await #expect(throws: failure) {
            try await harness.engine.syncNow()
        }

        #expect(harness.repository.game(id: game.id)?.notes == "Kept")
        #expect(try await harness.pending().map(\.fields) == [[.notes]])
        #expect(await harness.server.requests(.get).isEmpty)
        #expect(!harness.status.hasUndoneRejectedChanges)
    }

    @Test func anUndoWhoseServerVersionCantBeLoadedIsTriedAgainLater() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        try await harness.edit(game) { $0.title = "Rejected title" }
        let rejection = APIError.server(statusCode: 400, code: .validationFailed, details: [])
        let failure = APIError.server(statusCode: 500, code: .internalError, details: [])
        await harness.server.fail(.update, with: rejection)
        await harness.server.fail(.get, with: failure)

        await #expect(throws: failure) {
            try await harness.engine.syncNow()
        }
        #expect(harness.repository.game(id: game.id)?.title == "Rejected title")
        #expect(try await harness.pending().map(\.fields) == [[.title]])
        #expect(!harness.status.hasUndoneRejectedChanges)

        await harness.server.fail(.update, with: rejection)
        try await harness.engine.syncNow()
        #expect(harness.repository.game(id: game.id)?.title == "Chrono Trigger")
        #expect(try await harness.pending().isEmpty)
        #expect(harness.status.hasUndoneRejectedChanges)
    }

    @Test func aRejectedUpdateOfAGameTheServerDoesntKnowIsPushedAsANewGame() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame { $0.notes = "Local notes" }
        // Restored from a backup older than the game: the undo finds nothing to restore.
        await harness.server.loseWithoutTrace(game.id)
        try await harness.edit(game) { $0.title = "Edited" }
        await harness.server.fail(.update, with: .server(statusCode: 400, code: .validationFailed, details: []))

        try await harness.engine.syncNow()

        let requests = await harness.server.requests.filter { $0.operation != .changes }
        #expect(requests == [
            .init(operation: .update, gameID: game.id, fields: [.title]),
            .init(operation: .get, gameID: game.id),
            .init(operation: .create, gameID: game.id),
        ])
        let server = try #require(await harness.server.games[game.id])
        #expect(server.title == "Edited")
        #expect(server.notes == "Local notes")
        #expect(harness.repository.game(id: game.id)?.title == "Edited")
        #expect(try await harness.pending().isEmpty)
        #expect(!harness.status.hasUndoneRejectedChanges)
    }

    @Test func aRejectedDeleteOfAGameTheServerDoesntKnowIsDropped() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        await harness.server.loseWithoutTrace(game.id)
        try await harness.repository.delete(id: game.id)
        await harness.server.fail(.delete, with: .server(statusCode: 409, code: .conflict, details: []))

        try await harness.engine.syncNow()

        #expect(await harness.server.requests.filter { $0.operation != .changes }.map(\.operation) == [.delete, .get])
        #expect(harness.repository.game(id: game.id) == nil)
        #expect(try await harness.pending().isEmpty)
        #expect(!harness.status.hasUndoneRejectedChanges)
    }

    @Test func aRejectedChangeEditedMeanwhileIsSentAgainInsteadOfUndone() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        try await harness.edit(game) { $0.title = "Rejected title" }
        let rejection = APIError.server(statusCode: 400, code: .validationFailed, details: [])
        await harness.server.hold(.update)
        await harness.server.fail(.update, with: rejection)

        let run = Task { try await harness.engine.syncNow() }
        await harness.server.waitUntilHeld()
        try await harness.edit(game) { $0.title = "Fixed title" }
        await harness.server.release()
        await #expect(throws: rejection) {
            try await run.value
        }

        // Undoing it would lose the newer edit: it stays queued as a failure of this change.
        #expect(harness.repository.game(id: game.id)?.title == "Fixed title")
        #expect(try await harness.pending().map(\.fields) == [[.title]])
        #expect(!harness.status.hasUndoneRejectedChanges)
        #expect(harness.status.lastErrorMessage == "Please check the entered data.")

        try await harness.engine.syncNow()
        #expect(await harness.server.games[game.id]?.title == "Fixed title")
        #expect(try await harness.pending().isEmpty)
        #expect(!harness.status.hasUndoneRejectedChanges)
    }

    @Test func aLocalDeleteStillWinsOverARejectedChange() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        try await harness.edit(game) { $0.rating = 11 }
        let rejection = APIError.server(statusCode: 400, code: .validationFailed, details: [])
        await harness.server.hold(.update)
        await harness.server.fail(.update, with: rejection)

        let run = Task { try await harness.engine.syncNow() }
        await harness.server.waitUntilHeld()
        try await harness.repository.delete(id: game.id)
        await harness.server.release()
        await #expect(throws: rejection) {
            try await run.value
        }

        // Not restored: the delete stays queued, and is pushed next.
        #expect(harness.repository.game(id: game.id) == nil)
        #expect(try await harness.pending().map(\.kind) == [.delete])
        #expect(!harness.status.hasUndoneRejectedChanges)

        try await harness.engine.syncNow()
        #expect(await harness.server.games[game.id] == nil)
        #expect(harness.repository.game(id: game.id) == nil)
        #expect(try await harness.pending().isEmpty)
    }

    @Test func aGameTheServerLostIsCreatedAgainWithItsUnknownValuesUnchanged() async throws {
        let harness = try await SyncHarness()
        let game = try Fixtures.gameWithUnknownValues()
        await harness.server.seed(game)
        try await harness.engine.syncNow()
        #expect(harness.repository.game(id: game.id)?.unknownWireValues == Fixtures.unknownWireValues)
        await harness.server.loseWithoutTrace(game.id)
        try await harness.edit(game) { $0.rating = 8 }

        try await harness.engine.syncNow()

        let requests = await harness.server.requests.filter { $0.operation != .changes }
        #expect(requests == [
            .init(operation: .update, gameID: game.id, fields: [.rating]),
            .init(operation: .create, gameID: game.id),
        ])
        let server = try #require(await harness.server.games[game.id])
        #expect(server.rating == 8)
        #expect(server.platform == .other)
        #expect(server.unknownWireValues == Fixtures.unknownWireValues)
    }

    @Test func undoneRejectedChangesAreShownUntilSeenEvenAfterReactivation() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        try await harness.edit(game) { $0.rating = 3 }
        await harness.server.fail(.update, with: .server(statusCode: 400, code: .validationFailed, details: []))
        try await harness.engine.syncNow()
        #expect(harness.status.hasUndoneRejectedChanges)

        // No screen showed it before the session expired; it is still shown after the next sign-in.
        await harness.engine.sessionDidExpire()
        #expect(!harness.status.hasUndoneRejectedChanges)
        await harness.engine.activate(ownerID: SyncHarness.owner)
        #expect(harness.status.hasUndoneRejectedChanges)

        await harness.repository.acknowledgeUndoneRejectedChanges()?.value
        #expect(!harness.status.hasUndoneRejectedChanges)
        await harness.engine.sessionDidExpire()
        await harness.engine.activate(ownerID: SyncHarness.owner)
        #expect(!harness.status.hasUndoneRejectedChanges)
    }

    @Test func aGameMissingLocallyIsAFailureOfThatChangeNeverADeletion() async throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appending(path: "games.sqlite")
        let game = Fixtures.game("Created offline")
        do {
            let store = try GameStore(url: url)
            let (access, _) = try await store.activate(ownerID: SyncHarness.owner)
            _ = try await store.insert(game, at: .now, access: access)
        }
        // The stored game can't be read (e.g. written by a newer version of the app).
        try SQLiteConnection(url: url).run("UPDATE games SET data = ?", [.blob(Data("{}".utf8))])

        let server = FakeGameServer()
        let store = try GameStore(url: url)
        let repository = GameRepository(store: store, status: SyncStatus())
        let engine = SyncEngine(repository: repository, api: server)
        repository.onLocalChange = nil
        await engine.activate(ownerID: SyncHarness.owner)

        await #expect(throws: LocalStoreError.gameUnavailable) {
            try await engine.syncNow()
        }
        #expect(await server.requests(.create).isEmpty)
        #expect(try await store.pendingChanges().map(\.kind) == [.create])
        #expect(repository.status.pendingCount == 1)
        #expect(repository.status.lastErrorMessage == "Something went wrong. Please try again.")
        #expect(!repository.status.hasUndoneRejectedChanges)
        #expect(try SQLiteConnection(url: url).query("SELECT id FROM games") { $0.text(0) } == [game.id])
    }

    @Test func anAppUpdatePullsTheWholeCollectionAgain() async throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appending(path: "games.sqlite")
        let server = FakeGameServer()
        let game = Fixtures.game("Chrono Trigger", .snes)
        await server.seed(game)

        let before = try await SyncHarness(server: server, store: GameStore(url: url, appVersion: "1.0 (1)"))
        let cursor = try await before.store.cursor(access: try #require(before.repository.access))
        #expect(cursor != nil)
        // An edit made with the old version is still waiting when the app is updated.
        try await before.edit(game) { $0.notes = "Unsynced" }
        await before.engine.deactivate(erasingData: false)

        let after = try await SyncHarness(server: server, store: GameStore(url: url, appVersion: "1.1 (2)"))
        #expect(await server.requests(.changes).last?.cursor == nil)
        #expect(await server.games[game.id]?.notes == "Unsynced")
        #expect(after.repository.game(id: game.id)?.notes == "Unsynced")
        #expect(try await after.pending().isEmpty)
    }

    // MARK: Windows

    @Test func theAppIsInTheForegroundWhileAnyWindowIsActive() async throws {
        let harness = try await SyncHarness()
        let first = UUID()
        let second = UUID()
        #expect(!harness.engine.isAppActive)

        harness.engine.setScene(first, isActive: true)
        harness.engine.setScene(second, isActive: true)
        #expect(harness.engine.isAppActive)
        // One window going to the background (or closing) leaves the app in the foreground.
        harness.engine.setScene(first, isActive: false)
        #expect(harness.engine.isAppActive)
        harness.engine.setScene(second, isActive: false)
        #expect(!harness.engine.isAppActive)
        harness.engine.setScene(second, isActive: true)
        #expect(harness.engine.isAppActive)
    }

    @Test func backoffDoublesUpToFiveMinutes() {
        let backoff = SyncBackoff()
        #expect((1...5).map { backoff.delay(afterFailures: $0) } == [.seconds(2), .seconds(4), .seconds(8), .seconds(16), .seconds(32)])
        #expect(backoff.delay(afterFailures: 8) == .seconds(256))
        #expect(backoff.delay(afterFailures: 9) == .seconds(300))
        #expect(backoff.delay(afterFailures: 100) == .seconds(300))
    }

    // MARK: Owner

    @Test func anotherUsersSignInWipesTheData() async throws {
        let harness = try await SyncHarness()
        _ = try await harness.syncedGame()
        _ = try await harness.repository.create(SaveGameRequest(title: "Unsynced", platform: .pc))
        // Keep the new user's first sync from pulling the shared fake server's games.
        await harness.server.fail(.changes, with: .network(.timedOut))

        await harness.engine.activate(ownerID: "someone-else")
        #expect(harness.repository.isEmpty)
        #expect(harness.status.pendingCount == 0)
        #expect(try await harness.pending().isEmpty)

        // The first user's data does not come back either.
        await harness.engine.deactivate(erasingData: false)
        await harness.engine.activate(ownerID: SyncHarness.owner)
        #expect(try await harness.pending().isEmpty)
    }

    @Test func expiredSessionKeepsUnsyncedChangesForTheSameUser() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.repository.create(SaveGameRequest(title: "Unsynced", platform: .pc))
        // The sync after the next sign-in can't reach the server, so the change stays pending.
        await harness.server.fail(.create, with: .network(.timedOut))

        await harness.engine.sessionDidExpire()
        await #expect(throws: LocalStoreError.accessRevoked) {
            try await harness.repository.delete(id: game.id)
        }
        await harness.engine.activate(ownerID: SyncHarness.owner)

        #expect(harness.repository.game(id: game.id) != nil)
        #expect(try await harness.pending().map(\.kind) == [.create])
    }

    /// A memory-only cache holding one cover image.
    private func coverCache() -> (URLCache, URLRequest) {
        let cache = URLCache(memoryCapacity: 1_000_000, diskCapacity: 0)
        let request = URLRequest(url: URL(string: "https://example.com/cover.jpg")!)
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: nil)!
        cache.storeCachedResponse(CachedURLResponse(response: response, data: Data(repeating: 1, count: 100)), for: request)
        return (cache, request)
    }

    @Test func signingOutWipesTheDataAndTheCoverImagesAndThenTheMark() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        let (cache, cover) = coverCache()
        let harness = try await SyncHarness(store: GameStore(url: url), coverCache: cache)
        _ = try await harness.syncedGame()

        await harness.engine.userWillSignOut()
        #expect(GameStore.isWipePending(at: url))
        #expect(cache.cachedResponse(for: cover) != nil)

        await harness.engine.userDidSignOut(sessionCleared: true)
        #expect(!FileManager.default.fileExists(atPath: url.path(percentEncoded: false)))
        #expect(cache.cachedResponse(for: cover) == nil)
        #expect(!GameStore.isWipePending(at: url))
    }

    @Test func aWipeWhoseSessionCouldntBeClearedStaysMarkedUntilTheNextActivation() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        let harness = try await SyncHarness(store: GameStore(url: url))
        _ = try await harness.syncedGame()

        await harness.engine.userWillSignOut()
        await harness.engine.userDidSignOut(sessionCleared: false)
        #expect(!FileManager.default.fileExists(atPath: url.path(percentEncoded: false)))
        #expect(GameStore.isWipePending(at: url))

        // A sign-in replaces the session the wipe was marked for.
        await harness.engine.activate(ownerID: SyncHarness.owner)
        #expect(!GameStore.isWipePending(at: url))
        #expect(harness.repository.isLoaded)
        try await harness.engine.syncNow()
    }

    @Test func signOutCancelsTheRunningSyncBeforeWiping() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        try await harness.edit(game) { $0.notes = "In flight" }
        let access = try #require(harness.repository.access)

        await harness.server.hold(.update)
        let run = Task { try await harness.engine.syncNow() }
        await harness.server.waitUntilHeld()
        let signOut = Task { await harness.engine.userDidSignOut(sessionCleared: true) }
        await #expect(throws: CancellationError.self) {
            try await run.value
        }
        await harness.server.release()
        await signOut.value

        #expect(await harness.store.snapshot.games.isEmpty)
        #expect(try await harness.pending().isEmpty)
        #expect(!harness.status.isSyncing)
        // A write for the signed-out user is refused.
        await #expect(throws: LocalStoreError.accessRevoked) {
            _ = try await harness.store.insert(Fixtures.game("Late"), at: .now, access: access)
        }
    }
}
