import Foundation

/// `GameSearchResponse` from the API contract: games of the game database matching a title.
struct GameSearchResponse: Decodable, Hashable, Sendable {
    /// At most 20 games, the most likely first; empty when nothing matches.
    var items: [GameSearchResult]
    /// Databases the games come from, shown as attribution.
    var sources: [String]
}

/// `GameSearchResult` from the API contract: a game the form can be filled in from.
struct GameSearchResult: Decodable, Hashable, Identifiable, Sendable {
    var igdbId: Int
    var title: String
    /// Platforms the game came out on, in the order of the Platform enum; can be empty.
    var platforms: [Platform]
    var genre: String?
    var developer: String?
    var publisher: String?
    var releaseYear: Int?
    var coverImageUrl: String?

    var id: Int { igdbId }

    var coverURL: URL? {
        coverImageUrl.flatMap(URL.init(string:))
    }
}

extension GameSearchResult {
    private enum CodingKeys: String, CodingKey {
        case igdbId, title, platforms, genre, developer, publisher, releaseYear, coverImageUrl
    }

    /// Platforms this app version doesn't know (decoded as `OTHER`, which the API never lists) are dropped.
    init(from decoder: any Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        igdbId = try container.decode(Int.self, forKey: .igdbId)
        title = try container.decode(String.self, forKey: .title)
        platforms = try container.decode([Platform].self, forKey: .platforms).filter { $0 != .other }
        genre = try container.decodeIfPresent(String.self, forKey: .genre)
        developer = try container.decodeIfPresent(String.self, forKey: .developer)
        publisher = try container.decodeIfPresent(String.self, forKey: .publisher)
        releaseYear = try container.decodeIfPresent(Int.self, forKey: .releaseYear)
        coverImageUrl = try container.decodeIfPresent(String.self, forKey: .coverImageUrl)
    }
}
