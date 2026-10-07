import Foundation

struct Game: Codable, Identifiable, Hashable, Sendable {
    typealias ID = String

    var id: ID
    var title: String
    var platform: Platform
    var status: CollectionStatus
    var format: GameFormat
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
    var quantity: Int
    var purchasePrice: Decimal?
    var purchaseDate: LocalDate?
    var purchasePlace: String?
    var estimatedValue: Decimal?
    var currency: String
    var storageLocation: String?
    var rating: Int?
    var favorite: Bool
    var coverImageUrl: String?
    var notes: String?
    var createdAt: Date
    var updatedAt: Date

    var coverURL: URL? {
        coverImageUrl.flatMap(URL.init(string:))
    }
}

/// Response of `GET games/changes`: one page of the change feed.
struct GameChanges: Decodable, Hashable, Sendable {
    /// Games created or changed since the cursor.
    var games: [Game]
    var deletedIds: [Game.ID]
    /// Opaque position to continue from.
    var cursor: String
    var hasMore: Bool
}

struct FacetValue: Hashable, Sendable {
    var value: String
    var count: Int
}

/// Distinct values of the collection with their counts (computed on the device).
struct GameFacets: Hashable, Sendable {
    var totalItems: Int
    var platforms: [FacetValue]
    var statuses: [FacetValue]
    var genres: [FacetValue]
    var publishers: [FacetValue]
    var developers: [FacetValue]
    var storageLocations: [FacetValue]
    var releaseYearMin: Int?
    var releaseYearMax: Int?

    /// Platforms present in the collection with their counts, most frequent first.
    var platformCounts: [(platform: Platform, count: Int)] {
        platforms.compactMap { facet in
            Platform(rawValue: facet.value).map { ($0, facet.count) }
        }
    }

    func count(of platform: Platform) -> Int? {
        platforms.first { $0.value == platform.rawValue }?.count
    }

    func count(of status: CollectionStatus) -> Int? {
        statuses.first { $0.value == status.rawValue }?.count
    }
}

// MARK: - Field-level changes

extension Game {
    /// A new game with the values of `request`.
    init(id: ID, values request: SaveGameRequest, createdAt: Date) {
        self.init(
            id: id, title: request.title, platform: request.platform, status: request.status, format: request.format,
            region: request.region, edition: request.edition, completeness: request.completeness,
            condition: request.condition, playStatus: request.playStatus, genre: request.genre,
            developer: request.developer, publisher: request.publisher, releaseYear: request.releaseYear,
            barcode: request.barcode, productCode: request.productCode, quantity: request.quantity,
            purchasePrice: request.purchasePrice.map(SaveGameRequest.money), purchaseDate: request.purchaseDate,
            purchasePlace: request.purchasePlace, estimatedValue: request.estimatedValue.map(SaveGameRequest.money),
            currency: request.currency, storageLocation: request.storageLocation, rating: request.rating,
            favorite: request.favorite, coverImageUrl: request.coverImageUrl, notes: request.notes,
            createdAt: createdAt, updatedAt: createdAt
        )
    }

    /// Sets only `fields` to the values of `request`; every other value (including enum
    /// values unknown to this app version) stays untouched.
    mutating func apply(_ fields: Set<GameField>, from request: SaveGameRequest) {
        for field in fields {
            switch field {
            case .title: title = request.title
            case .platform: platform = request.platform
            case .status: status = request.status
            case .format: format = request.format
            case .region: region = request.region
            case .edition: edition = request.edition
            case .completeness: completeness = request.completeness
            case .condition: condition = request.condition
            case .playStatus: playStatus = request.playStatus
            case .genre: genre = request.genre
            case .developer: developer = request.developer
            case .publisher: publisher = request.publisher
            case .releaseYear: releaseYear = request.releaseYear
            case .barcode: barcode = request.barcode
            case .productCode: productCode = request.productCode
            case .quantity: quantity = request.quantity
            case .purchasePrice: purchasePrice = request.purchasePrice.map(SaveGameRequest.money)
            case .purchaseDate: purchaseDate = request.purchaseDate
            case .purchasePlace: purchasePlace = request.purchasePlace
            case .estimatedValue: estimatedValue = request.estimatedValue.map(SaveGameRequest.money)
            case .currency: currency = request.currency
            case .storageLocation: storageLocation = request.storageLocation
            case .rating: rating = request.rating
            case .favorite: favorite = request.favorite
            case .coverImageUrl: coverImageUrl = request.coverImageUrl
            case .notes: notes = request.notes
            }
        }
    }

    /// This game (e.g. the server's version) with the values of `fields` taken from `local`,
    /// for fields whose local change has not been pushed yet.
    func merging(_ fields: Set<GameField>, from local: Game) -> Game {
        var merged = self
        for field in fields {
            switch field {
            case .title: merged.title = local.title
            case .platform: merged.platform = local.platform
            case .status: merged.status = local.status
            case .format: merged.format = local.format
            case .region: merged.region = local.region
            case .edition: merged.edition = local.edition
            case .completeness: merged.completeness = local.completeness
            case .condition: merged.condition = local.condition
            case .playStatus: merged.playStatus = local.playStatus
            case .genre: merged.genre = local.genre
            case .developer: merged.developer = local.developer
            case .publisher: merged.publisher = local.publisher
            case .releaseYear: merged.releaseYear = local.releaseYear
            case .barcode: merged.barcode = local.barcode
            case .productCode: merged.productCode = local.productCode
            case .quantity: merged.quantity = local.quantity
            case .purchasePrice: merged.purchasePrice = local.purchasePrice
            case .purchaseDate: merged.purchaseDate = local.purchaseDate
            case .purchasePlace: merged.purchasePlace = local.purchasePlace
            case .estimatedValue: merged.estimatedValue = local.estimatedValue
            case .currency: merged.currency = local.currency
            case .storageLocation: merged.storageLocation = local.storageLocation
            case .rating: merged.rating = local.rating
            case .favorite: merged.favorite = local.favorite
            case .coverImageUrl: merged.coverImageUrl = local.coverImageUrl
            case .notes: merged.notes = local.notes
            }
        }
        if !fields.isEmpty {
            // The local edit is newer than the server's last write until it is pushed.
            merged.updatedAt = max(updatedAt, local.updatedAt)
        }
        return merged
    }
}
