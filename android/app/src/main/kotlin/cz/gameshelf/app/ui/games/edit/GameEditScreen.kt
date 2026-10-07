package cz.gameshelf.app.ui.games.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.gameshelf.app.R
import cz.gameshelf.app.ui.common.ObserveAsEvents
import cz.gameshelf.app.ui.common.asString
import cz.gameshelf.app.ui.common.resolve
import cz.gameshelf.app.ui.components.ConfirmDialog
import cz.gameshelf.app.ui.components.ErrorContent
import cz.gameshelf.app.ui.components.LoadingContent
import cz.gameshelf.app.ui.components.NavigationIconButton
import cz.gameshelf.app.ui.theme.GameShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun GameEditRoute(
    onClose: () -> Unit,
    viewModel: GameEditViewModel = viewModel(factory = GameEditViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is GameEditEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.resolve(resources))
            }
            GameEditEvent.Close -> onClose()
        }
    }

    BackHandler(enabled = state.hasChanges) { viewModel.requestClose() }

    GameEditScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onFormChange = viewModel::updateForm,
        onSave = viewModel::save,
        onClose = viewModel::requestClose,
        onRetry = viewModel::retryLoad,
    )

    if (state.showDiscardDialog) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            text = stringResource(R.string.discard_message),
            confirmLabel = stringResource(R.string.action_discard),
            dismissLabel = stringResource(R.string.action_keep_editing),
            destructive = true,
            onConfirm = viewModel::discardChanges,
            onDismiss = viewModel::dismissDiscardDialog,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameEditScreen(
    state: GameEditUiState,
    snackbarHostState: SnackbarHostState,
    onFormChange: ((GameForm) -> GameForm) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isEditing) R.string.form_title_edit else R.string.form_title_new)) },
                navigationIcon = {
                    NavigationIconButton(
                        onClick = onClose,
                        iconRes = R.drawable.ic_close,
                        contentDescriptionRes = R.string.action_close,
                    )
                },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(onClick = onSave, enabled = state.canSave) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> LoadingContent(Modifier.padding(padding))
            state.loadError != null -> ErrorContent(
                message = state.loadError.asString(),
                onRetry = onRetry,
                modifier = Modifier.padding(padding),
            )
            else -> GameFormContent(
                form = state.form,
                errors = state.errors,
                suggestions = state.suggestions,
                onFormChange = onFormChange,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1800)
@Composable
private fun GameEditScreenPreview() {
    GameShelfTheme(dynamicColor = false) {
        GameEditScreen(
            state = GameEditUiState(isEditing = false),
            snackbarHostState = SnackbarHostState(),
            onFormChange = {},
            onSave = {},
            onClose = {},
            onRetry = {},
        )
    }
}
