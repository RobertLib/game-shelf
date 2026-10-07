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
/// same refresh call, because refresh tokens are single-use and rotate.
actor TokenManager {
    typealias Refresh = @Sendable (_ refreshToken: String) async throws -> AuthResponse

    /// Emits whenever the session ends involuntarily (the refresh token was rejected).
    nonisolated let sessionExpirations: AsyncStream<Void>

    private let storage: any SessionStorage
    private let expirationContinuation: AsyncStream<Void>.Continuation
    private var session: StoredSession?
    private var refreshTask: Task<StoredSession, any Error>?

    init(storage: any SessionStorage, session: StoredSession?) {
        self.storage = storage
        self.session = session
        (sessionExpirations, expirationContinuation) = AsyncStream.makeStream(of: Void.self, bufferingPolicy: .bufferingNewest(1))
    }

    var accessToken: String? { session?.accessToken }
    var refreshToken: String? { session?.refreshToken }

    /// Starts or replaces the session after sign-in, registration or a password change.
    func begin(_ response: AuthResponse) {
        refreshTask?.cancel()
        refreshTask = nil
        persist(StoredSession(response))
    }

    func updateUser(_ user: User) {
        guard var session else { return }
        session.user = user
        persist(session)
    }

    /// Ends the session voluntarily (sign-out, account deletion).
    func clear() {
        refreshTask?.cancel()
        refreshTask = nil
        session = nil
        storage.clear()
    }

    /// Ends the session because the server no longer accepts it.
    func expire() {
        guard session != nil else { return }
        clear()
        expirationContinuation.yield()
    }

    /// Returns a fresh access token after `rejectedToken` was answered with `401`.
    ///
    /// If another caller already renewed the session in the meantime, its token is
    /// returned without a second refresh. A rejected refresh token ends the session.
    func accessToken(replacing rejectedToken: String, using refresh: @escaping Refresh) async throws -> String {
        if let refreshTask {
            return try await refreshTask.value.accessToken
        }
        guard let current = session else { throw APIError.sessionExpired }
        if current.accessToken != rejectedToken {
            return current.accessToken
        }

        let task = Task<StoredSession, any Error> {
            defer { refreshTask = nil }
            do {
                let renewed = StoredSession(try await refresh(current.refreshToken))
                try Task.checkCancellation()
                persist(renewed)
                return renewed
            } catch let error as APIError where error.endsSession {
                expire()
                throw APIError.sessionExpired
            }
        }
        refreshTask = task
        return try await task.value.accessToken
    }

    private func persist(_ session: StoredSession) {
        self.session = session
        storage.save(session)
    }
}

private extension APIError {
    /// Client errors from `POST auth/refresh` mean the refresh token is invalid,
    /// whereas network failures should not sign the user out.
    var endsSession: Bool {
        switch self {
        case .server(let statusCode, _, _): (400..<500).contains(statusCode) && statusCode != 429
        case .sessionExpired: true
        case .network, .invalidResponse: false
        }
    }
}
