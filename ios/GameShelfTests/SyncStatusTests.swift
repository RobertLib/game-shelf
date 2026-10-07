import Foundation
import Testing
@testable import GameShelf

@MainActor
@Suite("Sync status texts")
struct SyncStatusTests {
    @Test func profileSummaryFollowsThePriorityOfTheSpec() {
        let status = SyncStatus()
        #expect(status.summary == "Not synced yet")
        status.lastSyncedAt = .now
        #expect(status.summary == "All changes synced")
        status.pendingCount = 3
        #expect(status.summary.hasSuffix("unsynced changes"))
        status.isNetworkUnavailable = true
        #expect(status.summary == "Offline")
        status.isSyncing = true
        #expect(status.summary == "Syncing…")
    }

    @Test func headerIndicator() {
        let status = SyncStatus()
        #expect(status.indicator == nil)
        status.pendingCount = 1
        #expect(status.indicator == .unsynced(1))
        status.isServerUnreachable = true
        #expect(status.indicator == .offline)
        status.isSyncing = true
        #expect(status.indicator == .syncing)
    }

    @Test func unsyncedChangeCounts() {
        let english = Locale(identifier: "en_US")
        #expect(Pluralization.unsyncedChanges(1, locale: english) == "1 unsynced change")
        #expect(Pluralization.unsyncedChanges(3, locale: english) == "3 unsynced changes")
    }

    @Test func signOutWarning() {
        let english = Locale(identifier: "en_US")
        #expect(Pluralization.signOutWarning(unsyncedChanges: 1, locale: english)
            == "1 change hasn't been synced yet. It will be lost if you sign out now.")
        #expect(Pluralization.signOutWarning(unsyncedChanges: 3, locale: english)
            == "3 changes haven't been synced yet. They will be lost if you sign out now.")
    }

    @Test func lastSyncedIsRelative() {
        let english = Locale(identifier: "en_US")
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        #expect(AppFormat.relativeTime(now.addingTimeInterval(-300), now: now, locale: english) == "5 minutes ago")
        #expect(AppFormat.relativeTime(now, now: now, locale: english) == "now")
    }
}
