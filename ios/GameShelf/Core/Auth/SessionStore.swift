import Foundation
import Observation

/// The signed-in user's data on this device (the offline collection and its sync), whose
/// lifetime follows the session.
@MainActor
protocol UserDataLifecycle: AnyObject {
    /// A user signed in, or the app started signed in: another user's data is wiped, then it syncs.
    func userDidSignIn(_ user: User) async
    /// Sign-out or account deletion (also `401 ACCOUNT_DELETED`) is about to clear the stored session: the
    /// wipe of the data is marked pending first, so that a start after the app was killed half-way clears
    /// the stored session and wipes the data before anything is shown (docs/offline-sync.md, "Local data").
    func userWillSignOut() async
    /// Sign-out or account deletion, once the session is cleared: syncing stops and the data is wiped.
    /// The pending mark is removed when the data is gone – and only if `sessionCleared`, i.e. the stored
    /// session is gone too.
    func userDidSignOut(sessionCleared: Bool) async
    /// The server ended the session: syncing stops, but the data and unsynced changes are kept
    /// for the next sign-in of the same user.
    func sessionDidExpire() async
}

/// Source of truth for "who is signed in". The root view switches between the
/// auth flow and the main flow based on ``state``.
@Observable
@MainActor
final class SessionStore {
    enum State: Equatable {
        /// The stored session can't be read yet: the app was launched before the device was first
        /// unlocked after a restart. Nothing is shown until ``finishRestoring(_:)``.
        case restoring
        case signedOut
        case signedIn(User)
    }

    private(set) var state: State
    /// Why the app signed out by itself (the session expired, or the account was deleted), shown on the
    /// sign-in screen until the user signs in or leaves it.
    var signOutNotice: String?

    @ObservationIgnored private let auth: any AuthService
    @ObservationIgnored private let tokens: TokenManager
    @ObservationIgnored private let userData: (any UserDataLifecycle)?
    /// The end of the session in progress (``endSessionWipingData(notice:)``), so that it runs only once.
    @ObservationIgnored private var sessionEnd: Task<Void, Never>?

    /// `isRestoring`: the stored session couldn't be read yet (``State/restoring``).
    init(
        auth: any AuthService,
        tokens: TokenManager,
        restoredSession: StoredSession?,
        isRestoring: Bool = false,
        userData: (any UserDataLifecycle)? = nil
    ) {
        self.auth = auth
        self.tokens = tokens
        self.userData = userData
        state = isRestoring ? .restoring : restoredSession.map { .signedIn($0.user) } ?? .signedOut
    }

    var user: User? {
        if case .signedIn(let user) = state { user } else { nil }
    }

    /// The stored session could be read at last (``State/restoring``).
    func finishRestoring(_ restored: StoredSession?) async {
        guard state == .restoring else { return }
        if let restored {
            await tokens.restore(restored)
            state = .signedIn(restored.user)
        } else {
            state = .signedOut
        }
    }

    /// Prepares the restored user's data at launch.
    func resumeSession() async {
        guard let user else { return }
        await userData?.userDidSignIn(user)
    }

    /// Listens for sessions the server ended; runs for the lifetime of the caller's task.
    ///
    /// An expired session keeps the local data, so unsynced changes survive until the same user signs in
    /// again. A deleted account (`401 ACCOUNT_DELETED`, e.g. deleted on another device) signs out and wipes
    /// the local data exactly like a sign-out.
    func observeSessionEnds() async {
        for await end in tokens.sessionEnds {
            switch end {
            case .expired:
                // A new session may have begun since (e.g. a password change); its user stays signed in.
                guard user != nil, await tokens.accessToken == nil else { continue }
                state = .signedOut
                signOutNotice = ErrorMessage.sessionExpired
                await userData?.sessionDidExpire()
            case .accountDeleted(let accessToken):
                // Only the session that was rejected ends: a sign-out, an account deletion or another
                // sign-in meanwhile wins (a sync request may learn of the user's own deletion).
                guard user != nil, sessionEnd == nil, await tokens.accessToken == accessToken else { continue }
                await AppBackgroundTask.run(named: "Deleted account") {
                    await endSessionWipingData(notice: ErrorMessage.accountDeleted)
                }
            }
        }
    }

