package cz.gameshelf.app.data.auth

import android.util.Log
import cz.gameshelf.app.data.api.SessionTokens
import cz.gameshelf.app.data.api.dto.AuthResponse
import cz.gameshelf.app.domain.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

sealed interface SessionState {
    /** The persisted session is still being read. */
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: User) : SessionState
}

/** Why the app ended the session by itself; the sign-in screen says so (mobile-spec.md, "API basics"). */
enum class SignOutReason {
    /** The API rejected the refresh token (or refreshed tokens couldn't be stored); the local data is kept. */
    SESSION_EXPIRED,

    /** `401 ACCOUNT_DELETED`: the account was deleted, e.g. on another device; the local data is wiped. */
    ACCOUNT_DELETED,
}

/**
 * Single source of truth for the signed-in session. Tokens are kept in memory for the HTTP layer
 * and persisted through [SessionStore]; [state] drives navigation between the auth and main graphs.
 *
 * The result of a token refresh is applied only while the session it started from is still the
 * current one (offline-sync.md, "Local data"): otherwise a slow refresh of a user who signed out could
 * replace the session of the user who signed in meanwhile – and the sync would hand them the old
 * user's data.
 *
 * A session is stored before it is used (mobile-spec.md, "API basics"): tokens a restart would lose are
 * never handed out.
 */
class SessionManager(
    private val store: SessionStore,
    scope: CoroutineScope,
) : SessionTokens {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _signOutReason = MutableStateFlow<SignOutReason?>(null)

    /**
     * Why the last session ended by itself; `null` when the user signed out, or once the next session starts or
     * the sign-in screen no longer needs it ([dismissSignOutReason]). Set before [state] changes to signed out.
     */
    val signOutReason: StateFlow<SignOutReason?> = _signOutReason.asStateFlow()

    @Volatile
    private var current: StoredSession? = null
    private val mutex = Mutex()

    // Guarded by [mutex]; see [whilePasswordChanges].
    private var passwordChanges = 0
    private var rejectedDuringPasswordChange: String? = null

    init {
        scope.launch {
            val restored = try {
                store.load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never stuck on the splash screen: without a readable session, the user signs in again.
                Log.e(TAG, "Couldn't read the stored session", e)
                null
            }
            mutex.withLock {
                if (_state.value == SessionState.Loading) publish(restored)
            }
        }
    }

    override val accessToken: String? get() = current?.accessToken
    override val refreshToken: String? get() = current?.refreshToken

    /** Throws when the session cannot be stored (Keystore, DataStore); the previous session then stays. */
    suspend fun signIn(auth: AuthResponse) = mutex.withLock {
        persist(StoredSession(auth.accessToken, auth.refreshToken, auth.user))
    }

    override suspend fun onTokensRefreshed(refreshToken: String, auth: AuthResponse): Boolean = mutex.withLock {
        // Signed out or signed in again while the refresh was in flight: not this session's tokens.
        if (current?.refreshToken != refreshToken) return@withLock false
        try {
            persist(StoredSession(auth.accessToken, auth.refreshToken, auth.user))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The old refresh token, sent again after the API's grace period, would count as stolen and end the
            // sessions on every device; new tokens a restart would lose are no better. It ends as if it expired.
            Log.e(TAG, "Couldn't store the refreshed tokens; the session ends", e)
            end(SignOutReason.SESSION_EXPIRED)
            false
        }
    }

    override suspend fun onRefreshRejected(refreshToken: String) = mutex.withLock {
        // The session expired; one that replaced it meanwhile stays.
        if (current?.refreshToken != refreshToken) return@withLock
        // A password change revokes the token and brings a new pair: decided when the change completes.
        if (passwordChanges > 0) rejectedDuringPasswordChange = refreshToken else end(SignOutReason.SESSION_EXPIRED)
    }

    /**
     * Runs [change], a password change. The API revokes our refresh token before its response brings the new pair,
     * so a refresh rejected meanwhile (e.g. of a sync call that got `401`) ends the session only if the change
     * doesn't replace the tokens.
     */
    suspend fun <T> whilePasswordChanges(change: suspend () -> T): T {
        mutex.withLock { passwordChanges++ }
        try {
            return change()
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (--passwordChanges == 0) {
                        val rejected = rejectedDuringPasswordChange
                        rejectedDuringPasswordChange = null
                        if (rejected != null && current?.refreshToken == rejected) end(SignOutReason.SESSION_EXPIRED)
                    }
                }
            }
        }
    }

    /** A newer copy of the signed-in user's profile; ignored when someone else is signed in by now. */
    suspend fun updateUser(user: User) = mutex.withLock {
        val session = current?.takeIf { it.user.id == user.id } ?: return@withLock
        try {
            persist(session.copy(user = user))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Only a cached copy: the stored one is shown until the next refresh.
            Log.w(TAG, "Couldn't store the user's profile", e)
        }
    }

    /**
     * Ends the session; [reason] when the app ends it by itself (see [signOutReason]).
     * @return whether the stored session was deleted (see [end])
     */
    suspend fun signOut(reason: SignOutReason? = null): Boolean = mutex.withLock { end(reason) }

    /** The sign-in screen said why the session ended, and the user left it: not shown again. */
    fun dismissSignOutReason() {
        _signOutReason.value = null
    }

    /**
     * Signed out at once, also when the stored session can't be deleted: an expired one is then restored at the next
     * start, and a sign-out's is deleted then (the *wipe pending* marker, see [AuthRepository]).
     * @return whether the stored session was deleted
     */
    private suspend fun end(reason: SignOutReason?): Boolean {
        _signOutReason.value = reason
        publish(null)
        return try {
            store.clear()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't delete the stored session", e)
            false
        }
    }

    /** Stored first, so the tokens in use are always the ones a restart restores. */
    private suspend fun persist(session: StoredSession) {
        store.save(session)
        publish(session)
    }

    private fun publish(session: StoredSession?) {
        current = session
        if (session != null) _signOutReason.value = null
        _state.value = session?.let { SessionState.SignedIn(it.user) } ?: SessionState.SignedOut
    }

    private companion object {
        const val TAG = "SessionManager"
    }
}
