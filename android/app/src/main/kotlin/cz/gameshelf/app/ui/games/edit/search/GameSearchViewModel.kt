package cz.gameshelf.app.ui.games.edit.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cz.gameshelf.app.data.lookup.GameSearchOutcome
import cz.gameshelf.app.data.lookup.GameSearchRepository
import cz.gameshelf.app.domain.model.GameSearchResult
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.appContainer
import cz.gameshelf.app.ui.common.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameSearchUiState(
    /** The search field, exactly as typed. */
    val query: String,
    val content: GameSearchContent = GameSearchContent.Hint,
    /** A search is waiting or running; [content] still shows the previous results, if any. */
    val isSearching: Boolean = false,
    /** A picked game with several platforms, while the user chooses theirs. */
    val platformChoice: GameSearchResult? = null,
)

sealed interface GameSearchContent {
    /** Fewer than [GameSearchViewModel.MIN_QUERY_LENGTH] characters typed. */
    data object Hint : GameSearchContent

    /** Searching, with no results to show yet. */
    data object Loading : GameSearchContent

    data class Results(val query: String, val games: List<GameSearchResult>, val sources: List<String>) :
        GameSearchContent

    data class NoResults(val query: String) : GameSearchContent

    data class Failed(val message: UiText) : GameSearchContent
}

/** A game picked in the search, with the platform chosen for it (`null`: leave Platform as it is). */
data class GameSearchPick(val game: GameSearchResult, val platform: Platform?, val sources: List<String>)

sealed interface GameSearchEvent {
    /** Fill the form from [pick] and close the search. */
    data class Picked(val pick: GameSearchPick) : GameSearchEvent
}

/**
 * "Search game database" opened from the form: searches as the user types (debounced; a newer search
 * cancels an older one, so a late answer is ignored) and decides the platform of the picked game.
 */
class GameSearchViewModel(
    private val repository: GameSearchRepository,
    initialQuery: String,
    /** The platform chosen in the form: sent with every search and kept for the picked game. */
    private val formPlatform: Platform?,
    private val debounceMillis: Long = SEARCH_DEBOUNCE_MILLIS,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameSearchUiState(query = initialQuery.trim()))
    val uiState: StateFlow<GameSearchUiState> = _uiState.asStateFlow()

    private val _events = Channel<GameSearchEvent>(Channel.BUFFERED)
    val events: Flow<GameSearchEvent> = _events.receiveAsFlow()

    private var searchJob: Job? = null

    /** Trimmed query of the search waiting, running or shown; `null` while the hint is shown. */
    private var searchedQuery: String? = null

    init {
        // The title from the form is searched right away.
        val query = _uiState.value.query
        if (query.length >= MIN_QUERY_LENGTH) search(query, delayMillis = 0)
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        val query = value.trim()
        if (query == searchedQuery) return
        if (query.length < MIN_QUERY_LENGTH) {
            searchJob?.cancel()
            searchedQuery = null
            _uiState.update { it.copy(content = GameSearchContent.Hint, isSearching = false) }
        } else {
            search(query, delayMillis = debounceMillis)
        }
    }

    fun retry() {
        searchedQuery?.let { search(it, delayMillis = 0) }
    }

    fun pick(game: GameSearchResult) {
        when (val choice = PlatformChoice.of(formPlatform, game.platforms)) {
            is PlatformChoice.Decided -> finish(game, choice.platform)
            is PlatformChoice.Ask -> _uiState.update { it.copy(platformChoice = game) }
        }
    }

    /** The answer to "Which platform is your copy for?"; `null` is "Other platform". */
    fun choosePlatform(platform: Platform?) {
        val game = _uiState.value.platformChoice ?: return
        _uiState.update { it.copy(platformChoice = null) }
        finish(game, platform)
    }

    /** "Cancel" in the platform question: back to the results. */
    fun dismissPlatformChoice() = _uiState.update { it.copy(platformChoice = null) }

    private fun search(query: String, delayMillis: Long) {
        searchJob?.cancel()
        searchedQuery = query
        _uiState.update { state ->
            state.copy(
                content = state.content as? GameSearchContent.Results ?: GameSearchContent.Loading,
                isSearching = true,
            )
        }
        searchJob = viewModelScope.launch {
            delay(delayMillis)
            val outcome = repository.search(query.take(MAX_QUERY_LENGTH).trimEnd(), formPlatform)
            val content = when (outcome) {
                is GameSearchOutcome.Found -> {
                    val games = outcome.response.items.distinctBy { it.igdbId }
                    if (games.isEmpty()) {
                        GameSearchContent.NoResults(query)
                    } else {
                        GameSearchContent.Results(query, games, outcome.response.sources)
                    }
                }
                is GameSearchOutcome.Failed -> GameSearchContent.Failed(outcome.error.toUiText())
            }
            _uiState.update { it.copy(content = content, isSearching = false) }
        }
    }

    private fun finish(game: GameSearchResult, platform: Platform?) {
        val sources = (_uiState.value.content as? GameSearchContent.Results)?.sources.orEmpty()
        _events.trySend(GameSearchEvent.Picked(GameSearchPick(game, platform, sources)))
    }

    companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 400L
        const val MIN_QUERY_LENGTH = 2

        /** Longer text is cut for the request (the API accepts at most 100 characters). */
        const val MAX_QUERY_LENGTH = 100

        fun factory(initialQuery: String, formPlatform: Platform?): ViewModelProvider.Factory = viewModelFactory {
            initializer { GameSearchViewModel(appContainer.gameSearchRepository, initialQuery, formPlatform) }
        }
    }
}
