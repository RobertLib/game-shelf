package cz.gameshelf.app.testing

import cz.gameshelf.app.data.api.AccountApi
import cz.gameshelf.app.data.api.AuthApi
import cz.gameshelf.app.data.api.dto.AuthResponse
import cz.gameshelf.app.data.api.dto.ChangePasswordRequest
import cz.gameshelf.app.data.api.dto.DeleteAccountRequest
import cz.gameshelf.app.data.api.dto.LoginRequest
import cz.gameshelf.app.data.api.dto.RefreshTokenRequest
import cz.gameshelf.app.data.api.dto.RegisterRequest
import cz.gameshelf.app.domain.model.User

/** Signs in with [auth] (or fails with [failure]); every call is added to [log]. */
class FakeAuthApi(private val log: MutableList<String>) : AuthApi {
    var auth: AuthResponse? = null
    var failure: Exception? = null

    override suspend fun register(body: RegisterRequest): AuthResponse = signIn("register ${body.email}")

    override suspend fun login(body: LoginRequest): AuthResponse = signIn("login ${body.email}")

    override suspend fun refresh(body: RefreshTokenRequest): AuthResponse = error("not used")

    override suspend fun logout(body: RefreshTokenRequest) {
        log += "logout ${body.refreshToken}"
    }

    private fun signIn(call: String): AuthResponse {
        log += call
        failure?.let { throw it }
        return checkNotNull(auth) { "no AuthResponse set" }
    }
}

/** The signed-in account; deleting it waits for [onDeleteAccount], which may fail. Calls are added to [log]. */
class FakeAccountApi(private val log: MutableList<String>, private val user: User) : AccountApi {
    var onDeleteAccount: suspend () -> Unit = {}

    override suspend fun currentUser(): User = user

    override suspend fun changePassword(body: ChangePasswordRequest): AuthResponse = error("not used")

    override suspend fun deleteAccount(body: DeleteAccountRequest) {
        onDeleteAccount()
        log += "delete account"
    }
}
