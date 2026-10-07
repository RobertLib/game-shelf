import Foundation
import Observation

/// The signed-in user's collection as the screens see it: an in-memory copy of the local
/// database, and the local changes (create, edit, favorite, delete).
///
/// Changes are written to the device first and never fail because of the network; each one
/// then asks the sync engine to push it (``onLocalChange``). The sync engine publishes what it
/// writes through ``apply(_:)``, so every screen follows the stored data.
@Observable
@MainActor
final class GameRepository {
    /// Every game of the signed-in user, by id.
    private(set) var gamesByID: [Game.ID: Game] = [:]
    /// Increases whenever ``gamesByID`` changes, so derived data (list results, facets) knows when to recompute.
    private(set) var version = 0
    /// Whether the signed-in user's stored data has been loaded.
    private(set) var isLoaded = false

    @ObservationIgnored let status: SyncStatus
    @ObservationIgnored let store: GameStore
    /// Called after every local change; the sync engine pushes it.
    @ObservationIgnored var onLocalChange: (@MainActor () -> Void)?
    @ObservationIgnored private(set) var access: StoreAccess?
    /// Version of the last applied snapshot; snapshots arrive from concurrent writes.
    @ObservationIgnored private var snapshotVersion = 0
    @ObservationIgnored private let now: @Sendable () -> Date

    init(store: GameStore, status: SyncStatus, now: @escaping @Sendable () -> Date = { .now }) {
        self.store = store
        self.status = status
        self.now = now
    }

    var games: Dictionary<Game.ID, Game>.Values { gamesByID.values }

    var isEmpty: Bool { gamesByID.isEmpty }

    func game(id: Game.ID) -> Game? {
        gamesByID[id]
    }

    // MARK: Local changes

    /// Adds a game with an id generated on the device.
    @discardableResult
    func create(_ request: SaveGameRequest) async throws -> Game {
        let access = try currentAccess()
        let timestamp = now()
        let game = Game(id: UUID().uuidString.lowercased(), values: request, createdAt: timestamp)
        apply(try await store.insert(game, at: timestamp, access: access))
        onLocalChange?()
        return game
    }

    /// Saves an edit. Only the fields the user changed – compared with `original`, the game the
    /// edit started from (the stored game by default) – are changed and pushed, so changes made
    /// meanwhile on another device to other fields survive. An edit that changes nothing is ignored.
    func update(id: Game.ID, with request: SaveGameRequest, basedOn original: Game? = nil) async throws {
        let access = try currentAccess()
        let base = original.map(SaveGameRequest.init(game:))
        guard let snapshot = try await store.update(id: id, with: request, basedOn: base, at: now(), access: access) else {
            return
        }
        apply(snapshot)
        onLocalChange?()
    }

    func setFavorite(_ favorite: Bool, id: Game.ID) async throws {
        let access = try currentAccess()
        guard let snapshot = try await store.setFavorite(favorite, id: id, at: now(), access: access) else { return }
        apply(snapshot)
        onLocalChange?()
    }

    /// Removes the game at once; the deletion is pushed later.
    func delete(id: Game.ID) async throws {
        let access = try currentAccess()
        guard let snapshot = try await store.delete(id: id, at: now(), access: access) else { return }
        apply(snapshot)
        onLocalChange?()
    }

    private func currentAccess() throws -> StoreAccess {
        guard let access else { throw LocalStoreError.accessRevoked }
        return access
    }

    // MARK: Stored data

    /// Shows the data that was just activated for the signed-in user.
    func activate(_ access: StoreAccess, with snapshot: StoreSnapshot) {
        self.access = access
        apply(snapshot)
        isLoaded = true
    }

    /// Stops writing (sign-out, expired session). What is on screen stays until the next activation
    /// replaces it, so the main flow doesn't flash empty while it goes away.
    func deactivate() {
        access = nil
    }

    /// Publishes the result of a write. Older snapshots than the one shown are ignored.
    func apply(_ snapshot: StoreSnapshot) {
        guard access != nil, snapshot.version > snapshotVersion else { return }
        snapshotVersion = snapshot.version
        if gamesByID != snapshot.games {
            gamesByID = snapshot.games
            version += 1
        }
        if status.pendingCount != snapshot.pendingCount {
            status.pendingCount = snapshot.pendingCount
        }
        if status.lastSyncedAt != snapshot.lastSyncedAt {
            status.lastSyncedAt = snapshot.lastSyncedAt
        }
    }
}
