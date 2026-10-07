package cz.gameshelf.app.data.api.dto

import cz.gameshelf.app.domain.model.User
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String? = null,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String,
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class DeleteAccountRequest(
    val password: String,
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    /** Access token lifetime in seconds. */
    val expiresIn: Int,
    val refreshToken: String,
    val user: User,
)
