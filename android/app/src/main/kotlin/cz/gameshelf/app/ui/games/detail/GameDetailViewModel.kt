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
import cz.gameshelf.app.data.games.GamesRepository
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.appContainer
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
    /** The stored game is still being read. */
    val isLoading: Boolean = true,
    val showDeleteConfirmation: Boolean = false,
) {
    /** Not stored (any more), e.g. deleted on another device. */
    val isNotFound: Boolean get() = !isLoading && game == null
}

sealed interface GameDetailEvent {
    data class ShowMessage(val message: UiText) : GameDetailEvent
    data object Deleted : GameDetailEvent
}

/** Follows the stored game, so a sync updates the screen; favorite and delete are saved locally at once. */
class GameDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: GamesRepository,
) : ViewModel() {

    private val gameId: String = savedStateHandle.toRoute<GameDetail>().gameId

    private val _uiState = MutableStateFlow(GameDetailUiState())
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<GameDetailEvent>(Channel.BUFFERED)
    val events: Flow<GameDetailEvent> = _events.receiveAsFlow()

    /** Set once the user deleted the game: the screen keeps showing it until it is closed. */
    private var deleted = false

    init {
        viewModelScope.launch {
            repository.observeGame(gameId).collect { game ->
                if (game != null || !deleted) _uiState.update { it.copy(game = game, isLoading = false) }
            }
        }
    }

    fun toggleFavorite() {
        val game = _uiState.value.game ?: return
        if (deleted) return
        val favorite = !game.favorite
        viewModelScope.launch {
            if (repository.setFavorite(gameId, favorite) != null) {
                _events.send(
                    GameDetailEvent.ShowMessage(
                        UiText(if (favorite) R.string.favorite_added else R.string.favorite_removed),
                    ),
                )
            }
        }
    }

    fun requestDelete() = _uiState.update { it.copy(showDeleteConfirmation = true) }

    fun dismissDelete() = _uiState.update { it.copy(showDeleteConfirmation = false) }

    fun confirmDelete() {
        if (deleted) return
        deleted = true
        _uiState.update { it.copy(showDeleteConfirmation = false) }
        viewModelScope.launch {
            repository.deleteGame(gameId)
            _events.send(GameDetailEvent.Deleted)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { GameDetailViewModel(createSavedStateHandle(), appContainer.gamesRepository) }
        }
    }
}
