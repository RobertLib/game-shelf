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
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.GameFacets
import cz.gameshelf.app.domain.model.onSuccess
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.appContainer
import cz.gameshelf.app.ui.common.toUiText
import cz.gameshelf.app.ui.navigation.GameEdit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

sealed interface GameEditEvent {
    data class ShowMessage(val message: UiText) : GameEditEvent

    /** Saved, or changes discarded: leave the screen. */
    data object Close : GameEditEvent
}

class GameEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: GamesRepository,
) : ViewModel() {

    private val gameId: String? = savedStateHandle.toRoute<GameEdit>().gameId

    private val _uiState = MutableStateFlow(GameEditUiState(isEditing = gameId != null, isLoading = gameId != null))
    val uiState: StateFlow<GameEditUiState> = _uiState.asStateFlow()

    private val _events = Channel<GameEditEvent>(Channel.BUFFERED)
    val events: Flow<GameEditEvent> = _events.receiveAsFlow()

    /** After the first save attempt, errors follow the input live. */
    private var validateOnChange = false

    init {
        if (gameId != null) loadGame(gameId)
        viewModelScope.launch {
            repository.facets().onSuccess { facets -> _uiState.update { it.copy(suggestions = facets.toSuggestions()) } }
        }
    }

    fun retryLoad() {
        gameId?.let(::loadGame)
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
                    val result = if (gameId == null) {
                        repository.createGame(validation.request)
                    } else {
                        repository.updateGame(gameId, validation.request)
                    }
                    when (result) {
                        is ApiResult.Success -> {
                            _uiState.update { it.copy(isSaving = false, initialForm = it.form) }
                            _events.send(GameEditEvent.Close)
                        }
                        is ApiResult.Failure -> {
                            _uiState.update { it.copy(isSaving = false) }
                            _events.send(GameEditEvent.ShowMessage(result.error.toUiText()))
                        }
                    }
                }
            }
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
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.game(id)) {
                is ApiResult.Success -> {
                    val form = result.value.toForm()
                    _uiState.update { it.copy(form = form, initialForm = form, isLoading = false) }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoading = false, loadError = result.error.toUiText()) }
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
            initializer { GameEditViewModel(createSavedStateHandle(), appContainer.gamesRepository) }
        }
    }
}
