import Foundation

struct User: Codable, Hashable, Identifiable, Sendable {
    var id: String
    var email: String
    var displayName: String?
    var createdAt: Date
}

struct AuthResponse: Codable, Hashable, Sendable {
    var accessToken: String
    /// Lifetime of the access token in seconds.
    var expiresIn: Int
    var refreshToken: String
    var user: User
}

struct RegisterRequest: Encodable, Sendable {
    var email: String
    var password: String
    var displayName: String?
}

struct LoginRequest: Encodable, Sendable {
    var email: String
    var password: String
}

struct RefreshTokenRequest: Encodable, Sendable {
    var refreshToken: String
}

struct ChangePasswordRequest: Encodable, Sendable {
    var currentPassword: String
    var newPassword: String
}

struct DeleteAccountRequest: Encodable, Sendable {
    var password: String
}
