import Foundation
import Testing
@testable import GameShelf

@Suite("Local store (SQLite)")
struct GameStoreTests {
    private let owner = "user-1"
    private let now = Date(timeIntervalSince1970: 1_750_000_000)
    private static let rejection = APIError.server(statusCode: 400, code: .validationFailed, details: [])

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

    // MARK: Push

    @Test func aCreateEditedWhileInFlightBecomesAnUpdateOfTheEditedFieldsOnly() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Doom")
        _ = try await store.insert(game, at: now, access: access)
        let push = try #require(try await store.beginPush(of: game.id, access: access))
        _ = try await edit(store, game, access: access) { $0.rating = 9 }

        var server = game
        server.notes = "Changed on another device since"
        let completion = try await store.completePush(push, with: .created(server), access: access)

        let change = try #require(try await pending(store))
        #expect(change.kind == .update)
        #expect(change.fields == [.rating])
        #expect(completion.snapshot.games[game.id]?.rating == 9)
        #expect(completion.snapshot.games[game.id]?.notes == "Changed on another device since")
    }

    @Test func aRejectedCreateKeepsTheGameAndItsCreate() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Doom")
        _ = try await store.insert(game, at: now, access: access)
        let push = try #require(try await store.beginPush(of: game.id, access: access))
        let completion = try await store.completePush(push, with: .rejected(Self.rejection, restored: nil), access: access)
        #expect(completion.snapshot.games[game.id] == game)
        #expect(!completion.snapshot.hasUndoneRejectedChanges)
        #expect(try await store.pendingChanges().map(\.kind) == [.create])
    }

    @Test func anUpdateOfAGameTheServerDoesntKnowBecomesACreateNotAttempted() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Doom")
        try await pulled(game, into: store, access: access)
        _ = try await edit(store, game, access: access) { $0.rating = 9 }
        let push = try #require(try await store.beginPush(of: game.id, access: access))

        let completion = try await store.completePush(push, with: .gameUnknown, access: access)
        #expect(completion.pushAgain)
        let change = try #require(try await pending(store))
        #expect(change.kind == .create)
        #expect(change.fields.isEmpty)
        #expect(!change.attempted)
        // Deleted before it was sent again: the server never needs to hear of it.
        _ = try await store.delete(id: game.id, at: now, access: access)
        #expect(try await store.pendingChanges().isEmpty)
    }

    @Test func aRejectedUpdateIsUndoneOnlyWhileItIsUnchanged() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Doom")
        try await pulled(game, into: store, access: access)
        _ = try await edit(store, game, access: access) { $0.rating = 11 }
        let push = try #require(try await store.beginPush(of: game.id, access: access))
        // Edited again while the rejected request was on its way.
        let edited = try #require(await store.snapshot.games[game.id])
        _ = try await edit(store, edited, access: access) { $0.notes = "Fixed it" }

        let completion = try await store.completePush(push, with: .rejected(Self.rejection, restored: game), access: access)

        #expect(completion.failure == Self.rejection)
        #expect(!completion.snapshot.hasUndoneRejectedChanges)
        #expect(completion.snapshot.games[game.id]?.notes == "Fixed it")
        let change = try #require(try await pending(store))
        #expect(change.kind == .update)
        #expect(change.fields == [.rating, .notes])
        #expect(change.revision == 2)

        // Rejected again as it is: undone then.
        let again = try #require(try await store.beginPush(of: game.id, access: access))
        let undone = try await store.completePush(again, with: .rejected(Self.rejection, restored: game), access: access)
        #expect(undone.failure == nil)
        #expect(undone.snapshot.hasUndoneRejectedChanges)
        #expect(undone.snapshot.games[game.id] == game)
        #expect(try await store.pendingChanges().isEmpty)
    }

    @Test func aLocalDeleteStillWinsOverARejectedUpdate() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Doom")
        try await pulled(game, into: store, access: access)
        _ = try await edit(store, game, access: access) { $0.rating = 11 }
        let push = try #require(try await store.beginPush(of: game.id, access: access))
        _ = try await store.delete(id: game.id, at: now, access: access)

        let completion = try await store.completePush(push, with: .rejected(Self.rejection, restored: game), access: access)

        #expect(completion.snapshot.games[game.id] == nil)
        #expect(!completion.snapshot.hasUndoneRejectedChanges)
        #expect(try await store.pendingChanges().map(\.kind) == [.delete])
    }

    @Test func aRejectedUpdateOfAGameTheServerDoesntKnowIsPushedAsANewGame() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Doom")
        try await pulled(game, into: store, access: access)
        _ = try await edit(store, game, access: access) { $0.rating = 9 }
        let push = try #require(try await store.beginPush(of: game.id, access: access))

        let completion = try await store.completePush(push, with: .rejectedUnknownGame, access: access)

        #expect(completion.pushAgain)
        #expect(completion.failure == nil)
        #expect(!completion.snapshot.hasUndoneRejectedChanges)
        #expect(completion.snapshot.games[game.id]?.rating == 9, "the local game is the only copy")
        let change = try #require(try await pending(store))
        #expect(change.kind == .create)
        #expect(change.fields.isEmpty)
        #expect(!change.attempted)
    }

    @Test func aRejectedDeleteOfAGameTheServerDoesntKnowIsDropped() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Doom")
        try await pulled(game, into: store, access: access)
        _ = try await store.delete(id: game.id, at: now, access: access)
        let push = try #require(try await store.beginPush(of: game.id, access: access))

        let completion = try await store.completePush(push, with: .rejectedUnknownGame, access: access)

        #expect(!completion.pushAgain)
        #expect(!completion.snapshot.hasUndoneRejectedChanges)
        #expect(completion.snapshot.games[game.id] == nil)
        #expect(try await store.pendingChanges().isEmpty)
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
        let snapshot = try await store.applyChanges(page, at: now.addingTimeInterval(60), access: access).snapshot

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
        let snapshot = try await store.applyChanges(page, at: now, access: access).snapshot

        #expect(snapshot.games.isEmpty)
        #expect(snapshot.pendingCount == 0)
        #expect(snapshot.lastSyncedAt == now)
    }

    @Test func aResetMarksGamesUnconfirmedAndTheEndOfTheFullPullCreatesTheOnesItDidntBring() async throws {
        let (store, access) = try await makeStore()
        let confirmed = Fixtures.game("Confirmed")
        let deleted = Fixtures.game("Deleted on the server")
        let missing = Fixtures.game("Missing on the server")
        let edited = Fixtures.game("Edited locally")
        let removedLocally = Fixtures.game("Deleted locally")
        for game in [confirmed, deleted, missing, edited, removedLocally] {
            try await pulled(game, into: store, access: access)
        }
        _ = try await edit(store, edited, access: access) { $0.rating = 3 }

        try await store.reset(access: access)
        // Nothing is removed; only the cursor is forgotten.
        #expect(await store.snapshot.games.count == 5)
        #expect(try await store.cursor(access: access) == nil)
        // A local delete of an unconfirmed game queues its DELETE as usual.
        _ = try await store.delete(id: removedLocally.id, at: now, access: access)

        let first = try await store.applyChanges(
            GameChanges(games: [confirmed], deletedIds: [deleted.id], cursor: "1", hasMore: true), at: now, access: access
        )
        #expect(!first.queuedUnconfirmedGames)
        #expect(first.snapshot.games[deleted.id] == nil)
        #expect(first.snapshot.games[missing.id] != nil, "kept until the full pull has ended")

        let last = try await store.applyChanges(GameChanges(games: [], deletedIds: [], cursor: "2", hasMore: false), at: now, access: access)
        #expect(last.queuedUnconfirmedGames)
        #expect(Set(last.snapshot.games.keys) == [confirmed.id, missing.id, edited.id])
        let changes = try await store.pendingChanges()
        #expect(changes.map(\.gameID) == [edited.id, removedLocally.id, missing.id])
        #expect(changes.map(\.kind) == [.update, .delete, .create])
        #expect(changes.last?.attempted == false)

        // The marks are gone: the next full pull queues nothing.
        let again = try await store.applyChanges(GameChanges(games: [], deletedIds: [], cursor: "3", hasMore: false), at: now, access: access)
        #expect(!again.queuedUnconfirmedGames)
    }

    @Test func unconfirmedMarksSurviveARestart() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        let missing = Fixtures.game("Missing on the server")
        do {
            let store = try GameStore(url: url)
            let (access, _) = try await store.activate(ownerID: owner)
            try await pulled(missing, into: store, access: access)
            try await store.reset(access: access)
        }

        // The reset continues after a restart.
        let store = try GameStore(url: url)
        let (access, _) = try await store.activate(ownerID: owner)
        let completion = try await store.applyChanges(GameChanges(games: [], deletedIds: [], cursor: "1", hasMore: false), at: now, access: access)
        #expect(completion.queuedUnconfirmedGames)
        #expect(try await store.pendingChanges().map(\.gameID) == [missing.id])
    }

    @Test func aPendingUpdateOfAStoredGameThisVersionCantReadKeepsItAsItIs() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        let game = Fixtures.game("Doom")
        do {
            let store = try GameStore(url: url)
            let (access, _) = try await store.activate(ownerID: owner)
            try await pulled(game, into: store, access: access)
            _ = try await edit(store, game, access: access) { $0.notes = "Unpushed" }
        }
        // E.g. written by a newer app version that this one can't read.
        let unreadable = Data(#"{"id":"?","notes":"Unpushed"}"#.utf8)
        try SQLiteConnection(url: url).run("UPDATE games SET data = ?", [.blob(unreadable)])

        let store = try GameStore(url: url)
        let (access, _) = try await store.activate(ownerID: owner)
        var server = game
        server.title = "Doom (server)"
        let snapshot = try await store.applyChanges(
            GameChanges(games: [server], deletedIds: [], cursor: "2", hasMore: false), at: now, access: access
        ).snapshot

        #expect(snapshot.games[game.id] == nil)
        #expect(try SQLiteConnection(url: url).query("SELECT data FROM games") { $0.data(0) } == [unreadable])
        #expect(try await store.pendingChanges().map(\.fields) == [[.notes]])
        #expect(try await store.cursor(access: access) == "2")
    }

    @Test func unknownEnumValuesSurviveTheStoredCopy() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        let game = try Fixtures.gameWithUnknownValues()
        do {
            let store = try GameStore(url: url)
            let (access, _) = try await store.activate(ownerID: owner)
            try await pulled(game, into: store, access: access)
        }

        let (_, snapshot) = try await GameStore(url: url).activate(ownerID: owner)
        let stored = try #require(snapshot.games[game.id])
        #expect(stored == game)
        #expect(stored.unknownWireValues == Fixtures.unknownWireValues)
    }

    // MARK: Pulls from the beginning (docs/offline-sync.md, "Reset")

    @Test func aPullFromTheBeginningAfterAnAppUpdateReconcilesLikeAReset() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        let confirmed = Fixtures.game("Brought by the full pull")
        let lost = Fixtures.game("Lost in a restore")
        let edited = Fixtures.game("Edited locally")
        let created = Fixtures.game("Created offline")
        do {
            let store = try GameStore(url: url, appVersion: "1.0 (1)")
            let (access, _) = try await store.activate(ownerID: owner)
            for game in [confirmed, lost, edited] {
                try await pulled(game, into: store, access: access)
            }
            _ = try await edit(store, edited, access: access) { $0.rating = 3 }
            _ = try await store.insert(created, at: now, access: access)
        }

        do {
            let store = try GameStore(url: url, appVersion: "1.1 (2)")
            let (access, _) = try await store.activate(ownerID: owner)
            #expect(try await store.cursor(access: access) == nil)
            let first = try await store.applyChanges(
                GameChanges(games: [confirmed], deletedIds: [], cursor: "5", hasMore: true), at: now, access: access
            )
            #expect(!first.queuedUnconfirmedGames)
        }

        // Restarted half-way: the full pull continues where it was, with its marks.
        let store = try GameStore(url: url, appVersion: "1.1 (2)")
        let (access, _) = try await store.activate(ownerID: owner)
        #expect(try await store.cursor(access: access) == "5")
        let last = try await store.applyChanges(GameChanges(games: [], deletedIds: [], cursor: "6", hasMore: false), at: now, access: access)

        #expect(last.queuedUnconfirmedGames)
        #expect(Set(last.snapshot.games.keys) == [confirmed.id, lost.id, edited.id, created.id])
        let changes = try await store.pendingChanges()
        #expect(changes.map(\.gameID) == [edited.id, created.id, lost.id])
        #expect(changes.map(\.kind) == [.update, .create, .create])
        #expect(changes.last?.attempted == false)
    }

    @Test func droppingUnreadableGamesReconcilesTheOthers() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        let unreadable = Fixtures.game("Unreadable")
        let lost = Fixtures.game("Lost in a restore")
        do {
            let store = try GameStore(url: url)
            let (access, _) = try await store.activate(ownerID: owner)
            try await pulled(unreadable, into: store, access: access)
            try await pulled(lost, into: store, access: access)
        }
        try SQLiteConnection(url: url).run("UPDATE games SET data = ? WHERE id = ?", [.blob(Data("{}".utf8)), .text(unreadable.id)])

        let store = try GameStore(url: url)
        let (access, snapshot) = try await store.activate(ownerID: owner)
        #expect(Set(snapshot.games.keys) == [lost.id])
        #expect(try await store.cursor(access: access) == nil)
        // The full pull brings the unreadable game again, but not the lost one.
        let last = try await store.applyChanges(
            GameChanges(games: [unreadable], deletedIds: [], cursor: "9", hasMore: false), at: now, access: access
        )
        #expect(last.queuedUnconfirmedGames)
        #expect(try await store.pendingChanges().map(\.gameID) == [lost.id])
        #expect(last.snapshot.games[unreadable.id] == unreadable)
    }

    @Test func aGameOfAFullPullThatCantBeDecodedIsFoundOnTheServerButNotStored() async throws {
        let (store, access) = try await makeStore()
        let undecodable = Fixtures.game("Changed by a newer app") { $0.notes = "Local copy" }
        let lost = Fixtures.game("Lost in a restore")
        try await pulled(undecodable, into: store, access: access)
        try await pulled(lost, into: store, access: access)
        try await store.reset(access: access)

        let last = try await store.applyChanges(
            GameChanges(games: [], deletedIds: [], cursor: "2", hasMore: false, undecodableGameIds: [undecodable.id]),
            at: now, access: access
        )

        // Not pushed back as a new game: only the one the full pull didn't bring at all.
        #expect(try await store.pendingChanges().map(\.gameID) == [lost.id])
        #expect(last.snapshot.games[undecodable.id] == undecodable, "the local copy stays as it is")
        #expect(try await store.cursor(access: access) == "2")
    }

    @Test func aPullFromTheBeginningWithoutStoredGamesQueuesNothing() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        do {
            let store = try GameStore(url: url, appVersion: "1.0 (1)")
            let (access, _) = try await store.activate(ownerID: owner)
            _ = try await store.applyChanges(GameChanges(games: [], deletedIds: [], cursor: "3", hasMore: false), at: now, access: access)
        }

        let store = try GameStore(url: url, appVersion: "1.1 (2)")
        let (access, _) = try await store.activate(ownerID: owner)
        #expect(try await store.cursor(access: access) == nil)
        let last = try await store.applyChanges(GameChanges(games: [], deletedIds: [], cursor: "3", hasMore: false), at: now, access: access)
        #expect(!last.queuedUnconfirmedGames)
        #expect(try await store.pendingChanges().isEmpty)
    }

    // MARK: Database file

    /// A database file in a fresh temporary directory, removed by `cleanUp`.
    private func temporaryDatabase() throws -> (url: URL, cleanUp: () -> Void) {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return (directory.appending(path: "games.sqlite"), { try? FileManager.default.removeItem(at: directory) })
    }

    @Test func anAppUpdateForgetsTheCursorButKeepsGamesAndPendingChanges() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        let pulledGame = Fixtures.game("Pulled")
        let unsynced = Fixtures.game("Unsynced")
        do {
            let store = try GameStore(url: url, appVersion: "1.0 (1)")
            let (access, _) = try await store.activate(ownerID: owner)
            try await pulled(pulledGame, into: store, access: access)
            _ = try await store.insert(unsynced, at: now, access: access)
        }
        do {
            // The same version continues from its cursor.
            let store = try GameStore(url: url, appVersion: "1.0 (1)")
            let (access, _) = try await store.activate(ownerID: owner)
            #expect(try await store.cursor(access: access) == "1")
        }

        let updated = try GameStore(url: url, appVersion: "1.1 (2)")
        let (access, snapshot) = try await updated.activate(ownerID: owner)
        #expect(try await updated.cursor(access: access) == nil)
        #expect(Set(snapshot.games.keys) == [pulledGame.id, unsynced.id])
        #expect(try await updated.pendingChanges().map(\.gameID) == [unsynced.id])
        #expect(snapshot.lastSyncedAt == now)

        // A cursor stored by the new version is continued.
        _ = try await updated.applyChanges(GameChanges(games: [], deletedIds: [], cursor: "7", hasMore: false), at: now, access: access)
        let reopened = try GameStore(url: url, appVersion: "1.1 (2)")
        let (reopenedAccess, _) = try await reopened.activate(ownerID: owner)
        #expect(try await reopened.cursor(access: reopenedAccess) == "7")
    }

    @Test func migratesADatabaseOfSchemaVersion1() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        let game = Fixtures.game("Stored by an older version")
        do {
            let db = try SQLiteConnection(url: url)
            try db.execute("""
            PRAGMA journal_mode = WAL;
            CREATE TABLE games (id TEXT PRIMARY KEY NOT NULL, data BLOB NOT NULL);
            CREATE TABLE pending_changes (
                game_id TEXT PRIMARY KEY NOT NULL,
                kind TEXT NOT NULL CHECK (kind IN ('CREATE', 'UPDATE', 'DELETE')),
                fields TEXT NOT NULL DEFAULT '[]',
                revision INTEGER NOT NULL,
                attempted INTEGER NOT NULL DEFAULT 0,
                queued_at REAL NOT NULL
            );
            CREATE TABLE sync_state (id INTEGER PRIMARY KEY CHECK (id = 1), owner_user_id TEXT, cursor TEXT, last_synced_at REAL);
            INSERT INTO sync_state (id) VALUES (1);
            PRAGMA user_version = 1;
            """)
            try db.run(
                "UPDATE sync_state SET owner_user_id = ?, cursor = '99', last_synced_at = ? WHERE id = 1",
                [.text(owner), .real(now.timeIntervalSince1970)]
            )
            try db.run("INSERT INTO games (id, data) VALUES (?, ?)", [.text(game.id), .blob(JSONEncoder.api().encode(game))])
            try db.run(
                "INSERT INTO pending_changes (game_id, kind, fields, revision, queued_at) VALUES (?, 'UPDATE', '[\"notes\"]', 1, 0)",
                [.text(game.id)]
            )
        }

        let store = try GameStore(url: url)
        let (access, snapshot) = try await store.activate(ownerID: owner)
        #expect(snapshot.games[game.id] == game)
        #expect(snapshot.lastSyncedAt == now)
        #expect(!snapshot.hasUndoneRejectedChanges)
        #expect(try await store.pendingChanges().map(\.fields) == [[.notes]])
        // That version stored no app version with its cursor: the whole collection is pulled again.
        #expect(try await store.cursor(access: access) == nil)
        #expect(try SQLiteConnection(url: url).query("PRAGMA user_version") { $0.int(0) } == [3])
    }

    @Test func unreadableGamesAreNeverDroppedWithTheirPendingChanges() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        let synced = Fixtures.game("Synced")
        let unsynced = Fixtures.game("Created offline")
        do {
            let store = try GameStore(url: url)
            let (access, _) = try await store.activate(ownerID: owner)
            try await pulled(synced, into: store, access: access)
            _ = try await store.insert(unsynced, at: now, access: access)
        }
        // E.g. written by a newer app version that this one can't read.
        try SQLiteConnection(url: url).run("UPDATE games SET data = ?", [.blob(Data(#"{"id":"?"}"#.utf8))])

        let store = try GameStore(url: url)
        let (access, snapshot) = try await store.activate(ownerID: owner)
        #expect(snapshot.games.isEmpty)
        #expect(snapshot.pendingCount == 1)
        #expect(try await store.pendingChanges().map(\.gameID) == [unsynced.id])
        // The synced game comes back with a full pull; the unsynced one stays stored as it is.
        #expect(try await store.cursor(access: access) == nil)
        #expect(try SQLiteConnection(url: url).query("SELECT id FROM games") { $0.text(0) } == [unsynced.id])
    }

    @Test func aDamagedDatabaseFileIsReplaced() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        try Data(repeating: 0x41, count: 8192).write(to: url)

        let store = GameStore(openingOnFirstUse: url)
        let (access, snapshot) = try await store.activate(ownerID: owner)
        #expect(snapshot.games.isEmpty)
        _ = try await store.insert(Fixtures.game("Doom"), at: now, access: access)
        #expect(try await store.pendingChanges().count == 1)
    }

    @Test func aDatabaseThatCantBeOpenedIsKeptForTheNextAttempt() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        // Something at the database's path that can't be opened now – but isn't a damaged database.
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        let marker = url.appending(path: "marker")
        try Data("keep".utf8).write(to: marker)

        let store = GameStore(openingOnFirstUse: url)
        await #expect(throws: SQLiteError.self) {
            _ = try await store.activate(ownerID: owner)
        }
        #expect(FileManager.default.fileExists(atPath: marker.path(percentEncoded: false)))

        try FileManager.default.removeItem(at: url)
        let (_, snapshot) = try await store.activate(ownerID: owner)
        #expect(snapshot.games.isEmpty)
    }

    @Test func erasingDeletesTheDatabaseFiles() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        let store = try GameStore(url: url)
        let (access, _) = try await store.activate(ownerID: owner)
        _ = try await store.insert(Fixtures.game("Doom"), at: now, access: access)

        try await store.erase()
        for suffix in ["", "-wal", "-shm", "-wipe"] {
            #expect(!FileManager.default.fileExists(atPath: url.path(percentEncoded: false) + suffix))
        }
        let (_, snapshot) = try await store.activate(ownerID: owner)
        #expect(snapshot.games.isEmpty)
        #expect(try await store.pendingChanges().isEmpty)
    }

    @Test func aWipeThatCouldntFinishIsFinishedBeforeTheDataIsReadAgain() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        let marker = url.path(percentEncoded: false) + "-wipe"
        func storeAGame() async throws {
            let store = try GameStore(url: url)
            let (access, _) = try await store.activate(ownerID: owner)
            _ = try await store.insert(Fixtures.game("Doom"), at: now, access: access)
        }

        // A sign-out whose files couldn't be deleted left the marker: the next activation wipes them first.
        try await storeAGame()
        FileManager.default.createFile(atPath: marker, contents: nil)
        let store = GameStore(openingOnFirstUse: url)
        let (_, snapshot) = try await store.activate(ownerID: owner)
        #expect(snapshot.games.isEmpty)
        #expect(try await store.pendingChanges().isEmpty)
        #expect(!FileManager.default.fileExists(atPath: marker))

        // So does the next start, before anything is shown.
        try await storeAGame()
        FileManager.default.createFile(atPath: marker, contents: nil)
        try GameStore.finishPendingWipe(at: url)
        #expect(!FileManager.default.fileExists(atPath: url.path(percentEncoded: false)))
        #expect(!FileManager.default.fileExists(atPath: marker))
    }

    @Test func aWipeStaysMarkedUntilTheSessionIsClearedToo() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        let store = try GameStore(url: url)
        let (access, _) = try await store.activate(ownerID: owner)
        _ = try await store.insert(Fixtures.game("Doom"), at: now, access: access)

        try await store.markWipePending()
        #expect(GameStore.isWipePending(at: url))
        // The stored session couldn't be cleared: the data goes, the mark stays for the next start.
        try await store.erase(removingWipeMark: false)
        #expect(!FileManager.default.fileExists(atPath: url.path(percentEncoded: false)))
        #expect(GameStore.isWipePending(at: url))

        try GameStore.finishPendingWipe(at: url)
        #expect(!GameStore.isWipePending(at: url))
    }

    @Test func aWipeCanBeMarkedBeforeTheDatabaseWasOpened() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        // Its directory doesn't exist yet.
        let unopened = url.deletingLastPathComponent().appending(path: "OfflineData/games.sqlite")
        try await GameStore(openingOnFirstUse: unopened).markWipePending()
        #expect(GameStore.isWipePending(at: unopened))
    }

    @Test func undoneRejectedChangesAreRememberedUntilSeen() async throws {
        let (url, cleanUp) = try temporaryDatabase()
        defer { cleanUp() }
        do {
            let store = try GameStore(url: url)
            let (access, _) = try await store.activate(ownerID: owner)
            let game = Fixtures.game("Rejected")
            try await pulled(game, into: store, access: access)
            _ = try await edit(store, game, access: access) { $0.rating = 11 }
            let push = try #require(try await store.beginPush(of: game.id, access: access))
            let completion = try await store.completePush(push, with: .rejected(Self.rejection, restored: game), access: access)
            #expect(completion.snapshot.hasUndoneRejectedChanges)
            #expect(completion.snapshot.games[game.id] == game)
        }
        do {
            // Shown also when the screen opens only after a restart.
            let store = try GameStore(url: url)
            let (access, snapshot) = try await store.activate(ownerID: owner)
            #expect(snapshot.hasUndoneRejectedChanges)
            #expect(try await !store.acknowledgeUndoneRejectedChanges(access: access).hasUndoneRejectedChanges)
        }
        let store = try GameStore(url: url)
        #expect(try await !store.activate(ownerID: owner).1.hasUndoneRejectedChanges)
    }

    @Test func anotherUserDoesNotSeeTheUndoneRejectedChanges() async throws {
        let (store, access) = try await makeStore()
        let game = Fixtures.game("Rejected")
        try await pulled(game, into: store, access: access)
        _ = try await store.delete(id: game.id, at: now, access: access)
        let push = try #require(try await store.beginPush(of: game.id, access: access))
        #expect(try await store.completePush(push, with: .rejected(Self.rejection, restored: nil), access: access).snapshot.hasUndoneRejectedChanges)

        #expect(try await !store.activate(ownerID: "user-2").1.hasUndoneRejectedChanges)
    }
}
