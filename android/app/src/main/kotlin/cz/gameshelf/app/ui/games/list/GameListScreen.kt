package cz.gameshelf.app.ui.games.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.ActiveFilter
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.GameSortField
import cz.gameshelf.app.domain.model.SortOrder
import cz.gameshelf.app.ui.common.ObserveAsEvents
import cz.gameshelf.app.ui.common.asString
import cz.gameshelf.app.ui.common.resolve
import cz.gameshelf.app.ui.components.EmptyContent
import cz.gameshelf.app.ui.components.ErrorContent
import cz.gameshelf.app.ui.components.LoadingContent
import cz.gameshelf.app.ui.games.filter.FilterSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Start loading the next page when this many rows remain below the last visible one. */
private const val LOAD_MORE_THRESHOLD = 5

@Composable
fun GameListRoute(
    onGameClick: (String) -> Unit,
    onAddGame: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: GameListViewModel = viewModel(factory = GameListViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is GameListEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.resolve(resources))
            }
            GameListEvent.ScrollToTop -> scope.launch { listState.scrollToItem(0) }
        }
    }

    GameListScreen(
        state = state,
        listState = listState,
        snackbarHostState = snackbarHostState,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onSortFieldSelect = viewModel::setSortField,
        onSortOrderSelect = viewModel::setSortOrder,
        onOpenFilters = viewModel::openFilters,
        onRemoveFilter = viewModel::removeFilter,
        onClearFilters = viewModel::clearFilters,
        onClearSearchAndFilters = viewModel::clearSearchAndFilters,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadNextPage,
        onRetryLoadMore = viewModel::retryLoadMore,
        onGameClick = onGameClick,
        onAddGame = onAddGame,
        onOpenProfile = onOpenProfile,
    )

    state.filterDraft?.let { draft ->
        FilterSheet(
            draft = draft,
            facets = state.facets,
            onDraftChange = viewModel::updateFilterDraft,
            onApply = viewModel::applyFilterDraft,
            onReset = viewModel::resetFilterDraft,
            onDismiss = viewModel::dismissFilters,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(
    state: GameListUiState,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    onSearchQueryChange: (String) -> Unit,
    onSortFieldSelect: (GameSortField) -> Unit,
    onSortOrderSelect: (SortOrder) -> Unit,
    onOpenFilters: () -> Unit,
    onRemoveFilter: (ActiveFilter) -> Unit,
    onClearFilters: () -> Unit,
    onClearSearchAndFilters: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onGameClick: (String) -> Unit,
    onAddGame: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.list_title)) },
                actions = {
                    SortMenuButton(state.sort, onSortFieldSelect, onSortOrderSelect)
                    FilterActionButton(state.activeFilterCount, onOpenFilters)
                    IconButton(onClick = onOpenProfile) {
                        Icon(
                            painterResource(R.drawable.ic_account_circle),
                            contentDescription = stringResource(R.string.action_profile),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.action_add_game)) },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                onClick = onAddGame,
                expanded = fabExpanded,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(layoutDirection),
                ),
        ) {
            SearchField(
                query = state.searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            val activeFilters = state.filter.activeFilters
            if (activeFilters.isNotEmpty()) {
                ActiveFiltersRow(
                    filters = activeFilters,
                    onRemove = onRemoveFilter,
                    onClearAll = onClearFilters,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            ResultsHeader(state)

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val bottomPadding = padding.calculateBottomPadding()
                when {
                    state.isLoading && state.games.isEmpty() -> LoadingContent()
                    state.loadError != null -> ScrollableFill {
                        ErrorContent(message = state.loadError.asString(), onRetry = onRetry)
                    }
                    state.showEmptyCollection -> ScrollableFill {
                        EmptyContent(
                            iconRes = R.drawable.ic_inventory_2,
                            title = stringResource(R.string.empty_collection_title),
                            message = stringResource(R.string.empty_collection_message),
                            actionLabel = stringResource(R.string.empty_collection_action),
                            onAction = onAddGame,
                        )
                    }
                    state.showNoResults -> ScrollableFill {
                        EmptyContent(
                            iconRes = R.drawable.ic_search_off,
                            title = stringResource(R.string.no_results_title),
                            message = stringResource(R.string.no_results_message),
                            actionLabel = stringResource(R.string.no_results_action),
                            onAction = onClearSearchAndFilters,
                        )
                    }
                    else -> GameList(
                        games = state.games,
                        listState = listState,
                        isLoadingMore = state.isLoadingMore,
                        loadMoreFailed = state.loadMoreFailed,
                        contentPadding = PaddingValues(bottom = bottomPadding + 88.dp),
                        onGameClick = onGameClick,
                        onLoadMore = onLoadMore,
                        onRetryLoadMore = onRetryLoadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun ResultsHeader(state: GameListUiState) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val hasCount = state.loadError == null && !state.showEmptyCollection && !(state.isLoading && state.games.isEmpty())
        if (hasCount) {
            Text(
                text = pluralStringResource(R.plurals.games_count, state.totalItems, state.totalItems),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    if (state.isLoading && state.games.isNotEmpty()) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    } else {
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun GameList(
    games: List<Game>,
    listState: LazyListState,
    isLoadingMore: Boolean,
    loadMoreFailed: Boolean,
    contentPadding: PaddingValues,
    onGameClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
) {
    // Re-evaluated after every appended page, so a short page right at the end still triggers the next one.
    LaunchedEffect(listState, games.size) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            layoutInfo.totalItemsCount > 0 && lastVisible >= layoutInfo.totalItemsCount - 1 - LOAD_MORE_THRESHOLD
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(games, key = { it.id }, contentType = { "game" }) { game ->
            GameRow(
                game = game,
                onClick = { onGameClick(game.id) },
                modifier = Modifier.animateItem(),
            )
        }
        item(key = "footer", contentType = "footer") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoadingMore -> CircularProgressIndicator()
                    loadMoreFailed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.list_load_more_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = onRetryLoadMore, modifier = Modifier.padding(top = 8.dp)) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            }
        }
    }
}

/** Lets a non-scrolling state (empty, error) take the full height and still support pull-to-refresh. */
@Composable
private fun ScrollableFill(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
    }
}
