package cz.gameshelf.app.ui.games.list

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cz.gameshelf.app.R
import cz.gameshelf.app.data.sync.SyncStatus
import cz.gameshelf.app.ui.theme.GameShelfTheme

/** Compact sync status for the list header: a spinner, "Offline", the unsynced changes, or nothing. */
@Composable
fun SyncStatusIndicator(status: SyncStatus, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelLarge
    when {
        status.isSyncing -> {
            val description = stringResource(R.string.sync_syncing)
            CircularProgressIndicator(
                modifier = modifier
                    .size(16.dp)
                    .semantics { contentDescription = description },
                strokeWidth = 2.dp,
            )
        }
        status.isOffline -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_cloud_off),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.sync_offline), style = style, color = color)
        }
        status.pendingCount > 0 -> Text(
            text = pluralStringResource(R.plurals.sync_unsynced_changes, status.pendingCount, status.pendingCount),
            style = style,
            color = color,
            modifier = modifier,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SyncStatusIndicatorPreview() {
    GameShelfTheme(dynamicColor = false) {
        SyncStatusIndicator(SyncStatus(isOffline = true, pendingCount = 3))
    }
}
