import Foundation
@testable import GameShelf

enum Fixtures {
    static let user = User(
        id: "01a1163a-395c-7270-9196-b4eda9d89b80",
        email: "collector@example.com",
        displayName: "Retro Rob",
        createdAt: Date(timeIntervalSince1970: 1_700_000_000)
    )

    static func authResponseJSON(access: String, refresh: String) -> Data {
        Data("""
        {
          "accessToken": "\(access)",
          "expiresIn": 900,
          "refreshToken": "\(refresh)",
          "user": {
            "id": "01a1163a-395c-7270-9196-b4eda9d89b80",
            "email": "collector@example.com",
            "displayName": null,
            "createdAt": "2026-10-07T11:57:56.956Z"
          }
        }
        """.utf8)
    }

    static func errorJSON(status: Int, code: String, details: [String]? = nil) -> Data {
        let detailsJSON = details.map { ", \"details\": [" + $0.map { "\"\($0)\"" }.joined(separator: ",") + "]" } ?? ""
        return Data("{\"statusCode\": \(status), \"code\": \"\(code)\", \"message\": \"Some English text\"\(detailsJSON)}".utf8)
    }

    /// A page of the change feed with one game and one deletion.
    static let changesJSON = Data("""
    {
      "games": [\(gameJSON)],
      "deletedIds": ["01a1163b-0cb1-75e9-bd4e-4a7feec69238"],
      "cursor": "1234",
      "hasMore": false
    }
    """.utf8)

    /// A full `Game` as the API returns it.
    static let gameJSON = """
    {
      "id": "01a1163b-0cb1-75e9-bd4e-4a7feec69237",
      "title": "The Legend of Zelda: Ocarina of Time",
      "platform": "N64",
      "status": "OWNED",
      "format": "PHYSICAL",
      "region": "PAL",
      "edition": "Collector's Edition",
      "completeness": "CIB",
      "condition": "VERY_GOOD",
      "playStatus": "COMPLETED",
      "genre": "Action-adventure",
      "developer": "Nintendo EAD",
      "publisher": "Nintendo",
      "releaseYear": 1998,
      "barcode": "045496870058",
      "productCode": "NUS-NZLP-EUR",
      "quantity": 2,
      "purchasePrice": 1299.9,
      "purchaseDate": "2024-05-17",
      "purchasePlace": "Retro Corner",
      "estimatedValue": 2500,
      "currency": "CZK",
      "storageLocation": "Shelf A, row 3",
      "rating": 10,
      "favorite": true,
      "coverImageUrl": "https://example.com/zelda.jpg",
      "notes": "Complete with map.",
      "createdAt": "2026-10-07T11:58:51.057Z",
      "updatedAt": "2026-10-07T11:58:51Z"
    }
    """

    /// The game of ``gameJSON``.
    static var decodedGame: Game {
        get throws { try JSONDecoder.api().decode(Game.self, from: Data(gameJSON.utf8)) }
    }

    /// ``gameJSON`` with a value this app version doesn't know in every enum field, as a newer API sends it.
    static let unknownValuesGameJSON = gameJSON
        .replacingOccurrences(of: "\"N64\"", with: "\"PLAYDATE\"")
        .replacingOccurrences(of: "\"OWNED\"", with: "\"BORROWED_FROM_LIBRARY\"")
        .replacingOccurrences(of: "\"PHYSICAL\"", with: "\"CLOUD\"")
        .replacingOccurrences(of: "\"PAL\"", with: "\"SECAM\"")
        .replacingOccurrences(of: "\"CIB\"", with: "\"DELUXE\"")
        .replacingOccurrences(of: "\"VERY_GOOD\"", with: "\"GRADED_9_8\"")
        .replacingOccurrences(of: "\"COMPLETED\"", with: "\"SPEEDRUN\"")

    /// The enum fields of ``unknownValuesGameJSON`` with the strings the API sent.
    static let unknownWireValues: [GameField: String] = [
        .platform: "PLAYDATE", .status: "BORROWED_FROM_LIBRARY", .format: "CLOUD", .region: "SECAM",
        .completeness: "DELUXE", .condition: "GRADED_9_8", .playStatus: "SPEEDRUN",
    ]

    /// The game of ``unknownValuesGameJSON``; `id` replaces its id.
    static func gameWithUnknownValues(id: Game.ID = UUID().uuidString.lowercased()) throws -> Game {
        var game = try JSONDecoder.api().decode(Game.self, from: Data(unknownValuesGameJSON.utf8))
        game.id = id
        return game
    }

    /// A game with defaults for everything but the title; `configure` sets what a test is about.
    static func game(
        _ title: String,
        _ platform: Platform = .pc,
        id: Game.ID = UUID().uuidString.lowercased(),
        configure: (inout Game) -> Void = { _ in }
    ) -> Game {
        var game = Game(
            id: id,
            values: SaveGameRequest(title: title, platform: platform),
            createdAt: Date(timeIntervalSince1970: 1_700_000_000)
        )
        configure(&game)
        return game
    }

    static func storedSession(access: String = "old-access", refresh: String = "old-refresh") -> StoredSession {
        StoredSession(accessToken: access, refreshToken: refresh, user: user)
    }
}

extension Data {
    var jsonObject: [String: Any] {
        (try? JSONSerialization.jsonObject(with: self)) as? [String: Any] ?? [:]
    }
}

extension SaveGameRequest {
    /// The strings the API sent for the enum values this app version doesn't know, by field.
    var unknownWireValues: [GameField: String] {
        let values: [GameField: String?] = [
            .platform: $platform.unknownWireValue, .status: $status.unknownWireValue, .format: $format.unknownWireValue,
            .region: $region.unknownWireValue, .completeness: $completeness.unknownWireValue,
            .condition: $condition.unknownWireValue, .playStatus: $playStatus.unknownWireValue,
        ]
        return values.compactMapValues(\.self)
    }
}

extension Game {
    /// The strings the API sent for the enum values this app version doesn't know, by field.
    var unknownWireValues: [GameField: String] {
        let values: [GameField: String?] = [
            .platform: $platform.unknownWireValue, .status: $status.unknownWireValue, .format: $format.unknownWireValue,
            .region: $region.unknownWireValue, .completeness: $completeness.unknownWireValue,
            .condition: $condition.unknownWireValue, .playStatus: $playStatus.unknownWireValue,
        ]
        return values.compactMapValues(\.self)
    }
}

/// Waits until `condition` holds, for at most `timeout`; tests then check it.
@MainActor
func waitUntil(timeout: Duration = .seconds(5), _ condition: () -> Bool) async {
    let deadline = ContinuousClock.now + timeout
    while !condition(), ContinuousClock.now < deadline {
        try? await Task.sleep(for: .milliseconds(10))
    }
}
