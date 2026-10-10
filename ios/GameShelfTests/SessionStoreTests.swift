import Foundation
import Testing
@testable import GameShelf

/// Records the calls of one kind, e.g. the refresh tokens revoked with `POST auth/logout`.
private actor CallLog {
    private(set) var calls: [String] = []

    func record(_ call: String) { calls.append(call) }
}

/// Answers every call that starts a session with a new token pair.
private struct StubAuthService: AuthService {
    /// The refresh tokens revoked with `POST auth/logout`.
    var logouts = CallLog()

    func login(email: String, password: String) async throws -> AuthResponse { try Self.response() }

    func register(email: String, password: String, displayName: String?) async throws -> AuthResponse { try Self.response() }

    func currentUser() async throws -> User { Fixtures.user }

    func changePassword(currentPassword: String, newPassword: String) async throws -> AuthResponse { try Self.response() }

    func logout(refreshToken: String) async throws {
        // A request on its way while the sign-out goes on.
        try await Task.sleep(for: .milliseconds(50))
        await logouts.record(refreshToken)
    }

    func deleteAccount(password: String) async throws {}

    private static func response() throws -> AuthResponse {
        try JSONDecoder.api().decode(AuthResponse.self, from: Fixtures.authResponseJSON(access: "new-access", refresh: "new-refresh"))
    }
}

/// A task started in a callback, for the test to wait for.
@MainActor
private final class TaskBox {
    var task: Task<Void, Never>?
}

/// Records what the session asks of the user's data, and whether the session was stored at that moment.
@MainActor
private final class UserDataRecorder: UserDataLifecycle {
    enum Event: Equatable {
        case signIn
        /// The wipe is marked pending.
        case willSignOut(sessionStored: Bool)
        /// Syncing stops and the data is wiped.
        case didSignOut(sessionCleared: Bool, sessionStored: Bool)
        case expire
    }

    private(set) var events: [Event] = []
    /// Runs once, when the wipe is first marked: a test acts while the session is being ended.
    var onWillSignOut: (@MainActor () async -> Void)?
    private let storage: InMemorySessionStorage

    init(storage: InMemorySessionStorage) {
        self.storage = storage
    }

    func userDidSignIn(_ user: User) async { events.append(.signIn) }

    func userWillSignOut() async {
        events.append(.willSignOut(sessionStored: storage.load() != nil))
        if let onWillSignOut {
            self.onWillSignOut = nil
            await onWillSignOut()
        }
    }

    func userDidSignOut(sessionCleared: Bool) async {
        events.append(.didSignOut(sessionCleared: sessionCleared, sessionStored: storage.load() != nil))
    }

    func sessionDidExpire() async { events.append(.expire) }
}

@MainActor
@Suite("Session")
struct SessionStoreTests {
    @Test func aSignInWhoseSessionCantBeStoredShowsAnError() async {
        let storage = InMemorySessionStorage()
        storage.setSavingFails(true)
        let tokens = TokenManager(storage: storage, session: nil)
        let session = SessionStore(auth: StubAuthService(), tokens: tokens, restoredSession: nil)
        let model = LoginViewModel()
        model.email = "collector@example.com"
        model.password = "secret-password"

        await model.submit(using: session)

        #expect(model.errorMessage == ErrorMessage.generic)
        #expect(session.state == .signedOut)
        #expect(await tokens.accessToken == nil)
    }

    @Test func aSessionThatCouldntBeReadAtLaunchIsRestoredOnceItCanBe() async {
        let tokens = TokenManager(storage: InMemorySessionStorage(), session: nil)
        let session = SessionStore(auth: StubAuthService(), tokens: tokens, restoredSession: nil, isRestoring: true)
        #expect(session.state == .restoring)
        #expect(session.user == nil)

        await session.finishRestoring(Fixtures.storedSession())

        #expect(session.state == .signedIn(Fixtures.user))
        #expect(await tokens.accessToken == "old-access")
    }

    @Test func aSessionThatIsStillMissingOnceItCanBeReadIsSignedOut() async {
        let session = SessionStore(
            auth: StubAuthService(),
            tokens: TokenManager(storage: InMemorySessionStorage(), session: nil),
            restoredSession: nil,
            isRestoring: true
        )
        await session.finishRestoring(nil)
        #expect(session.state == .signedOut)
    }

    // MARK: Ending the session and wiping the data

    private struct SignedIn {
        let session: SessionStore
        let tokens: TokenManager
        let storage: InMemorySessionStorage
        let userData: UserDataRecorder
        let auth: StubAuthService
    }

    private func signedIn(auth: StubAuthService = StubAuthService(), tokens makeTokens: ((InMemorySessionStorage) -> TokenManager)? = nil) -> SignedIn {
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let tokens = makeTokens?(storage) ?? TokenManager(storage: storage, session: storage.load())
        let userData = UserDataRecorder(storage: storage)
        let session = SessionStore(auth: auth, tokens: tokens, restoredSession: storage.load(), userData: userData)
        return SignedIn(session: session, tokens: tokens, storage: storage, userData: userData, auth: auth)
    }

