import Foundation

/// Client-side validation with exactly the rules of the API (docs/mobile-spec.md, "Validation").
/// Each check returns an error message, or `nil` when the value is valid. Empty optional values
/// are valid. Lengths are counted in Unicode code points, like the API counts them: `"😀"` is 1,
/// `"🇨🇿"` is 2.
enum Validation {
    static let releaseYearRange = 1950...2100
    static let ratingRange = 1...10
    static let quantityRange = 1...999
    static let passwordLength = 8...128
    static let maxPrice = Decimal(string: "9999999999.99")!

    static let maxTitleLength = 200
    /// Edition, genre, developer, publisher, purchase place and storage location.
    static let maxTextLength = 100
    static let maxProductCodeLength = 50
    static let maxNotesLength = 5000
    static let maxCoverURLLength = 2048
    static let maxDisplayNameLength = 100

    /// The cover URL pattern shared by the API and both apps: ASCII only, character classes written
    /// out. Matched per Unicode scalar, as the other platforms' regex engines do.
    static let coverURLPattern = #"^[Hh][Tt][Tt][Pp][Ss]?://[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?(:[0-9]{1,5})?([/?#][!-~]*)?$"#

    static func email(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return "Enter your email." }
        return trimmed.wholeMatch(of: /[^\s@]+@[^\s@]+\.[^\s@]+/) == nil ? "Enter a valid email address." : nil
    }

    static func newPassword(_ value: String) -> String? {
        passwordLength.contains(value.codePointCount) ? nil : "Password must be 8–128 characters."
    }

    static func requiredPassword(_ value: String) -> String? {
        value.isEmpty ? "Enter your password." : nil
    }

    static func passwordConfirmation(_ value: String, matching password: String) -> String? {
        value == password ? nil : "Passwords don't match."
    }

    static func displayName(_ value: String) -> String? {
        maxLength(value, maxDisplayNameLength)
    }

    static func title(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return "Enter a title." }
        return maxLength(trimmed, maxTitleLength)
    }

    /// At most `limit` code points once trimmed.
    static func maxLength(_ value: String, _ limit: Int) -> String? {
        value.trimmingCharacters(in: .whitespacesAndNewlines).codePointCount > limit
            ? "Must be at most \(limit) characters."
            : nil
    }

    static func releaseYear(_ text: String) -> String? {
        guard !text.isBlank else { return nil }
        guard let year = NumberInput.integer(text), releaseYearRange.contains(year) else {
            return "Release year must be between 1950 and 2100."
        }
        return nil
    }

    /// A purchase price, estimated value or price filter bound: 0 – 9 999 999 999.99 with at most
    /// 2 decimal places as typed (``NumberInput/parse(_:)``).
    static func price(_ text: String) -> String? {
        guard !text.isBlank else { return nil }
        guard let number = NumberInput.parse(text) else {
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            let isNegative = trimmed.hasPrefix("-") && NumberInput.parse(String(trimmed.dropFirst())) != nil
            return isNegative ? "The amount can't be negative." : "Enter a number, e.g. 499.90."
        }
        if number.decimalPlaces > 2 { return "Use at most 2 decimal places." }
        if number.value > maxPrice { return "The amount is too high." }
        return nil
    }

    static func barcode(_ text: String) -> String? {
        let scalars = text.trimmingCharacters(in: .whitespacesAndNewlines).unicodeScalars
        guard !scalars.isEmpty else { return nil }
        let isValid = (8...14).contains(scalars.count) && scalars.allSatisfy { ("0"..."9").contains($0) }
        return isValid ? nil : "The barcode must have 8–14 digits."
    }

    static func coverURL(_ text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if let error = maxLength(trimmed, maxCoverURLLength) { return error }
        let pattern = (try? Regex(coverURLPattern))?.matchingSemantics(.unicodeScalar)
        guard let pattern, (try? pattern.wholeMatch(in: trimmed)) != nil else {
            return "Enter a valid URL starting with http:// or https://."
        }
        return nil
    }

    /// 3 letters A–Z in either case (sent upper-case); historic codes such as DEM are fine.
    static func currency(_ text: String) -> String? {
        let scalars = text.trimmingCharacters(in: .whitespacesAndNewlines).unicodeScalars
        let isValid = scalars.count == 3 && scalars.allSatisfy { ("A"..."Z").contains($0) || ("a"..."z").contains($0) }
        return isValid ? nil : "The currency must be 3 letters, e.g. CZK."
    }
}

extension String {
    var isBlank: Bool { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    /// Trimmed value, or `nil` when blank – for optional API fields.
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    /// Length as the API counts it: in Unicode code points.
    var codePointCount: Int { unicodeScalars.count }
}
