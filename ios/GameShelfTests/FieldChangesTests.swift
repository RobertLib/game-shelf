import Foundation
import Testing
@testable import GameShelf

@Suite("Field-level changes and sync request bodies")
struct FieldChangesTests {
    private func json(_ value: some Encodable) throws -> [String: Any] {
        try JSONEncoder.api().encode(value).jsonObject
    }

    private func text(_ value: some Encodable) throws -> String {
        String(decoding: try JSONEncoder.api().encode(value), as: UTF8.self)
    }

    // MARK: Diff

    @Test func unchangedCopyHasNoChangedFields() throws {
        let game = try Fixtures.decodedGame
        #expect(SaveGameRequest(game: game).changedFields(comparedTo: SaveGameRequest(game: game)).isEmpty)
    }

    @Test func changedAndClearedFieldsAreDetected() throws {
        let original = SaveGameRequest(game: try Fixtures.decodedGame)
        var edited = original
        edited.title = "Majora's Mask"
        edited.region = nil
        edited.purchaseDate = nil
        edited.favorite = false
        #expect(edited.changedFields(comparedTo: original) == [.title, .region, .purchaseDate, .favorite])
    }

    @Test func moneyIsComparedAtTwoDecimals() throws {
        var original = SaveGameRequest(title: "Zelda", platform: .n64)
        original.purchasePrice = Decimal(string: "1299.900000000001")
        var edited = original
        edited.purchasePrice = Decimal(string: "1299.90")
        #expect(edited.changedFields(comparedTo: original).isEmpty)
        edited.purchasePrice = Decimal(string: "1299.91")
        #expect(edited.changedFields(comparedTo: original) == [.purchasePrice])
    }

    @Test func untouchedUnknownEnumValuesAreNeitherSentNorLost() throws {
        var game = try Fixtures.decodedGame
        game.status = .unknown
        game.condition = .unknown
        // The form starts from the same sanitized copy, so the unknown values don't count as changes.
        var edited = SaveGameRequest(game: game)
        edited.notes = "Edited"
        let fields = edited.changedFields(comparedTo: SaveGameRequest(game: game))
        #expect(fields == [.notes])

        game.apply(fields, from: edited)
        #expect(game.notes == "Edited")
        #expect(game.status == .unknown)
        #expect(game.condition == .unknown)
    }

    @Test func mergingTakesOnlyTheGivenFieldsFromTheLocalGame() throws {
        let server = try Fixtures.decodedGame
        var local = server
        local.title = "Local title"
        local.notes = "Local notes"
        local.updatedAt = server.updatedAt.addingTimeInterval(60)

        let merged = server.merging([.title], from: local)
        #expect(merged.title == "Local title")
        #expect(merged.notes == server.notes)
        #expect(merged.updatedAt == local.updatedAt)
        #expect(server.merging([], from: local) == server)
    }

    // MARK: Bodies

    @Test func patchBodyContainsOnlyTheChangedFields() throws {
        var values = SaveGameRequest(game: try Fixtures.decodedGame)
        values.notes = nil
        values.rating = 7
        let body = try json(UpdateGameRequest(values: values, fields: [.notes, .rating]))
        #expect(Set(body.keys) == ["notes", "rating"])
        #expect(body["notes"] is NSNull, "a cleared field is sent as explicit null")
        #expect(body["rating"] as? Int == 7)
        #expect(try text(UpdateGameRequest(values: values, fields: [.notes])) == #"{"notes":null}"#)
    }

    @Test func patchBodyKeepsDecimalsExact() throws {
        var values = SaveGameRequest(title: "Zelda", platform: .n64)
        values.purchasePrice = Decimal(string: "1299.99")
        values.estimatedValue = Decimal(string: "12345678901.01")
        let body = try text(UpdateGameRequest(values: values, fields: [.purchasePrice, .estimatedValue]))
        #expect(body == #"{"estimatedValue":12345678901.01,"purchasePrice":1299.99}"#)
    }

    @Test func emptyPatchBodyIsAnEmptyObject() throws {
        let values = SaveGameRequest(title: "Zelda", platform: .n64)
        #expect(try text(UpdateGameRequest(values: values, fields: [])) == "{}")
    }

    @Test func createBodyHasTheIdAndEveryField() throws {
        let id = "01a1163b-0cb1-75e9-bd4e-4a7feec69237"
        let body = try json(CreateGameRequest(id: id, values: SaveGameRequest(title: "Doom", platform: .pc)))
        #expect(body.count == 27)
        #expect(body["id"] as? String == id)
        #expect(body["title"] as? String == "Doom")
        #expect(body["region"] is NSNull)
        #expect(Set(body.keys) == Set(GameField.allCases.map(\.rawValue) + ["id"]))
    }

    @Test func newGameTakesTheRequestValues() {
        var request = SaveGameRequest(title: "Doom", platform: .pc)
        request.purchasePrice = Decimal(string: "19.999")
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        let game = Game(id: "id", values: request, createdAt: now)
        #expect(game.title == "Doom")
        #expect(game.purchasePrice == Decimal(string: "20"))
        #expect(game.createdAt == now)
        #expect(game.updatedAt == now)
        #expect(SaveGameRequest(game: game).changedFields(comparedTo: request).isEmpty)
    }
}
