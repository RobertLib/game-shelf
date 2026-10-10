import Foundation

/// Persisted session: the token pair plus the last known profile.
struct StoredSession: Codable, Hashable, Sendable {
    var accessToken: String
    var refreshToken: String
    var user: User

    init(accessToken: String, refreshToken: String, user: User) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.user = user
    }

    init(_ response: AuthResponse) {
        self.init(accessToken: response.accessToken, refreshToken: response.refreshToken, user: response.user)
    }
}

/// Owns the current token pair and renews it.
///
/// Refreshing is single-flight: concurrent requests that hit `401` all await the
/// same refresh call, because refresh tokens are single-use and rotate. The result of a refresh is
/// applied only while the session it started from is still the current one (docs/offline-sync.md).
actor TokenManager {
    typealias Refresh = @Sendable (_ refreshToken: String) async throws -> AuthResponse

    /// Emits whenever the session ends involuntarily (the refresh token was rejected).
    nonisolated let sessionExpirations: AsyncStream<Void>

    private let storage: any SessionStorage
    private let expirationContinuation: AsyncStream<Void>.Continuation
    private var session: StoredSession?
    /// The refresh in flight, shared by every caller that hits `401` meanwhile. `id` tells it apart
    /// from a newer refresh that replaced it after ``begin(_:)`` or ``clear()``.
    private var inFlightRefresh: (id: Int, task: Task<StoredSession, any Error>)?
    private var refreshCount = 0

    init(storage: any SessionStorage, session: StoredSession?) {
        self.storage = storage
        self.session = session
        (sessionExpirations, expirationContinuation) = AsyncStream.makeStream(of: Void.self, bufferingPolicy: .bufferingNewest(1))
    }

    var accessToken: String? { session?.accessToken }
    var refreshToken: String? { session?.refreshToken }

    /// Starts or replaces the session after sign-in, registration or a password change.
    func begin(_ response: AuthResponse) {
        cancelRefresh()
        persist(StoredSession(response))
    }

    func updateUser(_ user: User) {
        guard var session else { return }
        session.user = user
        persist(session)
    }

    /// Ends the session voluntarily (sign-out, account deletion).
    func clear() {
        cancelRefresh()
        session = nil
        storage.clear()
    }

    /// Ends the session because the server rejected `accessToken` even right after a refresh –
    /// unless the session has been renewed or replaced since.
    func expire(rejecting accessToken: String) {
        guard session?.accessToken == accessToken else { return }
        expireSession()
    }

    /// Returns a fresh access token after `rejectedToken` was answered with `401`.
    ///
    /// If another caller already renewed the session in the meantime, its token is
    /// returned without a second refresh. A rejected refresh token ends the session. When the
    /// session is signed out or replaced while the refresh is in flight, its result is ignored and
    /// the callers get a `CancellationError`: their requests belonged to the session that is gone.
    func accessToken(replacing rejectedToken: String, using refresh: @escaping Refresh) async throws -> String {
        if let inFlightRefresh {
            return try Self.token(try await inFlightRefresh.task.value.accessToken, renewing: rejectedToken)
        }
        guard let current = session else { throw APIError.sessionExpired }
        if current.accessToken != rejectedToken {
            return try Self.token(current.accessToken, renewing: rejectedToken)
        }

        refreshCount += 1
        let id = refreshCount
        let task = Task<StoredSession, any Error> {
            defer {
                // A newer refresh (after begin() or clear()) is not this one's to forget.
                if inFlightRefresh?.id == id {
                    inFlightRefresh = nil
                }
            }
            // Once the request has reached the server, the old refresh token is used up: the new pair
            // must arrive and be stored even if the app is sent to the background meanwhile.
            return try await AppBackgroundTask.run(named: "Token refresh") {
                try await renew(current, using: refresh)
            }
        }
        inFlightRefresh = (id, task)
        return try await task.value.accessToken
    }

    /// Sends the refresh token of `session` and applies the answer – the new pair, or the end of a
    /// rejected session – only if `session` is still the current one.
    private func renew(_ session: StoredSession, using refresh: Refresh) async throws -> StoredSession {
        let response: AuthResponse
        do {
            response = try await refresh(session.refreshToken)
        } catch {
            guard isCurrent(session) else { throw CancellationError() }
            if let error = error as? APIError, error.endsSession {
                expireSession()
                throw APIError.sessionExpired
            }
            throw error
        }
        guard isCurrent(session) else { throw CancellationError() }
        let renewed = StoredSession(response)
        persist(renewed)
        return renewed
    }

    private func isCurrent(_ session: StoredSession) -> Bool {
        self.session?.refreshToken == session.refreshToken
    }

    /// `token`, renewed by another call meanwhile, for a request rejected with `rejectedToken` – unless
    /// another user has signed in since: a request of the previous user must not be repeated in their
    /// account.
    private static func token(_ token: String, renewing rejectedToken: String) throws -> String {
        guard let user = jwtSubject(token), user != jwtSubject(rejectedToken) else { return token }
        throw CancellationError()
    }

    /// The user id (`sub` claim) of a JWT access token, read without verifying it; `nil` when it isn't one.
    static func jwtSubject(_ token: String) -> String? {
        let parts = token.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3 else { return nil }
        var payload = parts[1].replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        payload += String(repeating: "=", count: (4 - payload.count % 4) % 4)
        guard let data = Data(base64Encoded: payload),
              let claims = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return nil }
        return claims["sub"] as? String
    }

    private func cancelRefresh() {
        inFlightRefresh?.task.cancel()
        inFlightRefresh = nil
    }

    /// Ends the session because the server no longer accepts it.
    private func expireSession() {
        guard session != nil else { return }
        clear()
        expirationContinuation.yield()
    }

    private func persist(_ session: StoredSession) {
        self.session = session
        storage.save(session)
    }
}

private extension APIError {
    /// Client errors from `POST auth/refresh` with an API error body mean the refresh token is invalid,
    /// whereas network failures, timeouts and responses that didn't come from the API (a proxy's page)
    /// should not sign the user out.
    var endsSession: Bool {
        switch self {
        case .server(let statusCode, _, _): (400..<500).contains(statusCode) && ![408, 429].contains(statusCode)
        case .sessionExpired: true
        case .http, .network, .invalidResponse: false
        }
    }
}
