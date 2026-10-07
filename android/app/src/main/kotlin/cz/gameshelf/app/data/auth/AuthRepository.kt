package cz.gameshelf.app.data.auth

import cz.gameshelf.app.data.api.AccountApi
import cz.gameshelf.app.data.api.AuthApi
import cz.gameshelf.app.data.api.apiCall
import cz.gameshelf.app.data.api.dto.AuthResponse
import cz.gameshelf.app.data.api.dto.ChangePasswordRequest
import cz.gameshelf.app.data.api.dto.DeleteAccountRequest
import cz.gameshelf.app.data.api.dto.LoginRequest
import cz.gameshelf.app.data.api.dto.RefreshTokenRequest
import cz.gameshelf.app.data.api.dto.RegisterRequest
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.User
import cz.gameshelf.app.domain.model.map
import cz.gameshelf.app.domain.model.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Sign-in, account and session operations. Signing in hands the local data over to the user
 * ([prepareUserData] wipes another user's data); signing out and deleting the account remove it
 * ([clearUserData]); a session that merely expires keeps it.
 */
class AuthRepository(
    private val authApi: AuthApi,
    private val accountApi: AccountApi,
    private val sessionManager: SessionManager,
    private val appScope: CoroutineScope,
    private val prepareUserData: suspend (userId: String) -> Unit,
    private val clearUserData: suspend () -> Unit,
) {
    val session: StateFlow<SessionState> get() = sessionManager.state

    suspend fun login(email: String, password: String): ApiResult<Unit> =
        apiCall { authApi.login(LoginRequest(email, password)) }
            .onSuccess { start(it) }
            .map { }

    suspend fun register(email: String, password: String, displayName: String?): ApiResult<Unit> =
        apiCall { authApi.register(RegisterRequest(email, password, displayName)) }
            .onSuccess { start(it) }
            .map { }

    suspend fun refreshCurrentUser(): ApiResult<User> =
        apiCall { accountApi.currentUser() }
            .onSuccess { sessionManager.updateUser(it) }

    /** All other sessions are revoked by the API; the returned pair replaces ours. */
    suspend fun changePassword(currentPassword: String, newPassword: String): ApiResult<Unit> =
        apiCall { accountApi.changePassword(ChangePasswordRequest(currentPassword, newPassword)) }
            .onSuccess { sessionManager.signIn(it) }
            .map { }

    suspend fun deleteAccount(password: String): ApiResult<Unit> =
        apiCall { accountApi.deleteAccount(DeleteAccountRequest(password)) }
            .onSuccess {
                clearUserData()
                sessionManager.signOut()
            }

    /** Signs out locally right away (local data included); revoking the refresh token is best effort. */
    suspend fun logout() {
        val refreshToken = sessionManager.refreshToken
        clearUserData()
        sessionManager.signOut()
        if (refreshToken != null) {
            appScope.launch { apiCall { authApi.logout(RefreshTokenRequest(refreshToken)) } }
        }
    }

    private suspend fun start(auth: AuthResponse) {
        prepareUserData(auth.user.id)
        sessionManager.signIn(auth)
    }
}
