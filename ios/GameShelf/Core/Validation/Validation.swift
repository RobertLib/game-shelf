import Foundation

/// Client-side validation mirroring the API rules. Each check returns an error
/// message, or `nil` when the value is valid. Empty optional values are valid.
enum Validation {
    static let releaseYearRange = 1950...2100
    static let ratingRange = 1...10
    static let quantityRange = 1...999
    static let passwordLength = 8...128
    static let maxPrice = Decimal(string: "9999999999.99")!

    static func email(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return "Enter your email." }
        return trimmed.wholeMatch(of: /[^\s@]+@[^\s@]+\.[^\s@]+/) == nil ? "Enter a valid email address." : nil
    }

    static func newPassword(_ value: String) -> String? {
        passwordLength.contains(value.count) ? nil : "Password must be 8–128 characters."
    }

    static func requiredPassword(_ value: String) -> String? {
        value.isEmpty ? "Enter your password." : nil
    }

    static func passwordConfirmation(_ value: String, matching password: String) -> String? {
        value == password ? nil : "Passwords don't match."
    }

    static func title(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return "Enter a title." }
        return maxLength(trimmed, 200)
    }

    static func maxLength(_ value: String, _ limit: Int) -> String? {
        value.trimmingCharacters(in: .whitespacesAndNewlines).count > limit
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

    static func price(_ text: String, locale: Locale = .current) -> String? {
        guard !text.isBlank else { return nil }
        guard let value = NumberInput.decimal(text, locale: locale) else { return "Enter a number, e.g. 499.90." }
        if value < 0 { return "The amount can't be negative." }
        if !NumberInput.hasAtMostTwoDecimalPlaces(value) { return "Use at most 2 decimal places." }
        if value > maxPrice { return "The amount is too high." }
        return nil
    }

    static func barcode(_ text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let isValid = (8...14).contains(trimmed.count) && trimmed.allSatisfy(\.isASCIIDigit)
        return isValid ? nil : "The barcode must have 8–14 digits."
    }

    static func coverURL(_ text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        guard trimmed.count <= 2048,
              let components = URLComponents(string: trimmed),
              let scheme = components.scheme?.lowercased(), ["http", "https"].contains(scheme),
              let host = components.host, host.contains(".") || host == "localhost"
        else { return "Enter a valid URL starting with http:// or https://." }
        return nil
    }

    static func currency(_ text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let isValid = trimmed.count == 3 && trimmed.allSatisfy { $0.isASCII && $0.isLetter }
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
}
