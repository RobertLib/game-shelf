package cz.gameshelf.app.ui.games.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import cz.gameshelf.app.R
import cz.gameshelf.app.data.games.GameChange
import cz.gameshelf.app.data.games.GamesRepository
import cz.gameshelf.app.domain.model.ApiResult
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.appContainer
import cz.gameshelf.app.ui.common.toUiText
import cz.gameshelf.app.ui.navigation.GameDetail
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameDetailUiState(
    val game: Game? = null,
    val isLoading: Boolean = true,
    val loadError: UiText? = null,
    val isUpdatingFavorite: Boolean = false,
    val isDeleting: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
)

sealed interface GameDetailEvent {
    data class ShowMessage(val message: UiText) : GameDetailEvent
    data object Deleted : GameDetailEvent
}

class GameDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: GamesRepository,
) : ViewModel() {

    private val gameId: String = savedStateHandle.toRoute<GameDetail>().gameId

    private val _uiState = MutableStateFlow(GameDetailUiState())
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<GameDetailEvent>(Channel.BUFFERED)
    val events: Flow<GameDetailEvent> = _events.receiveAsFlow()

    init {
        load()
        viewModelScope.launch {
            repository.changes.collect { change ->
                if (change is GameChange.Updated && change.game.id == gameId) {
                    _uiState.update { it.copy(game = change.game) }
                }
            }
        }
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.game(gameId)) {
                is ApiResult.Success -> _uiState.update { it.copy(game = result.value, isLoading = false) }
                is ApiResult.Failure -> _uiState.update { it.copy(isLoading = false, loadError = result.error.toUiText()) }
            }
        }
    }

    /** Optimistic toggle; reverted when the `PUT` fails. */
    fun toggleFavorite() {
        val state = _uiState.value
        val game = state.game ?: return
        if (state.isUpdatingFavorite || state.isDeleting) return
        val favorite = !game.favorite
        _uiState.update { it.copy(game = game.copy(favorite = favorite), isUpdatingFavorite = true) }
        viewModelScope.launch {
            when (val result = repository.setFavorite(game, favorite)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(game = result.value, isUpdatingFavorite = false) }
                    _events.send(
                        GameDetailEvent.ShowMessage(
                            UiText(if (favorite) R.string.favorite_added else R.string.favorite_removed),
                        ),
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(game = game, isUpdatingFavorite = false) }
                    _events.send(GameDetailEvent.ShowMessage(result.error.toUiText()))
                }
            }
        }
    }

    fun requestDelete() = _uiState.update { it.copy(showDeleteConfirmation = true) }

    fun dismissDelete() = _uiState.update { it.copy(showDeleteConfirmation = false) }

    fun confirmDelete() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(showDeleteConfirmation = false, isDeleting = true) }
        viewModelScope.launch {
            when (val result = repository.deleteGame(gameId)) {
                is ApiResult.Success -> _events.send(GameDetailEvent.Deleted)
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(isDeleting = false) }
                    _events.send(GameDetailEvent.ShowMessage(result.error.toUiText()))
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { GameDetailViewModel(createSavedStateHandle(), appContainer.gamesRepository) }
        }
    }
}
