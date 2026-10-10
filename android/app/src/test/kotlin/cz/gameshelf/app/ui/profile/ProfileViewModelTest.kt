package cz.gameshelf.app.ui.profile

import cz.gameshelf.app.R
import cz.gameshelf.app.data.api.AccountApi
import cz.gameshelf.app.data.api.AuthApi
import cz.gameshelf.app.data.api.dto.AuthResponse
import cz.gameshelf.app.data.api.dto.ChangePasswordRequest
import cz.gameshelf.app.data.api.dto.DeleteAccountRequest
import cz.gameshelf.app.data.api.dto.LoginRequest
import cz.gameshelf.app.data.api.dto.RefreshTokenRequest
import cz.gameshelf.app.data.api.dto.RegisterRequest
import cz.gameshelf.app.data.auth.AuthRepository
import cz.gameshelf.app.data.auth.PendingWipe
import cz.gameshelf.app.data.auth.SessionManager
import cz.gameshelf.app.data.auth.SessionState
import cz.gameshelf.app.data.auth.StoredSession
import cz.gameshelf.app.data.sync.SyncStatus
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.User
import cz.gameshelf.app.testing.FakeGamesRepository
import cz.gameshelf.app.testing.FakeSessionStore
import cz.gameshelf.app.testing.FakeSyncController
import cz.gameshelf.app.testing.testGame
import cz.gameshelf.app.ui.common.UiText
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.GeneralSecurityException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val sync = FakeSyncController()
    private val repository = FakeGamesRepository(
        listOf(
            testGame("1", platform = Platform.N64),
            testGame("2", platform = Platform.N64),
            testGame("3", platform = Platform.PS2),
        ),
    )

    /** What happened, in order: local data cleared, session state at that time, logout request. */
    private val log = mutableListOf<String>()

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var sessionManager: SessionManager
    private val sessionStore = FakeSessionStore(StoredSession("access", "refresh", USER))
    private val accountApi = FakeAccountApi()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** The test scope serves as the app scope: everything launched there finishes. */
    private fun TestScope.viewModel(): ProfileViewModel {
        sessionManager = SessionManager(sessionStore, this)
        val authRepository = AuthRepository(
            authApi = FakeAuthApi(log),
            accountApi = accountApi,
            sessionManager = sessionManager,
            appScope = this,
            pendingWipe = PendingWipe(File(folder.root, "pending-wipe")),
            prepareUserData = {},
            clearUserData = { log += "clear while ${sessionManager.state.value::class.simpleName}" },
        )
        return ProfileViewModel(authRepository, repository, sync, computeDispatcher = Dispatchers.Main)
    }

    @Test
    fun `shows the size of the stored collection`() = runTest {
        val viewModel = viewModel()

        advanceUntilIdle()

        assertEquals(CollectionSummary(games = 3, platforms = 2), viewModel.uiState.value.collection)
    }

    @Test
    fun `signing out asks first, then deletes the local data before ending the session`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        sync.status.value = SyncStatus(pendingCount = 2)

        viewModel.requestLogout()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showLogoutConfirmation)
        assertEquals(2, viewModel.uiState.value.sync.pendingCount)
        assertEquals(emptyList<String>(), log)

        viewModel.logout()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showLogoutConfirmation)
        assertEquals(listOf("clear while SignedIn", "logout refresh"), log)
        assertEquals(SessionState.SignedOut, sessionManager.state.value)
    }

    @Test
    fun `signing out finishes when the screen is closed right away`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.logout()
        // Back right after confirming.
        viewModel.viewModelScope.cancel()
        advanceUntilIdle()

        assertEquals(listOf("clear while SignedIn", "logout refresh"), log)
        assertEquals(SessionState.SignedOut, sessionManager.state.value)
    }

    @Test
    fun `deleting the account finishes when the screen is closed while it is on its way`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val deleted = CompletableDeferred<Unit>()
        accountApi.onDeleteAccount = { deleted.await() }

        viewModel.openDeleteAccount()
        viewModel.updateDeletePassword("secret123")
        viewModel.submitDeleteAccount()
        runCurrent()
        viewModel.viewModelScope.cancel()
        deleted.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("clear while SignedIn"), log)
        assertEquals(SessionState.SignedOut, sessionManager.state.value)
    }

    @Test
    fun `the new password must differ from the current one`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.openChangePassword()
        viewModel.updateChangePassword {
            it.copy(currentPassword = "secret123", newPassword = "secret123", confirmation = "secret123")
        }
        viewModel.submitChangePassword()
        advanceUntilIdle()

        val form = viewModel.uiState.value.changePassword
        assertEquals(UiText(R.string.validation_password_unchanged), form?.newPasswordError)
        assertFalse(form?.isSubmitting ?: true)
        assertEquals(0, accountApi.changePasswordCalls)
    }

    @Test
    fun `a refresh rejected while the password changes doesn't sign the user out`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        accountApi.onChangePassword = {
            // A sync call got 401 meanwhile, and the API rejected its refresh: the change revoked the token.
            sessionManager.onRefreshRejected("refresh")
            AuthResponse("access-2", 900, "refresh-2", USER)
        }

        changePassword(viewModel)

        assertNull(viewModel.uiState.value.changePassword)
        assertEquals(SessionState.SignedIn(USER), sessionManager.state.value)
        assertEquals("refresh-2", sessionManager.refreshToken)
        assertEquals("refresh-2", sessionStore.session?.refreshToken)
    }

    @Test
    fun `a new session that can't be stored is reported instead of crashing`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        accountApi.onChangePassword = { AuthResponse("access-2", 900, "refresh-2", USER) }
        sessionStore.saveFailure = GeneralSecurityException("keystore")

        changePassword(viewModel)

        assertEquals(UiText(R.string.error_unknown), viewModel.uiState.value.changePassword?.errorMessage)
        assertEquals(SessionState.SignedIn(USER), sessionManager.state.value)
    }

    private fun TestScope.changePassword(viewModel: ProfileViewModel) {
        viewModel.openChangePassword()
        viewModel.updateChangePassword {
            it.copy(currentPassword = "secret123", newPassword = "secret456", confirmation = "secret456")
        }
        viewModel.submitChangePassword()
        advanceUntilIdle()
    }

    @Test
    fun `a failed sync now shows the error until a later sync completes`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        sync.nextResult = ApiResult.Failure(AppError.Network)

        viewModel.syncNow()
        advanceUntilIdle()
        assertEquals(UiText(R.string.error_network), viewModel.uiState.value.syncError)

        sync.status.value = SyncStatus(lastSyncedAt = Instant.parse("2026-10-07T12:00:00Z"))
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.syncError)
    }

    private class FakeAuthApi(private val log: MutableList<String>) : AuthApi {
        override suspend fun register(body: RegisterRequest): AuthResponse = error("not used")

        override suspend fun login(body: LoginRequest): AuthResponse = error("not used")

        override suspend fun refresh(body: RefreshTokenRequest): AuthResponse = error("not used")

        override suspend fun logout(body: RefreshTokenRequest) {
            log += "logout ${body.refreshToken}"
        }
    }

    private class FakeAccountApi : AccountApi {
        var onChangePassword: suspend () -> AuthResponse = { error("not used") }
        var onDeleteAccount: suspend () -> Unit = {}
        var changePasswordCalls = 0

        override suspend fun currentUser(): User = USER

        override suspend fun changePassword(body: ChangePasswordRequest): AuthResponse {
            changePasswordCalls++
            return onChangePassword()
        }

        override suspend fun deleteAccount(body: DeleteAccountRequest) = onDeleteAccount()
    }

    private companion object {
        val USER = User("user-1", "collector@example.com", "Rob", Instant.EPOCH)
    }
}
