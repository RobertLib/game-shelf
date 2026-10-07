import Foundation
import Testing
@testable import GameShelf

@Suite("Filter → query parameters")
struct GameListQueryTests {
    private func pairs(_ items: [URLQueryItem]) -> [String] {
        items.map { "\($0.name)=\($0.value ?? "")" }
    }

    @Test func defaultQueryHasOnlySortAndPaging() {
        let items = GameListQuery().queryItems(page: 1)
        #expect(pairs(items) == ["sort=title", "order=asc", "page=1", "pageSize=25"])
    }

    @Test func searchIsTrimmedAndBlankSearchIsOmitted() {
        #expect(pairs(GameListQuery(search: "  zelda ocarina ").queryItems(page: 2)).first == "q=zelda ocarina")
        #expect(!pairs(GameListQuery(search: "   ").queryItems(page: 1)).contains { $0.hasPrefix("q=") })
    }

    @Test func multiValueFiltersAreRepeatedInDeclarationOrder() {
        var filter = GameFilter()
        filter.platforms = [.ps5, .ps2, .n64]
        filter.statuses = [.wishlist, .owned]
        filter.formats = [.digital]
        filter.regions = [.ntscJ]
        filter.completeness = [.loose, .cib]
        filter.conditions = [.mint]
        filter.playStatuses = [.playing]
        filter.genres = ["RPG", "Action"]

        #expect(pairs(filter.queryItems) == [
            "platform=PS2", "platform=PS5", "platform=N64",
            "status=OWNED", "status=WISHLIST",
            "format=DIGITAL",
            "region=NTSC_J",
            "completeness=CIB", "completeness=LOOSE",
            "condition=MINT",
            "playStatus=PLAYING",
            "genre=Action", "genre=RPG",
        ])
    }

    @Test func scalarFiltersUseContractNamesAndFormats() {
        var filter = GameFilter()
        filter.publisher = " Nintendo "
        filter.developer = "Square"
        filter.storageLocation = "Shelf A"
        filter.favoritesOnly = true
        filter.cover = .withoutCover
        filter.releaseYearFrom = 1990
        filter.releaseYearTo = 1999
        filter.purchaseDateFrom = LocalDate("2024-01-01")
        filter.purchaseDateTo = LocalDate("2024-12-31")
        filter.purchasePriceMin = Decimal(string: "100.5")
        filter.purchasePriceMax = 2000
        filter.estimatedValueMin = 0
        filter.estimatedValueMax = Decimal(string: "9999.99")
        filter.ratingMin = 7

        #expect(pairs(filter.queryItems) == [
            "publisher=Nintendo",
            "developer=Square",
            "storageLocation=Shelf A",
            "favorite=true",
            "hasCover=false",
            "releaseYearFrom=1990",
            "releaseYearTo=1999",
            "purchaseDateFrom=2024-01-01",
            "purchaseDateTo=2024-12-31",
            "purchasePriceMin=100.5",
            "purchasePriceMax=2000",
            "estimatedValueMin=0",
            "estimatedValueMax=9999.99",
            "ratingMin=7",
        ])
    }

    @Test(arguments: [
        (GameFilter.CoverFilter.any, nil as String?),
        (.withCover, "true"),
        (.withoutCover, "false"),
    ])
    func coverFilter(cover: GameFilter.CoverFilter, expected: String?) {
        var filter = GameFilter()
        filter.cover = cover
        #expect(filter.queryItems.first { $0.name == "hasCover" }?.value == expected)
    }

    @Test func favoriteFalseIsNotSent() {
        #expect(GameFilter().queryItems.isEmpty)
    }

    @Test func fullQueryCombinesSearchFilterSortAndPaging() {
        var query = GameListQuery(search: "mario", sort: .estimatedValue, order: .desc)
        query.filter.platforms = [.snes]
        #expect(pairs(query.queryItems(page: 3, pageSize: 25)) == [
            "q=mario", "platform=SNES", "sort=estimatedValue", "order=desc", "page=3", "pageSize=25",
        ])
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

    @Test func requestURLEncodesRepeatedParametersAndPlusSign() throws {
        let tokens = TokenManager(storage: InMemorySessionStorage(), session: nil)
        let client = APIClient(baseURL: URL(string: "http://localhost:3000/api/v1/")!, tokens: tokens)
        var query = GameListQuery(search: "c++ guide")
        query.filter.platforms = [.ps2, .ps5]
        let request = try client.makeRequest(for: .games(query, page: 1), accessToken: "token")

        let url = try #require(request.url?.absoluteString)
        #expect(url.hasPrefix("http://localhost:3000/api/v1/games?"))
        #expect(url.contains("q=c%2B%2B%20guide"))
        #expect(url.contains("platform=PS2&platform=PS5"))
        #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer token")
        #expect(request.httpMethod == "GET")
    }
}
