package cz.gameshelf.app.ui.games.list

import app.cash.turbine.test
import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.testing.FakeGamesRepository
import cz.gameshelf.app.ui.common.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameListViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads the first page on start`() = runTest {
        val repository = FakeGamesRepository(totalItems = 60)
        val viewModel = GameListViewModel(repository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(25, state.games.size)
        assertEquals(60, state.totalItems)
        assertFalse(state.isLoading)
        assertFalse(state.endReached)
        assertEquals(listOf(1), repository.listCalls.map { it.page })
    }

    @Test
    fun `next page is requested once even when asked repeatedly`() = runTest {
        val repository = FakeGamesRepository(totalItems = 60)
        val viewModel = GameListViewModel(repository)
        advanceUntilIdle()

        repository.gate = CompletableDeferred()
        repeat(3) { viewModel.loadNextPage() }
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoadingMore)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(1, 2), repository.listCalls.map { it.page })
        assertEquals(50, viewModel.uiState.value.games.size)
    }

    @Test
    fun `stops at the last page`() = runTest {
        val repository = FakeGamesRepository(totalItems = 30)
        val viewModel = GameListViewModel(repository)
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(1, 2), repository.listCalls.map { it.page })
        assertEquals(30, viewModel.uiState.value.games.size)
        assertTrue(viewModel.uiState.value.endReached)
    }

    @Test
    fun `failed next page waits for an explicit retry`() = runTest {
        val repository = FakeGamesRepository(totalItems = 60)
        val viewModel = GameListViewModel(repository)
        advanceUntilIdle()

        repository.failNextList = true
        viewModel.loadNextPage()
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.loadMoreFailed)
        assertEquals(2, repository.listCalls.size)

        viewModel.retryLoadMore()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.loadMoreFailed)
        assertEquals(50, viewModel.uiState.value.games.size)
    }

    @Test
    fun `search is debounced`() = runTest {
        val repository = FakeGamesRepository(totalItems = 5)
        val viewModel = GameListViewModel(repository)
        advanceUntilIdle()

        viewModel.onSearchQueryChange("z")
        advanceTimeBy(100)
        viewModel.onSearchQueryChange("ze")
        advanceTimeBy(100)
        viewModel.onSearchQueryChange("zelda ")
        advanceTimeBy(GameListViewModel.SEARCH_DEBOUNCE_MILLIS - 1)
        assertEquals(1, repository.listCalls.size)

        advanceUntilIdle()

        assertEquals(listOf("", "zelda"), repository.listCalls.map { it.query.search })
    }

    @Test
    fun `applying the filter draft reloads from the first page`() = runTest {
        val repository = FakeGamesRepository(totalItems = 60)
        val viewModel = GameListViewModel(repository)
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()

        viewModel.openFilters()
        viewModel.updateFilterDraft { it.copy(platforms = setOf(Platform.PS2)) }
        viewModel.applyFilterDraft()
        advanceUntilIdle()

        val last = repository.listCalls.last()
        assertEquals(1, last.page)
        assertEquals(setOf(Platform.PS2), last.query.filter.platforms)
        assertEquals(null, viewModel.uiState.value.filterDraft)
        assertEquals(1, viewModel.uiState.value.activeFilterCount)
    }

    @Test
    fun `invalid draft is not applied`() = runTest {
        val viewModel = GameListViewModel(FakeGamesRepository(totalItems = 5))
        advanceUntilIdle()

        viewModel.openFilters()
        viewModel.updateFilterDraft { it.copy(releaseYearFrom = "1800") }
        viewModel.applyFilterDraft()

        assertTrue(viewModel.uiState.value.filterDraft != null)
        assertTrue(viewModel.uiState.value.filter.isEmpty)
    }

    @Test
    fun `a game deleted elsewhere disappears with a message`() = runTest {
        val repository = FakeGamesRepository(totalItems = 3)
        val viewModel = GameListViewModel(repository)
        advanceUntilIdle()

        viewModel.events.test {
            assertEquals(GameListEvent.ScrollToTop, awaitItem())
            repository.deleteGame("2")
            advanceUntilIdle()
            assertEquals(GameListEvent.ShowMessage(UiText(R.string.game_deleted)), awaitItem())
        }
        assertEquals(listOf("1", "3"), viewModel.uiState.value.games.map { it.id })
        assertEquals(2, viewModel.uiState.value.totalItems)
    }

    @Test
    fun `empty collection and no results are told apart`() = runTest {
        val viewModel = GameListViewModel(FakeGamesRepository(totalItems = 0))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showEmptyCollection)

        viewModel.onSearchQueryChange("mario")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showEmptyCollection)
        assertTrue(viewModel.uiState.value.showNoResults)
    }
}
