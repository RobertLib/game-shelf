import Foundation

/// Permission to write the local data on behalf of its current owner.
///
/// Handed out when the data is activated for a user; every write checks it inside its
/// transaction, so nothing is written for a user who has signed out or been replaced –
/// not even by a sync run that was still in flight.
struct StoreAccess: Hashable, Sendable {
    let ownerID: User.ID
    let generation: Int
}

/// The stored collection after a write, published to the UI.
struct StoreSnapshot: Sendable {
    /// Increases with every write.
    var version: Int
    var games: [Game.ID: Game]
    /// Number of changes not pushed yet.
    var pendingCount: Int
    /// End of the last pull that reached the end of the change feed.
    var lastSyncedAt: Date?
    /// Changes the server rejected were undone, and the user hasn't been told yet.
    var hasUndoneRejectedChanges = false
}

/// A local change waiting to be pushed; at most one per game.
struct PendingChange: Hashable, Sendable {
    enum Kind: String, Sendable {
        case create = "CREATE"
        case update = "UPDATE"
        case delete = "DELETE"
    }

    var gameID: Game.ID
    var kind: Kind
    /// `UPDATE`: the changed fields. `CREATE`: fields edited after the first attempt to send it.
    var fields: Set<GameField>
    /// Increases with every local change of the game.
    var revision: Int
    /// Set when the change is sent for the first time.
    var attempted: Bool
    /// When the change was queued; changes are pushed oldest first.
    var queuedAt: Date
}

/// A pending change being pushed, with the local game it refers to (`nil` for a `DELETE`).
struct PendingPush: Sendable {
    var change: PendingChange
    var game: Game?
}

/// Outcome of pushing one pending change.
enum PushResult: Sendable {
    /// `POST` answered `201`: the server has the content that was sent.
    case created(Game)
    /// `POST` answered `200`: an earlier attempt had created the game; nothing was changed.
    case alreadyExisted(Game)
    /// `PATCH` succeeded.
    case updated(Game)
    /// `DELETE` succeeded (or the game was already gone).
    case deleted
    /// `404 GAME_NOT_FOUND`: the game was deleted on another device.
    case gameGone
    /// Permanently rejected (`4xx` with an API error body); for an `UPDATE` or `DELETE`, the server's
    /// current version of the game (`nil` when it no longer exists).
    case rejected(restored: Game?)
}

struct PushCompletion: Sendable {
    var snapshot: StoreSnapshot
    /// The change became an `UPDATE` that has to be pushed in the same run.
    var pushAgain: Bool
}

enum LocalStoreError: Error, Equatable {
    case gameNotFound
    /// The data no longer belongs to the user the write was made for.
    case accessRevoked
    /// A pending `CREATE` or `UPDATE` refers to a game that is missing from the local store (or
    /// can't be read). A local, temporary failure: the change is kept and tried again, and it never
    /// counts as a deletion on the server.
    case gameUnavailable
}
