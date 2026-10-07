import Foundation

/// English count phrases ("1 game", "0 games", "1,234 games") with the number
/// formatted for the device locale.
enum Pluralization {
    static func format(_ count: Int, one: String, other: String, locale: Locale = .current) -> String {
        let noun = count == 1 ? one : other
        return "\(count.formatted(.number.locale(locale))) \(noun)"
    }

    static func games(_ count: Int, locale: Locale = .current) -> String {
        format(count, one: "game", other: "games", locale: locale)
    }

    /// "3 unsynced changes" (sync status).
    static func unsyncedChanges(_ count: Int, locale: Locale = .current) -> String {
        format(count, one: "unsynced change", other: "unsynced changes", locale: locale)
    }

    /// Warning of the sign-out confirmation when changes would be lost.
    static func signOutWarning(unsyncedChanges count: Int, locale: Locale = .current) -> String {
        let changes = format(count, one: "change", other: "changes", locale: locale)
        return count == 1
            ? "\(changes) hasn't been synced yet. It will be lost if you sign out now."
            : "\(changes) haven't been synced yet. They will be lost if you sign out now."
    }
}
