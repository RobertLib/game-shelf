package cz.gameshelf.app.ui.games.list

import app.cash.turbine.test
import cz.gameshelf.app.R
import cz.gameshelf.app.data.sync.SyncEvent
import cz.gameshelf.app.data.sync.SyncStatus
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.testing.FakeGamesRepository
import cz.gameshelf.app.testing.FakeSyncController
import cz.gameshelf.app.testing.testGame
import cz.gameshelf.app.ui.common.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
class GameListViewModelTest {

    private val games = listOf(
        testGame("1", title = "Zelda", platform = Platform.N64),
        testGame("2", title = "Gran Turismo", platform = Platform.PS2),
        testGame("3", title = "banjo", platform = Platform.N64),
    )

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repository: FakeGamesRepository, sync: FakeSyncController = FakeSyncController()) =
        GameListViewModel(repository, sync, computeDispatcher = Dispatchers.Main)

    private val GameListUiState.titles get() = games.map { it.title }

    @Test
    fun `shows the stored games sorted by title`() = runTest {
        val viewModel = viewModel(FakeGamesRepository(games))

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("banjo", "Gran Turismo", "Zelda"), state.titles)
        assertEquals(3, state.totalItems)
        assertFalse(state.isLoading)
    }

    @Test
    fun `follows the stored collection`() = runTest {
        val repository = FakeGamesRepository(games)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        repository.stored.update { it.orEmpty() + testGame("4", title = "Asteroids") }
        advanceUntilIdle()

        assertEquals(listOf("Asteroids", "banjo", "Gran Turismo", "Zelda"), viewModel.uiState.value.titles)
    }

    @Test
    fun `loads until the collection is read and the first sync completes`() = runTest {
        val repository = FakeGamesRepository(initial = null)
        val sync = FakeSyncController(SyncStatus(isSyncing = true))
        val viewModel = viewModel(repository, sync)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)

        repository.stored.value = emptyList()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isLoading)

        sync.status.value = SyncStatus(lastSyncedAt = Instant.EPOCH)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.showEmptyCollection)
    }

    @Test
    fun `stored games are shown before the first sync completes`() = runTest {
        val sync = FakeSyncController(SyncStatus(isSyncing = true))
        val viewModel = viewModel(FakeGamesRepository(games), sync)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(3, viewModel.uiState.value.totalItems)
    }

    @Test
    fun `a failed first sync with nothing stored shows the error and retries`() = runTest {
        val sync = FakeSyncController(SyncStatus(isOffline = true, lastError = AppError.Network))
        val viewModel = viewModel(FakeGamesRepository(), sync)
        advanceUntilIdle()
        assertEquals(UiText(R.string.error_network), viewModel.uiState.value.loadError)
        assertFalse(viewModel.uiState.value.isLoading)

        sync.gate = CompletableDeferred()
        viewModel.retry()
        runCurrent()
        assertEquals(1, sync.syncNowCount)
        assertNull(viewModel.uiState.value.loadError)
        assertTrue(viewModel.uiState.value.isLoading)

        sync.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRetrying)
    }

    @Test
    fun `search is debounced`() = runTest {
        val viewModel = viewModel(FakeGamesRepository(games))
        advanceUntilIdle()

        viewModel.onSearchQueryChange("z")
        advanceTimeBy(100)
        viewModel.onSearchQueryChange("zel")
        advanceTimeBy(GameListViewModel.SEARCH_DEBOUNCE_MILLIS - 1)
        assertEquals(3, viewModel.uiState.value.totalItems)

        advanceUntilIdle()

        assertEquals(listOf("Zelda"), viewModel.uiState.value.titles)
        assertEquals("zel", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `applying the filter draft filters the list and scrolls to the top`() = runTest {
        val viewModel = viewModel(FakeGamesRepository(games))
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.openFilters()
            viewModel.updateFilterDraft { it.copy(platforms = setOf(Platform.N64)) }
            viewModel.applyFilterDraft()
            advanceUntilIdle()

            assertEquals(GameListEvent.ScrollToTop, awaitItem())
        }
        assertEquals(listOf("banjo", "Zelda"), viewModel.uiState.value.titles)
        assertNull(viewModel.uiState.value.filterDraft)
        assertEquals(1, viewModel.uiState.value.activeFilterCount)
    }

    @Test
    fun `groups by platform until grouping is turned off`() = runTest {
        val viewModel = viewModel(FakeGamesRepository(games))
        advanceUntilIdle()

        val sections = viewModel.uiState.value.sections.orEmpty()
            .map { section -> section.platform to section.games.map { it.title } }
        assertEquals(listOf(Platform.PS2 to listOf("Gran Turismo"), Platform.N64 to listOf("banjo", "Zelda")), sections)

        viewModel.events.test {
            viewModel.setGroupByPlatform(false)
            advanceUntilIdle()

            assertEquals(GameListEvent.ScrollToTop, awaitItem())
        }
        assertFalse(viewModel.uiState.value.groupByPlatform)
        assertNull(viewModel.uiState.value.sections)
        assertEquals(listOf("banjo", "Gran Turismo", "Zelda"), viewModel.uiState.value.titles)
    }

    @Test
    fun `invalid draft is not applied`() = runTest {
        val viewModel = viewModel(FakeGamesRepository(games))
        advanceUntilIdle()

        viewModel.openFilters()
        viewModel.updateFilterDraft { it.copy(releaseYearFrom = "1800") }
        viewModel.applyFilterDraft()

        assertTrue(viewModel.uiState.value.filterDraft != null)
        assertTrue(viewModel.uiState.value.filter.isEmpty)
    }

    @Test
    fun `facets come from the stored collection`() = runTest {
        val viewModel = viewModel(FakeGamesRepository(games))

        advanceUntilIdle()

        assertEquals(mapOf(Platform.N64 to 2, Platform.PS2 to 1), viewModel.uiState.value.facets.platformCounts)
    }

    @Test
    fun `a failed pull-to-refresh is reported and the list is kept`() = runTest {
        val sync = FakeSyncController().apply { nextResult = ApiResult.Failure(AppError.Network) }
        val viewModel = viewModel(FakeGamesRepository(games), sync)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.refresh()
            assertTrue(viewModel.uiState.value.isRefreshing)
            advanceUntilIdle()

            assertEquals(GameListEvent.ShowMessage(UiText(R.string.error_network)), awaitItem())
        }
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(3, viewModel.uiState.value.totalItems)
    }

    @Test
    fun `confirms local saves and deletions and reports rejected changes`() = runTest {
        val repository = FakeGamesRepository(games)
        val sync = FakeSyncController()
        val viewModel = viewModel(repository, sync)
        advanceUntilIdle()

        viewModel.events.test {
            repository.deleteGame("2")
            advanceUntilIdle()
            assertEquals(GameListEvent.ShowMessage(UiText(R.string.game_deleted)), awaitItem())

            sync.events.emit(SyncEvent.ChangesRejected)
            advanceUntilIdle()
            assertEquals(GameListEvent.ShowMessage(UiText(R.string.error_changes_rejected)), awaitItem())
        }
        assertEquals(listOf("banjo", "Zelda"), viewModel.uiState.value.titles)
    }

    @Test
    fun `empty collection and no results are told apart`() = runTest {
        val viewModel = viewModel(FakeGamesRepository())
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showEmptyCollection)

        viewModel.onSearchQueryChange("mario")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showEmptyCollection)
        assertTrue(viewModel.uiState.value.showNoResults)
    }

    @Test
    fun `the sync status is passed through`() = runTest {
        val sync = FakeSyncController()
        val viewModel = viewModel(FakeGamesRepository(games), sync)

        sync.status.value = SyncStatus(pendingCount = 2, isOffline = true, lastSyncedAt = Instant.EPOCH)
        advanceUntilIdle()

        assertEquals(sync.status.value, viewModel.uiState.value.sync)
    }
}
