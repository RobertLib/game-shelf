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

    init(server: FakeGameServer = FakeGameServer(), store: GameStore? = nil) async throws {
        self.server = server
        self.store = try store ?? GameStore(url: nil)
        repository = GameRepository(store: self.store, status: SyncStatus())
        engine = SyncEngine(repository: repository, api: server)
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

    @Test func aResetIsStoredTogetherWithTheFirstPageOfTheFullPull() async throws {
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
        // Nothing was thrown away yet: the collection is shown as it was.
        #expect(Set(harness.repository.games.map(\.id)) == [kept.id, other.id])
        #expect(harness.status.lastSyncedAt == lastSyncedAt)
        #expect(try await harness.store.cursor(access: access) == cursor)

        await harness.server.loseWithoutTrace(other.id)
        await harness.server.fail(.changes, with: .server(statusCode: 410, code: .syncResetRequired, details: []))
        try await harness.engine.syncNow()
        #expect(Set(harness.repository.games.map(\.id)) == [kept.id])
        let changesRequests = await harness.server.requests(.changes)
        #expect(changesRequests.suffix(2).map(\.cursor) == [cursor, nil])
    }

    @Test func resetRequiredKeepsGamesWithPendingChangesAndPullsAgain() async throws {
        let harness = try await SyncHarness()
        let kept = try await harness.syncedGame("Kept")
        let lost = try await harness.syncedGame("Lost")
        // The server was restored from a backup: one game is gone without a tombstone, and the
        // stored cursor can't be continued.
        await harness.server.loseWithoutTrace(lost.id)
        let added = Fixtures.game("Added after the restore")
        await harness.server.seed(added)
        await harness.server.hold(.changes)
        await harness.server.fail(.changes, with: .server(statusCode: 410, code: .syncResetRequired, details: []))

        let run = Task { try await harness.engine.syncNow() }
        await harness.server.waitUntilHeld()
        try await harness.edit(kept) { $0.title = "Kept (edited)" }
        await harness.server.release()
        try await run.value

        #expect(Set(harness.repository.games.map(\.id)) == [kept.id, added.id])
        #expect(harness.repository.game(id: kept.id)?.title == "Kept (edited)")
        #expect(try await harness.pending().map(\.gameID) == [kept.id])
        let changesRequests = await harness.server.requests(.changes)
        #expect(changesRequests.count >= 2)
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
        await harness.server.fail(.delete, with: .server(statusCode: 403, code: .forbidden, details: []))

        try await harness.engine.syncNow()

        #expect(harness.repository.game(id: game.id)?.title == game.title)
        #expect(try await harness.pending().isEmpty)
        #expect(harness.status.hasUndoneRejectedChanges)
    }

    @Test func rejectedCreateRemovesTheGame() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.repository.create(SaveGameRequest(title: "Doom", platform: .pc))
        await harness.server.fail(.create, with: .server(statusCode: 409, code: .conflict, details: []))

        try await harness.engine.syncNow()

        #expect(harness.repository.game(id: game.id) == nil)
        #expect(try await harness.pending().isEmpty)
        #expect(harness.status.hasUndoneRejectedChanges)
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

    @Test func serverErrorsAreTemporaryButNotOffline() async throws {
        let harness = try await SyncHarness()
        _ = try await harness.repository.create(SaveGameRequest(title: "Doom", platform: .pc))
        await harness.server.fail(.create, with: .server(statusCode: 503, code: .unknown, details: []))

        await #expect(throws: APIError.self) {
            try await harness.engine.syncNow()
        }
        #expect(!harness.status.isOffline)
        #expect(harness.status.pendingCount == 1)
        #expect(!harness.status.hasUndoneRejectedChanges)
    }

    nonisolated private static let classificationCases: [(error: APIError, kind: SyncEngine.FailureKind)] = [
        (.server(statusCode: 404, code: .gameNotFound, details: []), .gameGone),
        (.server(statusCode: 400, code: .validationFailed, details: []), .rejected),
        (.server(statusCode: 409, code: .conflict, details: []), .rejected),
        (.server(statusCode: 404, code: .notFound, details: []), .rejected),
        (.server(statusCode: 403, code: .unknown, details: []), .rejected),
        (.server(statusCode: 401, code: .unauthorized, details: []), .temporary),
        (.server(statusCode: 408, code: .unknown, details: []), .temporary),
        (.server(statusCode: 429, code: .tooManyRequests, details: []), .temporary),
        (.server(statusCode: 500, code: .internalError, details: []), .temporary),
        // Responses without an API error body never undo a change or delete a game.
        (.http(statusCode: 400), .temporary),
        (.http(statusCode: 403), .temporary),
        (.http(statusCode: 404), .temporary),
        (.http(statusCode: 409), .temporary),
        (.http(statusCode: 502), .temporary),
        (.network(.timedOut), .temporary),
        (.invalidResponse, .temporary),
        (.sessionExpired, .temporary),
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

    @Test func a404WithAnotherCodeIsARejectionNotADeletion() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        try await harness.edit(game) { $0.notes = "Rejected" }
        await harness.server.fail(.update, with: .server(statusCode: 404, code: .notFound, details: []))

        try await harness.engine.syncNow()

        #expect(harness.repository.game(id: game.id) == game, "the server's version is restored")
        #expect(try await harness.pending().isEmpty)
        #expect(harness.status.hasUndoneRejectedChanges)
    }

    @Test func undoneRejectedChangesAreShownUntilSeenEvenAfterReactivation() async throws {
        let harness = try await SyncHarness()
        _ = try await harness.repository.create(SaveGameRequest(title: "Doom", platform: .pc))
        await harness.server.fail(.create, with: .server(statusCode: 400, code: .validationFailed, details: []))
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

    @Test func aGameMissingLocallyIsATemporaryErrorNeverADeletion() async throws {
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

    @Test func signOutCancelsTheRunningSyncBeforeWiping() async throws {
        let harness = try await SyncHarness()
        let game = try await harness.syncedGame()
        try await harness.edit(game) { $0.notes = "In flight" }
        let access = try #require(harness.repository.access)

        await harness.server.hold(.update)
        let run = Task { try await harness.engine.syncNow() }
        await harness.server.waitUntilHeld()
        let signOut = Task { await harness.engine.userWillSignOut() }
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
