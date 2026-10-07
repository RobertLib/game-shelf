import Foundation
import Observation

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

    init(auth: any AuthService, tokens: TokenManager, restoredSession: StoredSession?) {
        self.auth = auth
        self.tokens = tokens
        state = restoredSession.map { .signedIn($0.user) } ?? .signedOut
    }

    var user: User? {
        if case .signedIn(let user) = state { user } else { nil }
    }

    /// Listens for sessions rejected by the server; runs for the lifetime of the caller's task.
    func observeSessionExpiration() async {
        for await _ in tokens.sessionExpirations {
            guard user != nil else { continue }
            state = .signedOut
            signOutNotice = ErrorMessage.sessionExpired
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

    func signOut() async {
        let refreshToken = await tokens.refreshToken
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
        await tokens.clear()
        state = .signedOut
    }

    private func start(_ response: AuthResponse) async {
        await tokens.begin(response)
        signOutNotice = nil
        state = .signedIn(response.user)
    }
}
