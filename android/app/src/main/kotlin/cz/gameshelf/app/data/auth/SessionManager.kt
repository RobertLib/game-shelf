package cz.gameshelf.app.data.auth

import android.util.Log
import cz.gameshelf.app.data.api.SessionTokens
import cz.gameshelf.app.data.api.dto.AuthResponse
import cz.gameshelf.app.domain.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

sealed interface SessionState {
    /** The persisted session is still being read. */
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: User) : SessionState
}

/**
 * Single source of truth for the signed-in session. Tokens are kept in memory for the HTTP layer
 * and persisted through [SessionStore]; [state] drives navigation between the auth and main graphs.
 *
 * The result of a token refresh is applied only while the session it started from is still the
 * current one (offline-sync.md, "Local data"): otherwise a slow refresh of a user who signed out could
 * replace the session of the user who signed in meanwhile – and the sync would hand them the old
 * user's data.
 */
class SessionManager(
    private val store: SessionStore,
    scope: CoroutineScope,
) : SessionTokens {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile
    private var current: StoredSession? = null
    private val mutex = Mutex()

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

    suspend fun signIn(auth: AuthResponse) = mutex.withLock {
        persist(StoredSession(auth.accessToken, auth.refreshToken, auth.user))
    }

    override suspend fun onTokensRefreshed(refreshToken: String, auth: AuthResponse): Boolean = mutex.withLock {
        // Signed out or signed in again while the refresh was in flight: not this session's tokens.
        if (current?.refreshToken != refreshToken) return@withLock false
        persist(StoredSession(auth.accessToken, auth.refreshToken, auth.user))
        true
    }

    override suspend fun onRefreshRejected(refreshToken: String) = mutex.withLock {
        // The session expired; one that replaced it meanwhile stays.
        if (current?.refreshToken == refreshToken) end()
    }

    /** A newer copy of the signed-in user's profile; ignored when someone else is signed in by now. */
    suspend fun updateUser(user: User) = mutex.withLock {
        current?.takeIf { it.user.id == user.id }?.let { persist(it.copy(user = user)) }
    }

    suspend fun signOut() = mutex.withLock { end() }

    private suspend fun end() {
        publish(null)
        store.clear()
    }

    private suspend fun persist(session: StoredSession) {
        publish(session)
        store.save(session)
    }

    private fun publish(session: StoredSession?) {
        current = session
        _state.value = session?.let { SessionState.SignedIn(it.user) } ?: SessionState.SignedOut
    }

    private companion object {
        const val TAG = "SessionManager"
    }
}
