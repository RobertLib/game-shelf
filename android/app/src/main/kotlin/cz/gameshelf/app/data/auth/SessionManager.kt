package cz.gameshelf.app.data.auth

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

sealed interface SessionState {
    /** The persisted session is still being read. */
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: User) : SessionState
}

/**
 * Single source of truth for the signed-in session. Tokens are kept in memory for the HTTP layer
 * and persisted through [SessionStore]; [state] drives navigation between the auth and main graphs.
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
            val restored = store.load()
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

    override suspend fun onTokensRefreshed(auth: AuthResponse) = mutex.withLock {
        // Do not resurrect a session that was signed out while the refresh was in flight.
        if (current != null) persist(StoredSession(auth.accessToken, auth.refreshToken, auth.user))
    }

    override suspend fun onRefreshRejected() = signOut()

    suspend fun updateUser(user: User) = mutex.withLock {
        current?.let { persist(it.copy(user = user)) }
    }

    suspend fun signOut() = mutex.withLock {
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
}
