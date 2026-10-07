import Foundation
import Observation

/// What the screens show about synchronization (docs/offline-sync.md, "Status shown in the apps").
///
/// Written by the ``SyncEngine`` and, for the stored counts, by the ``GameRepository``.
@Observable
@MainActor
final class SyncStatus {
    /// A sync run is in progress.
    var isSyncing = false
    /// The last attempt could not reach the server.
    var isServerUnreachable = false
    /// The device has no network connection.
    var isNetworkUnavailable = false
    /// Number of local changes waiting to be pushed.
    var pendingCount = 0
    /// Time of the last completed sync.
    var lastSyncedAt: Date?
    /// Message of the last failed run; cleared by a successful one.
    var lastErrorMessage: String?
    /// The engine undid changes the server rejected; the UI shows ``ErrorMessage/changesRejected``
    /// once and resets it.
    var hasUndoneRejectedChanges = false

    enum Indicator: Equatable {
        case syncing
        case offline
        case unsynced(Int)
    }

    var isOffline: Bool { isServerUnreachable || isNetworkUnavailable }

    /// Until the first complete pull, an empty collection is not known to be empty.
    var hasCompletedInitialSync: Bool { lastSyncedAt != nil }

    /// Status at the end of the collection header; `nil` when everything is synced.
    var indicator: Indicator? {
        if isSyncing { return .syncing }
        if isOffline { return .offline }
        if pendingCount > 0 { return .unsynced(pendingCount) }
        return nil
    }

    /// Status line of the "Sync" section in Profile.
    var summary: String {
        switch indicator {
        case .syncing: "Syncing…"
        case .offline: "Offline"
        case .unsynced(let count): Pluralization.unsyncedChanges(count)
        case nil: hasCompletedInitialSync ? "All changes synced" : "Not synced yet"
        }
    }
}
