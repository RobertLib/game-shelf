package cz.gameshelf.app.data.auth

import android.util.Log
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
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.User
import cz.gameshelf.app.domain.model.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Sign-in, account and session operations. Signing in hands the local data over to the user
 * ([prepareUserData] wipes another user's data); signing out, deleting the account and `401 ACCOUNT_DELETED` remove
 * it ([clearUserData]: the sync is cancelled, the games and the cover cache wiped); a session that merely expires
 * keeps it.
 *
 * A wipe survives the app being killed half-way (offline-sync.md, "Local data"): [pendingWipe] is marked before the
 * data is wiped and the stored session cleared, and removed only when both are done; the app finishes a pending wipe
 * at its next start. Signing in and ending the session never interleave.
 *
 * A session that cannot be stored (Keystore, DataStore) is reported as [AppError.Unexpected]. Signing out
 * and deleting the account run in [appScope], so they finish even when the screen that started them closes.
 */
class AuthRepository(
    private val authApi: AuthApi,
    private val accountApi: AccountApi,
    private val sessionManager: SessionManager,
    private val appScope: CoroutineScope,
    private val pendingWipe: PendingWipe,
    private val prepareUserData: suspend (userId: String) -> Unit,
    private val clearUserData: suspend () -> Unit,
) {
    val session: StateFlow<SessionState> get() = sessionManager.state

    /** Why the app ended the last session by itself, for the sign-in screen; see [SessionManager.signOutReason]. */
    val signOutReason: StateFlow<SignOutReason?> get() = sessionManager.signOutReason

    /** Signing in (handing the local data over) and ending the session (wiping it) one at a time. */
    private val sessionChange = Mutex()

    suspend fun login(email: String, password: String): ApiResult<Unit> =
        apiCall { authApi.login(LoginRequest(email, password)) }.thenStore(::start)

    suspend fun register(email: String, password: String, displayName: String?): ApiResult<Unit> =
        apiCall { authApi.register(RegisterRequest(email, password, displayName)) }.thenStore(::start)

    suspend fun refreshCurrentUser(): ApiResult<User> =
        apiCall { accountApi.currentUser() }
            .onSuccess { sessionManager.updateUser(it) }

    /**
     * All other sessions are revoked by the API; the returned pair replaces ours. A refresh rejected meanwhile
     * (our refresh token is revoked too) doesn't end the session when the change succeeds.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): ApiResult<Unit> =
        sessionManager.whilePasswordChanges {
            apiCall { accountApi.changePassword(ChangePasswordRequest(currentPassword, newPassword)) }
                .thenStore(sessionManager::signIn)
        }

    /** Once the API confirmed the deletion, the session ends like a sign-out. */
    suspend fun deleteAccount(password: String): ApiResult<Unit> = appScope.async {
        apiCall { accountApi.deleteAccount(DeleteAccountRequest(password)) }
            .onSuccess { endSession() }
    }.await()

    /** Signs out locally right away (local data included); revoking the refresh token is best effort. */
    fun logout() {
        val refreshToken = sessionManager.refreshToken
        appScope.launch {
            endSession()
            if (refreshToken != null) apiCall { authApi.logout(RefreshTokenRequest(refreshToken)) }
        }
    }

    /**
     * An authenticated call sent with [accessToken] got `401 ACCOUNT_DELETED` (mobile-spec.md, "API basics"): the
     * account was deleted, e.g. on another device. The session ends like a sign-out – the local data is wiped – and
     * the sign-in screen says why; a session that replaced that token meanwhile stays (should it be the same deleted
     * account's, its next call gets the same answer).
     */
    fun onAccountDeleted(accessToken: String) {
        appScope.launch {
            endSession(SignOutReason.ACCOUNT_DELETED) { sessionManager.accessToken == accessToken }
        }
    }

    /** The sign-in screen showed why the session ended and the user left it. */
    fun dismissSignOutReason() = sessionManager.dismissSignOutReason()

    /** Hands the local data over to the user, then stores the session; a wipe left pending is finished first. */
    private suspend fun start(auth: AuthResponse) = sessionChange.withLock {
        val wipePending = pendingWipe.isPending
        if (wipePending) clearUserData()
        prepareUserData(auth.user.id)
        sessionManager.signIn(auth)
        // The stored session and the local data are the new user's now.
        if (wipePending) pendingWipe.clear()
    }

    /**
     * Ends the session like a sign-out, if [isCurrent] still holds: marks the wipe as pending, wipes the local data
     * (which cancels the sync), clears the session and removes the mark when both succeeded. The session ends also
     * when the wipe fails; the mark then makes the next start finish it.
     */
    private suspend fun endSession(reason: SignOutReason? = null, isCurrent: () -> Boolean = { true }) =
        sessionChange.withLock {
            if (!isCurrent()) return@withLock
            pendingWipe.mark()
            val wiped = try {
                clearUserData()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't wipe the local data", e)
                false
            }
            val sessionCleared = sessionManager.signOut(reason)
            if (wiped && sessionCleared) pendingWipe.clear()
        }

    /** Stores the session of a successful response; when that fails, the result is [AppError.Unexpected]. */
    private suspend fun ApiResult<AuthResponse>.thenStore(store: suspend (AuthResponse) -> Unit): ApiResult<Unit> =
        when (this) {
            is ApiResult.Failure -> this
            is ApiResult.Success -> try {
                store(value)
                ApiResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't store the session", e)
                ApiResult.Failure(AppError.Unexpected)
            }
        }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
