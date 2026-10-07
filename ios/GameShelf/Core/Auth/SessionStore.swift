import Foundation
import Observation

/// The signed-in user's data on this device (the offline collection and its sync), whose
/// lifetime follows the session.
@MainActor
protocol UserDataLifecycle: AnyObject {
    /// A user signed in, or the app started signed in: another user's data is wiped, then it syncs.
    func userDidSignIn(_ user: User) async
    /// Sign-out or account deletion: syncing stops and the data is wiped.
    func userWillSignOut() async
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
        case signedOut
        case signedIn(User)
    }

    private(set) var state: State
    /// One-off explanation shown on the login screen after an involuntary sign-out.
    var signOutNotice: String?

    @ObservationIgnored private let auth: any AuthService
    @ObservationIgnored private let tokens: TokenManager
    @ObservationIgnored private let userData: (any UserDataLifecycle)?

    init(
        auth: any AuthService,
        tokens: TokenManager,
        restoredSession: StoredSession?,
        userData: (any UserDataLifecycle)? = nil
    ) {
        self.auth = auth
        self.tokens = tokens
        self.userData = userData
        state = restoredSession.map { .signedIn($0.user) } ?? .signedOut
    }

    var user: User? {
        if case .signedIn(let user) = state { user } else { nil }
    }

    /// Prepares the restored user's data at launch.
    func resumeSession() async {
        guard let user else { return }
        await userData?.userDidSignIn(user)
    }

    /// Listens for sessions rejected by the server; runs for the lifetime of the caller's task.
    /// The local data is kept, so unsynced changes survive until the same user signs in again.
    func observeSessionExpiration() async {
        for await _ in tokens.sessionExpirations {
            guard user != nil else { continue }
            state = .signedOut
            signOutNotice = ErrorMessage.sessionExpired
            await userData?.sessionDidExpire()
        }
    }

    func signIn(email: String, password: String) async throws {
        let response = try await auth.login(email: email.trimmingCharacters(in: .whitespacesAndNewlines), password: password)
        await start(response)
    }

    func register(email: String, password: String, displayName: String?) async throws {
        let response = try await auth.register(
            email: email.trimmingCharacters(in: .whitespacesAndNewlines),
            password: password,
            displayName: displayName?.nilIfBlank
        )
        await start(response)
    }

    /// Re-fetches the profile; failures keep the cached profile.
    func refreshProfile() async {
        guard let user = try? await auth.currentUser() else { return }
        await tokens.updateUser(user)
        if self.user != nil {
            state = .signedIn(user)
        }
    }

    /// Changes the password. The server revokes all other sessions and returns a new token pair.
    func changePassword(currentPassword: String, newPassword: String) async throws {
        let response = try await auth.changePassword(currentPassword: currentPassword, newPassword: newPassword)
        await start(response)
    }

    /// Signs out and removes the collection from the device (unsynced changes are lost).
    func signOut() async {
        let refreshToken = await tokens.refreshToken
        await userData?.userWillSignOut()
        await tokens.clear()
        state = .signedOut
        if let refreshToken {
            // Revoking the token is best effort; the local session is gone either way.
            let auth = auth
            Task.detached { try? await auth.logout(refreshToken: refreshToken) }
        }
    }

    func deleteAccount(password: String) async throws {
        try await auth.deleteAccount(password: password)
        await userData?.userWillSignOut()
        await tokens.clear()
        state = .signedOut
    }

    /// Starts or renews the session. The user's data is ready (another user's wiped) before the
    /// main flow is shown.
    private func start(_ response: AuthResponse) async {
        await tokens.begin(response)
        await userData?.userDidSignIn(response.user)
        signOutNotice = nil
        state = .signedIn(response.user)
    }
}
