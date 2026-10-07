import Foundation
import Testing
@testable import GameShelf

/// Records what the stub server saw.
private actor RequestLog {
    private(set) var refreshBodies: [String] = []
    private(set) var authorizations: [String] = []

    var refreshCount: Int { refreshBodies.count }

    func recordRefresh(_ body: String) { refreshBodies.append(body) }
    func recordAuthorization(_ header: String) { authorizations.append(header) }
}

@Suite("Token refresh")
struct TokenRefreshTests {
    private struct Harness {
        let client: APIClient
        let tokens: TokenManager
        let storage: InMemorySessionStorage
        let log: RequestLog

        var games: RemoteGameService { RemoteGameService(api: client) }
    }

    /// API stub: `games/facets` accepts only `validToken`; `auth/refresh` answers via `refreshResponse`.
    private func makeHarness(
        validToken: String = "new-access",
        refreshDelay: Duration = .milliseconds(150),
        refreshResponse: @escaping @Sendable () -> (Int, Data) = { (200, Fixtures.authResponseJSON(access: "new-access", refresh: "new-refresh")) }
    ) -> Harness {
        let log = RequestLog()
        let host = uniqueTestHost()
        let session = StubURLProtocol.session(host: host) { request in
            let path = request.url?.path ?? ""
            switch path {
            case "/api/v1/auth/refresh":
                await log.recordRefresh(String(decoding: request.httpBody ?? Data(), as: UTF8.self))
                try await Task.sleep(for: refreshDelay)
                return refreshResponse()
            case "/api/v1/auth/login":
                return (401, Fixtures.errorJSON(status: 401, code: "INVALID_CREDENTIALS"))
            case "/api/v1/games/facets":
                let authorization = request.value(forHTTPHeaderField: "Authorization") ?? ""
                await log.recordAuthorization(authorization)
                return authorization == "Bearer \(validToken)"
                    ? (200, Fixtures.facetsJSON)
                    : (401, Fixtures.errorJSON(status: 401, code: "UNAUTHORIZED"))
            default:
                return (404, Fixtures.errorJSON(status: 404, code: "NOT_FOUND"))
            }
        }
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        let client = APIClient(baseURL: URL(string: "https://\(host)/api/v1/")!, tokens: tokens, session: session)
        return Harness(client: client, tokens: tokens, storage: storage, log: log)
    }

    @Test func concurrentUnauthorizedRequestsShareOneRefresh() async throws {
        let harness = makeHarness()
        let games = harness.games

        let results = try await withThrowingTaskGroup(of: GameFacets.self) { group in
            for _ in 0..<6 {
                group.addTask { try await games.facets() }
            }
            return try await group.reduce(into: [GameFacets]()) { $0.append($1) }
        }

        #expect(results.count == 6)
        #expect(results.allSatisfy { $0.totalItems == 3 })
        #expect(await harness.log.refreshCount == 1)
        #expect(await harness.log.refreshBodies.first?.contains("\"refreshToken\":\"old-refresh\"") == true)
        #expect(harness.storage.load()?.accessToken == "new-access")
        #expect(harness.storage.load()?.refreshToken == "new-refresh")
        #expect(await harness.tokens.accessToken == "new-access")
    }

    @Test func requestAfterRefreshUsesNewTokenWithoutRefreshingAgain() async throws {
        let harness = makeHarness(refreshDelay: .zero)
        _ = try await harness.games.facets()
        _ = try await harness.games.facets()

        #expect(await harness.log.refreshCount == 1)
        #expect(await harness.log.authorizations == ["Bearer old-access", "Bearer new-access", "Bearer new-access"])
    }

    @Test func rejectedRefreshClearsSessionAndNotifies() async throws {
        let harness = makeHarness(refreshDelay: .zero) {
            (401, Fixtures.errorJSON(status: 401, code: "INVALID_REFRESH_TOKEN"))
        }
        let expirations = Task {
            var iterator = harness.tokens.sessionExpirations.makeAsyncIterator()
            return await iterator.next() != nil
        }

        await #expect(throws: APIError.sessionExpired) {
            _ = try await harness.games.facets()
        }
        #expect(harness.storage.load() == nil)
        #expect(await harness.tokens.accessToken == nil)
        #expect(await expirations.value)

        // Without a session, further calls fail fast without hitting the network.
        await #expect(throws: APIError.sessionExpired) {
            _ = try await harness.games.facets()
        }
        #expect(await harness.log.refreshCount == 1)
    }

    @Test func networkFailureDuringRefreshKeepsSession() async throws {
        let harness = makeHarness(refreshDelay: .zero) {
            (503, Data("Service Unavailable".utf8))
        }
        await #expect(throws: APIError.self) {
            _ = try await harness.games.facets()
        }
        #expect(harness.storage.load()?.refreshToken == "old-refresh")
    }

    @Test func retriedRequestIsNotRetriedTwice() async throws {
        // The server keeps rejecting even the refreshed token: give up and end the session.
        let harness = makeHarness(validToken: "never-valid", refreshDelay: .zero)
        await #expect(throws: APIError.sessionExpired) {
            _ = try await harness.games.facets()
        }
        #expect(await harness.log.refreshCount == 1)
        #expect(await harness.log.authorizations.count == 2)
        #expect(harness.storage.load() == nil)
    }

    @Test func unauthenticatedEndpointDoesNotTriggerRefresh() async throws {
        let harness = makeHarness()
        let auth = RemoteAuthService(api: harness.client)
        await #expect(throws: APIError.server(statusCode: 401, code: .invalidCredentials, details: [])) {
            _ = try await auth.login(email: "a@b.cz", password: "wrong-password")
        }
        #expect(await harness.log.refreshCount == 0)
        #expect(harness.storage.load() != nil)
    }
}
