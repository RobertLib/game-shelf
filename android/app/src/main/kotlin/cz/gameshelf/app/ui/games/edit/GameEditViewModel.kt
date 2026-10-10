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
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.appContainer
import cz.gameshelf.app.ui.common.toUiText
import cz.gameshelf.app.ui.games.edit.search.GameSearchPick
import cz.gameshelf.app.ui.navigation.GameEdit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class GameEditUiState(
    val isEditing: Boolean,
    /** What the user entered; with [initialForm], it survives process death (see [GameEditViewModel]). */
    val form: GameForm = GameForm(),
    /** The form as it was opened (or last saved): the base of change detection and "Discard changes?". */
    val initialForm: GameForm = form,
    val errors: Map<GameField, UiText> = emptyMap(),
    val isLoading: Boolean = false,
    val loadError: UiText? = null,
    val isSaving: Boolean = false,
    val showDiscardDialog: Boolean = false,
    val suggestions: FormSuggestions = FormSuggestions(),
    /** Filling the form from the game database: a barcode lookup, or a game picked in the search. */
    val lookup: BarcodeLookupStatus? = null,
    /**
     * A game in the collection like the one filled in: with the scanned barcode, or with the title and
     * platform of the game picked in the search.
     */
    val duplicate: Game? = null,
    /** "Search game database" is open. */
    val showGameSearch: Boolean = false,
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

/**
 * Progress and outcome of filling the form from the game database: looking up a scanned barcode, or a
 * game picked in the database search (always [Found]).
 */
sealed interface BarcodeLookupStatus {
    data object Loading : BarcodeLookupStatus

    /** The form was filled in; [sources] are the databases to credit. */
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
 * they know fills the fields that are still empty. A game picked in the database search (see
 * [GameSearchPick]) replaces the fields it knows instead.
 *
 * The form and the state it started from are kept in the [SavedStateHandle], so an edit survives the
 * process being killed in the background – including what counts as changed.
 */
class GameEditViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: GamesRepository,
    private val barcodeLookup: BarcodeLookupRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<GameEdit>()
    private val gameId: String? = route.gameId

    /** The form as it was when the previous process was killed, if it was. */
    private val restored: SavedForm? = savedStateHandle.get<String>(KEY_FORM)?.let(::decodeSavedForm)

    private val _uiState = MutableStateFlow(
        restored?.let { GameEditUiState(isEditing = gameId != null, form = it.form, initialForm = it.initialForm) }
            ?: GameEditUiState(isEditing = gameId != null, isLoading = gameId != null),
    )
    val uiState: StateFlow<GameEditUiState> = _uiState.asStateFlow()

    private val _events = Channel<GameEditEvent>(Channel.BUFFERED)
    val events: Flow<GameEditEvent> = _events.receiveAsFlow()

    /** After the first save attempt, errors follow the input live. */
    private var validateOnChange = false

    /** The barcode lookup, or the duplicate check after a search pick. */
    private var lookupJob: Job? = null

    init {
        when {
            restored != null -> Unit
            gameId != null -> loadGame(gameId)
            // Scanned before the form opened ("Scan barcode" in the list).
            else -> route.barcode?.let(::onBarcodeScanned)
        }
        viewModelScope.launch {
            _uiState
                .filter { !it.isLoading && it.loadError == null }
                .map { SavedForm(it.form, it.initialForm) }
                .distinctUntilChanged()
                .collect { savedStateHandle[KEY_FORM] = SavedStateJson.encodeToString(SavedForm.serializer(), it) }
        }
        viewModelScope.launch {
            val games = repository.games.first()
            val suggestions = withContext(computeDispatcher) { GameFacetsCalculator.calculate(games).toSuggestions() }
            _uiState.update { it.copy(suggestions = suggestions) }
        }
    }

    fun updateForm(transform: (GameForm) -> GameForm) = _uiState.update { state ->
        val updated = state.copy(form = transform(state.form))
        if (validateOnChange) updated.copy(errors = updated.validationErrors()) else updated
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        validateOnChange = true
        when (val validation = state.validate()) {
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
                        // Only the fields edited in this form are saved, not stale copies of the others. The
                        // request the edit started from is built the same way, so untouched values never differ.
                        val initial = state.initialForm
                        val base = (GameFormValidator.validate(initial, initial) as? GameFormValidation.Valid)?.request
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

    fun openGameSearch() = _uiState.update { it.copy(showGameSearch = true) }

    fun closeGameSearch() = _uiState.update { it.copy(showGameSearch = false) }

    /**
     * A game picked in the database search: replaces what the database knows about it and closes the search.
     * A barcode lookup still in progress is cancelled, so it can't overwrite the pick.
     */
    fun applyGameSearchPick(pick: GameSearchPick) {
        lookupJob?.cancel()
        updateForm { it.fillFrom(pick.game, pick.platform) }
        _uiState.update {
            it.copy(showGameSearch = false, lookup = BarcodeLookupStatus.Found(pick.sources), duplicate = null)
        }
        val form = _uiState.value.form
        lookupJob = viewModelScope.launch {
            val duplicate = findSameGame(form.title, form.platform)
            _uiState.update { it.copy(duplicate = duplicate) }
        }
    }

    /** Another game in the collection with the same title (trimmed, ignoring case) on the same platform. */
    private suspend fun findSameGame(title: String, platform: Platform?): Game? {
        if (platform == null) return null
        val trimmed = title.trim()
        return repository.games.first().firstOrNull { game ->
            game.id != gameId && game.platform == platform && game.title.trim().equals(trimmed, ignoreCase = true)
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

    /** An edit validates only the values the user changed (mobile-spec.md, "Validation"); a new game all of them. */
    private fun GameEditUiState.validate(): GameFormValidation =
        GameFormValidator.validate(form, initial = initialForm.takeIf { isEditing })

    private fun GameEditUiState.validationErrors(): Map<GameField, UiText> =
        (validate() as? GameFormValidation.Invalid)?.errors.orEmpty()

    private fun GameFacets.toSuggestions() = FormSuggestions(
        genres = genres.map { it.value },
        publishers = publishers.map { it.value },
        developers = developers.map { it.value },
        storageLocations = storageLocations.map { it.value },
    )

    /** What the saved state keeps of the form. */
    @Serializable
    private data class SavedForm(val form: GameForm, val initialForm: GameForm)

    companion object {
        private const val KEY_FORM = "gameEditForm"
        private val SavedStateJson = Json { ignoreUnknownKeys = true }

        /** `null` for a saved form this app version can't read; the form then starts afresh. */
        private fun decodeSavedForm(json: String): SavedForm? =
            runCatching { SavedStateJson.decodeFromString(SavedForm.serializer(), json) }.getOrNull()

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
