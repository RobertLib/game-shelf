import Foundation

/// Formatting of values shown in the UI. Everything follows the device locale;
/// prices always use the game's own currency.
enum AppFormat {
    /// `CZK 1,500` for whole amounts, `CZK 1,299.90` otherwise (in en_US).
    static func currency(_ value: Decimal, code: String, locale: Locale = .current) -> String {
        let isWhole = value == value.rounded(scale: 0)
        return value.formatted(.currency(code: code).precision(.fractionLength(isWhole ? 0 : 2)).locale(locale))
    }

    static func number(_ value: Decimal, locale: Locale = .current) -> String {
        value.formatted(.number.locale(locale))
    }

    static func integer(_ value: Int, locale: Locale = .current) -> String {
        value.formatted(.number.locale(locale))
    }

    /// Decimal for editing in a text field: locale decimal separator, no grouping.
    static func editableNumber(_ value: Decimal, locale: Locale = .current) -> String {
        value.formatted(.number.grouping(.never).locale(locale))
    }

    static func date(_ date: LocalDate, locale: Locale = .current) -> String {
        date.date().formatted(Date.FormatStyle(date: .abbreviated, time: .omitted, locale: locale))
    }

    static func timestamp(_ date: Date, locale: Locale = .current) -> String {
        date.formatted(Date.FormatStyle(date: .abbreviated, time: .shortened, locale: locale))
    }

    /// `"1990–1999"`, `"from 1990"` or `"to 1999"`.
    static func range(from lower: String?, to upper: String?) -> String {
        switch (lower, upper) {
        case let (lower?, upper?) where lower == upper: lower
        case let (lower?, upper?): "\(lower)–\(upper)"
        case let (lower?, nil): "from \(lower)"
        case let (nil, upper?): "to \(upper)"
        case (nil, nil): ""
        }
    }
}

/// Lenient parsing of numbers typed by the user.
enum NumberInput {
    /// Accepts `.` or `,` as the decimal separator. When both appear, the locale's
    /// grouping separator is dropped first (`1,299.90` in en_US, `1.299,90` in de_DE).
    static func decimal(_ text: String, locale: Locale = .current) -> Decimal? {
        var normalized = text.filter { !$0.isWhitespace }
        if normalized.contains("."), normalized.contains(","),
           let grouping = locale.groupingSeparator, grouping == "." || grouping == "," {
            normalized = normalized.replacingOccurrences(of: grouping, with: "")
        }
        normalized = normalized.replacingOccurrences(of: ",", with: ".")
        let unsigned = normalized.hasPrefix("-") ? normalized.dropFirst() : normalized[...]
        guard unsigned.contains(where: \.isASCIIDigit),
              unsigned.allSatisfy({ $0.isASCIIDigit || $0 == "." }),
              unsigned.filter({ $0 == "." }).count <= 1
        else { return nil }
        return Decimal(string: normalized, locale: Locale(identifier: "en_US_POSIX"))
    }

    static func integer(_ text: String) -> Int? {
        let trimmed = text.filter { !$0.isWhitespace }
        guard !trimmed.isEmpty, trimmed.allSatisfy(\.isASCIIDigit) else { return nil }
        return Int(trimmed)
    }

    /// Same rule as the API: the value must not change when rounded to two decimals.
    static func hasAtMostTwoDecimalPlaces(_ value: Decimal) -> Bool {
        value.rounded(scale: 2) == value
    }
}

extension Decimal {
    func rounded(scale: Int, mode: NSDecimalNumber.RoundingMode = .plain) -> Decimal {
        var input = self
        var result = Decimal()
        NSDecimalRound(&result, &input, scale, mode)
        return result
    }
}

extension Character {
    var isASCIIDigit: Bool { isASCII && isNumber }
}
