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

struct GamePage: Codable, Hashable, Sendable {
    var items: [Game]
    var page: Int
    var pageSize: Int
    var totalItems: Int
    var totalPages: Int
}

struct FacetValue: Codable, Hashable, Sendable {
    var value: String
    var count: Int
}

struct GameFacets: Codable, Hashable, Sendable {
    var totalItems: Int
    var platforms: [FacetValue]
    var statuses: [FacetValue]
    var genres: [FacetValue]
    var publishers: [FacetValue]
    var developers: [FacetValue]
    var storageLocations: [FacetValue]
    var releaseYearMin: Int?
    var releaseYearMax: Int?

    static let empty = GameFacets(
        totalItems: 0, platforms: [], statuses: [], genres: [], publishers: [],
        developers: [], storageLocations: [], releaseYearMin: nil, releaseYearMax: nil
    )

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
