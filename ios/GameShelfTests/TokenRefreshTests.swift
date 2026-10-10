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

/// `POST auth/refresh` whose answers the test gives, one call at a time.
private actor RefreshServer {
    private(set) var sentTokens: [String] = []
    private var answers: [Int: CheckedContinuation<Result<AuthResponse, any Error>, Never>] = [:]
    private var arrivalWaiters: [(count: Int, continuation: CheckedContinuation<Void, Never>)] = []

    func refresh(_ refreshToken: String) async throws -> AuthResponse {
        let call = sentTokens.count
        sentTokens.append(refreshToken)
        let ready = arrivalWaiters.filter { $0.count <= sentTokens.count }
        arrivalWaiters.removeAll { $0.count <= sentTokens.count }
        ready.forEach { $0.continuation.resume() }
        // Like a request that has reached the server, it is answered even when its task is cancelled.
        let result = await withCheckedContinuation { answers[call] = $0 }
        return try result.get()
    }

    /// Returns once `count` refreshes have been sent.
    func waitForRefreshes(_ count: Int) async {
        guard sentTokens.count < count else { return }
        await withCheckedContinuation { arrivalWaiters.append((count, $0)) }
    }

    func answer(_ call: Int, with result: Result<AuthResponse, any Error>) {
        answers.removeValue(forKey: call)?.resume(returning: result)
    }

    func answer(_ call: Int, access: String, refresh: String) throws {
        answer(call, with: .success(try JSONDecoder.api().decode(AuthResponse.self, from: Fixtures.authResponseJSON(access: access, refresh: refresh))))
    }
}

@Suite("Token refresh")
struct TokenRefreshTests {
    private struct Harness {
        let client: APIClient
        let tokens: TokenManager
        let storage: InMemorySessionStorage
        let log: RequestLog

        var games: RemoteGameAPI { RemoteGameAPI(api: client) }
    }

    /// API stub: `games/changes` accepts only `validToken`; `auth/refresh` answers via `refreshResponse`.
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
            case "/api/v1/games/changes":
                let authorization = request.value(forHTTPHeaderField: "Authorization") ?? ""
                await log.recordAuthorization(authorization)
                return authorization == "Bearer \(validToken)"
                    ? (200, Fixtures.changesJSON)
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

        let results = try await withThrowingTaskGroup(of: GameChanges.self) { group in
            for _ in 0..<6 {
                group.addTask { try await games.changes(after: nil, limit: 500) }
            }
            return try await group.reduce(into: [GameChanges]()) { $0.append($1) }
        }

