package cz.gameshelf.app.ui.auth.login

import cz.gameshelf.app.R
import cz.gameshelf.app.data.api.dto.AuthResponse
import cz.gameshelf.app.data.auth.AuthRepository
import cz.gameshelf.app.data.auth.PendingWipe
import cz.gameshelf.app.data.auth.SessionManager
import cz.gameshelf.app.data.auth.SessionState
import cz.gameshelf.app.data.auth.StoredSession
import cz.gameshelf.app.domain.model.User
import cz.gameshelf.app.testing.FakeAccountApi
import cz.gameshelf.app.testing.FakeAuthApi
import cz.gameshelf.app.testing.FakeSessionStore
import cz.gameshelf.app.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val log = mutableListOf<String>()
    private val authApi = FakeAuthApi(log)
    private lateinit var sessionManager: SessionManager
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Signed in at first, like before the app ended the session; the test scope serves as the app scope. */
    private fun TestScope.signedIn() {
        sessionManager = SessionManager(FakeSessionStore(StoredSession("access-1", "refresh-1", USER)), this)
        authRepository = AuthRepository(
            authApi = authApi,
            accountApi = FakeAccountApi(log, USER),
            sessionManager = sessionManager,
            appScope = this,
            pendingWipe = PendingWipe(File(folder.root, "pending-wipe")),
            prepareUserData = {},
            clearUserData = { log += "wipe" },
        )
        advanceUntilIdle()
    }

    private fun TestScope.loginScreen(): LoginViewModel = LoginViewModel(authRepository).also { advanceUntilIdle() }

    @Test
    fun `an expired session is explained until the user leaves the screen`() = runTest {
        signedIn()
        sessionManager.onRefreshRejected("refresh-1")

        val viewModel = loginScreen()
        assertEquals(UiText(R.string.login_session_expired), viewModel.uiState.value.notice)

        // E.g. to create an account; coming back, it is not shown again.
        viewModel.onLeave()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.notice)
        assertNull(loginScreen().uiState.value.notice)
    }

    @Test
    fun `a deleted account is explained until the user signs in`() = runTest {
        signedIn()
        authRepository.onAccountDeleted("access-1")
        advanceUntilIdle()
        assertEquals(listOf("wipe"), log)

        val viewModel = loginScreen()
        assertEquals(UiText(R.string.error_account_deleted), viewModel.uiState.value.notice)

        authApi.auth = AuthResponse("access-2", 900, "refresh-2", OTHER_USER)
        viewModel.onEmailChange("b@example.com")
        viewModel.onPasswordChange("secret123")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(SessionState.SignedIn(OTHER_USER), sessionManager.state.value)
        assertNull(viewModel.uiState.value.notice)
    }

    @Test
    fun `the user's own sign-out needs no explanation`() = runTest {
        signedIn()
        authRepository.logout()
        advanceUntilIdle()

        assertNull(loginScreen().uiState.value.notice)
    }

    @Test
    fun `signing in checks only that the email has an @`() = runTest {
        signedIn()
        authRepository.logout()
        advanceUntilIdle()
        log.clear()
        val viewModel = loginScreen()
        authApi.auth = AuthResponse("access-2", 900, "refresh-2", USER)

        viewModel.onEmailChange("collector.example.com")
        viewModel.onPasswordChange("secret123")
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(UiText(R.string.validation_email), viewModel.uiState.value.emailError)
        assertEquals(emptyList<String>(), log)

        // Not a valid address for a new account, but an existing one must be able to sign in.
        viewModel.onEmailChange(" a..b@localhost ")
        viewModel.submit()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.emailError)
        assertEquals(listOf("login a..b@localhost"), log)
    }

    private companion object {
        val USER = User("user-1", "a@example.com", null, Instant.EPOCH)
        val OTHER_USER = User("user-2", "b@example.com", null, Instant.EPOCH)
    }
}
