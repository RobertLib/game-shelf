package cz.gameshelf.app.ui.games.edit.search

import android.graphics.Color
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.GameSearchResult
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.ui.common.ObserveAsEvents
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.asString
import cz.gameshelf.app.ui.common.labelRes
import cz.gameshelf.app.ui.components.EmptyContent
import cz.gameshelf.app.ui.components.ErrorContent
import cz.gameshelf.app.ui.components.LoadingContent
import cz.gameshelf.app.ui.components.NavigationIconButton
import cz.gameshelf.app.ui.theme.GameShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

/**
 * "Search game database" over the game form, shown while composed. Its view model lives only as long as
 * this dialog (it survives rotation), so every opening starts from the form's current title and platform.
 */
@Composable
fun GameSearchRoute(
    initialQuery: String,
    formPlatform: Platform?,
    onPick: (GameSearchPick) -> Unit,
    onClose: () -> Unit,
) {
    val viewModelStoreOwner = rememberViewModelStoreOwner()
    val viewModel: GameSearchViewModel = viewModel(
        viewModelStoreOwner = viewModelStoreOwner,
        factory = GameSearchViewModel.factory(initialQuery, formPlatform),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is GameSearchEvent.Picked -> onPick(event.pick)
        }
    }

    GameSearchDialog(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onRetry = viewModel::retry,
        onPick = viewModel::pick,
        onChoosePlatform = viewModel::choosePlatform,
        onDismissPlatformChoice = viewModel::dismissPlatformChoice,
        onClose = onClose,
    )
}

/** Full-screen dialog; closing it changes nothing. */
@Composable
fun GameSearchDialog(
    state: GameSearchUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onPick: (GameSearchResult) -> Unit,
    onChoosePlatform: (Platform?) -> Unit,
    onDismissPlatformChoice: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        // Edge to edge like the activity; the Scaffold handles the system bar and keyboard insets.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        TransparentSystemBars()
        GameSearchScreen(
            state = state,
            onQueryChange = onQueryChange,
            onRetry = onRetry,
            onPick = onPick,
            onClose = onClose,
        )
        state.platformChoice?.let { game ->
            PlatformChoiceDialog(
                platforms = game.platforms,
                onChoose = onChoosePlatform,
                onDismiss = onDismissPlatformChoice,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSearchScreen(
    state: GameSearchUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onPick: (GameSearchResult) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.game_search_title)) },
                navigationIcon = {
                    NavigationIconButton(
                        onClick = onClose,
                        iconRes = R.drawable.ic_close,
                        contentDescriptionRes = R.string.action_close,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            SearchField(
                initialQuery = state.query,
                onQueryChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val announced = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                when (val content = state.content) {
                    GameSearchContent.Hint -> EmptyContent(
                        iconRes = R.drawable.ic_search,
                        title = stringResource(R.string.game_search_hint),
                        modifier = announced,
                    )
                    GameSearchContent.Loading -> LoadingContent(
                        message = stringResource(R.string.game_search_searching),
                        modifier = announced,
                    )
                    is GameSearchContent.Results -> {
                        SearchResults(content, onPick)
                        if (state.isSearching) {
                            LinearProgressIndicator(
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter),
                            )
                        }
                    }
                    is GameSearchContent.NoResults -> EmptyContent(
                        iconRes = R.drawable.ic_search_off,
                        title = stringResource(R.string.game_search_no_results, content.query),
                        message = stringResource(R.string.game_search_no_results_message),
                        modifier = announced,
                    )
                    is GameSearchContent.Failed -> ErrorContent(
                        title = stringResource(R.string.game_search_failed),
                        message = content.message.asString(),
                        onRetry = onRetry,
                        modifier = announced,
                    )
                }
            }
        }
    }
}

/** Holds its own text and selection (the cursor starts after the title); the view model gets the text. */
@Composable
private fun SearchField(
    initialQuery: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialQuery, TextRange(initialQuery.length)))
    }
    fun change(newValue: TextFieldValue) {
        value = newValue
        onQueryChange(newValue.text)
    }

    val focusRequester = remember { FocusRequester() }
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(focusRequester, windowInfo) {
        // The keyboard opens only once the dialog's window has focus.
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        focusRequester.requestFocus()
    }
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = value,
        onValueChange = ::change,
        modifier = modifier.focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.game_search_field)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            if (value.text.isNotEmpty()) {
                IconButton(onClick = { change(TextFieldValue()) }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun SearchResults(content: GameSearchContent.Results, onPick: (GameSearchResult) -> Unit) {
    // A new query starts at the top; rotation keeps the position.
    val listState = rememberSaveable(content.query, saver = LazyListState.Saver) { LazyListState() }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(content.games, key = { it.igdbId }) { game ->
            GameSearchResultRow(game = game, onClick = { onPick(game) })
        }
        if (content.sources.isNotEmpty()) {
            item(key = "sources") {
                Text(
                    text = stringResource(R.string.lookup_sources, content.sources.joinToString(" · ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** "Which platform is your copy for?" – the game's platforms in their order; `null` is "Other platform". */
@Composable
private fun PlatformChoiceDialog(
    platforms: List<Platform>,
    onChoose: (Platform?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_search_platform_question)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                platforms.forEach { platform ->
                    Text(
                        text = stringResource(platform.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { onChoose(platform) }
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoose(null) }) { Text(stringResource(R.string.game_search_other_platform)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * A dialog window has system bars of its own: like the activity's (`enableEdgeToEdge`), they are
 * transparent, with icons matching the theme.
 */
@Composable
private fun TransparentSystemBars() {
    val view = LocalView.current
    val lightBars = !isSystemInDarkTheme()
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Android 15+ ignores bar colors and draws them transparent.
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GameSearchScreenPreview() {
    GameShelfTheme(dynamicColor = false) {
        GameSearchScreen(
            state = GameSearchUiState(
                query = "mario kart",
                content = GameSearchContent.Results(
                    query = "mario kart",
                    games = listOf(
                        PreviewSearchResult,
                        PreviewSearchResult.copy(
                            igdbId = 1,
                            title = "Mario Kart 64",
                            platforms = listOf(Platform.N64, Platform.WII, Platform.WII_U, Platform.SWITCH),
                            developer = "Nintendo EAD",
                            releaseYear = 1996,
                        ),
                    ),
                    sources = listOf("IGDB"),
                ),
                isSearching = true,
            ),
            onQueryChange = {},
            onRetry = {},
            onPick = {},
            onClose = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GameSearchScreenFailedPreview() {
    GameShelfTheme(dynamicColor = false) {
        GameSearchScreen(
            state = GameSearchUiState(
                query = "mario kart",
                content = GameSearchContent.Failed(UiText(R.string.error_lookup_unavailable)),
            ),
            onQueryChange = {},
            onRetry = {},
            onPick = {},
            onClose = {},
        )
    }
}
