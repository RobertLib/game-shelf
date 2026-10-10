import Foundation

/// A value a ``Verbatim`` field holds: an ``APIEnum``, or an optional one.
protocol VerbatimValue: Codable, Hashable, Sendable {
    /// The string the value is sent as; `nil` for an empty optional field.
    var wireString: String? { get }
    /// Whether this app version knows the value; an empty optional field counts as known.
    var isKnownValue: Bool { get }
}

extension Optional: VerbatimValue where Wrapped: APIEnum {
    var wireString: String? { self?.rawValue }
    var isKnownValue: Bool { self?.isKnown ?? true }
}

/// An enum field of a game that keeps the value the API sent, verbatim (docs/offline-sync.md, "Local data").
///
/// A value this app version doesn't know reads as its fallback ("unknown", or `OTHER` for platform and
/// region) – that is what the screens show, filter and sort by – but the string the API sent stays with it
/// (``unknownWireValue``) and is encoded instead: into the stored game, and into every request that sends
/// the field. So a `CREATE` of the whole game sends it unchanged, while a `PATCH` sends the field only
/// when the user changed it. Setting another value forgets the string.
@propertyWrapper
struct Verbatim<Value: VerbatimValue>: Hashable, Sendable {
    var wrappedValue: Value {
        didSet {
            // Picking the value that is already shown (e.g. "Unknown" again) keeps the string.
            if wrappedValue != oldValue {
                unknownWireValue = nil
            }
        }
    }

    /// The string the API sent for a value this app version doesn't know; `nil` for a known value.
    private(set) var unknownWireValue: String?

    init(wrappedValue: Value) {
        self.wrappedValue = wrappedValue
    }

    /// The field with the string the API sent (`game.$status`).
    var projectedValue: Self {
        get { self }
        set { self = newValue }
    }
}

extension Verbatim {
    /// A required field as an optional one, e.g. the platform of the edit form, which can be empty.
    init<Wrapped: APIEnum>(_ field: Verbatim<Wrapped>) where Value == Wrapped? {
        self.init(wrappedValue: field.wrappedValue)
        unknownWireValue = field.unknownWireValue
    }

    /// The optional field as a required one; `nil` while it is empty.
    func unwrapped<Wrapped: APIEnum>() -> Verbatim<Wrapped>? where Value == Wrapped? {
        guard let value = wrappedValue else { return nil }
        var field = Verbatim<Wrapped>(wrappedValue: value)
        field.unknownWireValue = unknownWireValue
        return field
    }

    /// The field, or `replacement` for a placeholder of an unknown value without the string the API sent:
    /// app versions before ``Verbatim`` stored only the placeholder ("unknown"), which the API doesn't accept.
    func replacingPlaceholder(with replacement: Value) -> Self {
        guard !wrappedValue.isKnownValue, unknownWireValue == nil else { return self }
        return Verbatim(wrappedValue: replacement)
    }
}

extension Verbatim: Codable {
    init(from decoder: any Decoder) throws {
        let container = try decoder.singleValueContainer()
        let string = container.decodeNil() ? nil : try container.decode(String.self)
        self.init(wrappedValue: try Value(from: decoder))
        // A string that isn't the value it decoded to is one this app version doesn't know. The placeholder's
        // own string ("UNKNOWN", stored by older app versions) is not: it was never the API's.
        if let string, string != wrappedValue.wireString {
            unknownWireValue = string
        }
    }

    func encode(to encoder: any Encoder) throws {
        guard let unknownWireValue else {
            try wrappedValue.encode(to: encoder)
            return
        }
        var container = encoder.singleValueContainer()
        try container.encode(unknownWireValue)
    }
}

extension KeyedDecodingContainer {
    /// A missing optional field is empty, as it is for a plain optional property.
    func decode<Wrapped: APIEnum>(_ type: Verbatim<Wrapped?>.Type, forKey key: Key) throws -> Verbatim<Wrapped?> {
        try decodeIfPresent(type, forKey: key) ?? Verbatim(wrappedValue: nil)
    }
}
