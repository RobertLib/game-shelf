package cz.gameshelf.app.ui.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.gameshelf.app.BuildConfig
import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.User
import cz.gameshelf.app.ui.common.Formatters
import cz.gameshelf.app.ui.common.ObserveAsEvents
import cz.gameshelf.app.ui.common.resolve
import cz.gameshelf.app.ui.components.NavigationIconButton
import cz.gameshelf.app.ui.theme.GameShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ProfileEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.resolve(resources))
            }
        }
    }

    ProfileScreen(
        user = state.user,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onChangePassword = viewModel::openChangePassword,
        onLogout = viewModel::logout,
        onDeleteAccount = viewModel::openDeleteAccount,
    )

    state.changePassword?.let { form ->
        ChangePasswordDialog(
            form = form,
            onChange = viewModel::updateChangePassword,
            onSubmit = viewModel::submitChangePassword,
            onDismiss = viewModel::dismissChangePassword,
        )
    }
    state.deleteAccount?.let { form ->
        DeleteAccountDialog(
            form = form,
            onPasswordChange = viewModel::updateDeletePassword,
            onSubmit = viewModel::submitDeleteAccount,
            onDismiss = viewModel::dismissDeleteAccount,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: User?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = { NavigationIconButton(onClick = onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (user != null) UserCard(user)
            Text(
                stringResource(R.string.profile_security),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .semantics { heading() },
            )
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                ProfileAction(R.drawable.ic_key, stringResource(R.string.action_change_password), onChangePassword)
                ProfileAction(R.drawable.ic_logout, stringResource(R.string.action_logout), onLogout)
                ProfileAction(
                    iconRes = R.drawable.ic_person_remove,
                    text = stringResource(R.string.action_delete_account),
                    onClick = onDeleteAccount,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun UserCard(user: User) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_account_circle),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(
                    text = user.displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_no_display_name),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(user.email, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(
                        R.string.profile_member_since,
                        Formatters.date(user.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ProfileAction(
    @DrawableRes iconRes: Int,
    text: String,
    onClick: () -> Unit,
    color: Color = Color.Unspecified,
) {
    ListItem(
        headlineContent = { Text(text, color = color) },
        leadingContent = {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    GameShelfTheme(dynamicColor = false) {
        ProfileScreen(
            user = User("1", "collector@example.com", "Retro Rob", Instant.parse("2024-05-17T10:00:00Z")),
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onChangePassword = {},
            onLogout = {},
            onDeleteAccount = {},
        )
    }
}