    func signIn(email: String, password: String) async throws {
        let response = try await auth.login(email: email.normalizedText, password: password)
        try await start(response)
    }

    func register(email: String, password: String, displayName: String?) async throws {
        let response = try await auth.register(
            email: email.normalizedText,
            password: password,
            displayName: displayName?.nilIfBlank
        )
        try await start(response)
    }

    /// Re-fetches the profile; failures keep the cached profile.
    func refreshProfile() async {
        guard let user = try? await auth.currentUser() else { return }
        await tokens.updateUser(user)
        if self.user != nil {
            state = .signedIn(user)
        }
    }

    /// Changes the password. The server revokes all sessions and returns a new token pair; a sync whose
    /// token refresh it rejects meanwhile doesn't sign the user out (``TokenManager/changePassword(_:)``).
    func changePassword(currentPassword: String, newPassword: String) async throws {
        let auth = auth
        let response = try await tokens.changePassword {
            try await auth.changePassword(currentPassword: currentPassword, newPassword: newPassword)
        }
        await didStart(response.user)
    }

    /// Signs out and removes the collection from the device (unsynced changes are lost). Once begun, it
    /// finishes even when the screen closes or the app leaves the foreground right away – including the
    /// request that revokes the refresh token, sent once the sign-in screen is shown.
    func signOut() async {
        await AppBackgroundTask.run(named: "Sign out") {
            let refreshToken = await tokens.refreshToken
            await endSessionWipingData()
            if let refreshToken {
                // Revoking the token is best effort; the local session is gone either way.
                try? await auth.logout(refreshToken: refreshToken)
            }
        }
    }

    /// Deletes the account and removes the collection from the device; like ``signOut()``, it finishes
    /// even when the screen closes or the app leaves the foreground right away.
    func deleteAccount(password: String) async throws {
        try await AppBackgroundTask.run(named: "Delete account") {
            try await auth.deleteAccount(password: password)
            await endSessionWipingData()
        }
    }

    /// Ends the session and removes the collection from the device: sign-out, account deletion and
    /// `401 ACCOUNT_DELETED`. `notice` says on the sign-in screen why, when the app did it by itself.
    ///
    /// The tokens go first, so a token refresh still in flight is ignored rather than ending the session
    /// as "expired" or storing a new pair; the running sync is cancelled before the data is wiped. The wipe
    /// is marked pending before the stored session is cleared, and the mark is removed only once both the
    /// session and the data are gone, so a start after the app was killed half-way finishes it before
    /// anything is shown (docs/offline-sync.md, "Local data").
    ///
    /// Runs once at a time: a second end while one is in progress waits for it instead of wiping again,
    /// and an end the user started (no `notice`) wins – the sign-in screen then says nothing.
    private func endSessionWipingData(notice: String? = nil) async {
        if let sessionEnd {
            await sessionEnd.value
            if notice == nil {
                signOutNotice = nil
            }
            return
        }
        let end = Task {
            await userData?.userWillSignOut()
            var isSessionCleared = true
            do {
                try await tokens.clear()
            } catch {
                debugLog("Failed to clear the stored session: \(error)")
                isSessionCleared = false
            }
            await userData?.userDidSignOut(sessionCleared: isSessionCleared)
            signOutNotice = notice
            state = .signedOut
        }
        sessionEnd = end
        await end.value
        sessionEnd = nil
    }

    /// Starts the session. When it can't be stored, the error is thrown and the user stays signed out.
    private func start(_ response: AuthResponse) async throws {
        try await tokens.begin(response)
        await didStart(response.user)
    }

    /// The user's data is ready (another user's wiped) before the main flow is shown.
    private func didStart(_ user: User) async {
        await userData?.userDidSignIn(user)
        signOutNotice = nil
        state = .signedIn(user)
    }
}
