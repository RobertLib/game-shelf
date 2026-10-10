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
    /// `PATCH` answered `404 GAME_UNKNOWN`: the server has no record of the game at all (it was restored
    /// from a backup older than the game).
    case gameUnknown
    /// An `UPDATE` or `DELETE` permanently rejected with `error` (`400 VALIDATION_FAILED`, `409 CONFLICT`),
    /// with the server's current version of the game (`nil` when it no longer exists). A rejected `CREATE`
    /// is not undone: the game exists only on this device.
    case rejected(APIError, restored: Game?)
    /// An `UPDATE` or `DELETE` permanently rejected, whose game the server has no record of at all (the `GET`
    /// of its undo answered `404 GAME_UNKNOWN`): there is nothing to restore, the local game is the only copy.
    case rejectedUnknownGame
}

struct PushCompletion: Sendable {
    var snapshot: StoreSnapshot
    /// The change became an `UPDATE` or a `CREATE` that has to be pushed in the same run.
    var pushAgain: Bool
    /// A rejected change that was changed locally after it was sent, so it isn't undone: it stays queued as
    /// a failure of this change (with the rejection) and is sent again in the next run.
    var failure: APIError?
}

struct PullCompletion: Sendable {
    var snapshot: StoreSnapshot
    /// The last page of a full pull after a reset left games unconfirmed: they got a `CREATE`, which
    /// another run pushes.
    var queuedUnconfirmedGames: Bool
}

enum LocalStoreError: Error, Equatable {
    case gameNotFound
    /// The data no longer belongs to the user the write was made for.
    case accessRevoked
    /// A pending `CREATE` or `UPDATE` refers to a game that is missing from the local store (or
    /// can't be read). A local failure of that change: it is kept and tried again in later runs, the
    /// other changes are pushed meanwhile, and it never counts as a deletion on the server.
    case gameUnavailable
}
