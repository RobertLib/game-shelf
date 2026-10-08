package cz.gameshelf.app.ui.games.edit.search

import app.cash.turbine.test
import cz.gameshelf.app.R
import cz.gameshelf.app.data.lookup.GameSearchOutcome
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.ErrorCode
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.testing.FakeGameSearchRepository
import cz.gameshelf.app.testing.FakeGameSearchRepository.Search
import cz.gameshelf.app.testing.MARIO_KART_SEARCH_RESULT
import cz.gameshelf.app.testing.ZELDA_SEARCH_RESULT
import cz.gameshelf.app.testing.searchFound
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.games.edit.search.GameSearchViewModel.Companion.SEARCH_DEBOUNCE_MILLIS
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameSearchViewModelTest {

    private val repository = FakeGameSearchRepository(
        mapOf(
            "mario kart" to searchFound(MARIO_KART_SEARCH_RESULT),
            "zelda" to searchFound(ZELDA_SEARCH_RESULT),
        ),
    )

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(initialQuery: String = "", formPlatform: Platform? = null) =
        GameSearchViewModel(repository, initialQuery, formPlatform)

    private val GameSearchViewModel.content get() = uiState.value.content

    private val GameSearchViewModel.titles
        get() = (content as? GameSearchContent.Results)?.games?.map { it.title }

    @Test
    fun `the title from the form is searched right away with the form's platform`() = runTest {
        val viewModel = viewModel("  mario kart ", formPlatform = Platform.SWITCH)

        assertEquals("mario kart", viewModel.uiState.value.query)
        assertEquals(GameSearchContent.Loading, viewModel.content)
        assertTrue(viewModel.uiState.value.isSearching)
        runCurrent()

        assertEquals(listOf(Search("mario kart", Platform.SWITCH)), repository.searches)
        assertEquals(
            GameSearchContent.Results("mario kart", listOf(MARIO_KART_SEARCH_RESULT), listOf("IGDB")),
            viewModel.content,
        )
        assertFalse(viewModel.uiState.value.isSearching)
    }

    @Test
    fun `a title shorter than 2 characters shows the hint and is not searched`() = runTest {
        val viewModel = viewModel(" m ")
        advanceUntilIdle()

        assertEquals(GameSearchContent.Hint, viewModel.content)
        assertTrue(repository.searches.isEmpty())
    }

    @Test
    fun `typing is searched after a pause`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onQueryChange("ma")
        advanceTimeBy(100)
        viewModel.onQueryChange("mario")
        advanceTimeBy(100)
        viewModel.onQueryChange("mario kart")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS - 1)
        runCurrent()

        assertTrue(repository.searches.isEmpty())
        assertEquals(GameSearchContent.Loading, viewModel.content)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf(Search("mario kart", null)), repository.searches)
        assertEquals(listOf("Mario Kart 8 Deluxe"), viewModel.titles)
    }

    @Test
    fun `fewer than 2 characters show the hint instead of results`() = runTest {
        val viewModel = viewModel("mario kart")
        advanceUntilIdle()

        viewModel.onQueryChange("m ")

        assertEquals(GameSearchContent.Hint, viewModel.content)
        assertFalse(viewModel.uiState.value.isSearching)
        advanceUntilIdle()
        assertEquals(1, repository.searches.size)
    }

    @Test
    fun `spaces around the text don't start a new search`() = runTest {
        val viewModel = viewModel("mario kart")
        advanceUntilIdle()

        viewModel.onQueryChange(" mario kart ")
        advanceUntilIdle()

        assertEquals(" mario kart ", viewModel.uiState.value.query)
        assertEquals(1, repository.searches.size)
        assertEquals(listOf("Mario Kart 8 Deluxe"), viewModel.titles)
    }

    @Test
    fun `searching again keeps the previous results until the new ones arrive`() = runTest {
        val viewModel = viewModel("mario kart")
        advanceUntilIdle()
        val zelda = CompletableDeferred<Unit>()
        repository.gates["zelda"] = zelda

        viewModel.onQueryChange("zelda")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)

        assertEquals(Search("zelda", null), repository.searches.last())
        assertEquals(listOf("Mario Kart 8 Deluxe"), viewModel.titles)
        assertTrue(viewModel.uiState.value.isSearching)

        zelda.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("The Legend of Zelda: Ocarina of Time"), viewModel.titles)
        assertFalse(viewModel.uiState.value.isSearching)
    }

    @Test
    fun `an answer that comes late is ignored`() = runTest {
        val marioKart = CompletableDeferred<Unit>()
        repository.gates["mario kart"] = marioKart
        val viewModel = viewModel("mario kart")
        runCurrent()

        viewModel.onQueryChange("zelda")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)
        assertEquals(listOf("The Legend of Zelda: Ocarina of Time"), viewModel.titles)

        marioKart.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("mario kart", "zelda"), repository.searches.map { it.query })
        assertEquals(listOf("The Legend of Zelda: Ocarina of Time"), viewModel.titles)
    }

    @Test
    fun `nothing found`() = runTest {
        val viewModel = viewModel("xyzzy")
        advanceUntilIdle()

        assertEquals(GameSearchContent.NoResults("xyzzy"), viewModel.content)
    }

    @Test
    fun `a failed search can be retried`() = runTest {
        repository.results = mapOf(
            "mario kart" to GameSearchOutcome.Failed(AppError.Api(503, ErrorCode.LOOKUP_UNAVAILABLE)),
        )
        val viewModel = viewModel("mario kart")
        advanceUntilIdle()
        assertEquals(GameSearchContent.Failed(UiText(R.string.error_lookup_unavailable)), viewModel.content)

        repository.results = mapOf("mario kart" to searchFound(MARIO_KART_SEARCH_RESULT))
        viewModel.retry()
        assertEquals(GameSearchContent.Loading, viewModel.content)
        runCurrent()

        assertEquals(2, repository.searches.size)
        assertEquals(listOf("Mario Kart 8 Deluxe"), viewModel.titles)
    }

    @Test
    fun `a network failure shows the connection message`() = runTest {
        repository.results = mapOf("mario kart" to GameSearchOutcome.Failed(AppError.Network))
        val viewModel = viewModel("mario kart")
        advanceUntilIdle()

        assertEquals(GameSearchContent.Failed(UiText(R.string.error_network)), viewModel.content)
    }

    @Test
    fun `text longer than the API accepts is cut for the request`() = runTest {
        viewModel("a".repeat(150))
        advanceUntilIdle()

        assertEquals("a".repeat(GameSearchViewModel.MAX_QUERY_LENGTH), repository.searches.single().query)
    }

    @Test
    fun `picking a game on one platform fills in that platform`() = runTest {
        val viewModel = viewModel("mario kart")
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.pick(MARIO_KART_SEARCH_RESULT)

            assertEquals(
                GameSearchEvent.Picked(GameSearchPick(MARIO_KART_SEARCH_RESULT, Platform.SWITCH, listOf("IGDB"))),
                awaitItem(),
            )
        }
        assertNull(viewModel.uiState.value.platformChoice)
    }

    @Test
    fun `the platform chosen in the form stays`() = runTest {
        val viewModel = viewModel("zelda", formPlatform = Platform.WII)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.pick(ZELDA_SEARCH_RESULT)

            assertEquals(Platform.WII, (awaitItem() as GameSearchEvent.Picked).pick.platform)
        }
        assertNull(viewModel.uiState.value.platformChoice)
    }

    @Test
    fun `a game on several platforms asks which one the copy is for`() = runTest {
        val viewModel = viewModel("zelda")
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.pick(ZELDA_SEARCH_RESULT)
            assertEquals(ZELDA_SEARCH_RESULT, viewModel.uiState.value.platformChoice)
            expectNoEvents()

            viewModel.choosePlatform(Platform.GAMECUBE)

            assertEquals(
                GameSearchEvent.Picked(GameSearchPick(ZELDA_SEARCH_RESULT, Platform.GAMECUBE, listOf("IGDB"))),
                awaitItem(),
            )
        }
        assertNull(viewModel.uiState.value.platformChoice)
    }

    @Test
    fun `other platform leaves the platform empty`() = runTest {
        val viewModel = viewModel("zelda")
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.pick(ZELDA_SEARCH_RESULT)
            viewModel.choosePlatform(null)

            assertNull((awaitItem() as GameSearchEvent.Picked).pick.platform)
        }
    }

    @Test
    fun `cancelling the platform question goes back to the results`() = runTest {
        val viewModel = viewModel("zelda")
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.pick(ZELDA_SEARCH_RESULT)
            viewModel.dismissPlatformChoice()

            expectNoEvents()
        }
        assertNull(viewModel.uiState.value.platformChoice)
        assertEquals(listOf("The Legend of Zelda: Ocarina of Time"), viewModel.titles)
    }
}
