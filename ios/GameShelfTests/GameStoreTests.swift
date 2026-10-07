import Foundation
import Testing
@testable import GameShelf

@Suite("Local store (SQLite)")
struct GameStoreTests {
    private let owner = "user-1"
    private let now = Date(timeIntervalSince1970: 1_750_000_000)

    private func makeStore() async throws -> (GameStore, StoreAccess) {
        let store = try GameStore(url: nil)
        let (access, _) = try await store.activate(ownerID: owner)
        return (store, access)
    }

    private func pending(_ store: GameStore) async throws -> PendingChange? {
        try await store.pendingChanges().first
    }

    /// A game the server already has (pulled, nothing pending).
    private func pulled(_ game: Game, into store: GameStore, access: StoreAccess) async throws {
        _ = try await store.applyChanges(
            GameChanges(games: [game], deletedIds: [], cursor: "1", hasMore: false), at: now, access: access
        )
    }

    private func edit(_ store: GameStore, _ game: Game, access: StoreAccess, _ change: (inout SaveGameRequest) -> Void) async throws -> StoreSnapshot? {
        var request = SaveGameRequest(game: game)
        change(&request)
        return try await store.update(id: game.id, with: request, at: now, access: access)
    }

    // MARK: Basics

    @Test func persistsAcrossReopening() async throws {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appending(path: "games.sqlite")
        let game = try Fixtures.decodedGame

        do {
            let store = try GameStore(url: url)
            let (access, _) = try await store.activate(ownerID: owner)
            _ = try await store.insert(game, at: now, access: access)
            _ = try await store.applyChanges(GameChanges(games: [], deletedIds: [], cursor: "42", hasMore: false), at: now, access: access)
        }

        let reopened = try GameStore(url: url)
        let (access, snapshot) = try await reopened.activate(ownerID: owner)
        #expect(snapshot.games[game.id] == game)
        #expect(snapshot.pendingCount == 1)
        #expect(snapshot.lastSyncedAt == now)
        #expect(try await reopened.cursor(access: access) == "42")
        #expect(try await pending(reopened)?.kind == .create)
    }

    @Test func activatingAnotherOwnerWipesEverything() async throws {
        let (store, access) = try await makeStore()
        _ = try await store.insert(Fixtures.game("Doom"), at: now, access: access)
        _ = try await store.applyChanges(GameChanges(games: [Fixtures.game("Quake")], deletedIds: [], cursor: "7", hasMore: false), at: now, access: access)

        let (otherAccess, snapshot) = try await store.activate(ownerID: "user-2")
        #expect(snapshot.games.isEmpty)
        #expect(snapshot.pendingCount == 0)
        #expect(snapshot.lastSyncedAt == nil)
        #expect(try await store.cursor(access: otherAccess) == nil)
        #expect(try await store.pendingChanges().isEmpty)
    }

    @Test func reactivatingTheSameOwnerKeepsTheData() async throws {
        let (store, access) = try await makeStore()
        _ = try await store.insert(Fixtures.game("Doom"), at: now, access: access)
        let (_, snapshot) = try await store.activate(ownerID: owner)
        #expect(snapshot.games.count == 1)
        #expect(snapshot.pendingCount == 1)
    }

