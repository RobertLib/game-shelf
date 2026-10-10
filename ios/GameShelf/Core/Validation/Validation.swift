import Foundation

/// Client-side validation with exactly the rules of the API (docs/mobile-spec.md, "Validation").
/// Each check returns an error message, or `nil` when the value is valid. Empty optional values
/// are valid. Text is checked as it is saved (``Swift/String/normalizedText``), and lengths are
/// counted in Unicode code points, like the API counts them: `"😀"` is 1, `"🇨🇿"` is 2.
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
    static let maxEmailLength = 254
    /// The part of an email address before the `@`, in UTF-8 bytes.
    static let maxEmailLocalPartBytes = 64
    /// The part of an email address after the `@`, in UTF-8 bytes.
    static let maxEmailDomainBytes = 254
    /// Purchase dates the API accepts; the date pickers offer only these (``purchaseDatePickerRange``).
    static let purchaseDateRange = LocalDate(year: 1900, month: 1, day: 1)!...LocalDate(year: 2100, month: 12, day: 31)!

    /// The cover URL pattern shared by the API and both apps: ASCII only, character classes written
    /// out. Matched per Unicode scalar, as the other platforms' regex engines do.
    static let coverURLPattern = #"^[Hh][Tt][Tt][Pp][Ss]?://[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?(:[0-9]{1,5})?([/?#][!-~]*)?$"#

    /// The email pattern shared by both apps (docs/mobile-spec.md, "Email pattern"): every address it
    /// accepts is accepted by the API's `IsEmail` (validator.js). Matched by ICU (`NSRegularExpression`),
    /// which supports `\p{L}`, `\uXXXX` and lookahead like JavaScript; its ranges are all inside the Basic
    /// Multilingual Plane, and addresses with characters outside it are rejected before it is applied.
    static let emailPattern = #"^(?!.*[\uFF01-\uFF5E])[A-Za-z0-9!#$%&'*+/=?^_`{|}~\u00A1-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~\u00A1-\uD7FF\uF900-\uFDCF\uFDF0-\uFFEF-]+)*@(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]{0,61}[\p{L}\p{N}])?\.)+(?:\p{L}{2,63}|[Xx][Nn]--[A-Za-z0-9-]{0,58}[A-Za-z0-9])$"#

    /// The email of a new account: at most 254 code points, no character outside the Basic Multilingual
    /// Plane (e.g. an emoji), at most 64 UTF-8 bytes before the `@` and 254 after it, matching
    /// ``emailPattern`` (docs/mobile-spec.md, "Validation").
    static func newAccountEmail(_ value: String) -> String? {
        let email = value.normalizedText
        if email.isEmpty { return "Enter your email." }
        if let error = maxLength(email, maxEmailLength) { return error }
        let invalid = "Enter a valid email address."
        guard email.unicodeScalars.allSatisfy({ $0.value <= 0xFFFF }), let at = email.lastIndex(of: "@") else { return invalid }
        guard email[..<at].utf8.count <= maxEmailLocalPartBytes,
              email[email.index(after: at)...].utf8.count <= maxEmailDomainBytes
        else { return invalid }
        let whole = NSRange(email.startIndex..., in: email)
        let regex = try? NSRegularExpression(pattern: emailPattern)
        return regex?.firstMatch(in: email, range: whole)?.range == whole ? nil : invalid
    }

    /// The email to sign in with: only required, at most 254 code points and with an `@` – any existing
    /// account must be able to sign in, whatever the format of its address (docs/mobile-spec.md, "Validation").
    static func signInEmail(_ value: String) -> String? {
        let email = value.normalizedText
        if email.isEmpty { return "Enter your email." }
        if let error = maxLength(email, maxEmailLength) { return error }
        return email.contains("@") ? nil : "Enter a valid email address."
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

    /// A new password has to differ from the current one. Compared as the API compares them, code point
    /// by code point (Swift's `==` would equate a precomposed and a decomposed "é").
    static func differentPassword(_ newPassword: String, from currentPassword: String) -> String? {
        newPassword.unicodeScalars.elementsEqual(currentPassword.unicodeScalars)
            ? "The new password must be different from the current one."
            : nil
    }

    static func displayName(_ value: String) -> String? {
        maxLength(value, maxDisplayNameLength)
    }

    static func title(_ value: String) -> String? {
        let title = value.normalizedText
        if title.isEmpty { return "Enter a title." }
        return maxLength(title, maxTitleLength)
    }

    /// At most `limit` code points once normalized.
    static func maxLength(_ value: String, _ limit: Int) -> String? {
        value.normalizedText.codePointCount > limit
            ? "Must be at most \(limit) characters."
            : nil
    }

    /// A purchase date the API accepts: 1900-01-01 – 2100-12-31.
    static func purchaseDate(_ date: LocalDate?) -> String? {
        guard let date, !purchaseDateRange.contains(date) else { return nil }
        return "Enter a date between 1900 and 2100."
    }

    /// ``purchaseDateRange`` for a date picker: midnight of its first and of its last day in the calendar and
    /// time zone that ``LocalDate`` maps picked dates with, so the bounds are exactly those days.
    static var purchaseDatePickerRange: ClosedRange<Date> {
        purchaseDateRange.lowerBound.date()...purchaseDateRange.upperBound.date()
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
            let trimmed = text.normalizedText
            let isNegative = trimmed.hasPrefix("-") && NumberInput.parse(String(trimmed.dropFirst())) != nil
            return isNegative ? "The amount can't be negative." : "Enter a number, e.g. 499.90."
        }
        if number.decimalPlaces > 2 { return "Use at most 2 decimal places." }
        if number.value > maxPrice { return "The amount is too high." }
        return nil
    }

    static func barcode(_ text: String) -> String? {
        let scalars = text.normalizedText.unicodeScalars
        guard !scalars.isEmpty else { return nil }
        let isValid = (8...14).contains(scalars.count) && scalars.allSatisfy { ("0"..."9").contains($0) }
        return isValid ? nil : "The barcode must have 8–14 digits."
    }

    static func coverURL(_ text: String) -> String? {
        let trimmed = text.normalizedText
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
        let scalars = text.normalizedText.unicodeScalars
        let isValid = scalars.count == 3 && scalars.allSatisfy { ("A"..."Z").contains($0) || ("a"..."z").contains($0) }
        return isValid ? nil : "The currency must be 3 letters, e.g. CZK."
    }
}

extension String {
    /// The text as it is validated, saved and sent (docs/mobile-spec.md, "Validation"): U+0000 removed
    /// wherever it occurs, then trimmed of white space and U+FEFF (a pasted byte order mark) – the
    /// characters JavaScript's `trim()` removes on the API, which `.whitespacesAndNewlines` lacks.
    var normalizedText: String {
        String(unicodeScalars.filter { $0 != "\u{0}" }).trimmingCharacters(in: .apiWhitespace)
    }

    var isBlank: Bool { normalizedText.isEmpty }

    /// Normalized value (``normalizedText``), or `nil` when blank – for optional API fields.
    var nilIfBlank: String? {
        let normalized = normalizedText
        return normalized.isEmpty ? nil : normalized
    }

    /// Length as the API counts it: in Unicode code points.
    var codePointCount: Int { unicodeScalars.count }
}

extension CharacterSet {
    /// White space as the API trims it: every Unicode white space and line break, and U+FEFF.
    static let apiWhitespace = CharacterSet.whitespacesAndNewlines.union(CharacterSet(charactersIn: "\u{FEFF}"))
}
