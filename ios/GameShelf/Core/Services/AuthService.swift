import Foundation

protocol AuthService: Sendable {
    func login(email: String, password: String) async throws -> AuthResponse
    func register(email: String, password: String, displayName: String?) async throws -> AuthResponse
    func currentUser() async throws -> User
    func changePassword(currentPassword: String, newPassword: String) async throws -> AuthResponse
    func logout(refreshToken: String) async throws
    func deleteAccount(password: String) async throws
}

struct RemoteAuthService: AuthService {
    let api: APIClient

    func login(email: String, password: String) async throws -> AuthResponse {
        try await api.send(.login(LoginRequest(email: email, password: password)))
    }

    func register(email: String, password: String, displayName: String?) async throws -> AuthResponse {
        try await api.send(.register(RegisterRequest(email: email, password: password, displayName: displayName)))
    }

    func currentUser() async throws -> User {
        try await api.send(.currentUser)
    }

    func changePassword(currentPassword: String, newPassword: String) async throws -> AuthResponse {
        try await api.send(.changePassword(ChangePasswordRequest(currentPassword: currentPassword, newPassword: newPassword)))
    }

    func logout(refreshToken: String) async throws {
        _ = try await api.send(.logout(RefreshTokenRequest(refreshToken: refreshToken)))
    }

    func deleteAccount(password: String) async throws {
        _ = try await api.send(.deleteAccount(DeleteAccountRequest(password: password)))
    }
}
