import Foundation
import Testing
@testable import GameShelf

/// Records requests seen by the stub server.
private actor Recorder {
    private(set) var requests: [URLRequest] = []

    func record(_ request: URLRequest) {
        requests.append(request)
    }
}

@Suite("Game API requests")
struct GameAPIRequestTests {
    private func makeAPI(
        handler: @escaping @Sendable (URLRequest) async throws -> (Int, Data)
    ) -> (RemoteGameAPI, Recorder, APIClient) {
        let recorder = Recorder()
        let host = uniqueTestHost()
        let session = StubURLProtocol.session(host: host) { request in
            await recorder.record(request)
            return try await handler(request)
        }
        let storage = InMemorySessionStorage(Fixtures.storedSession(access: "token"))
        let client = APIClient(
            baseURL: URL(string: "https://\(host)/api/v1/")!,
            tokens: TokenManager(storage: storage, session: storage.load()),
            session: session
        )
        return (RemoteGameAPI(api: client), recorder, client)
    }

    @Test(arguments: [(201, true), (200, false)])
    func createTellsCreatedFromAlreadyExisting(status: Int, created: Bool) async throws {
        let (api, recorder, _) = makeAPI { _ in (status, Data(Fixtures.gameJSON.utf8)) }
        let id = "01a1163b-0cb1-75e9-bd4e-4a7feec69237"

        let result = try await api.create(id: id, SaveGameRequest(title: "Zelda", platform: .n64))

        let game = try Fixtures.decodedGame
        #expect(result == (created ? .created(game) : .alreadyExisted(game)))
        let request = try #require(await recorder.requests.first)
        #expect(request.httpMethod == "POST")
        #expect(request.url?.path == "/api/v1/games")
        let body = (request.httpBody ?? Data()).jsonObject
        #expect(body["id"] as? String == id)
        #expect(body["title"] as? String == "Zelda")
    }

    @Test func updateSendsAPatchWithTheChangedFieldsOnly() async throws {
        let (api, recorder, _) = makeAPI { _ in (200, Data(Fixtures.gameJSON.utf8)) }
        var values = SaveGameRequest(title: "Zelda", platform: .n64)
        values.rating = 9

        _ = try await api.update(id: "abc", fields: [.rating, .notes], from: values)

        let request = try #require(await recorder.requests.first)
        #expect(request.httpMethod == "PATCH")
        #expect(request.url?.path == "/api/v1/games/abc")
        #expect(request.value(forHTTPHeaderField: "Content-Type") == "application/json")
        #expect(String(decoding: request.httpBody ?? Data(), as: UTF8.self) == #"{"notes":null,"rating":9}"#)
    }

    @Test func deleteSendsDelete() async throws {
        let (api, recorder, _) = makeAPI { _ in (204, Data()) }
        try await api.delete(id: "abc")
        let request = try #require(await recorder.requests.first)
        #expect(request.httpMethod == "DELETE")
        #expect(request.url?.path == "/api/v1/games/abc")
    }

    @Test func changesRequestEncodesTheCursorAndLimit() throws {
        let (_, _, client) = makeAPI { _ in (200, Fixtures.changesJSON) }
        let first = try client.makeRequest(for: .gameChanges(cursor: nil, limit: 500), accessToken: "token")
        #expect(first.url?.absoluteString.hasSuffix("/api/v1/games/changes?limit=500") == true)

        let next = try client.makeRequest(for: .gameChanges(cursor: "a+b/c", limit: 500), accessToken: "token")
        let url = try #require(next.url?.absoluteString)
        #expect(url.contains("cursor=a%2Bb/c"))
        #expect(url.contains("limit=500"))
        #expect(next.value(forHTTPHeaderField: "Authorization") == "Bearer token")
        #expect(next.httpMethod == "GET")
    }

    @Test(arguments: [
        (404, "<html><body>Not Found</body></html>", APIError.http(statusCode: 404)),
        (403, "Forbidden by the firewall", .http(statusCode: 403)),
        (502, "", .http(statusCode: 502)),
        (404, #"{"statusCode":404,"error":"Not Found"}"#, .http(statusCode: 404)),
        (404, #"{"statusCode":404,"code":"GAME_NOT_FOUND","message":"Game not found"}"#, .server(statusCode: 404, code: .gameNotFound, details: [])),
        (418, #"{"code":"BRAND_NEW_CODE"}"#, .server(statusCode: 418, code: .unknown, details: [])),
        (400, #"{"statusCode":400,"code":"VALIDATION_FAILED","message":"Bad","details":["title is too long"]}"#,
         .server(statusCode: 400, code: .validationFailed, details: ["title is too long"])),
    ])
    func onlyResponsesWithAnAPIErrorBodyAreServerErrors(status: Int, body: String, expected: APIError) async {
        let (api, _, _) = makeAPI { _ in (status, Data(body.utf8)) }
        await #expect(throws: expected) {
            try await api.delete(id: "abc")
        }
    }

    @Test func resetRequiredIsRecognized() async throws {
        let (api, _, _) = makeAPI { _ in (410, Fixtures.errorJSON(status: 410, code: "SYNC_RESET_REQUIRED")) }
        await #expect(throws: APIError.server(statusCode: 410, code: .syncResetRequired, details: [])) {
            _ = try await api.changes(after: "1", limit: 500)
        }
    }
}
