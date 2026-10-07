package cz.gameshelf.app.ui.profile

import android.text.format.DateUtils
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import cz.gameshelf.app.data.sync.SyncStatus
import cz.gameshelf.app.domain.model.User
import cz.gameshelf.app.ui.common.Formatters
import cz.gameshelf.app.ui.common.ObserveAsEvents
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.asString
import cz.gameshelf.app.ui.common.resolve
import cz.gameshelf.app.ui.components.NavigationIconButton
import cz.gameshelf.app.ui.theme.GameShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** How often "Last synced" (a relative time) is refreshed while the screen is shown. */
private const val RELATIVE_TIME_REFRESH_MILLIS = 30_000L

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
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onSyncNow = viewModel::syncNow,
        onChangePassword = viewModel::openChangePassword,
        onLogout = viewModel::requestLogout,
        onDeleteAccount = viewModel::openDeleteAccount,
    )

    if (state.showLogoutConfirmation) {
        LogoutDialog(
            unsyncedChanges = state.sync.pendingCount,
            onConfirm = viewModel::logout,
            onDismiss = viewModel::dismissLogout,
        )
    }

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
    state: ProfileUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
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
            state.user?.let { UserCard(it) }
            state.collection?.let { CollectionSection(it) }
            SyncSection(state.sync, state.syncError, onSyncNow)
            SectionTitle(stringResource(R.string.profile_security))
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
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun CollectionSection(summary: CollectionSummary) {
    SectionTitle(stringResource(R.string.profile_collection))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = { Text(pluralStringResource(R.plurals.games_count, summary.games, summary.games)) },
            supportingContent = {
                Text(pluralStringResource(R.plurals.platforms_count, summary.platforms, summary.platforms))
            },
            leadingContent = {
                Icon(
                    painterResource(R.drawable.ic_inventory_2),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun SyncSection(status: SyncStatus, error: UiText?, onSyncNow: () -> Unit) {
    SectionTitle(stringResource(R.string.profile_sync))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SyncInfo(stringResource(R.string.sync_status_label), syncStatusText(status))
        SyncInfo(stringResource(R.string.sync_last_synced_label), lastSyncedText(status.lastSyncedAt))
        if (error != null) {
            Text(
                error.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        OutlinedButton(
            onClick = onSyncNow,
            enabled = !status.isSyncing,
            modifier = Modifier.padding(16.dp),
        ) { Text(stringResource(R.string.action_sync_now)) }
    }
}

@Composable
private fun SyncInfo(label: String, value: String) {
    ListItem(
        overlineContent = { Text(label) },
        headlineContent = { Text(value) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    )
}

@Composable
private fun syncStatusText(status: SyncStatus): String = when {
    status.isSyncing -> stringResource(R.string.sync_syncing)
    status.isOffline -> stringResource(R.string.sync_offline)
    status.pendingCount > 0 ->
        pluralStringResource(R.plurals.sync_unsynced_changes, status.pendingCount, status.pendingCount)
    status.lastSyncedAt != null -> stringResource(R.string.sync_all_synced)
    else -> stringResource(R.string.sync_not_synced_yet)
}

/** "5 minutes ago", refreshed while shown; "Never" before the first completed sync. */
@Composable
private fun lastSyncedText(lastSyncedAt: Instant?): String {
    val now by produceState(Instant.now(), lastSyncedAt) {
        while (true) {
            value = Instant.now()
            delay(RELATIVE_TIME_REFRESH_MILLIS)
        }
    }
    return when {
        lastSyncedAt == null -> stringResource(R.string.sync_never)
        Duration.between(lastSyncedAt, now) < Duration.ofMinutes(1) -> stringResource(R.string.sync_just_now)
        else -> DateUtils.getRelativeTimeSpanString(
            lastSyncedAt.toEpochMilli(),
            now.toEpochMilli(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
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
            state = ProfileUiState(
                user = User("1", "collector@example.com", "Retro Rob", Instant.parse("2024-05-17T10:00:00Z")),
                collection = CollectionSummary(games = 132, platforms = 7),
                sync = SyncStatus(pendingCount = 3, lastSyncedAt = Instant.now().minusSeconds(300)),
            ),
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onSyncNow = {},
            onChangePassword = {},
            onLogout = {},
            onDeleteAccount = {},
        )
    }
}
