package cz.gameshelf.app.ui.games.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.ui.common.Formatters
import cz.gameshelf.app.ui.common.ObserveAsEvents
import cz.gameshelf.app.ui.common.labelRes
import cz.gameshelf.app.ui.common.resolve
import cz.gameshelf.app.ui.components.ConfirmDialog
import cz.gameshelf.app.ui.components.ErrorContent
import cz.gameshelf.app.ui.components.GameCover
import cz.gameshelf.app.ui.components.LabelBadge
import cz.gameshelf.app.ui.components.LoadingContent
import cz.gameshelf.app.ui.components.NavigationIconButton
import cz.gameshelf.app.ui.games.list.PreviewGame
import cz.gameshelf.app.ui.theme.GameShelfTheme
import cz.gameshelf.app.ui.theme.GameShelfThemeExtras
import kotlinx.coroutines.launch

@Composable
fun GameDetailRoute(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: GameDetailViewModel = viewModel(factory = GameDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is GameDetailEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.resolve(resources))
            }
            // The list shows the confirmation snackbar.
            GameDetailEvent.Deleted -> onBack()
        }
    }

    GameDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onEdit = { state.game?.let { onEdit(it.id) } },
        onToggleFavorite = viewModel::toggleFavorite,
        onDelete = viewModel::requestDelete,
    )

    val game = state.game
    if (state.showDeleteConfirmation && game != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_game_title),
            text = stringResource(R.string.delete_game_message, game.title),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    state: GameDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val game = state.game
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { NavigationIconButton(onClick = onBack) },
                actions = {
                    if (game != null) {
                        GameDetailActions(
                            game = game,
                            onToggleFavorite = onToggleFavorite,
                            onEdit = onEdit,
                            onDelete = onDelete,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            game != null -> GameDetailContent(game, padding)
            state.isLoading -> LoadingContent(Modifier.padding(padding))
            else -> ErrorContent(
                message = stringResource(R.string.error_game_not_found),
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun GameDetailActions(
    game: Game,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    IconButton(onClick = onToggleFavorite) {
        Icon(
            painter = painterResource(if (game.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline),
            contentDescription = stringResource(if (game.favorite) R.string.favorite_remove else R.string.favorite_add),
            tint = if (game.favorite) {
                GameShelfThemeExtras.colors.favorite
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
    IconButton(onClick = onEdit) {
        Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.action_edit))
    }
    IconButton(onClick = onDelete) {
        Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.action_delete))
    }
}

@Composable
private fun GameDetailContent(game: Game, padding: PaddingValues) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (!game.coverImageUrl.isNullOrBlank()) {
                GameCover(
                    coverUrl = game.coverImageUrl,
                    platform = game.platform,
                    title = game.title,
                    contentScale = ContentScale.Fit,
                    shape = RoundedCornerShape(16.dp),
                    placeholderStyle = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                )
            }
            GameHeader(game)
            GameDetailSections(game)
        }
    }
}

@Composable
private fun GameHeader(game: Game) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(game.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = listOfNotNull(stringResource(game.platform.labelRes), game.edition).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LabelBadge(
                text = stringResource(game.status.labelRes),
                containerColor = if (game.status == CollectionStatus.OWNED) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
            )
            game.estimatedValue?.let {
                LabelBadge(
                    text = Formatters.money(it, game.currency),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
    }
}

@Composable
private fun GameDetailSections(game: Game) {
    DetailSection(
        title = stringResource(R.string.section_basic),
        fields = listOfNotNull(
            DetailField(stringResource(R.string.field_platform), stringResource(game.platform.labelRes)),
            game.edition?.let { DetailField(stringResource(R.string.field_edition), it) },
            game.genre?.let { DetailField(stringResource(R.string.field_genre), it) },
            game.developer?.let { DetailField(stringResource(R.string.field_developer), it) },
            game.publisher?.let { DetailField(stringResource(R.string.field_publisher), it) },
            game.releaseYear?.let { DetailField(stringResource(R.string.field_release_year), it.toString()) },
        ),
    )
    DetailSection(
        title = stringResource(R.string.section_collector),
        fields = listOfNotNull(
            DetailField(stringResource(R.string.field_status), stringResource(game.status.labelRes)),
            DetailField(stringResource(R.string.field_format), stringResource(game.format.labelRes)),
            game.region?.let { DetailField(stringResource(R.string.field_region), stringResource(it.labelRes)) },
            game.completeness?.let { DetailField(stringResource(R.string.field_completeness), stringResource(it.labelRes)) },
            game.condition?.let { DetailField(stringResource(R.string.field_condition), stringResource(it.labelRes)) },
            game.barcode?.let { DetailField(stringResource(R.string.field_barcode), it) },
            game.productCode?.let { DetailField(stringResource(R.string.field_product_code), it) },
            DetailField(stringResource(R.string.field_quantity), game.quantity.toString()),
            game.storageLocation?.let { DetailField(stringResource(R.string.field_storage), it) },
        ),
    )
    DetailSection(
        title = stringResource(R.string.section_purchase),
        fields = listOfNotNull(
            game.purchasePrice?.let {
                DetailField(stringResource(R.string.field_purchase_price), Formatters.money(it, game.currency))
            },
            game.estimatedValue?.let {
                DetailField(stringResource(R.string.field_estimated_value), Formatters.money(it, game.currency))
            },
            game.purchaseDate?.let { DetailField(stringResource(R.string.field_purchase_date), Formatters.date(it)) },
            game.purchasePlace?.let { DetailField(stringResource(R.string.field_purchase_place), it) },
        ),
    )
    DetailSection(
        title = stringResource(R.string.section_other),
        fields = listOfNotNull(
            game.playStatus?.let { DetailField(stringResource(R.string.field_play_status), stringResource(it.labelRes)) },
            game.rating?.let { DetailField(stringResource(R.string.field_rating), stringResource(R.string.rating_value, it)) },
            if (game.favorite) DetailField(stringResource(R.string.field_favorite), stringResource(R.string.value_yes)) else null,
            game.notes?.let { DetailField(stringResource(R.string.field_notes), it, multiline = true) },
            DetailField(stringResource(R.string.field_created_at), Formatters.dateTime(game.createdAt)),
            DetailField(stringResource(R.string.field_updated_at), Formatters.dateTime(game.updatedAt)),
        ),
    )
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun GameDetailScreenPreview() {
    GameShelfTheme(dynamicColor = false) {
        GameDetailScreen(
            state = GameDetailUiState(game = PreviewGame, isLoading = false),
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onEdit = {},
            onToggleFavorite = {},
            onDelete = {},
        )
    }
}
