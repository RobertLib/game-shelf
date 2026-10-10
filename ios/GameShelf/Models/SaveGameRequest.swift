import Foundation

/// The editable fields of a game, as sent to the API.
///
/// `POST games` sends every field (empty optionals as explicit JSON `null`); `PATCH games/{id}`
/// sends only the changed ones (see ``UpdateGameRequest``). Enum values this app version doesn't know
/// are sent as the API sent them (``Verbatim``).
struct SaveGameRequest: Encodable, Hashable, Sendable {
    var title: String
    @Verbatim var platform: Platform
    @Verbatim var status: CollectionStatus = .owned
    @Verbatim var format: GameFormat = .physical
    @Verbatim var region: Region? = nil
    var edition: String?
    @Verbatim var completeness: Completeness? = nil
    @Verbatim var condition: Condition? = nil
    @Verbatim var playStatus: PlayStatus? = nil
    var genre: String?
    var developer: String?
    var publisher: String?
    var releaseYear: Int?
    var barcode: String?
    var productCode: String?
    var quantity: Int = 1
    var purchasePrice: Decimal?
    var purchaseDate: LocalDate?
    var purchasePlace: String?
    var estimatedValue: Decimal?
    var currency: String = "CZK"
    var storageLocation: String?
    var rating: Int?
    var favorite: Bool = false
    var coverImageUrl: String?
    var notes: String?

    /// The contract's field names; also the coding keys. Pending updates store these names.
    enum Field: String, CodingKey, CaseIterable, Sendable {
        case title, platform, status, format, region, edition, completeness, condition, playStatus
        case genre, developer, publisher, releaseYear, barcode, productCode, quantity
        case purchasePrice, purchaseDate, purchasePlace, estimatedValue, currency
        case storageLocation, rating, favorite, coverImageUrl, notes
    }

    func encode(to encoder: any Encoder) throws {
        try encode(Set(Field.allCases), to: encoder)
    }

    /// Encodes only `fields`; empty optionals among them are sent as explicit JSON `null`.
    func encode(_ fields: Set<Field>, to encoder: any Encoder) throws {
        var container = encoder.container(keyedBy: Field.self)
        for field in Field.allCases where fields.contains(field) {
            try encode(field, into: &container)
        }
    }

    private func encode(_ field: Field, into container: inout KeyedEncodingContainer<Field>) throws {
        switch field {
        case .title: try container.encode(title, forKey: field)
        // The enum fields encode an empty value as `null` themselves.
        case .platform: try container.encode($platform, forKey: field)
        case .status: try container.encode($status, forKey: field)
        case .format: try container.encode($format, forKey: field)
        case .region: try container.encode($region, forKey: field)
        case .edition: try container.encodeExplicitly(edition, forKey: field)
        case .completeness: try container.encode($completeness, forKey: field)
        case .condition: try container.encode($condition, forKey: field)
        case .playStatus: try container.encode($playStatus, forKey: field)
        case .genre: try container.encodeExplicitly(genre, forKey: field)
        case .developer: try container.encodeExplicitly(developer, forKey: field)
        case .publisher: try container.encodeExplicitly(publisher, forKey: field)
        case .releaseYear: try container.encodeExplicitly(releaseYear, forKey: field)
        case .barcode: try container.encodeExplicitly(barcode, forKey: field)
        case .productCode: try container.encodeExplicitly(productCode, forKey: field)
        case .quantity: try container.encode(quantity, forKey: field)
        case .purchasePrice: try container.encodeExplicitly(purchasePrice.map(Self.money), forKey: field)
        case .purchaseDate: try container.encodeExplicitly(purchaseDate, forKey: field)
        case .purchasePlace: try container.encodeExplicitly(purchasePlace, forKey: field)
        case .estimatedValue: try container.encodeExplicitly(estimatedValue.map(Self.money), forKey: field)
        case .currency: try container.encode(currency, forKey: field)
        case .storageLocation: try container.encodeExplicitly(storageLocation, forKey: field)
        case .rating: try container.encodeExplicitly(rating, forKey: field)
        case .favorite: try container.encode(favorite, forKey: field)
        case .coverImageUrl: try container.encodeExplicitly(coverImageUrl, forKey: field)
        case .notes: try container.encodeExplicitly(notes, forKey: field)
        }
    }

    /// The API accepts at most two decimal places; this also guards against
    /// binary floating point noise from decoding.
    static func money(_ value: Decimal) -> Decimal {
        value.rounded(scale: 2)
    }
}

typealias GameField = SaveGameRequest.Field

