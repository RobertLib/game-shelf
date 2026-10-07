import Foundation

/// Body of `POST games` and `PUT games/{id}`.
///
/// `PUT` is a full replacement, so every field is always encoded and empty
/// optionals are sent as explicit JSON `null`.
struct SaveGameRequest: Encodable, Hashable, Sendable {
    var title: String
    var platform: Platform
    var status: CollectionStatus = .owned
    var format: GameFormat = .physical
    var region: Region?
    var edition: String?
    var completeness: Completeness?
    var condition: Condition?
    var playStatus: PlayStatus?
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

    private enum CodingKeys: String, CodingKey {
        case title, platform, status, format, region, edition, completeness, condition, playStatus
        case genre, developer, publisher, releaseYear, barcode, productCode, quantity
        case purchasePrice, purchaseDate, purchasePlace, estimatedValue, currency
        case storageLocation, rating, favorite, coverImageUrl, notes
    }

    func encode(to encoder: any Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(title, forKey: .title)
        try container.encode(platform, forKey: .platform)
        try container.encode(status, forKey: .status)
        try container.encode(format, forKey: .format)
        try container.encodeExplicitly(region, forKey: .region)
        try container.encodeExplicitly(edition, forKey: .edition)
        try container.encodeExplicitly(completeness, forKey: .completeness)
        try container.encodeExplicitly(condition, forKey: .condition)
        try container.encodeExplicitly(playStatus, forKey: .playStatus)
        try container.encodeExplicitly(genre, forKey: .genre)
        try container.encodeExplicitly(developer, forKey: .developer)
        try container.encodeExplicitly(publisher, forKey: .publisher)
        try container.encodeExplicitly(releaseYear, forKey: .releaseYear)
        try container.encodeExplicitly(barcode, forKey: .barcode)
        try container.encodeExplicitly(productCode, forKey: .productCode)
        try container.encode(quantity, forKey: .quantity)
        try container.encodeExplicitly(purchasePrice.map(Self.money), forKey: .purchasePrice)
        try container.encodeExplicitly(purchaseDate, forKey: .purchaseDate)
        try container.encodeExplicitly(purchasePlace, forKey: .purchasePlace)
        try container.encodeExplicitly(estimatedValue.map(Self.money), forKey: .estimatedValue)
        try container.encode(currency, forKey: .currency)
        try container.encodeExplicitly(storageLocation, forKey: .storageLocation)
        try container.encodeExplicitly(rating, forKey: .rating)
        try container.encode(favorite, forKey: .favorite)
        try container.encodeExplicitly(coverImageUrl, forKey: .coverImageUrl)
        try container.encodeExplicitly(notes, forKey: .notes)
    }

    /// The API accepts at most two decimal places; this also guards against
    /// binary floating point noise from decoding.
    private static func money(_ value: Decimal) -> Decimal {
        value.rounded(scale: 2)
    }
}

extension SaveGameRequest {
    /// A full copy of an existing game, e.g. for toggling a single field via `PUT`.
    ///
    /// Enum values unknown to this app version cannot be sent back, so they fall
    /// back to the API defaults (or `null` for optional fields).
    init(game: Game) {
        self.init(
            title: game.title,
            platform: game.platform,
            status: game.status.isKnown ? game.status : .owned,
            format: game.format.isKnown ? game.format : .physical,
            region: game.region,
            edition: game.edition,
            completeness: game.completeness.flatMap { $0.isKnown ? $0 : nil },
            condition: game.condition.flatMap { $0.isKnown ? $0 : nil },
            playStatus: game.playStatus.flatMap { $0.isKnown ? $0 : nil },
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
