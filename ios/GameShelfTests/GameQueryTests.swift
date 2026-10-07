import Foundation
import Testing
@testable import GameShelf

/// The collection of `api/test/games.e2e-spec.ts` ("listing").
private enum Collection {
    static let zelda = Fixtures.game("The Legend of Zelda: Ocarina of Time", .n64) {
        $0.region = .pal
        $0.completeness = .cib
        $0.condition = .veryGood
        $0.genre = "Action-adventure"
        $0.developer = "Nintendo EAD"
        $0.publisher = "Nintendo"
        $0.releaseYear = 1998
        $0.productCode = "NUS-NZLP-EUR"
        $0.purchasePrice = Decimal(string: "1299.9")
        $0.purchaseDate = LocalDate("2024-05-17")
        $0.estimatedValue = 2500
        $0.storageLocation = "Shelf A"
        $0.rating = 10
        $0.favorite = true
        $0.coverImageUrl = "https://example.com/zelda.jpg"
    }
    static let granTurismo = Fixtures.game("Gran Turismo 3: A-Spec", .ps2) {
        $0.genre = "Racing"
        $0.publisher = "Sony Computer Entertainment"
        $0.releaseYear = 2001
        $0.purchasePrice = 150
        $0.purchaseDate = LocalDate("2023-01-10")
        $0.completeness = .loose
        $0.rating = 7
    }
    static let dizzy = Fixtures.game("Dizzy: Prince of the Yolkfolk", .zxSpectrum) {
        $0.status = .wishlist
        $0.genre = "platformer"
        $0.publisher = "Codemasters"
        $0.releaseYear = 1991
        $0.notes = "Looking for the original cassette"
    }
    static let mario = Fixtures.game("Super Mario 64", .n64) {
        $0.genre = "Platformer"
        $0.publisher = "Nintendo"
        $0.releaseYear = 1996
        $0.estimatedValue = 900
        $0.favorite = true
        $0.format = .digital
    }

    static let games = [zelda, granTurismo, dizzy, mario]
}

private func titles(_ query: GameListQuery, in games: [Game] = Collection.games) -> [String] {
    query.results(in: games).map(\.title)
}

private func filtered(search: String = "", _ configure: (inout GameFilter) -> Void = { _ in }) -> GameListQuery {
    var query = GameListQuery(search: search)
    configure(&query.filter)
    return query
}

