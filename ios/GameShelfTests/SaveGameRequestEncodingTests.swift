import Foundation
import Testing
@testable import GameShelf

@Suite("SaveGameRequest encoding")
struct SaveGameRequestEncodingTests {
    private func encode(_ request: SaveGameRequest) throws -> [String: Any] {
        try JSONEncoder.api().encode(request).jsonObject
    }

    private static let nullableKeys = [
        "region", "edition", "completeness", "condition", "playStatus", "genre", "developer", "publisher",
        "releaseYear", "barcode", "productCode", "purchasePrice", "purchaseDate", "purchasePlace",
        "estimatedValue", "storageLocation", "rating", "coverImageUrl", "notes",
    ]

    @Test func emptyOptionalsAreEncodedAsExplicitNull() throws {
        let json = try encode(SaveGameRequest(title: "Doom", platform: .pc))

        #expect(json.count == 26, "every contract field must be present")
        for key in Self.nullableKeys {
            #expect(json.keys.contains(key), "missing key \(key)")
            #expect(json[key] is NSNull, "\(key) should be null")
        }
        #expect(json["title"] as? String == "Doom")
        #expect(json["platform"] as? String == "PC")
        #expect(json["status"] as? String == "OWNED")
        #expect(json["format"] as? String == "PHYSICAL")
        #expect(json["quantity"] as? Int == 1)
        #expect(json["currency"] as? String == "CZK")
        #expect(json["favorite"] as? Bool == false)
    }

    @Test func rawJSONContainsNullLiterals() throws {
        let text = String(decoding: try JSONEncoder.api().encode(SaveGameRequest(title: "Doom", platform: .pc)), as: UTF8.self)
        #expect(text.contains("\"region\":null"))
        #expect(text.contains("\"purchaseDate\":null"))
    }

    @Test func filledValuesUseContractFormats() throws {
        var request = SaveGameRequest(title: "Zelda", platform: .n64)
        request.region = .ntscU
        request.purchaseDate = LocalDate("2024-05-17")
        request.purchasePrice = Decimal(string: "1299.9")
        request.estimatedValue = 2500
        request.releaseYear = 1998
        request.rating = 9
        request.status = .forSale
        request.playStatus = .abandoned

        let json = try encode(request)
        #expect(json["region"] as? String == "NTSC_U")
        #expect(json["purchaseDate"] as? String == "2024-05-17")
        #expect((json["purchasePrice"] as? NSNumber)?.decimalValue == Decimal(string: "1299.9"))
        #expect(json["estimatedValue"] as? Int == 2500)
        #expect(json["releaseYear"] as? Int == 1998)
        #expect(json["status"] as? String == "FOR_SALE")
        #expect(json["playStatus"] as? String == "ABANDONED")
    }

    @Test func moneyIsRoundedToTwoDecimals() throws {
        var request = SaveGameRequest(title: "Zelda", platform: .n64)
        request.purchasePrice = Decimal(string: "1299.900000000001")
        let text = String(decoding: try JSONEncoder.api().encode(request), as: UTF8.self)
        #expect(text.contains("\"purchasePrice\":1299.9"))
        #expect(!text.contains("1299.900000000001"))
    }

    @Test func copyOfGameKeepsEveryFieldAndDropsUnknownEnums() throws {
        var game = try JSONDecoder.api().decode(Game.self, from: Data(Fixtures.gameJSON.utf8))
        let copy = SaveGameRequest(game: game)
        #expect(copy.title == game.title)
        #expect(copy.barcode == game.barcode)
        #expect(copy.purchaseDate == game.purchaseDate)
        #expect(copy.quantity == 2)
        #expect(copy.favorite)

        game.status = .unknown
        game.condition = .unknown
        let sanitized = SaveGameRequest(game: game)
        #expect(sanitized.status == .owned)
        #expect(sanitized.condition == nil)
    }
}