    /// The wipe is marked pending while the session is still stored, and wiped – with the mark removed –
    /// once it isn't.
    private static let wipe: [UserDataRecorder.Event] = [
        .willSignOut(sessionStored: true),
        .didSignOut(sessionCleared: true, sessionStored: false),
    ]

    @Test func signingOutMarksTheWipeBeforeTheSessionIsClearedAndRevokesTheToken() async {
        let signedIn = signedIn()

        await signedIn.session.signOut()

        #expect(signedIn.userData.events == Self.wipe)
        #expect(signedIn.session.state == .signedOut)
        #expect(signedIn.session.signOutNotice == nil)
        #expect(await signedIn.tokens.accessToken == nil)
        #expect(signedIn.storage.load() == nil)
        // Sent within the sign-out (and its background time), not left behind.
        #expect(await signedIn.auth.logouts.calls == ["old-refresh"])
    }

    @Test func aSessionThatCantBeClearedKeepsTheWipeMarked() async {
        let signedIn = signedIn()
        signedIn.storage.setClearingFails(true)

        await signedIn.session.signOut()

        #expect(signedIn.userData.events == [
            .willSignOut(sessionStored: true),
            .didSignOut(sessionCleared: false, sessionStored: true),
        ])
        #expect(signedIn.session.state == .signedOut)
        #expect(await signedIn.tokens.accessToken == nil)
    }

    @Test func deletingTheAccountWipesTheDataLikeASignOut() async throws {
        let signedIn = signedIn()

        try await signedIn.session.deleteAccount(password: "secret-password")

        #expect(signedIn.userData.events == Self.wipe)
        #expect(signedIn.session.state == .signedOut)
        #expect(signedIn.storage.load() == nil)
    }

    @Test func aDeletedAccountSignsOutWipesTheDataAndSaysWhy() async throws {
        let refreshes = CallLog()
        let host = uniqueTestHost()
        let urlSession = StubURLProtocol.session(host: host) { request in
            if request.url?.path == "/api/v1/auth/refresh" {
                await refreshes.record("refresh")
            }
            return (401, Fixtures.errorJSON(status: 401, code: "ACCOUNT_DELETED"))
        }
        let signedIn = signedIn()
        let games = RemoteGameAPI(api: APIClient(baseURL: URL(string: "https://\(host)/api/v1/")!, tokens: signedIn.tokens, session: urlSession))
        let observer = Task { await signedIn.session.observeSessionEnds() }
        defer { observer.cancel() }

        await #expect(throws: APIError.server(statusCode: 401, code: .accountDeleted, details: [])) {
            _ = try await games.changes(after: nil, limit: 500)
        }
        await waitUntil { signedIn.session.state == .signedOut }

        #expect(signedIn.session.state == .signedOut)
        #expect(signedIn.session.signOutNotice == "This account has been deleted.")
        #expect(signedIn.userData.events == Self.wipe)
        #expect(signedIn.storage.load() == nil)
        #expect(await refreshes.calls.isEmpty, "never refreshed")
    }

    @Test func aDeletedAccountReportedForAReplacedSessionIsIgnored() async throws {
        let signedIn = signedIn()
        await signedIn.tokens.accountWasDeleted(rejecting: "old-access")
        // Another sign-in replaced the session before the report was handled.
        try await signedIn.tokens.begin(JSONDecoder.api().decode(
            AuthResponse.self, from: Fixtures.authResponseJSON(access: "next-access", refresh: "next-refresh")
        ))
        let observer = Task { await signedIn.session.observeSessionEnds() }
        defer { observer.cancel() }
        try await Task.sleep(for: .milliseconds(200))

        #expect(signedIn.session.state == .signedIn(Fixtures.user))
        #expect(signedIn.userData.events.isEmpty)
        #expect(signedIn.storage.load()?.refreshToken == "next-refresh")
    }

    @Test func aDeletedAccountReportedWhileTheUserDeletesItSaysNothing() async throws {
        let signedIn = signedIn()
        let observer = Task { await signedIn.session.observeSessionEnds() }
        defer { observer.cancel() }
        signedIn.userData.onWillSignOut = {
            // A sync request still on its way learns of the deletion before the tokens are cleared.
            await signedIn.tokens.accountWasDeleted(rejecting: "old-access")
            try? await Task.sleep(for: .milliseconds(200))
        }

        try await signedIn.session.deleteAccount(password: "secret-password")
        try await Task.sleep(for: .milliseconds(100))

        #expect(signedIn.userData.events == Self.wipe, "wiped once")
        #expect(signedIn.session.state == .signedOut)
        #expect(signedIn.session.signOutNotice == nil)
    }

    @Test func signingOutWhileADeletedAccountIsHandledWipesOnceAndSaysNothing() async throws {
        let signedIn = signedIn()
        let observer = Task { await signedIn.session.observeSessionEnds() }
        defer { observer.cancel() }
        let signOut = TaskBox()
        signedIn.userData.onWillSignOut = {
            // The user taps "Sign out" while the deleted account is being handled.
            signOut.task = Task { await signedIn.session.signOut() }
            try? await Task.sleep(for: .milliseconds(100))
        }

        await signedIn.tokens.accountWasDeleted(rejecting: "old-access")
        await waitUntil { signOut.task != nil }
        await signOut.task?.value

        #expect(signedIn.userData.events == Self.wipe, "wiped once")
        #expect(signedIn.session.state == .signedOut)
        #expect(signedIn.session.signOutNotice == nil, "the user's own sign-out wins")
    }

    @Test func anExpiredSessionKeepsTheDataAndSaysWhy() async throws {
        let signedIn = signedIn()
        let observer = Task { await signedIn.session.observeSessionEnds() }
        defer { observer.cancel() }

        #expect(await signedIn.tokens.expire(rejecting: "old-access"))
        await waitUntil { signedIn.session.state == .signedOut }

        #expect(signedIn.session.signOutNotice == "Your session has expired. Please sign in again.")
        #expect(signedIn.userData.events == [.expire])
    }
}