@Suite("Local list query (same semantics as GET games)")
struct GameQueryTests {
    @Test func sortsByTitleByDefault() {
        #expect(titles(GameListQuery()) == [
            "Dizzy: Prince of the Yolkfolk",
            "Gran Turismo 3: A-Spec",
            "Super Mario 64",
            "The Legend of Zelda: Ocarina of Time",
        ])
    }

    @Test func putsMissingValuesLastRegardlessOfDirection() {
        #expect(titles(GameListQuery(sort: .purchasePrice, order: .desc)) == [
            "The Legend of Zelda: Ocarina of Time",
            "Gran Turismo 3: A-Spec",
            "Dizzy: Prince of the Yolkfolk",
            "Super Mario 64",
        ])
        #expect(titles(GameListQuery(sort: .purchasePrice, order: .asc)) == [
            "Gran Turismo 3: A-Spec",
            "The Legend of Zelda: Ocarina of Time",
            "Dizzy: Prince of the Yolkfolk",
            "Super Mario 64",
        ])
        #expect(titles(GameListQuery(sort: .releaseYear, order: .asc)) == [
            "Dizzy: Prince of the Yolkfolk",
            "Super Mario 64",
            "The Legend of Zelda: Ocarina of Time",
            "Gran Turismo 3: A-Spec",
        ])
    }

    @Test func platformSortsInEnumOrderThenByTitle() {
        #expect(titles(GameListQuery(sort: .platform, order: .asc)) == [
            "Gran Turismo 3: A-Spec",
            "Super Mario 64",
            "The Legend of Zelda: Ocarina of Time",
            "Dizzy: Prince of the Yolkfolk",
        ])
        // Ties stay in title order in both directions.
        #expect(titles(GameListQuery(sort: .platform, order: .desc)) == [
            "Dizzy: Prince of the Yolkfolk",
            "Super Mario 64",
            "The Legend of Zelda: Ocarina of Time",
            "Gran Turismo 3: A-Spec",
        ])
    }

    @Test func titlesCompareCaseInsensitivelyAndNumerically() {
        let games = ["game 10", "Game 2", "alpha", "Beta"].map { Fixtures.game($0) }
        #expect(titles(GameListQuery(), in: games) == ["alpha", "Beta", "Game 2", "game 10"])
        #expect(titles(GameListQuery(order: .desc), in: games) == ["game 10", "Game 2", "Beta", "alpha"])
    }

    @Test func equalValuesAreOrderedByTitleThenId() {
        let games = [
            Fixtures.game("Same", id: "b"),
            Fixtures.game("Same", id: "a"),
            Fixtures.game("Other", id: "c"),
        ]
        #expect(GameListQuery(order: .desc).results(in: games).map(\.id) == ["a", "b", "c"])
        #expect(GameListQuery(sort: .createdAt, order: .desc).results(in: games).map(\.id) == ["c", "a", "b"])
    }

    private static let filterCases: [(query: GameListQuery, titles: [String])] = [
        (filtered(search: "zelda ocarina"), ["The Legend of Zelda: Ocarina of Time"]),
        (filtered(search: "NUS-NZLP"), ["The Legend of Zelda: Ocarina of Time"]),
        (filtered(search: "cassette"), ["Dizzy: Prince of the Yolkfolk"]),
        (filtered(search: "  ZELDA  "), ["The Legend of Zelda: Ocarina of Time"]),
        (filtered(search: "mario zelda"), []),
        (filtered { $0.platforms = [.n64] }, ["Super Mario 64", "The Legend of Zelda: Ocarina of Time"]),
        (filtered { $0.platforms = [.ps2, .zxSpectrum] }, ["Dizzy: Prince of the Yolkfolk", "Gran Turismo 3: A-Spec"]),
        (filtered { $0.statuses = [.wishlist] }, ["Dizzy: Prince of the Yolkfolk"]),
        (filtered { $0.formats = [.digital] }, ["Super Mario 64"]),
        (filtered { $0.completeness = [.cib, .loose] }, ["Gran Turismo 3: A-Spec", "The Legend of Zelda: Ocarina of Time"]),
        (filtered { $0.regions = [.pal] }, ["The Legend of Zelda: Ocarina of Time"]),
        (filtered { $0.genres = ["PLATFORMER"] }, ["Dizzy: Prince of the Yolkfolk", "Super Mario 64"]),
        (filtered { $0.publisher = "nintendo" }, ["Super Mario 64", "The Legend of Zelda: Ocarina of Time"]),
        (filtered { $0.developer = " EAD " }, ["The Legend of Zelda: Ocarina of Time"]),
        (filtered { $0.storageLocation = "shelf" }, ["The Legend of Zelda: Ocarina of Time"]),
        (filtered { $0.favoritesOnly = true }, ["Super Mario 64", "The Legend of Zelda: Ocarina of Time"]),
        (filtered { $0.cover = .withCover }, ["The Legend of Zelda: Ocarina of Time"]),
        (filtered { $0.cover = .withoutCover }, ["Dizzy: Prince of the Yolkfolk", "Gran Turismo 3: A-Spec", "Super Mario 64"]),
        (filtered { $0.releaseYearFrom = 1995; $0.releaseYearTo = 1999 }, ["Super Mario 64", "The Legend of Zelda: Ocarina of Time"]),
        (filtered { $0.purchaseDateFrom = LocalDate("2024-01-01") }, ["The Legend of Zelda: Ocarina of Time"]),
        (filtered { $0.purchaseDateTo = LocalDate("2023-01-10") }, ["Gran Turismo 3: A-Spec"]),
        (filtered { $0.purchasePriceMin = 100; $0.purchasePriceMax = 200 }, ["Gran Turismo 3: A-Spec"]),
        (filtered { $0.purchasePriceMin = Decimal(string: "1299.9"); $0.purchasePriceMax = Decimal(string: "1299.9") }, ["The Legend of Zelda: Ocarina of Time"]),
        (filtered { $0.estimatedValueMin = 1000 }, ["The Legend of Zelda: Ocarina of Time"]),
        (filtered { $0.estimatedValueMax = 1000 }, ["Super Mario 64"]),
        (filtered { $0.ratingMin = 8 }, ["The Legend of Zelda: Ocarina of Time"]),
        (filtered(search: "mario") { $0.platforms = [.n64]; $0.favoritesOnly = true }, ["Super Mario 64"]),
    ]

    @Test(arguments: filterCases)
    func filters(query: GameListQuery, expected: [String]) {
        #expect(titles(query) == expected)
    }

    @Test func chipsMatchActiveCountAndCanBeRemoved() {
        var filter = GameFilter()
        filter.platforms = [.ps2, .snes]
        filter.genres = ["RPG"]
        filter.releaseYearFrom = 1990
        filter.favoritesOnly = true
        #expect(filter.activeCount == 5)
        #expect(filter.chips.map(\.label).contains("Release year: from 1990"))

        filter.remove(.platform(.ps2))
        filter.remove(.releaseYear)
        filter.remove(.favorite)
        #expect(filter.platforms == [.snes])
        #expect(filter.releaseYearFrom == nil)
        #expect(filter.activeCount == 2)

        filter.remove(.platform(.snes))
        filter.remove(.genre("RPG"))
        #expect(filter.isEmpty)
    }
}