    @Test func writesWithARevokedAccessAreRefusedAndRolledBack() async throws {
        let (store, access) = try await makeStore()
        try await store.erase()
        await #expect(throws: LocalStoreError.accessRevoked) {
            _ = try await store.insert(Fixtures.game("Doom"), at: now, access: access)
        }
        let page = GameChanges(games: [Fixtures.game("Quake")], deletedIds: [], cursor: "1", hasMore: false)
        await #expect(throws: LocalStoreError.accessRevoked) {
            _ = try await store.applyChanges(page, at: now, access: access)
        }
        // A new activation of the same user does not revive the old access either.
        let (newAccess, snapshot) = try await store.activate(ownerID: owner)
        #expect(newAccess != access)
        #expect(snapshot.games.isEmpty)
        await #expect(throws: LocalStoreError.accessRevoked) {
            _ = try await store.insert(Fixtures.game("Doom"), at: now, access: access)
        }
    }

    @Test func pendingChangesArePushedOldestFirst() async throws {
        let (store, access) = try await makeStore()
        let games = (1...3).map { Fixtures.game("Game \($0)") }
        for (offset, game) in games.enumerated() {
            _ = try await store.insert(game, at: now.addingTimeInterval(Double(offset)), access: access)
        }
        #expect(try await store.pendingGameIDs(access: access) == games.map(\.id))
    }

    // MARK: Local changes (coalescing table)

    @Test func createQueuesACreate() async throws {
        let (store, access) = try await makeStore()
        let snapshot = try await store.insert(Fixtures.game("Doom"), at: now, access: access)
        #expect(snapshot.pendingCount == 1)
        let change = try #require(try await pending(store))
        #expect(change.kind == .create)
        #expect(!change.attempted)
        #expect(change.revision == 1)
    }

    @Test func editQueuesAnUpdateOfTheChangedFields() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Doom")
        try await pulled(game, into: store, access: access)

        let snapshot = try #require(try await edit(store, game, access: access) { $0.rating = 9; $0.notes = "Big box" })
        #expect(snapshot.games[game.id]?.rating == 9)
        #expect(snapshot.games[game.id]?.updatedAt == now)
        let change = try #require(try await pending(store))
        #expect(change.kind == .update)
        #expect(change.fields == [.rating, .notes])
    }

    @Test func editThatChangesNothingQueuesNothing() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Doom")
        try await pulled(game, into: store, access: access)
        #expect(try await edit(store, game, access: access) { _ in } == nil)
        #expect(try await store.pendingChanges().isEmpty)
    }

    @Test func editsAreMergedIntoOnePendingUpdate() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Doom")
        try await pulled(game, into: store, access: access)
        _ = try await edit(store, game, access: access) { $0.rating = 9 }
        let edited = try #require(await store.snapshot.games[game.id])
        _ = try await edit(store, edited, access: access) { $0.notes = "Big box" }

        let changes = try await store.pendingChanges()
        #expect(changes.count == 1)
        #expect(changes.first?.fields == [.rating, .notes])
        #expect(changes.first?.revision == 2)
    }

    @Test func editOfAGameNotSentYetKeepsTheCreate() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Doom")
        _ = try await store.insert(game, at: now, access: access)
        _ = try await edit(store, game, access: access) { $0.rating = 9 }
        let change = try #require(try await pending(store))
        #expect(change.kind == .create)
        #expect(change.fields.isEmpty)
        #expect(change.revision == 2)
    }

    @Test func editAfterTheCreateWasAttemptedIsRemembered() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Doom")
        _ = try await store.insert(game, at: now, access: access)
        _ = try await store.beginPush(of: game.id, access: access)
        _ = try await edit(store, game, access: access) { $0.rating = 9 }
        let change = try #require(try await pending(store))
        #expect(change.kind == .create)
        #expect(change.attempted)
        #expect(change.fields == [.rating])
    }

    @Test func editBasedOnAnOlderCopyChangesOnlyTheEditedFields() async throws {
        let (store, access) = try await makeStore()
        let original = Fixtures.game("Doom") { $0.notes = "Old notes" }
        try await pulled(original, into: store, access: access)
        // Another device changed the notes while the form was open.
        var remote = original
        remote.notes = "Notes from another device"
        try await pulled(remote, into: store, access: access)

        var request = SaveGameRequest(game: original)
        request.rating = 8
        _ = try await store.update(id: original.id, with: request, basedOn: SaveGameRequest(game: original), at: now, access: access)

        let stored = try #require(await store.snapshot.games[original.id])
        #expect(stored.notes == "Notes from another device")
        #expect(stored.rating == 8)
        #expect(try await pending(store)?.fields == [.rating])
    }

    @Test func deleteQueuesADeleteAndRemovesTheGame() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Doom")
        try await pulled(game, into: store, access: access)
        let snapshot = try #require(try await store.delete(id: game.id, at: now, access: access))
        #expect(snapshot.games[game.id] == nil)
        #expect(try await pending(store)?.kind == .delete)
    }

    @Test func deleteReplacesAPendingUpdate() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Doom")
        try await pulled(game, into: store, access: access)
        _ = try await edit(store, game, access: access) { $0.rating = 9 }
        _ = try await store.delete(id: game.id, at: now, access: access)
        let change = try #require(try await pending(store))
        #expect(change.kind == .delete)
        #expect(change.fields.isEmpty)
        #expect(change.revision == 2)
    }

    @Test func deleteOfAGameTheServerNeverSawLeavesNothing() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Doom")
        _ = try await store.insert(game, at: now, access: access)
        let snapshot = try await store.delete(id: game.id, at: now, access: access)
        #expect(snapshot?.pendingCount == 0)
        #expect(try await store.pendingChanges().isEmpty)
    }

    @Test func deleteAfterTheCreateWasAttemptedQueuesADelete() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Doom")
        _ = try await store.insert(game, at: now, access: access)
        _ = try await store.beginPush(of: game.id, access: access)
        _ = try await store.delete(id: game.id, at: now, access: access)
        #expect(try await pending(store)?.kind == .delete)
    }

    // MARK: Pull (merge table)

    @Test func pulledPagesFollowTheMergeTable() async throws {
        let (store, access) = try await makeStore()
        let plain = Fixtures.game("Plain")
        let updated = Fixtures.game("Updated") { $0.notes = "Server notes" }
        let created = Fixtures.game("Created locally")
        let deleted = Fixtures.game("Deleted locally")
        try await pulled(plain, into: store, access: access)
        try await pulled(updated, into: store, access: access)
        try await pulled(deleted, into: store, access: access)
        _ = try await edit(store, updated, access: access) { $0.title = "Local title" }
        _ = try await store.insert(created, at: now, access: access)
        _ = try await store.delete(id: deleted.id, at: now, access: access)

        func fromServer(_ game: Game) -> Game {
            var game = game
            game.title += " (server)"
            game.notes = "Changed on the server"
            return game
        }
        let page = GameChanges(
            games: [fromServer(plain), fromServer(updated), fromServer(created), fromServer(deleted)],
            deletedIds: [], cursor: "9", hasMore: true
        )
        let snapshot = try await store.applyChanges(page, at: now.addingTimeInterval(60), access: access)

        #expect(snapshot.games[plain.id] == fromServer(plain))
        #expect(snapshot.games[updated.id]?.title == "Local title")
        #expect(snapshot.games[updated.id]?.notes == "Changed on the server")
        #expect(snapshot.games[created.id] == created)
        #expect(snapshot.games[deleted.id] == nil)
        #expect(snapshot.lastSyncedAt == now, "the feed has more pages, so the last sync time stays")
        #expect(try await store.cursor(access: access) == "9")
        #expect(try await store.pendingChanges().map(\.kind) == [.update, .create, .delete])
    }

    @Test func pulledDeletionsWinOverEveryPendingChange() async throws {
        let (store, access) = try await makeStore()
        let plain = Fixtures.game("Plain")
        let updated = Fixtures.game("Updated")
        let created = Fixtures.game("Created")
        let deleted = Fixtures.game("Deleted")
        for game in [plain, updated, deleted] {
            try await pulled(game, into: store, access: access)
        }
        _ = try await edit(store, updated, access: access) { $0.rating = 3 }
        _ = try await store.insert(created, at: now, access: access)
        _ = try await store.delete(id: deleted.id, at: now, access: access)

        let page = GameChanges(games: [], deletedIds: [plain.id, updated.id, created.id, deleted.id], cursor: "10", hasMore: false)
        let snapshot = try await store.applyChanges(page, at: now, access: access)

        #expect(snapshot.games.isEmpty)
        #expect(snapshot.pendingCount == 0)
        #expect(snapshot.lastSyncedAt == now)
    }

    @Test func resetKeepsOnlyGamesWithPendingChanges() async throws {
        let (store, access) = try await makeStore()
        let plain = Fixtures.game("Plain")
        let updated = Fixtures.game("Updated")
        try await pulled(plain, into: store, access: access)
        try await pulled(updated, into: store, access: access)
        _ = try await edit(store, updated, access: access) { $0.rating = 3 }

        let snapshot = try await store.resetForFullPull(access: access)
        #expect(Set(snapshot.games.keys) == [updated.id])
        #expect(try await store.cursor(access: access) == nil)
        #expect(snapshot.pendingCount == 1)
    }
}
