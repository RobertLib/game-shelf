import Foundation
import Testing
@testable import GameShelf

@Suite("JSON decoding")
struct GameDecodingTests {
    private let decoder = JSONDecoder.api()

    private func decodeGame(_ json: String) throws -> Game {
        try decoder.decode(Game.self, from: Data(json.utf8))
    }

    @Test func decodesFullGame() throws {
        let game = try decodeGame(Fixtures.gameJSON)
        #expect(game.id == "01a1163b-0cb1-75e9-bd4e-4a7feec69237")
        #expect(game.platform == .n64)
        #expect(game.status == .owned)
        #expect(game.format == .physical)
        #expect(game.region == .pal)
        #expect(game.completeness == .cib)
        #expect(game.condition == .veryGood)
        #expect(game.playStatus == .completed)
        #expect(game.releaseYear == 1998)
        #expect(game.quantity == 2)
        #expect(game.purchasePrice == Decimal(string: "1299.9"))
        #expect(game.estimatedValue == 2500)
        #expect(game.purchaseDate == LocalDate(year: 2024, month: 5, day: 17))
        #expect(game.favorite)
        #expect(game.coverURL == URL(string: "https://example.com/zelda.jpg"))
    }

    @Test func decodesTimestampsWithAndWithoutFractionalSeconds() throws {
        let game = try decodeGame(Fixtures.gameJSON)
        let expectedCreated = Date(timeIntervalSince1970: 1_791_374_331.057)
        #expect(abs(game.createdAt.timeIntervalSince(expectedCreated)) < 0.001)
        #expect(game.updatedAt == Date(timeIntervalSince1970: 1_791_374_331))
    }

    @Test func decodesNullsAsNil() throws {
        let json = """
        {
          "id": "1", "title": "Chrono Trigger", "platform": "SNES", "status": "WISHLIST", "format": "PHYSICAL",
          "region": null, "edition": null, "completeness": null, "condition": null, "playStatus": null,
          "genre": null, "developer": null, "publisher": null, "releaseYear": null, "barcode": null,
          "productCode": null, "quantity": 1, "purchasePrice": null, "purchaseDate": null, "purchasePlace": null,
          "estimatedValue": null, "currency": "CZK", "storageLocation": null, "rating": null, "favorite": false,
          "coverImageUrl": null, "notes": null,
          "createdAt": "2026-10-07T11:58:51.057Z", "updatedAt": "2026-10-07T11:58:51.057Z"
        }
        """
        let game = try decodeGame(json)
        #expect(game.region == nil)
        #expect(game.completeness == nil)
        #expect(game.playStatus == nil)
        #expect(game.releaseYear == nil)
        #expect(game.purchasePrice == nil)
        #expect(game.purchaseDate == nil)
        #expect(game.rating == nil)
        #expect(game.coverURL == nil)
        #expect(game.status == .wishlist)
    }

    @Test func unknownEnumValuesFallBackInsteadOfFailing() throws {
        let json = Fixtures.gameJSON
            .replacingOccurrences(of: "\"N64\"", with: "\"PLAYDATE\"")
            .replacingOccurrences(of: "\"OWNED\"", with: "\"BORROWED_FROM_LIBRARY\"")
            .replacingOccurrences(of: "\"PHYSICAL\"", with: "\"CLOUD\"")
            .replacingOccurrences(of: "\"PAL\"", with: "\"SECAM\"")
            .replacingOccurrences(of: "\"CIB\"", with: "\"DELUXE\"")
            .replacingOccurrences(of: "\"VERY_GOOD\"", with: "\"GRADED_9_8\"")
            .replacingOccurrences(of: "\"COMPLETED\"", with: "\"SPEEDRUN\"")
        let game = try decodeGame(json)
        #expect(game.platform == .other)
        #expect(game.status == .unknown)
        #expect(game.format == .unknown)
        #expect(game.region == .other)
        #expect(game.completeness == .unknown)
        #expect(game.condition == .unknown)
        #expect(game.playStatus == .unknown)
        #expect(!game.status.isKnown)
        #expect(!CollectionStatus.allCases.contains(.unknown))
    }

    @Test func decodesChangesFeedPage() throws {
        let page = try decoder.decode(GameChanges.self, from: Fixtures.changesJSON)
        #expect(page.games.map(\.title) == ["The Legend of Zelda: Ocarina of Time"])
        #expect(page.deletedIds == ["01a1163b-0cb1-75e9-bd4e-4a7feec69238"])
        #expect(page.cursor == "1234")
        #expect(!page.hasMore)
    }

    @Test func storedGamesRoundTripThroughTheAPICoders() throws {
        let game = try decodeGame(Fixtures.gameJSON)
        let stored = try decoder.decode(Game.self, from: JSONEncoder.api().encode(game))
        #expect(stored == game)
    }

    @Test func decodesAuthResponseWithNullDisplayName() throws {
        let response = try decoder.decode(AuthResponse.self, from: Fixtures.authResponseJSON(access: "a", refresh: "r"))
        #expect(response.accessToken == "a")
        #expect(response.expiresIn == 900)
        #expect(response.user.displayName == nil)
    }

    @Test func rejectsMalformedPurchaseDate() {
        let json = Fixtures.gameJSON.replacingOccurrences(of: "\"2024-05-17\"", with: "\"17.5.2024\"")
        #expect(throws: DecodingError.self) { try decodeGame(json) }
    }

    @Test(arguments: ["2024-02-30", "2024-13-01", "24-05-17", "2024-5-17", "2024-05-17T00:00:00Z", ""])
    func localDateRejectsInvalidStrings(value: String) {
        #expect(LocalDate(value) == nil)
    }

    @Test func localDateRoundTrip() {
        let date = LocalDate("2024-02-29")
        #expect(date?.description == "2024-02-29")
        #expect(date.map { LocalDate($0.date()) } == date)
        #expect(LocalDate("2023-12-31")! < LocalDate("2024-01-01")!)
    }
}