/// The start of the app after a sign-out or account deletion that didn't finish (docs/offline-sync.md,
/// "Local data").
@MainActor
@Suite("Finishing a wipe at the start")
struct PendingWipeAtStartTests {
    private let owner = "user-1"

    /// A database with a game in a fresh temporary directory, removed by `cleanUp`.
    private func storedCollection() async throws -> (url: URL, cleanUp: () -> Void) {
        let directory = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appending(path: "games.sqlite")
        let store = try GameStore(url: url)
        let (access, _) = try await store.activate(ownerID: owner)
        _ = try await store.insert(Fixtures.game("Doom"), at: .now, access: access)
        return (url, { try? FileManager.default.removeItem(at: directory) })
    }

    /// A memory-only cache holding one cover image.
    private func coverCache() -> (URLCache, URLRequest) {
        let cache = URLCache(memoryCapacity: 1_000_000, diskCapacity: 0)
        let request = URLRequest(url: URL(string: "https://example.com/cover.jpg")!)
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: nil)!
        cache.storeCachedResponse(CachedURLResponse(response: response, data: Data(repeating: 1, count: 100)), for: request)
        return (cache, request)
    }

    private func exists(_ path: String) -> Bool {
        FileManager.default.fileExists(atPath: path)
    }

    @Test func aStartAfterAnUnfinishedSignOutClearsTheSessionAndWipesTheDataFirst() async throws {
        let (url, cleanUp) = try await storedCollection()
        defer { cleanUp() }
        // The app was killed right after the wipe was marked pending.
        try await GameStore(url: url).markWipePending()
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let (cache, cover) = coverCache()
        #expect(cache.cachedResponse(for: cover) != nil)

        let restored = try AppContainer.restoredSession(from: storage, databaseURL: url, coverCache: cache)

        #expect(restored == nil)
        #expect(storage.load() == nil)
        #expect(!exists(url.path(percentEncoded: false)))
        #expect(!GameStore.isWipePending(at: url))
        #expect(cache.cachedResponse(for: cover) == nil)
    }

    @Test func aStartWithoutAPendingWipeRestoresTheSession() async throws {
        let (url, cleanUp) = try await storedCollection()
        defer { cleanUp() }
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        let (cache, cover) = coverCache()

        let restored = try AppContainer.restoredSession(from: storage, databaseURL: url, coverCache: cache)

        #expect(restored == Fixtures.storedSession())
        #expect(exists(url.path(percentEncoded: false)))
        #expect(cache.cachedResponse(for: cover) != nil)
    }

    @Test func aSessionThatCantBeClearedAtTheStartIsNotRestoredAndTheWipeStaysMarked() async throws {
        let (url, cleanUp) = try await storedCollection()
        defer { cleanUp() }
        try await GameStore(url: url).markWipePending()
        let storage = InMemorySessionStorage(Fixtures.storedSession())
        storage.setClearingFails(true)

        let restored = try AppContainer.restoredSession(from: storage, databaseURL: url, coverCache: coverCache().0)

        #expect(restored == nil)
        #expect(!exists(url.path(percentEncoded: false)))
        #expect(GameStore.isWipePending(at: url))

        // The next start finishes it.
        storage.setClearingFails(false)
        #expect(try AppContainer.restoredSession(from: storage, databaseURL: url, coverCache: coverCache().0) == nil)
        #expect(storage.load() == nil)
        #expect(!GameStore.isWipePending(at: url))
    }
}