        #expect(results.count == 6)
        #expect(results.allSatisfy { $0.cursor == "1234" })
        #expect(await harness.log.refreshCount == 1)
        #expect(await harness.log.refreshBodies.first?.contains("\"refreshToken\":\"old-refresh\"") == true)
        #expect(harness.storage.load()?.accessToken == "new-access")
        #expect(harness.storage.load()?.refreshToken == "new-refresh")
        #expect(await harness.tokens.accessToken == "new-access")
    }

    @Test func requestAfterRefreshUsesNewTokenWithoutRefreshingAgain() async throws {
        let harness = makeHarness(refreshDelay: .zero)
        _ = try await harness.games.changes(after: nil, limit: 500)
        _ = try await harness.games.changes(after: nil, limit: 500)

        #expect(await harness.log.refreshCount == 1)
        #expect(await harness.log.authorizations == ["Bearer old-access", "Bearer new-access", "Bearer new-access"])
    }

    @Test func rejectedRefreshClearsSessionAndNotifies() async throws {
        let harness = makeHarness(refreshDelay: .zero) {
            (401, Fixtures.errorJSON(status: 401, code: "INVALID_REFRESH_TOKEN"))
        }
        let expirations = Task {
            var iterator = harness.tokens.sessionEnds.makeAsyncIterator()
            return await iterator.next() == .expired
        }

        await #expect(throws: APIError.sessionExpired) {
            _ = try await harness.games.changes(after: nil, limit: 500)
        }
        #expect(harness.storage.load() == nil)
        #expect(await harness.tokens.accessToken == nil)
        #expect(await expirations.value)

        // Without a session, further calls fail fast without hitting the network.
        await #expect(throws: APIError.sessionExpired) {
            _ = try await harness.games.changes(after: nil, limit: 500)
        }
        #expect(await harness.log.refreshCount == 1)
    }

    @Test func networkFailureDuringRefreshKeepsSession() async throws {
        let harness = makeHarness(refreshDelay: .zero) {
            (503, Data("Service Unavailable".utf8))
        }
        await #expect(throws: APIError.self) {
            _ = try await harness.games.changes(after: nil, limit: 500)
        }
        #expect(harness.storage.load()?.refreshToken == "old-refresh")
    }

    @Test(arguments: [408, 429])
    func aRefreshAnsweredWithATimeoutOrTooManyRequestsKeepsTheSession(status: Int) async throws {
        let harness = makeHarness(refreshDelay: .zero) {
            (status, Fixtures.errorJSON(status: status, code: status == 429 ? "TOO_MANY_REQUESTS" : "REQUEST_TIMEOUT"))
        }
        await #expect(throws: APIError.self) {
            _ = try await harness.games.changes(after: nil, limit: 500)
        }
        #expect(harness.storage.load()?.refreshToken == "old-refresh")
        #expect(await harness.tokens.accessToken == "old-access")
    }

    @Test func retriedRequestIsNotRetriedTwice() async throws {
        // The server keeps rejecting even the refreshed token: give up and end the session.
        let harness = makeHarness(validToken: "never-valid", refreshDelay: .zero)
        await #expect(throws: APIError.sessionExpired) {
            _ = try await harness.games.changes(after: nil, limit: 500)
        }
        #expect(await harness.log.refreshCount == 1)
        #expect(await harness.log.authorizations.count == 2)
        #expect(harness.storage.load() == nil)
    }

    // MARK: Session changes during a refresh

    private static let rejectedRefresh = APIError.server(statusCode: 401, code: .invalidRefreshToken, details: [])

    private func authResponse(access: String, refresh: String) throws -> AuthResponse {
        try JSONDecoder.api().decode(AuthResponse.self, from: Fixtures.authResponseJSON(access: access, refresh: refresh))
    }

    /// A request whose `old-access` was rejected: it refreshes the session through `server`.
    private func startRefresh(
        _ tokens: TokenManager,
        server: RefreshServer
    ) -> Task<String, any Error> {
        Task { try await tokens.accessToken(replacing: "old-access") { try await server.refresh($0) } }
    }

    @Test func signOutDuringARefreshIgnoresItsNewTokens() async throws {
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        let server = RefreshServer()
        let caller = startRefresh(tokens, server: server)
        await server.waitForRefreshes(1)

        try await tokens.clear()
        try await server.answer(0, access: "late-access", refresh: "late-refresh")

        await #expect(throws: CancellationError.self) { try await caller.value }
        #expect(await tokens.accessToken == nil)
        #expect(storage.load() == nil)
    }

    @Test func signInDuringARefreshKeepsTheNewSession() async throws {
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        let server = RefreshServer()
        let caller = startRefresh(tokens, server: server)
        await server.waitForRefreshes(1)

        try await tokens.begin(authResponse(access: "new-session-access", refresh: "new-session-refresh"))
        try await server.answer(0, access: "late-access", refresh: "late-refresh")

        await #expect(throws: CancellationError.self) { try await caller.value }
        #expect(await tokens.accessToken == "new-session-access")
        #expect(storage.load()?.refreshToken == "new-session-refresh")
    }

    @Test func aRejectionOfTheOldSessionsRefreshDoesNotEndTheNewSession() async throws {
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        let server = RefreshServer()
        let caller = startRefresh(tokens, server: server)
        await server.waitForRefreshes(1)

        try await tokens.begin(authResponse(access: "new-session-access", refresh: "new-session-refresh"))
        await server.answer(0, with: .failure(Self.rejectedRefresh))

        await #expect(throws: CancellationError.self) { try await caller.value }
        #expect(await tokens.accessToken == "new-session-access")
        #expect(storage.load()?.refreshToken == "new-session-refresh")
    }

    @Test func anOldRefreshFinishingLateDoesNotLetTheNewSessionRefreshTwice() async throws {
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        let server = RefreshServer()
        let old = startRefresh(tokens, server: server)
        await server.waitForRefreshes(1)

        // A new session starts while the old one's refresh is in flight, and its token expires too.
        try await tokens.begin(authResponse(access: "s2-access", refresh: "s2-refresh"))
        let first = Task { try await tokens.accessToken(replacing: "s2-access") { try await server.refresh($0) } }
        await server.waitForRefreshes(2)

        // The old refresh ends now; it must not forget the new session's refresh in flight.
        await server.answer(0, with: .failure(Self.rejectedRefresh))
        await #expect(throws: CancellationError.self) { try await old.value }
        let second = Task { try await tokens.accessToken(replacing: "s2-access") { try await server.refresh($0) } }
        // Let the second caller reach the token manager before the refresh is answered.
        try await Task.sleep(for: .milliseconds(100))

        try await server.answer(1, access: "s3-access", refresh: "s3-refresh")
        #expect(try await first.value == "s3-access")
        #expect(try await second.value == "s3-access")
        #expect(await server.sentTokens == ["old-refresh", "s2-refresh"])
        #expect(storage.load()?.refreshToken == "s3-refresh")
    }

    // MARK: Another user signed in meanwhile

    /// An access token shaped like the API's (only the payload matters here).
    private static func jwt(user: String, nonce: Int) -> String {
        let payload = Data(#"{"sub":"\#(user)","n":\#(nonce)}"#.utf8).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        return "eyJhbGciOiJIUzI1NiJ9.\(payload).signature"
    }

    @Test func aRequestOfAReplacedUserIsNotRepeatedWithTheNewUsersToken() async throws {
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        try await tokens.begin(authResponse(access: Self.jwt(user: "u2", nonce: 1), refresh: "u2-refresh"))

        await #expect(throws: CancellationError.self) {
            _ = try await tokens.accessToken(replacing: Self.jwt(user: "u1", nonce: 1)) { _ in
                Issue.record("must not refresh")
                throw CancellationError()
            }
        }
        #expect(await tokens.accessToken == Self.jwt(user: "u2", nonce: 1))
    }

    @Test func aRequestOfTheSameUserGetsTheTokenRenewedMeanwhile() async throws {
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        try await tokens.begin(authResponse(access: Self.jwt(user: "u1", nonce: 2), refresh: "u1-refresh"))

        let token = try await tokens.accessToken(replacing: Self.jwt(user: "u1", nonce: 1)) { _ in
            Issue.record("must not refresh")
            throw CancellationError()
        }
        #expect(token == Self.jwt(user: "u1", nonce: 2))
    }

    @Test func jwtSubjectReadsTheSubClaim() {
        #expect(TokenManager.jwtSubject(Self.jwt(user: "u1", nonce: 1)) == "u1")
        #expect(TokenManager.jwtSubject("opaque-token") == nil)
        #expect(TokenManager.jwtSubject("a.%%%.c") == nil)
    }

    @Test func aRejectedRetryDoesNotEndASessionThatWasRenewedMeanwhile() async throws {
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        #expect(await !tokens.expire(rejecting: "some-older-access"))
        #expect(await tokens.accessToken == "old-access")

        #expect(await tokens.expire(rejecting: "old-access"))
        #expect(await tokens.accessToken == nil)
        #expect(storage.load() == nil)
    }

    @Test func aRejectedRetryDuringAPasswordChangeIsAFailureOfThatRequestOnly() async throws {
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        let host = uniqueTestHost()
        let session = StubURLProtocol.session(host: host) { request in
            if request.url?.path == "/api/v1/auth/refresh" {
                return (200, Fixtures.authResponseJSON(access: "new-access", refresh: "new-refresh"))
            }
            // The password change in flight has revoked every token.
            return (401, Fixtures.errorJSON(status: 401, code: "UNAUTHORIZED"))
        }
        let games = RemoteGameAPI(api: APIClient(baseURL: URL(string: "https://\(host)/api/v1/")!, tokens: tokens, session: session))
        let passwordChange = RefreshServer()
        let change = Task { try await tokens.changePassword { try await passwordChange.refresh("password") } }
        await passwordChange.waitForRefreshes(1)

        // The end of the session is deferred, so the request fails as itself – not as an expired session.
        await #expect(throws: APIError.server(statusCode: 401, code: .unauthorized, details: [])) {
            _ = try await games.changes(after: nil, limit: 500)
        }
        #expect(await tokens.accessToken == "new-access")

        try await passwordChange.answer(0, access: "changed-access", refresh: "changed-refresh")
        _ = try await change.value
        #expect(await tokens.accessToken == "changed-access")
        #expect(storage.load()?.refreshToken == "changed-refresh")
    }

    @Test func aRejectedRetryOfASessionReplacedMeanwhileIsAFailureOfThatRequestOnly() async throws {
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        let newest = try authResponse(access: "newest-access", refresh: "newest-refresh")
        let host = uniqueTestHost()
        let session = StubURLProtocol.session(host: host) { request in
            if request.url?.path == "/api/v1/auth/refresh" {
                return (200, Fixtures.authResponseJSON(access: "new-access", refresh: "new-refresh"))
            }
            if request.value(forHTTPHeaderField: "Authorization") == "Bearer new-access" {
                // Another sign-in replaces the session while the retry is on its way.
                try await tokens.begin(newest)
            }
            return (401, Fixtures.errorJSON(status: 401, code: "UNAUTHORIZED"))
        }
        let games = RemoteGameAPI(api: APIClient(baseURL: URL(string: "https://\(host)/api/v1/")!, tokens: tokens, session: session))

        await #expect(throws: APIError.server(statusCode: 401, code: .unauthorized, details: [])) {
            _ = try await games.changes(after: nil, limit: 500)
        }
        #expect(await tokens.accessToken == "newest-access")
        #expect(storage.load()?.refreshToken == "newest-refresh")
    }

    // MARK: Deleted account

    /// API stub that answers `games/changes` sent with `deletedToken` with `401 ACCOUNT_DELETED` and every
    /// other access token with `401 UNAUTHORIZED`; `auth/refresh` renews the pair to `new-access`.
    private func makeDeletedAccountClient(deletedToken: String) -> (RemoteGameAPI, TokenManager, InMemorySessionStorage, RequestLog) {
        let log = RequestLog()
        let host = uniqueTestHost()
        let session = StubURLProtocol.session(host: host) { request in
            if request.url?.path == "/api/v1/auth/refresh" {
                await log.recordRefresh("")
                return (200, Fixtures.authResponseJSON(access: "new-access", refresh: "new-refresh"))
            }
            let authorization = request.value(forHTTPHeaderField: "Authorization") ?? ""
            await log.recordAuthorization(authorization)
            return authorization == "Bearer \(deletedToken)"
                ? (401, Fixtures.errorJSON(status: 401, code: "ACCOUNT_DELETED"))
                : (401, Fixtures.errorJSON(status: 401, code: "UNAUTHORIZED"))
        }
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        let client = APIClient(baseURL: URL(string: "https://\(host)/api/v1/")!, tokens: tokens, session: session)
        return (RemoteGameAPI(api: client), tokens, storage, log)
    }

    private static let accountDeleted = APIError.server(statusCode: 401, code: .accountDeleted, details: [])

    @Test func aDeletedAccountIsNeverRefreshedAndEndsTheSession() async throws {
        let (games, tokens, storage, log) = makeDeletedAccountClient(deletedToken: "old-access")
        let ends = Task {
            var iterator = tokens.sessionEnds.makeAsyncIterator()
            return await iterator.next()
        }

        await #expect(throws: Self.accountDeleted) {
            _ = try await games.changes(after: nil, limit: 500)
        }
        #expect(await log.refreshCount == 0)
        #expect(await ends.value == .accountDeleted(accessToken: "old-access"))
        // The session store clears it, once it has marked the wipe of the local data.
        #expect(storage.load() != nil)
    }

    @Test func aRetryAnsweredWithADeletedAccountEndsTheSession() async throws {
        let (games, tokens, _, log) = makeDeletedAccountClient(deletedToken: "new-access")
        let ends = Task {
            var iterator = tokens.sessionEnds.makeAsyncIterator()
            return await iterator.next()
        }

        await #expect(throws: Self.accountDeleted) {
            _ = try await games.changes(after: nil, limit: 500)
        }
        #expect(await log.refreshCount == 1)
        #expect(await log.authorizations == ["Bearer old-access", "Bearer new-access"])
        #expect(await ends.value == .accountDeleted(accessToken: "new-access"))
    }

    @Test func aDeletedAccountReportedForAReplacedTokenIsIgnored() async throws {
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        let ends = Task {
            var iterator = tokens.sessionEnds.makeAsyncIterator()
            return await iterator.next()
        }
        await tokens.accountWasDeleted(rejecting: "some-older-access")
        await tokens.accountWasDeleted(rejecting: "old-access")
        #expect(await ends.value == .accountDeleted(accessToken: "old-access"))
    }

    @Test func aRefreshAnsweredWithoutAnAPIErrorBodyKeepsTheSession() async throws {
        let harness = makeHarness(refreshDelay: .zero) {
            (401, Data("<html>Proxy authentication required</html>".utf8))
        }
        await #expect(throws: APIError.http(statusCode: 401)) {
            _ = try await harness.games.changes(after: nil, limit: 500)
        }
        #expect(harness.storage.load()?.refreshToken == "old-refresh")
    }

    @Test func a401WithoutAnAPIErrorBodyNeitherRefreshesNorEndsTheSession() async throws {
        let log = RequestLog()
        let host = uniqueTestHost()
        let session = StubURLProtocol.session(host: host) { request in
            if request.url?.path == "/api/v1/auth/refresh" {
                await log.recordRefresh("")
            }
            return (401, Data("<html>Unauthorized</html>".utf8))
        }
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        let games = RemoteGameAPI(api: APIClient(baseURL: URL(string: "https://\(host)/api/v1/")!, tokens: tokens, session: session))

        await #expect(throws: APIError.http(statusCode: 401)) {
            _ = try await games.changes(after: nil, limit: 500)
        }
        #expect(await log.refreshCount == 0)
        #expect(storage.load()?.refreshToken == "old-refresh")
    }

    @Test func a401WithoutAnAPIErrorBodyAfterTheRefreshDoesNotEndTheSession() async throws {
        let host = uniqueTestHost()
        let session = StubURLProtocol.session(host: host) { request in
            if request.url?.path == "/api/v1/auth/refresh" {
                return (200, Fixtures.authResponseJSON(access: "new-access", refresh: "new-refresh"))
            }
            // The API rejects the old token; a proxy answers the retry.
            return request.value(forHTTPHeaderField: "Authorization") == "Bearer old-access"
                ? (401, Fixtures.errorJSON(status: 401, code: "UNAUTHORIZED"))
                : (401, Data("<html>Proxy authentication required</html>".utf8))
        }
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        let games = RemoteGameAPI(api: APIClient(baseURL: URL(string: "https://\(host)/api/v1/")!, tokens: tokens, session: session))

        await #expect(throws: APIError.http(statusCode: 401)) {
            _ = try await games.changes(after: nil, limit: 500)
        }
        #expect(await tokens.accessToken == "new-access")
        #expect(storage.load()?.refreshToken == "new-refresh")
    }

    // MARK: Storing the session

    @Test func aRenewedPairThatCantBeStoredEndsTheSessionAsExpired() async throws {
        let harness = makeHarness(refreshDelay: .zero)
        let expirations = Task {
            var iterator = harness.tokens.sessionEnds.makeAsyncIterator()
            return await iterator.next() == .expired
        }
        harness.storage.setSavingFails(true)

        await #expect(throws: APIError.sessionExpired) {
            _ = try await harness.games.changes(after: nil, limit: 500)
        }
        #expect(await harness.log.refreshCount == 1)
        #expect(await harness.tokens.accessToken == nil)
        #expect(await expirations.value)
        // The new pair was never used.
        #expect(await harness.log.authorizations == ["Bearer old-access"])
    }

    @Test func aSessionThatCantBeStoredIsNotBegun() async throws {
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        storage.setSavingFails(true)

        await #expect(throws: CocoaError.self) {
            try await tokens.begin(authResponse(access: "new-access", refresh: "new-refresh"))
        }
        #expect(await tokens.accessToken == "old-access")
        #expect(storage.load()?.refreshToken == "old-refresh")
    }

    // MARK: Password change

    @Test func aRefreshRejectedDuringAPasswordChangeDoesNotEndTheSession() async throws {
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        let passwordChange = RefreshServer()
        let change = Task { try await tokens.changePassword { try await passwordChange.refresh("password") } }
        await passwordChange.waitForRefreshes(1)

        // A sync's refresh is rejected: the change in flight revoked the refresh token.
        let server = RefreshServer()
        let sync = startRefresh(tokens, server: server)
        await server.waitForRefreshes(1)
        await server.answer(0, with: .failure(Self.rejectedRefresh))
        await #expect(throws: APIError.sessionExpired) { try await sync.value }
        #expect(await tokens.accessToken == "old-access")
        #expect(storage.load() != nil)

        try await passwordChange.answer(0, access: "changed-access", refresh: "changed-refresh")
        #expect(try await change.value.refreshToken == "changed-refresh")
        #expect(await tokens.accessToken == "changed-access")
        #expect(storage.load()?.refreshToken == "changed-refresh")
    }

    @Test func aRefreshRejectedDuringAFailedPasswordChangeEndsTheSession() async throws {
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = TokenManager(storage: storage, session: storage.load())
        let expirations = Task {
            var iterator = tokens.sessionEnds.makeAsyncIterator()
            return await iterator.next() == .expired
        }
        let passwordChange = RefreshServer()
        let change = Task { try await tokens.changePassword { try await passwordChange.refresh("password") } }
        await passwordChange.waitForRefreshes(1)
        let server = RefreshServer()
        let sync = startRefresh(tokens, server: server)
        await server.waitForRefreshes(1)
        await server.answer(0, with: .failure(Self.rejectedRefresh))
        await #expect(throws: APIError.sessionExpired) { try await sync.value }

        await passwordChange.answer(0, with: .failure(APIError.network(.timedOut)))
        await #expect(throws: APIError.network(.timedOut)) { try await change.value }
        #expect(await tokens.accessToken == nil)
        #expect(storage.load() == nil)
        #expect(await expirations.value)
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
