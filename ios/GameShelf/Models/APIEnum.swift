import Foundation

/// A string-backed enum from the API contract.
///
/// Values introduced by a newer API version decode into ``fallback`` instead of
/// failing the whole response; a game's fields keep the string the API sent (``Verbatim``).
protocol APIEnum: RawRepresentable, CaseIterable, VerbatimValue where RawValue == String, AllCases == [Self] {
    static var fallback: Self { get }
    /// Display name.
    var label: String { get }
}

extension APIEnum {
    init(from decoder: any Decoder) throws {
        let rawValue = try decoder.singleValueContainer().decode(String.self)
        self = Self(rawValue: rawValue) ?? Self.fallback
    }

    func encode(to encoder: any Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }

    /// Whether this app version knows the value; `false` for the placeholder of one it doesn't know.
    var isKnown: Bool { Self.allCases.contains(self) }

    var wireString: String? { rawValue }

    var isKnownValue: Bool { isKnown }
}
