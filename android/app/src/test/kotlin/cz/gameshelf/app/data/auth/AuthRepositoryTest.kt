package cz.gameshelf.app.data.auth

import cz.gameshelf.app.data.api.dto.AuthResponse
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.User
import cz.gameshelf.app.testing.FakeAccountApi
import cz.gameshelf.app.testing.FakeAuthApi
import cz.gameshelf.app.testing.FakeSessionStore
import cz.gameshelf.app.testing.httpError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.time.Instant

/**
 * Handing the local data over at sign-in and wiping it when the session ends (offline-sync.md, "Local data"),
 * with the *wipe pending* marker in a temporary folder.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** What happened, in order: each step with the session state and whether the wipe was marked at that time. */
    private val log = mutableListOf<String>()
    private val sessionStore = FakeSessionStore(StoredSession("access-1", "refresh-1", USER))
    private val authApi = FakeAuthApi(log)
    private val accountApi = FakeAccountApi(log, USER)
    private val pendingWipe by lazy { PendingWipe(File(folder.root, "pending-wipe")) }
    private lateinit var sessionManager: SessionManager

    /** Runs when the local data is wiped; it may fail like the database, or take its time. */
    private var onWipe: suspend () -> Unit = {}

    /** The test scope serves as the app scope: everything launched there finishes. */
    private fun TestScope.repository(): AuthRepository {
        sessionManager = SessionManager(sessionStore, this)
        advanceUntilIdle()
        return AuthRepository(
            authApi = authApi,
            accountApi = accountApi,
            sessionManager = sessionManager,
            appScope = this,
            pendingWipe = pendingWipe,
            prepareUserData = { log += "prepare $it ${moment()}" },
            clearUserData = {
                log += "wipe ${moment()}"
                onWipe()
            },
        )
    }

    private fun moment(): String {
        val state = sessionManager.state.value::class.simpleName
        return if (pendingWipe.isPending) "($state, marked)" else "($state)"
    }

    @Test
    fun `signing in hands the local data over before the session starts`() = runTest {
        sessionStore.session = null
        val repository = repository()
        authApi.auth = AuthResponse("access-2", 900, "refresh-2", OTHER_USER)

        assertEquals(ApiResult.Success(Unit), repository.login("b@example.com", "secret123"))

        assertEquals(listOf("login b@example.com", "prepare user-2 (SignedOut)"), log)
        assertEquals(SessionState.SignedIn(OTHER_USER), sessionManager.state.value)
        assertEquals(StoredSession("access-2", "refresh-2", OTHER_USER), sessionStore.session)
    }

    @Test
    fun `a sign-in that fails changes nothing, and one whose session can't be stored is an error`() = runTest {
        sessionStore.session = null
        val repository = repository()
        authApi.failure = httpError(401, "INVALID_CREDENTIALS")

        assertTrue(repository.login("b@example.com", "secret123") is ApiResult.Failure)
        assertEquals(listOf("login b@example.com"), log)

        authApi.failure = null
        authApi.auth = AuthResponse("access-2", 900, "refresh-2", OTHER_USER)
        sessionStore.saveFailure = GeneralSecurityException("keystore")
        assertEquals(ApiResult.Failure(AppError.Unexpected), repository.register("b@example.com", "secret123", null))
        assertEquals(SessionState.SignedOut, sessionManager.state.value)
    }

    @Test
    fun `signing out marks the wipe, wipes, ends the session and then removes the mark`() = runTest {
        val repository = repository()

        repository.logout()
        advanceUntilIdle()

        assertEquals(listOf("wipe (SignedIn, marked)", "logout refresh-1"), log)
        assertEquals(SessionState.SignedOut, sessionManager.state.value)
        assertNull(sessionStore.session)
        assertFalse(pendingWipe.isPending)
        // The user's own decision: nothing to explain on the sign-in screen.
        assertNull(repository.signOutReason.value)
    }

    @Test
    fun `a wipe that fails still signs out, and leaves the mark for the next start`() = runTest {
        val repository = repository()
        onWipe = { throw IOException("disk") }

        repository.logout()
        advanceUntilIdle()

        assertEquals(SessionState.SignedOut, sessionManager.state.value)
        assertNull(sessionStore.session)
        assertTrue(pendingWipe.isPending)
    }

    @Test
    fun `a stored session that can't be cleared leaves the mark for the next start`() = runTest {
        val repository = repository()
        sessionStore.clearFailure = IOException("disk")

        repository.logout()
        advanceUntilIdle()

        assertEquals(listOf("wipe (SignedIn, marked)", "logout refresh-1"), log)
        assertEquals(SessionState.SignedOut, sessionManager.state.value)
        assertTrue(pendingWipe.isPending)
    }

    @Test
    fun `a sign-in finishes a wipe left pending, and removes the mark once the session is stored`() = runTest {
        sessionStore.session = null
        val repository = repository()
        pendingWipe.mark()
        authApi.auth = AuthResponse("access-1", 900, "refresh-1", USER)
        sessionStore.saveFailure = GeneralSecurityException("keystore")

        assertEquals(ApiResult.Failure(AppError.Unexpected), repository.login("a@example.com", "secret123"))
        assertTrue(pendingWipe.isPending)

        sessionStore.saveFailure = null
        log.clear()
        assertEquals(ApiResult.Success(Unit), repository.login("a@example.com", "secret123"))

        assertEquals(
            listOf("login a@example.com", "wipe (SignedOut, marked)", "prepare user-1 (SignedOut, marked)"),
            log,
        )
        assertEquals(SessionState.SignedIn(USER), sessionManager.state.value)
        assertFalse(pendingWipe.isPending)
    }

    @Test
    fun `signing in waits for a wipe in progress`() = runTest {
        val repository = repository()
        val wiping = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        onWipe = {
            wiping.complete(Unit)
            gate.await()
            log += "wiped"
        }
        authApi.auth = AuthResponse("access-2", 900, "refresh-2", OTHER_USER)

        repository.logout()
        wiping.await()
        val login = async { repository.login("b@example.com", "secret123") }
        runCurrent()
        gate.complete(Unit)

        assertEquals(ApiResult.Success(Unit), login.await())
        advanceUntilIdle()
        assertEquals(
            listOf("wipe (SignedIn, marked)", "login b@example.com", "wiped", "prepare user-2 (SignedOut)"),
            log.filterNot { it.startsWith("logout") },
        )
        assertEquals(SessionState.SignedIn(OTHER_USER), sessionManager.state.value)
        assertFalse(pendingWipe.isPending)
    }

    @Test
    fun `deleting the account wipes the local data once the API confirmed it`() = runTest {
        val repository = repository()
        accountApi.onDeleteAccount = { throw httpError(400, "INVALID_CURRENT_PASSWORD") }

        assertTrue(repository.deleteAccount("wrong") is ApiResult.Failure)
        assertEquals(emptyList<String>(), log)
        assertEquals(SessionState.SignedIn(USER), sessionManager.state.value)

        accountApi.onDeleteAccount = {}
        assertEquals(ApiResult.Success(Unit), repository.deleteAccount("secret123"))

        assertEquals(listOf("delete account", "wipe (SignedIn, marked)"), log)
        assertEquals(SessionState.SignedOut, sessionManager.state.value)
        assertNull(sessionStore.session)
        assertFalse(pendingWipe.isPending)
    }

    @Test
    fun `a deleted account ends the session like a sign-out and says why`() = runTest {
        val repository = repository()

        repository.onAccountDeleted("access-1")
        advanceUntilIdle()

        assertEquals(listOf("wipe (SignedIn, marked)"), log)
        assertEquals(SessionState.SignedOut, sessionManager.state.value)
        assertNull(sessionStore.session)
        assertFalse(pendingWipe.isPending)
        assertEquals(SignOutReason.ACCOUNT_DELETED, repository.signOutReason.value)

        // Reported by every call that was on its way: wiped once.
        repository.onAccountDeleted("access-1")
        advanceUntilIdle()
        assertEquals(1, log.size)

        repository.dismissSignOutReason()
        assertNull(repository.signOutReason.value)
    }

    @Test
    fun `a deleted account reported for a session that is no longer current changes nothing`() = runTest {
        val repository = repository()

        repository.onAccountDeleted("access-of-another-session")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), log)
        assertEquals(SessionState.SignedIn(USER), sessionManager.state.value)
        assertNull(repository.signOutReason.value)
    }

    @Test
    fun `an expired session keeps the local data and says why until the next sign-in`() = runTest {
        val repository = repository()

        sessionManager.onRefreshRejected("refresh-1")

        assertEquals(emptyList<String>(), log)
        assertEquals(SessionState.SignedOut, sessionManager.state.value)
        assertEquals(SignOutReason.SESSION_EXPIRED, repository.signOutReason.value)

        authApi.auth = AuthResponse("access-2", 900, "refresh-2", USER)
        repository.login("a@example.com", "secret123")
        assertNull(repository.signOutReason.value)
    }

    private companion object {
        val USER = User("user-1", "a@example.com", null, Instant.EPOCH)
        val OTHER_USER = User("user-2", "b@example.com", null, Instant.EPOCH)
    }
}
