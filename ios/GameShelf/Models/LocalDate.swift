import Foundation

/// A calendar date without time or time zone, serialized as `YYYY-MM-DD`.
struct LocalDate: Hashable, Comparable, Sendable, Codable, CustomStringConvertible {
    let year: Int
    let month: Int
    let day: Int

    /// Gregorian calendar in the device time zone, used to map to and from `Date` for pickers.
    static var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        return calendar
    }

    init?(year: Int, month: Int, day: Int) {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let components = DateComponents(year: year, month: month, day: day)
        guard (1...9999).contains(year), components.isValidDate(in: utc) else { return nil }
        self.year = year
        self.month = month
        self.day = day
    }

    /// Parses the strict `YYYY-MM-DD` form used by the API.
    init?(_ string: String) {
        let parts = string.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.count == 3,
              parts[0].count == 4, parts[1].count == 2, parts[2].count == 2,
              parts.allSatisfy({ $0.allSatisfy(\.isASCIIDigit) }),
              let year = Int(parts[0]), let month = Int(parts[1]), let day = Int(parts[2])
        else { return nil }
        self.init(year: year, month: month, day: day)
    }

    init(_ date: Date, calendar: Calendar = LocalDate.calendar) {
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        year = components.year ?? 1970
        month = components.month ?? 1
        day = components.day ?? 1
    }

    static func today(calendar: Calendar = LocalDate.calendar) -> LocalDate {
        LocalDate(.now, calendar: calendar)
    }

    /// Midnight of this day in the given calendar's time zone.
    func date(calendar: Calendar = LocalDate.calendar) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day)) ?? .distantPast
    }

    var description: String {
        String(format: "%04d-%02d-%02d", year, month, day)
    }

    static func < (lhs: LocalDate, rhs: LocalDate) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    init(from decoder: any Decoder) throws {
        let container = try decoder.singleValueContainer()
        let string = try container.decode(String.self)
        guard let date = LocalDate(string) else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Expected a YYYY-MM-DD date, got \(string)"
            )
        }
        self = date
    }

    func encode(to encoder: any Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(description)
    }
}
