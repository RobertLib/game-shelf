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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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

    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** The test scope serves as the app scope: everything launched there finishes. */
    private fun TestScope.viewModel(): ProfileViewModel {
        sessionManager = SessionManager(FakeSessionStore(StoredSession("access", "refresh", USER)), this)
        val authRepository = AuthRepository(
            authApi = FakeAuthApi(log),
            accountApi = FakeAccountApi(),
            sessionManager = sessionManager,
            appScope = this,
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
        override suspend fun currentUser(): User = USER

        override suspend fun changePassword(body: ChangePasswordRequest): AuthResponse = error("not used")

        override suspend fun deleteAccount(body: DeleteAccountRequest) = Unit
    }

    private companion object {
        val USER = User("user-1", "collector@example.com", "Rob", Instant.EPOCH)
    }
}
