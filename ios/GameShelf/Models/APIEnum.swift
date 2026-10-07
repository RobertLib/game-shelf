import Foundation

/// A string-backed enum from the API contract.
///
/// Values introduced by a newer API version decode into ``fallback`` instead of
/// failing the whole response.
protocol APIEnum: RawRepresentable, CaseIterable, Codable, Hashable, Sendable where RawValue == String, AllCases == [Self] {
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

    /// Whether this value is a placeholder for something this app version does not know.
    var isKnown: Bool { Self.allCases.contains(self) }
}
