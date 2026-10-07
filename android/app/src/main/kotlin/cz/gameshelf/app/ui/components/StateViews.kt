package cz.gameshelf.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cz.gameshelf.app.R
import cz.gameshelf.app.ui.theme.GameShelfTheme

@Composable
fun LoadingContent(modifier: Modifier = Modifier, message: String? = null) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The [message] alone, or under a [title] when one is given. */
@Composable
fun ErrorContent(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    MessageContent(
        iconRes = R.drawable.ic_cloud_off,
        title = title ?: message,
        message = message.takeIf { title != null },
        modifier = modifier,
        action = onRetry?.let { retry ->
            { OutlinedButton(onClick = retry) { Text(stringResource(R.string.action_retry)) } }
        },
    )
}

@Composable
fun EmptyContent(
    @DrawableRes iconRes: Int,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    MessageContent(
        iconRes = iconRes,
        title = title,
        message = message,
        modifier = modifier,
        action = if (actionLabel != null && onAction != null) {
            { Button(onClick = onAction) { Text(actionLabel) } }
        } else {
            null
        },
    )
}

@Composable
private fun MessageContent(
    @DrawableRes iconRes: Int,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyContentPreview() {
    GameShelfTheme(dynamicColor = false) {
        EmptyContent(
            iconRes = R.drawable.ic_inventory_2,
            title = "Your collection is empty",
            message = "Add your first game to start building your collection.",
            actionLabel = "Add your first game",
            onAction = {},
        )
    }
}
