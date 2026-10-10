package cz.gameshelf.app.data.auth

import cz.gameshelf.app.data.api.dto.AuthResponse
import cz.gameshelf.app.domain.model.User
import cz.gameshelf.app.testing.FakeSessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.GeneralSecurityException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    private val store = FakeSessionStore()

    private fun TestScope.sessionManager(): SessionManager = SessionManager(store, this).also { advanceUntilIdle() }

    @Test
    fun `restores the stored session`() = runTest {
        store.session = StoredSession("access-a", "refresh-a", USER_A)

        val manager = sessionManager()

        assertEquals(SessionState.SignedIn(USER_A), manager.state.value)
        assertEquals("access-a", manager.accessToken)
        assertEquals("refresh-a", manager.refreshToken)
    }

    @Test
    fun `a stored session that can't be read starts signed out`() = runTest {
        listOf(IOException("disk"), GeneralSecurityException("key invalidated"), IllegalStateException("bug")).forEach {
            store.loadFailure = it

            val manager = sessionManager()

            assertEquals(SessionState.SignedOut, manager.state.value)
            assertNull(manager.accessToken)
        }
    }

    @Test
    fun `tokens refreshed for the current session are stored`() = runTest {
        store.session = StoredSession("access-a", "refresh-a", USER_A)
        val manager = sessionManager()

        assertTrue(manager.onTokensRefreshed("refresh-a", auth("access-a2", "refresh-a2", USER_A)))

        assertEquals("access-a2", manager.accessToken)
        assertEquals(StoredSession("access-a2", "refresh-a2", USER_A), store.session)
    }

    @Test
    fun `a refresh that completes after signing out does not bring the session back`() = runTest {
        store.session = StoredSession("access-a", "refresh-a", USER_A)
        val manager = sessionManager()
        manager.signOut()

        assertFalse(manager.onTokensRefreshed("refresh-a", auth("access-a2", "refresh-a2", USER_A)))

        assertEquals(SessionState.SignedOut, manager.state.value)
        assertNull(manager.accessToken)
        assertNull(store.session)
    }

    @Test
    fun `a slow refresh of a signed-out user leaves the next user's session alone`() = runTest {
        store.session = StoredSession("access-a", "refresh-a", USER_A)
        val manager = sessionManager()
        // A signs out and B signs in while A's refresh is in flight.
        manager.signOut()
        manager.signIn(auth("access-b", "refresh-b", USER_B))

        assertFalse(manager.onTokensRefreshed("refresh-a", auth("access-a2", "refresh-a2", USER_A)))
        manager.onRefreshRejected("refresh-a")

        assertEquals(SessionState.SignedIn(USER_B), manager.state.value)
        assertEquals("access-b", manager.accessToken)
        assertEquals(StoredSession("access-b", "refresh-b", USER_B), store.session)
    }

    @Test
    fun `a rejected refresh ends the session that sent it`() = runTest {
        store.session = StoredSession("access-a", "refresh-a", USER_A)
        val manager = sessionManager()

        manager.onRefreshRejected("refresh-a")

        assertEquals(SessionState.SignedOut, manager.state.value)
        assertNull(manager.refreshToken)
        assertNull(store.session)
    }

    @Test
    fun `a rejected refresh of tokens replaced meanwhile keeps the session`() = runTest {
        store.session = StoredSession("access-a", "refresh-a", USER_A)
        val manager = sessionManager()
        // E.g. a password change replaced the pair (and revoked the old refresh token) during the refresh.
        manager.signIn(auth("access-a2", "refresh-a2", USER_A))

        manager.onRefreshRejected("refresh-a")

        assertEquals(SessionState.SignedIn(USER_A), manager.state.value)
        assertEquals("refresh-a2", manager.refreshToken)
    }

    @Test
    fun `a profile update is applied only to the same user`() = runTest {
        store.session = StoredSession("access-b", "refresh-b", USER_B)
        val manager = sessionManager()

        manager.updateUser(USER_A.copy(displayName = "Late answer"))
        assertEquals(SessionState.SignedIn(USER_B), manager.state.value)

        manager.updateUser(USER_B.copy(displayName = "Bea"))
        assertEquals(SessionState.SignedIn(USER_B.copy(displayName = "Bea")), manager.state.value)
        assertEquals("Bea", store.session?.user?.displayName)
    }

    @Test
    fun `a session that can't be persisted still holds in memory, and the failure is reported`() = runTest {
        store.session = StoredSession("access-a", "refresh-a", USER_A)
        val manager = sessionManager()
        store.saveFailure = GeneralSecurityException("keystore")

        val failure = runCatching { manager.onTokensRefreshed("refresh-a", auth("access-a2", "refresh-a2", USER_A)) }

        assertTrue(failure.exceptionOrNull() is GeneralSecurityException)
        assertEquals("access-a2", manager.accessToken)
    }

    private fun auth(access: String, refresh: String, user: User) = AuthResponse(access, 900, refresh, user)

    private companion object {
        val USER_A = User("user-a", "a@example.com", "A", Instant.EPOCH)
        val USER_B = User("user-b", "b@example.com", "B", Instant.EPOCH)
    }
}
