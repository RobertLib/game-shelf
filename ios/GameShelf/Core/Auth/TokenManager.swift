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

/// Why a session ended without the user signing out (``TokenManager/sessionEnds``).
enum SessionEnd: Equatable, Sendable {
    /// The server no longer accepts the session (its refresh was rejected), or its renewal couldn't be
    /// stored. The session is already cleared; the local data is kept.
    case expired
    /// `401 ACCOUNT_DELETED` for a request sent with `accessToken`, the session's token then: the account was
    /// deleted (e.g. on another device). The session store signs out and wipes the local data.
    case accountDeleted(accessToken: String)
}

/// Owns the current token pair and renews it.
///
/// Refreshing is single-flight: concurrent requests that hit `401` all await the
/// same refresh call, because refresh tokens are single-use and rotate. The result of a refresh is
/// applied only while the session it started from is still the current one (docs/offline-sync.md).
/// A new pair is stored before it is used (docs/mobile-spec.md, "API basics").
actor TokenManager {
    typealias Refresh = @Sendable (_ refreshToken: String) async throws -> AuthResponse

    /// Emits whenever the session ends involuntarily.
    nonisolated let sessionEnds: AsyncStream<SessionEnd>

    private let storage: any SessionStorage
    private let sessionEndContinuation: AsyncStream<SessionEnd>.Continuation
    private var session: StoredSession?
    /// The refresh in flight, shared by every caller that hits `401` meanwhile. `id` tells it apart
    /// from a newer refresh that replaced it after ``begin(_:)`` or ``clear()``.
    private var inFlightRefresh: (id: Int, task: Task<StoredSession, any Error>)?
    private var refreshCount = 0
    /// Password changes in flight (``changePassword(_:)``); meanwhile a rejected session isn't ended yet.
    private var passwordChanges = 0
    /// The session was rejected while a password change was in flight.
    private var isExpirationDeferred = false

    init(storage: any SessionStorage, session: StoredSession?) {
        self.storage = storage
        self.session = session
        (sessionEnds, sessionEndContinuation) = AsyncStream.makeStream(of: SessionEnd.self)
    }

    var accessToken: String? { session?.accessToken }
    var refreshToken: String? { session?.refreshToken }

    /// Starts or replaces the session after sign-in or registration. When it can't be stored, it is not
    /// started and the error is thrown: a session a restart would lose must not be used.
    func begin(_ response: AuthResponse) throws {
        let session = StoredSession(response)
        try storage.save(session)
        cancelRefresh()
        isExpirationDeferred = false
        self.session = session
    }

    /// The session read from storage only after launch (the device was still locked then); a session
    /// begun meanwhile wins.
    func restore(_ session: StoredSession) {
        guard self.session == nil else { return }
        self.session = session
    }

    /// Changes the password with `change` and begins the session it returns (``begin(_:)``).
    ///
    /// The server revokes the session's refresh token while the change is in flight, so a refresh sent
    /// meanwhile (e.g. by a sync) is rejected. That rejection ends the session only if the change fails;
    /// otherwise the new session replaces the rejected one without signing the user out.
    func changePassword(_ change: @Sendable () async throws -> AuthResponse) async throws -> AuthResponse {
        passwordChanges += 1
        defer {
            passwordChanges -= 1
            if passwordChanges == 0, isExpirationDeferred {
                isExpirationDeferred = false
                expireSession()
            }
        }
        let response = try await change()
        // Signed out meanwhile: the sign-out wins.
        guard session != nil else { throw CancellationError() }
        try begin(response)
        return response
    }

    func updateUser(_ user: User) {
        guard var session else { return }
        session.user = user
        self.session = session
        do {
            try storage.save(session)
        } catch {
            // Only the cached profile is outdated; the tokens stored with it are still the current ones.
            debugLog("Failed to store the profile: \(error)")
        }
    }

    /// Ends the session voluntarily (sign-out, account deletion). It is gone from memory at once; throws
    /// when the stored session couldn't be removed.
    func clear() throws {
        cancelRefresh()
        session = nil
        try storage.clear()
    }

    /// Ends the session because the server rejected `accessToken` even right after a refresh – unless the
    /// session has been renewed or replaced since, or a password change in flight defers it. Returns whether
    /// the session ended.
    @discardableResult
    func expire(rejecting accessToken: String) -> Bool {
        guard session?.accessToken == accessToken else { return false }
        return expireSession()
    }

    /// The API answered a request sent with `accessToken` with `401 ACCOUNT_DELETED`. Nothing is refreshed: if
    /// `accessToken` is still the session's, its end is reported (``SessionEnd/accountDeleted(accessToken:)``),
    /// and the session store signs out and wipes the local data like a sign-out – clearing the session only
    /// after it has marked the wipe pending.
    func accountWasDeleted(rejecting accessToken: String) {
        guard session?.accessToken == accessToken else { return }
        sessionEndContinuation.yield(.accountDeleted(accessToken: accessToken))
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
        do {
            try storage.save(renewed)
        } catch {
            // Tokens a restart would lose are never used: the old refresh token, sent again once the
            // server's grace period is over, would count as stolen and end the session on every device.
            // The session ends as if it had expired; the local data is kept.
            debugLog("Failed to store the renewed session: \(error)")
            expireSession()
            throw APIError.sessionExpired
        }
        self.session = renewed
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

    /// Ends the session because the server no longer accepts it (or its renewal couldn't be stored) –
    /// once no password change is in flight: its new session replaces this one. Returns whether it ended.
    @discardableResult
    private func expireSession() -> Bool {
        guard session != nil else { return false }
        guard passwordChanges == 0 else {
            isExpirationDeferred = true
            return false
        }
        do {
            try clear()
        } catch {
            debugLog("Failed to clear the expired session: \(error)")
        }
        sessionEndContinuation.yield(.expired)
        return true
    }
}

private extension APIError {
    /// Client errors from `POST auth/refresh` with an API error body mean the refresh token is invalid,
    /// whereas network failures, timeouts, `5xx` and responses that didn't come from the API (a proxy's
    /// page) keep the session.
    var endsSession: Bool {
        switch self {
        case .server(let statusCode, _, _): (400..<500).contains(statusCode) && ![408, 429].contains(statusCode)
        case .sessionExpired: true
        case .http, .network, .undecodable, .invalidResponse: false
        }
    }
}
