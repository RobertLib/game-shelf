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
}
