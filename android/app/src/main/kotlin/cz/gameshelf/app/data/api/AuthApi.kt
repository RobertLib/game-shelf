package cz.gameshelf.app.data.api

import cz.gameshelf.app.data.api.dto.AuthResponse
import cz.gameshelf.app.data.api.dto.ChangePasswordRequest
import cz.gameshelf.app.data.api.dto.DeleteAccountRequest
import cz.gameshelf.app.data.api.dto.LoginRequest
import cz.gameshelf.app.data.api.dto.RefreshTokenRequest
import cz.gameshelf.app.data.api.dto.RegisterRequest
import cz.gameshelf.app.domain.model.User
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST

/** Public auth endpoints; served by a client without the bearer token or the refresh authenticator. */
interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshTokenRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshTokenRequest)
}

/** Endpoints of the signed-in account. */
interface AccountApi {
    @GET("auth/me")
    suspend fun currentUser(): User

    @POST("auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): AuthResponse

    @HTTP(method = "DELETE", path = "auth/me", hasBody = true)
    suspend fun deleteAccount(@Body body: DeleteAccountRequest)
}