// MARK: - Field-level changes

extension SaveGameRequest {
    /// Fields whose value differs from `original`. Money is compared at the precision the API stores; an
    /// enum value this app version doesn't know differs from every value the user can pick.
    func changedFields(comparedTo original: SaveGameRequest) -> Set<Field> {
        Set(Field.allCases.filter { !hasSameValue(in: $0, as: original) })
    }

    private func hasSameValue(in field: Field, as other: SaveGameRequest) -> Bool {
        switch field {
        case .title: title == other.title
        case .platform: $platform == other.$platform
        case .status: $status == other.$status
        case .format: $format == other.$format
        case .region: $region == other.$region
        case .edition: edition == other.edition
        case .completeness: $completeness == other.$completeness
        case .condition: $condition == other.$condition
        case .playStatus: $playStatus == other.$playStatus
        case .genre: genre == other.genre
        case .developer: developer == other.developer
        case .publisher: publisher == other.publisher
        case .releaseYear: releaseYear == other.releaseYear
        case .barcode: barcode == other.barcode
        case .productCode: productCode == other.productCode
        case .quantity: quantity == other.quantity
        case .purchasePrice: purchasePrice.map(Self.money) == other.purchasePrice.map(Self.money)
        case .purchaseDate: purchaseDate == other.purchaseDate
        case .purchasePlace: purchasePlace == other.purchasePlace
        case .estimatedValue: estimatedValue.map(Self.money) == other.estimatedValue.map(Self.money)
        case .currency: currency == other.currency
        case .storageLocation: storageLocation == other.storageLocation
        case .rating: rating == other.rating
        case .favorite: favorite == other.favorite
        case .coverImageUrl: coverImageUrl == other.coverImageUrl
        case .notes: notes == other.notes
        }
    }
}

extension SaveGameRequest {
    /// A full copy of an existing game, e.g. as the starting point of an edit, or the body of a `CREATE`.
    ///
    /// Enum values unknown to this app version are kept as the API sent them (``Verbatim``), so a `CREATE`
    /// sends them unchanged; because edits are compared with a copy built the same way, untouched ones are
    /// never sent in a `PATCH`. Only a placeholder without the API's string (stored by an older app version)
    /// falls back to the API default (or `null` for optional fields), as that version sent it.
    init(game: Game) {
        self.init(
            title: game.title,
            platform: game.platform,
            edition: game.edition,
            genre: game.genre,
            developer: game.developer,
            publisher: game.publisher,
            releaseYear: game.releaseYear,
            barcode: game.barcode,
            productCode: game.productCode,
            quantity: game.quantity,
            purchasePrice: game.purchasePrice,
            purchaseDate: game.purchaseDate,
            purchasePlace: game.purchasePlace,
            estimatedValue: game.estimatedValue,
            currency: game.currency,
            storageLocation: game.storageLocation,
            rating: game.rating,
            favorite: game.favorite,
            coverImageUrl: game.coverImageUrl,
            notes: game.notes
        )
        $platform = game.$platform
        $status = game.$status.replacingPlaceholder(with: .owned)
        $format = game.$format.replacingPlaceholder(with: .physical)
        $region = game.$region
        $completeness = game.$completeness.replacingPlaceholder(with: nil)
        $condition = game.$condition.replacingPlaceholder(with: nil)
        $playStatus = game.$playStatus.replacingPlaceholder(with: nil)
    }
}

// MARK: - Bodies of the sync requests

/// Body of `POST games`: every field plus the id generated on the device (`CreateGameRequest`).
struct CreateGameRequest: Encodable, Sendable {
    var id: Game.ID
    var values: SaveGameRequest

    private enum CodingKeys: String, CodingKey {
        case id
    }

    func encode(to encoder: any Encoder) throws {
        try values.encode(to: encoder)
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
    }
}

/// Body of `PATCH games/{id}` (`UpdateGameRequest`): only `fields`, cleared optionals as explicit `null`.
struct UpdateGameRequest: Encodable, Sendable {
    var values: SaveGameRequest
    var fields: Set<GameField>

    func encode(to encoder: any Encoder) throws {
        try values.encode(fields, to: encoder)
    }
}

private extension KeyedEncodingContainer {
    /// Encodes `nil` as JSON `null` rather than omitting the key.
    mutating func encodeExplicitly<Value: Encodable>(_ value: Value?, forKey key: Key) throws {
        if let value {
            try encode(value, forKey: key)
        } else {
            try encodeNil(forKey: key)
        }
    }
}