@Suite("Local facets (same semantics as GET games/facets)")
struct GameFacetsTests {
    @Test func facetsOfTheCollection() {
        let facets = GameFacets(games: Collection.games)
        #expect(facets == GameFacets(
            totalItems: 4,
            platforms: [.init(value: "N64", count: 2), .init(value: "PS2", count: 1), .init(value: "ZX_SPECTRUM", count: 1)],
            statuses: [.init(value: "OWNED", count: 3), .init(value: "WISHLIST", count: 1)],
            genres: [.init(value: "platformer", count: 2), .init(value: "Action-adventure", count: 1), .init(value: "Racing", count: 1)],
            publishers: [
                .init(value: "Nintendo", count: 2), .init(value: "Codemasters", count: 1),
                .init(value: "Sony Computer Entertainment", count: 1),
            ],
            developers: [.init(value: "Nintendo EAD", count: 1)],
            storageLocations: [.init(value: "Shelf A", count: 1)],
            releaseYearMin: 1991,
            releaseYearMax: 2001
        ))
        #expect(facets.count(of: .n64) == 2)
        #expect(facets.count(of: .owned) == 3)
        #expect(facets.platformCounts.map(\.platform) == [.n64, .ps2, .zxSpectrum])
    }

    @Test func caseVariantsAreOneFacetWithTheMostCommonSpelling() {
        let genres = ["rpg", "RPG", "RPG", "RPG", "Racing", "Racing"]
        let facets = GameFacets(games: genres.map { genre in Fixtures.game("Game") { $0.genre = genre } })
        #expect(facets.genres == [.init(value: "RPG", count: 4), .init(value: "Racing", count: 2)])
    }

    @Test func emptyCollection() {
        let facets = GameFacets(games: [Game]())
        #expect(facets.totalItems == 0)
        #expect(facets.platforms.isEmpty)
        #expect(facets.releaseYearMin == nil)
        #expect(facets.releaseYearMax == nil)
    }
}
