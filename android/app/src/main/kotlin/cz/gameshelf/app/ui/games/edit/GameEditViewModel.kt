package cz.gameshelf.app.ui.games.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import cz.gameshelf.app.R
import cz.gameshelf.app.data.games.GamesRepository
import cz.gameshelf.app.data.lookup.BarcodeLookupRepository
import cz.gameshelf.app.data.lookup.BarcodeLookupResult
import cz.gameshelf.app.domain.collection.GameFacetsCalculator
import cz.gameshelf.app.domain.model.Barcodes
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.GameFacets
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.appContainer
import cz.gameshelf.app.ui.common.toUiText
import cz.gameshelf.app.ui.navigation.GameEdit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GameEditUiState(
    val isEditing: Boolean,
    val form: GameForm = GameForm(),
    val initialForm: GameForm = form,
    val errors: Map<GameField, UiText> = emptyMap(),
    val isLoading: Boolean = false,
    val loadError: UiText? = null,
    val isSaving: Boolean = false,
    val showDiscardDialog: Boolean = false,
    val suggestions: FormSuggestions = FormSuggestions(),
    val lookup: BarcodeLookupStatus? = null,
    /** A game in the collection with the scanned barcode. */
    val duplicate: Game? = null,
) {
    val hasChanges: Boolean get() = form != initialForm
    val canSave: Boolean get() = !isLoading && loadError == null && !isSaving
}

/** Values already used in the collection, offered while typing. */
data class FormSuggestions(
    val genres: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val developers: List<String> = emptyList(),
    val storageLocations: List<String> = emptyList(),
)

/** Progress and outcome of looking up a scanned barcode. */
sealed interface BarcodeLookupStatus {
    data object Loading : BarcodeLookupStatus

    data class Found(val sources: List<String>) : BarcodeLookupStatus

    data object NotFound : BarcodeLookupStatus

    data class Failed(val message: UiText) : BarcodeLookupStatus
}

sealed interface GameEditEvent {
    data class ShowMessage(val message: UiText) : GameEditEvent

    /** Saved, or changes discarded: leave the screen. */
    data object Close : GameEditEvent
}

/**
 * Add / edit form. The game is read from and saved to the local collection, so saving works offline.
 * A scanned barcode is looked up in the game databases behind the API, which needs a connection; what
 * they know fills the fields that are still empty.
 */
class GameEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: GamesRepository,
    private val barcodeLookup: BarcodeLookupRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<GameEdit>()
    private val gameId: String? = route.gameId

    private val _uiState = MutableStateFlow(GameEditUiState(isEditing = gameId != null, isLoading = gameId != null))
    val uiState: StateFlow<GameEditUiState> = _uiState.asStateFlow()

    private val _events = Channel<GameEditEvent>(Channel.BUFFERED)
    val events: Flow<GameEditEvent> = _events.receiveAsFlow()

    /** After the first save attempt, errors follow the input live. */
    private var validateOnChange = false

    private var lookupJob: Job? = null

    init {
        if (gameId != null) loadGame(gameId)
        // Scanned before the form opened ("Scan barcode" in the list).
        if (gameId == null) route.barcode?.let(::onBarcodeScanned)
        viewModelScope.launch {
            val games = repository.games.first()
            val suggestions = withContext(computeDispatcher) { GameFacetsCalculator.calculate(games).toSuggestions() }
            _uiState.update { it.copy(suggestions = suggestions) }
        }
    }

    fun updateForm(transform: (GameForm) -> GameForm) = _uiState.update { state ->
        val form = transform(state.form)
        state.copy(form = form, errors = if (validateOnChange) form.errors() else state.errors)
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        validateOnChange = true
        when (val validation = GameFormValidator.validate(state.form)) {
            is GameFormValidation.Invalid -> {
                _uiState.update { it.copy(errors = validation.errors) }
                _events.trySend(GameEditEvent.ShowMessage(UiText(R.string.validation_fix_errors)))
            }
            is GameFormValidation.Valid -> {
                _uiState.update { it.copy(errors = emptyMap(), isSaving = true) }
                viewModelScope.launch {
                    val saved = if (gameId == null) {
                        repository.createGame(validation.request)
                    } else {
                        // Only the fields edited in this form are saved, not stale copies of the others.
                        val base = (GameFormValidator.validate(state.initialForm) as? GameFormValidation.Valid)?.request
                        repository.updateGame(gameId, validation.request, base)
                    }
                    if (saved != null) {
                        _uiState.update { it.copy(isSaving = false, initialForm = it.form) }
                        _events.send(GameEditEvent.Close)
                    } else {
                        // Deleted (e.g. on another device) while being edited.
                        _uiState.update { it.copy(isSaving = false) }
                        _events.send(GameEditEvent.ShowMessage(UiText(R.string.error_game_not_found)))
                    }
                }
            }
        }
    }

    /** A barcode from the camera: fills it in and looks the game up. */
    fun onBarcodeScanned(code: String) {
        val barcode = Barcodes.normalize(code)
        updateForm { it.copy(barcode = barcode) }
        if (Barcodes.isValid(barcode)) {
            lookUp(barcode)
        } else {
            // A code typed by hand in the scanner; the field shows what is wrong with it.
            lookupJob?.cancel()
            _uiState.update { it.copy(lookup = null, duplicate = null) }
        }
    }

    fun retryLookup() {
        val barcode = Barcodes.normalize(_uiState.value.form.barcode)
        if (Barcodes.isValid(barcode)) lookUp(barcode)
    }

    fun dismissLookup() {
        lookupJob?.cancel()
        _uiState.update { it.copy(lookup = null, duplicate = null) }
    }

    private fun lookUp(barcode: String) {
        lookupJob?.cancel()
        _uiState.update { it.copy(lookup = BarcodeLookupStatus.Loading) }
        lookupJob = viewModelScope.launch {
            val duplicate = repository.games.first().firstOrNull { game ->
                game.id != gameId && game.barcode?.let { Barcodes.sameProduct(it, barcode) } == true
            }
            _uiState.update { it.copy(duplicate = duplicate) }

            val status = when (val result = barcodeLookup.lookup(barcode)) {
                is BarcodeLookupResult.Found -> {
                    updateForm { it.fillFrom(result.game) }
                    BarcodeLookupStatus.Found(result.game.sources)
                }
                BarcodeLookupResult.NotFound -> BarcodeLookupStatus.NotFound
                is BarcodeLookupResult.Failed -> BarcodeLookupStatus.Failed(result.error.toUiText())
            }
            _uiState.update { it.copy(lookup = status) }
        }
    }

    /** Back / close: asks first when there are unsaved changes. */
    fun requestClose() {
        if (_uiState.value.hasChanges && !_uiState.value.isSaving) {
            _uiState.update { it.copy(showDiscardDialog = true) }
        } else {
            _events.trySend(GameEditEvent.Close)
        }
    }

    fun dismissDiscardDialog() = _uiState.update { it.copy(showDiscardDialog = false) }

    fun discardChanges() {
        _uiState.update { it.copy(showDiscardDialog = false, form = it.initialForm) }
        _events.trySend(GameEditEvent.Close)
    }

    private fun loadGame(id: String) {
        viewModelScope.launch {
            val game = repository.game(id)
            if (game != null) {
                val form = game.toForm()
                _uiState.update { it.copy(form = form, initialForm = form, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false, loadError = UiText(R.string.error_game_not_found)) }
            }
        }
    }

    private fun GameForm.errors(): Map<GameField, UiText> =
        (GameFormValidator.validate(this) as? GameFormValidation.Invalid)?.errors.orEmpty()

    private fun GameFacets.toSuggestions() = FormSuggestions(
        genres = genres.map { it.value },
        publishers = publishers.map { it.value },
        developers = developers.map { it.value },
        storageLocations = storageLocations.map { it.value },
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GameEditViewModel(
                    createSavedStateHandle(),
                    appContainer.gamesRepository,
                    appContainer.barcodeLookupRepository,
                )
            }
        }
    }
}
