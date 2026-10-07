package cz.gameshelf.app.ui.games.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.asString
import cz.gameshelf.app.ui.common.labelRes
import cz.gameshelf.app.ui.theme.GameShelfTheme

/** What the barcode lookup is doing or found, above the form; nothing when there is nothing to say. */
@Composable
fun BarcodeLookupCard(
    status: BarcodeLookupStatus?,
    duplicate: Game?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status == null && duplicate == null) return
    val isError = status is BarcodeLookupStatus.Failed
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp),
            ) {
                when (status) {
                    BarcodeLookupStatus.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.lookup_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    is BarcodeLookupStatus.Found -> {
                        Text(stringResource(R.string.lookup_found), style = MaterialTheme.typography.bodyMedium)
                        if (status.sources.isNotEmpty()) {
                            Text(
                                stringResource(R.string.lookup_sources, status.sources.joinToString(" · ")),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    BarcodeLookupStatus.NotFound ->
                        Text(stringResource(R.string.lookup_not_found), style = MaterialTheme.typography.bodyMedium)
                    is BarcodeLookupStatus.Failed -> {
                        Text(
                            stringResource(R.string.lookup_failed, status.message.asString()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                    }
                    null -> Unit
                }
                if (duplicate != null) {
                    Text(
                        stringResource(R.string.lookup_duplicate, duplicate.title, stringResource(duplicate.platform.labelRes)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (status != BarcodeLookupStatus.Loading) {
                IconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_dismiss))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BarcodeLookupCardPreview() {
    GameShelfTheme(dynamicColor = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BarcodeLookupCard(BarcodeLookupStatus.Loading, null, onRetry = {}, onDismiss = {})
            BarcodeLookupCard(BarcodeLookupStatus.Found(listOf("UPCitemdb", "IGDB")), null, onRetry = {}, onDismiss = {})
            BarcodeLookupCard(BarcodeLookupStatus.NotFound, null, onRetry = {}, onDismiss = {})
            BarcodeLookupCard(
                BarcodeLookupStatus.Failed(UiText(R.string.error_network)),
                null,
                onRetry = {},
                onDismiss = {},
            )
        }
    }
}
