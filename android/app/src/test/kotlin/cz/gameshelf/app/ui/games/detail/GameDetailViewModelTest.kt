package cz.gameshelf.app.ui.games.detail

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import cz.gameshelf.app.R
import cz.gameshelf.app.testing.FakeGamesRepository
import cz.gameshelf.app.testing.testGame
import cz.gameshelf.app.ui.common.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric because the navigation route is decoded from the [SavedStateHandle]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GameDetailViewModelTest {

    private val repository = FakeGamesRepository(listOf(testGame("1", title = "Zelda")))

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(id: String = "1") = GameDetailViewModel(SavedStateHandle(mapOf("gameId" to id)), repository)

    @Test
    fun `follows the stored game`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals("Zelda", viewModel.uiState.value.game?.title)

        repository.stored.update { games -> games?.map { it.copy(title = "Zelda (synced)") } }
        advanceUntilIdle()

        assertEquals("Zelda (synced)", viewModel.uiState.value.game?.title)
    }

    @Test
    fun `a game deleted elsewhere is not found`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        repository.stored.value = emptyList()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isNotFound)
    }

    @Test
    fun `toggling the favorite is saved at once`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.toggleFavorite()
            advanceUntilIdle()
            assertEquals(GameDetailEvent.ShowMessage(UiText(R.string.favorite_added)), awaitItem())
        }
        assertEquals(true, repository.game("1")?.favorite)
        assertEquals(true, viewModel.uiState.value.game?.favorite)
    }

    @Test
    fun `a deleted game closes the screen without flashing not found`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.requestDelete()
            viewModel.confirmDelete()
            advanceUntilIdle()
            assertEquals(GameDetailEvent.Deleted, awaitItem())
        }
        assertEquals(emptyList<Any>(), repository.stored.value)
        assertFalse(viewModel.uiState.value.isNotFound)
        assertFalse(viewModel.uiState.value.showDeleteConfirmation)
    }
}
