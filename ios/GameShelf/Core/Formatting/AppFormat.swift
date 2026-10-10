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

    /// Decimal for editing in a text field, in a form ``NumberInput`` reads back in any locale: ASCII
    /// digits, no grouping and the locale's decimal comma or point.
    static func editableNumber(_ value: Decimal, locale: Locale = .current) -> String {
        let text = value.formatted(.number.grouping(.never).locale(Locale(identifier: "en_US_POSIX")))
        return locale.decimalSeparator == "," ? text.replacingOccurrences(of: ".", with: ",") : text
    }

    static func date(_ date: LocalDate, locale: Locale = .current) -> String {
        date.date().formatted(Date.FormatStyle(date: .abbreviated, time: .omitted, locale: locale))
    }

    static func timestamp(_ date: Date, locale: Locale = .current) -> String {
        date.formatted(Date.FormatStyle(date: .abbreviated, time: .shortened, locale: locale))
    }

    /// `"5 minutes ago"`, `"now"`, `"yesterday"` …
    static func relativeTime(_ date: Date, now: Date = .now, locale: Locale = .current) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.locale = locale
        formatter.unitsStyle = .full
        formatter.dateTimeStyle = .named
        // A sync that finished a moment ago must not read as "in 0 seconds".
        return formatter.localizedString(for: min(date, now), relativeTo: now)
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
    /// A decimal typed with a decimal point or comma and optional grouping, read the same way in every
    /// locale (docs/mobile-spec.md, "Decimal input"); `nil` when the text is not such a number.
    static func decimal(_ text: String) -> Decimal? {
        parse(text)?.value
    }

    /// The value of a typed decimal and its number of decimal places as typed (`"1,50"` has 2,
    /// `"1,500"` is 1500 with none):
    ///
    /// 1. All whitespace is removed (including no-break and thin spaces).
    /// 2. What remains may only contain digits, `.` and `,`, with at least one digit.
    /// 3. When both `.` and `,` occur, the last one is the decimal separator (it must occur once) and
    ///    the other one is grouping. A single kind occurring more than once is grouping. Occurring
    ///    once, it is the decimal separator unless exactly 3 digits follow it (prices never have 3
    ///    decimals, so `1,500` and `1.500` are 1500).
    /// 4. With grouping, the whole part is 1–3 digits not starting with `0`, followed by groups of
    ///    exactly 3 digits (`0,500` is invalid, not 500).
    /// 5. The whole part or the decimal part may be empty, not both (`,5` is 0.5, `5,` is 5).
    static func parse(_ text: String) -> (value: Decimal, decimalPlaces: Int)? {
        let scalars = text.unicodeScalars.filter { !$0.properties.isWhitespace }
        func isDigit(_ scalar: Unicode.Scalar) -> Bool { ("0"..."9").contains(scalar) }
        guard scalars.contains(where: isDigit),
              scalars.allSatisfy({ isDigit($0) || $0 == "." || $0 == "," })
        else { return nil }
        let input = String(scalars)

        let points = input.filter { $0 == "." }.count
        let commas = input.filter { $0 == "," }.count
        let decimalSeparator: Character?
        let groupingSeparator: Character?
        switch (points, commas) {
        case (0, 0):
            (decimalSeparator, groupingSeparator) = (nil, nil)
        case (_, 0), (0, _):
            let separator: Character = points > 0 ? "." : ","
            let digitsAfter = input.split(separator: separator, omittingEmptySubsequences: false).last?.count ?? 0
            if points + commas > 1 || digitsAfter == 3 {
                (decimalSeparator, groupingSeparator) = (nil, separator)
            } else {
                (decimalSeparator, groupingSeparator) = (separator, nil)
            }
        default:
            guard let last = input.last(where: { $0 == "." || $0 == "," }),
                  (last == "." ? points : commas) == 1
            else { return nil }
            (decimalSeparator, groupingSeparator) = (last, last == "." ? "," : ".")
        }

        var whole = Substring(input)
        var fraction = Substring()
        if let decimalSeparator, let index = input.firstIndex(of: decimalSeparator) {
            whole = input[..<index]
            fraction = input[input.index(after: index)...]
        }
        if let groupingSeparator {
            let groups = whole.split(separator: groupingSeparator, omittingEmptySubsequences: false)
            guard let first = groups.first, (1...3).contains(first.count), first.first != "0",
                  groups.dropFirst().allSatisfy({ $0.count == 3 })
            else { return nil }
            whole = Substring(groups.joined())
        }
        guard !whole.isEmpty || !fraction.isEmpty else { return nil }
        let number = "\(whole.isEmpty ? "0" : whole).\(fraction.isEmpty ? "0" : fraction)"
        guard let value = Decimal(string: number, locale: Locale(identifier: "en_US_POSIX")) else { return nil }
        return (value, fraction.count)
    }

    static func integer(_ text: String) -> Int? {
        let trimmed = text.filter { !$0.isWhitespace }
        guard !trimmed.isEmpty, trimmed.allSatisfy(\.isASCIIDigit) else { return nil }
        return Int(trimmed)
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
