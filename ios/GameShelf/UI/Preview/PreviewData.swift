#if DEBUG
import Foundation

/// Sample data for SwiftUI previews.
enum PreviewData {
    static let user = User(
        id: "01a1163a-395c-7270-9196-b4eda9d89b80",
        email: "collector@example.com",
        displayName: "Retro Rob",
        createdAt: Date(timeIntervalSince1970: 1_715_000_000)
    )

    static let games: [Game] = [
        game(
            "The Legend of Zelda: Ocarina of Time", .n64, region: .pal, completeness: .cib, condition: .veryGood,
            playStatus: .completed, genre: "Action-adventure", developer: "Nintendo EAD", publisher: "Nintendo",
            releaseYear: 1998, purchasePrice: Decimal(string: "1299.9"), purchaseDate: LocalDate("2024-05-17"),
            estimatedValue: 2500, storageLocation: "Shelf A, row 3", rating: 10, favorite: true,
            notes: "Complete with map and manual."
        ),
        game(
            "Final Fantasy VII", .ps1, status: .owned, region: .pal, completeness: .gameAndBox, condition: .good,
            genre: "RPG", developer: "Square", publisher: "Sony", releaseYear: 1997, estimatedValue: 1500, rating: 9
        ),
        game("Chrono Trigger", .snes, status: .wishlist, region: .ntscU, genre: "RPG", releaseYear: 1995, estimatedValue: 4500),
        game("Super Mario World", .snes, region: .pal, completeness: .loose, releaseYear: 1990, currency: "EUR", estimatedValue: 45),
        game("Elden Ring", .ps5, format: .digital, playStatus: .playing, genre: "RPG", releaseYear: 2022, purchasePrice: 1599),
        game("Doom", .pc, status: .forSale, completeness: .boxOnly, condition: .poor, releaseYear: 1993),
    ]

    static let facets = GameFacets(games: games)

    @MainActor
    static func sessionStore(signedIn: Bool) -> SessionStore {
        let session = signedIn ? StoredSession(accessToken: "preview", refreshToken: "preview", user: user) : nil
        let storage = InMemorySessionStorage(session)
        return SessionStore(
            auth: PreviewAuthService(),
            tokens: TokenManager(storage: storage, session: session),
            restoredSession: session
        )
    }

    private static func game(
        _ title: String,
        _ platform: Platform,
        status: CollectionStatus = .owned,
        format: GameFormat = .physical,
        region: Region? = nil,
        completeness: Completeness? = nil,
        condition: Condition? = nil,
        playStatus: PlayStatus? = nil,
        genre: String? = nil,
        developer: String? = nil,
        publisher: String? = nil,
        releaseYear: Int? = nil,
        purchasePrice: Decimal? = nil,
        purchaseDate: LocalDate? = nil,
        currency: String = "CZK",
        estimatedValue: Decimal? = nil,
        storageLocation: String? = nil,
        rating: Int? = nil,
        favorite: Bool = false,
        notes: String? = nil
    ) -> Game {
        Game(
            id: UUID().uuidString.lowercased(), title: title, platform: platform, status: status, format: format,
            region: region, edition: nil, completeness: completeness, condition: condition, playStatus: playStatus,
            genre: genre, developer: developer, publisher: publisher, releaseYear: releaseYear, barcode: nil,
            productCode: nil, quantity: 1, purchasePrice: purchasePrice, purchaseDate: purchaseDate, purchasePlace: nil,
            estimatedValue: estimatedValue, currency: currency, storageLocation: storageLocation, rating: rating,
            favorite: favorite, coverImageUrl: nil, notes: notes, createdAt: .now, updatedAt: .now
        )
    }
}
#endif
