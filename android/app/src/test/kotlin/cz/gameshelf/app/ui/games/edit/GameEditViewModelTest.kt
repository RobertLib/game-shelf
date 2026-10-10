package cz.gameshelf.app.ui.games.edit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import cz.gameshelf.app.R
import cz.gameshelf.app.data.lookup.BarcodeLookupResult
import cz.gameshelf.app.domain.model.AppError
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Condition
import cz.gameshelf.app.domain.model.GameSearchResult
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.Region
import cz.gameshelf.app.testing.FakeBarcodeLookupRepository
import cz.gameshelf.app.testing.FakeGamesRepository
import cz.gameshelf.app.testing.MARIO_KART_LOOKUP
import cz.gameshelf.app.testing.MARIO_KART_SEARCH_RESULT
import cz.gameshelf.app.testing.testGame
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.games.edit.search.GameSearchPick
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric because the navigation route is decoded from the [SavedStateHandle]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GameEditViewModelTest {

    private val repository = FakeGamesRepository()
    private val lookup = FakeBarcodeLookupRepository(
        mapOf(MARIO_KART_LOOKUP.barcode to BarcodeLookupResult.Found(MARIO_KART_LOOKUP)),
    )

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(vararg route: Pair<String, String>) =
        GameEditViewModel(SavedStateHandle(mapOf(*route)), repository, lookup, computeDispatcher = Dispatchers.Main)

    private fun searchPick(game: GameSearchResult = MARIO_KART_SEARCH_RESULT, platform: Platform? = Platform.SWITCH) =
        GameSearchPick(game, platform, sources = listOf("IGDB"))

    @Test
    fun `a barcode scanned from the list is looked up and fills in the form`() = runTest {
        val viewModel = viewModel("barcode" to "0045496420055")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("045496420055"), lookup.lookedUp)
        assertEquals("045496420055", state.form.barcode)
        assertEquals("Mario Kart 8 Deluxe", state.form.title)
        assertEquals(Platform.SWITCH, state.form.platform)
        assertEquals("Racing", state.form.genre)
        assertEquals("2017", state.form.releaseYear)
        assertEquals(BarcodeLookupStatus.Found(listOf("UPCitemdb", "IGDB")), state.lookup)
        assertNull(state.duplicate)
        assertTrue(state.hasChanges)
    }

    @Test
    fun `a scan keeps what the user already entered`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.updateForm { it.copy(title = "MK8") }

        viewModel.onBarcodeScanned("045496420055")
        advanceUntilIdle()

        assertEquals("MK8", viewModel.uiState.value.form.title)
        assertEquals("Nintendo EPD", viewModel.uiState.value.form.developer)
    }

    @Test
    fun `an unknown barcode is reported and kept`() = runTest {
        val viewModel = viewModel("barcode" to "96385074")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BarcodeLookupStatus.NotFound, state.lookup)
        assertEquals("96385074", state.form.barcode)
        assertEquals("", state.form.title)
    }

    @Test
    fun `a failed lookup can be retried`() = runTest {
        lookup.results = mapOf(MARIO_KART_LOOKUP.barcode to BarcodeLookupResult.Failed(AppError.Network))
        val viewModel = viewModel("barcode" to "045496420055")
        advanceUntilIdle()
        assertEquals(BarcodeLookupStatus.Failed(UiText(R.string.error_network)), viewModel.uiState.value.lookup)

        lookup.results = mapOf(MARIO_KART_LOOKUP.barcode to BarcodeLookupResult.Found(MARIO_KART_LOOKUP))
        viewModel.retryLookup()
        advanceUntilIdle()

        assertEquals("Mario Kart 8 Deluxe", viewModel.uiState.value.form.title)
        assertTrue(viewModel.uiState.value.lookup is BarcodeLookupStatus.Found)
    }

    @Test
    fun `warns when the collection already has a game with the barcode`() = runTest {
        repository.stored.value = listOf(testGame("1", title = "Mario Kart 8 Deluxe").copy(barcode = "0045496420055"))

        val viewModel = viewModel("barcode" to "045496420055")
        advanceUntilIdle()

        assertEquals("1", viewModel.uiState.value.duplicate?.id)
    }

    @Test
    fun `scanning while editing fills only the empty fields of that game`() = runTest {
        repository.stored.value = listOf(testGame("1", title = "MK8 DX").copy(barcode = "045496420055"))
        val viewModel = viewModel("gameId" to "1")
        advanceUntilIdle()

        viewModel.onBarcodeScanned("045496420055")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.duplicate)
        assertEquals("MK8 DX", state.form.title)
        assertEquals(Platform.PS2, state.form.platform)
        assertEquals("Racing", state.form.genre)
    }

    @Test
    fun `a code typed by hand that is not a barcode is not looked up`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onBarcodeScanned("1234")
        advanceUntilIdle()

        assertTrue(lookup.lookedUp.isEmpty())
        assertEquals("1234", viewModel.uiState.value.form.barcode)
        assertNull(viewModel.uiState.value.lookup)
    }

    @Test
    fun `dismissing hides the lookup result`() = runTest {
        val viewModel = viewModel("barcode" to "045496420055")
        advanceUntilIdle()

        viewModel.dismissLookup()

        assertNull(viewModel.uiState.value.lookup)
        assertEquals("Mario Kart 8 Deluxe", viewModel.uiState.value.form.title)
    }

    @Test
    fun `closing the game database search changes nothing`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.updateForm { it.copy(title = "mario") }

        viewModel.openGameSearch()
        assertTrue(viewModel.uiState.value.showGameSearch)
        viewModel.closeGameSearch()

        val state = viewModel.uiState.value
        assertFalse(state.showGameSearch)
        assertEquals("mario", state.form.title)
        assertNull(state.lookup)
    }

    @Test
    fun `a game picked in the search fills in the form and closes the search`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.updateForm { it.copy(title = "mario kart", genre = "Kart racing", notes = "Gift") }
        viewModel.openGameSearch()

        viewModel.applyGameSearchPick(searchPick())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showGameSearch)
        assertEquals("Mario Kart 8 Deluxe", state.form.title)
        assertEquals(Platform.SWITCH, state.form.platform)
        assertEquals("Racing", state.form.genre)
        assertEquals("Nintendo EPD", state.form.developer)
        assertEquals("2017", state.form.releaseYear)
        assertEquals("Gift", state.form.notes)
        assertEquals(BarcodeLookupStatus.Found(listOf("IGDB")), state.lookup)
        assertNull(state.duplicate)
    }

    @Test
    fun `warns when the collection has the picked game on the same platform`() = runTest {
        repository.stored.value = listOf(
            testGame("1", title = "Mario Kart 8 Deluxe", platform = Platform.WII_U),
            testGame("2", title = "Mario Kart 8", platform = Platform.SWITCH),
            testGame("3", title = " mario kart 8 DELUXE ", platform = Platform.SWITCH),
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.applyGameSearchPick(searchPick())
        advanceUntilIdle()

        assertEquals("3", viewModel.uiState.value.duplicate?.id)
    }

    @Test
    fun `no duplicate warning for the game being edited or without a platform`() = runTest {
        repository.stored.value = listOf(testGame("1", title = "Mario Kart 8 Deluxe", platform = Platform.SWITCH))
        val editing = viewModel("gameId" to "1")
        val adding = viewModel()
        advanceUntilIdle()

        editing.applyGameSearchPick(searchPick())
        adding.applyGameSearchPick(searchPick(MARIO_KART_SEARCH_RESULT.copy(platforms = emptyList()), platform = null))
        advanceUntilIdle()

        assertEquals(Platform.SWITCH, editing.uiState.value.form.platform)
        assertNull(editing.uiState.value.duplicate)
        assertNull(adding.uiState.value.form.platform)
        assertNull(adding.uiState.value.duplicate)
    }

    @Test
    fun `a value unknown to this version doesn't block saving an edit of another field`() = runTest {
        val stored = testGame("1", title = "Doom").copy(status = CollectionStatus.UNKNOWN, condition = Condition.UNKNOWN)
        repository.stored.value = listOf(stored)
        val viewModel = viewModel("gameId" to "1")
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.updateForm { it.copy(title = "Doom II") }
            viewModel.save()
            advanceUntilIdle()

            assertEquals(GameEditEvent.Close, awaitItem())
        }
        assertEquals(stored.copy(title = "Doom II"), repository.game("1"))
    }

    @Test
    fun `an edit still reports problems with the values the user changed`() = runTest {
        repository.stored.value = listOf(testGame("1", title = "Doom").copy(status = CollectionStatus.UNKNOWN))
        val viewModel = viewModel("gameId" to "1")
        advanceUntilIdle()

        viewModel.updateForm { it.copy(coverImageUrl = "nas/cover.jpg") }
        viewModel.save()
        advanceUntilIdle()

        assertEquals(setOf(GameField.COVER_URL), viewModel.uiState.value.errors.keys)
        assertEquals("Doom", repository.game("1")?.title)
    }

    @Test
    fun `an edit survives process death together with what counts as changed`() = runTest {
        repository.stored.value = listOf(testGame("1", title = "Doom"))
        val handle = SavedStateHandle(mapOf("gameId" to "1"))
        val viewModel = GameEditViewModel(handle, repository, lookup, computeDispatcher = Dispatchers.Main)
        advanceUntilIdle()
        viewModel.updateForm { it.copy(title = "Doom II", notes = "Big box") }
        advanceUntilIdle()

        // The process is killed; the saved state is all that is left. A sync changed the game meanwhile.
        repository.stored.value = listOf(testGame("1", title = "DOOM").copy(rating = 9))
        val restored = GameEditViewModel(handle.copy(), repository, lookup, computeDispatcher = Dispatchers.Main)
        advanceUntilIdle()

        val state = restored.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Doom II", state.form.title)
        assertEquals("Big box", state.form.notes)
        assertEquals("Doom", state.initialForm.title)
        assertTrue(state.hasChanges)
        restored.requestClose()
        assertTrue(restored.uiState.value.showDiscardDialog)

        restored.save()
        advanceUntilIdle()
        // Only the edited fields are saved; the rating the sync brought in stays.
        val saved = repository.game("1")
        assertEquals("Doom II", saved?.title)
        assertEquals("Big box", saved?.notes)
        assertEquals(9, saved?.rating)
    }

    @Test
    fun `a new game survives process death without looking the barcode up again`() = runTest {
        val handle = SavedStateHandle(mapOf("barcode" to MARIO_KART_LOOKUP.barcode))
        GameEditViewModel(handle, repository, lookup, computeDispatcher = Dispatchers.Main)
            .updateForm { it.copy(notes = "Gift") }
        advanceUntilIdle()
        lookup.lookedUp.clear()

        val restored = GameEditViewModel(handle.copy(), repository, lookup, computeDispatcher = Dispatchers.Main)
        advanceUntilIdle()

        val state = restored.uiState.value
        assertEquals("Mario Kart 8 Deluxe", state.form.title)
        assertEquals("Gift", state.form.notes)
        assertEquals(GameForm(), state.initialForm)
        assertTrue(lookup.lookedUp.isEmpty())
    }

    @Test
    fun `an unreadable saved form is ignored`() = runTest {
        repository.stored.value = listOf(testGame("1", title = "Doom"))
        val handle = SavedStateHandle(mapOf("gameId" to "1", "gameEditForm" to "{not json"))

        val viewModel = GameEditViewModel(handle, repository, lookup, computeDispatcher = Dispatchers.Main)
        advanceUntilIdle()

        assertEquals("Doom", viewModel.uiState.value.form.title)
        assertFalse(viewModel.uiState.value.hasChanges)
    }

    /** What survives process death: the saved state's values (all of them strings here). */
    private fun SavedStateHandle.copy() = SavedStateHandle(keys().associateWith { get<Any>(it) })

    @Test
    fun `a pick cancels a barcode lookup in progress`() = runTest {
        val scanned = MARIO_KART_LOOKUP.copy(title = "Mario Kart 8 Deluxe (Bundle)", region = Region.PAL)
        lookup.results = mapOf(MARIO_KART_LOOKUP.barcode to BarcodeLookupResult.Found(scanned))
        val gate = CompletableDeferred<Unit>()
        lookup.gate = gate
        val viewModel = viewModel("barcode" to MARIO_KART_LOOKUP.barcode)
        advanceUntilIdle()
        assertEquals(BarcodeLookupStatus.Loading, viewModel.uiState.value.lookup)

        viewModel.applyGameSearchPick(searchPick())
        gate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BarcodeLookupStatus.Found(listOf("IGDB")), state.lookup)
        assertEquals("Mario Kart 8 Deluxe", state.form.title)
        assertNull(state.form.region)
        assertEquals(MARIO_KART_LOOKUP.barcode, state.form.barcode)
    }
}
