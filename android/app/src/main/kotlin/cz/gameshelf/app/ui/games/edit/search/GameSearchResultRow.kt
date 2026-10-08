package cz.gameshelf.app.ui.games.edit.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.GameSearchResult
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.ui.common.labelRes
import cz.gameshelf.app.ui.components.GameCover
import cz.gameshelf.app.ui.theme.GameShelfTheme

internal const val MAX_SHOWN_PLATFORMS = 3

/** One game found in the database; reads as a single accessibility element. */
@Composable
fun GameSearchResultRow(
    game: GameSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = searchResultSubtitle(
        releaseYear = game.releaseYear,
        platformLabels = game.platforms.map { stringResource(it.labelRes) },
        moreLabel = { stringResource(R.string.game_search_more_platforms, it) },
    )
    val developer = game.developer?.takeIf { it.isNotBlank() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameCover(
            coverUrl = game.coverImageUrl,
            platform = null,
            title = game.title,
            modifier = Modifier.size(width = 44.dp, height = 58.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (developer != null) {
                Text(
                    text = developer,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * "2017 · Nintendo Switch, Wii U": the release year and the platform labels, each only when known. More
 * than [MAX_SHOWN_PLATFORMS] platforms are cut to the first ones and [moreLabel] of the rest ("+2").
 */
internal inline fun searchResultSubtitle(
    releaseYear: Int?,
    platformLabels: List<String>,
    moreLabel: (hidden: Int) -> String,
): String {
    val hidden = platformLabels.size - MAX_SHOWN_PLATFORMS
    val platforms = if (hidden > 0) {
        platformLabels.take(MAX_SHOWN_PLATFORMS).joinToString(", ") + " " + moreLabel(hidden)
    } else {
        platformLabels.joinToString(", ")
    }
    return listOfNotNull(releaseYear?.toString(), platforms.ifEmpty { null }).joinToString(" · ")
}

internal val PreviewSearchResult = GameSearchResult(
    igdbId = 26758,
    title = "Mario Kart 8 Deluxe",
    platforms = listOf(Platform.SWITCH, Platform.WII_U),
    genre = "Racing",
    developer = "Nintendo EPD",
    publisher = "Nintendo",
    releaseYear = 2017,
    coverImageUrl = null,
)

@Preview(showBackground = true)
@Composable
private fun GameSearchResultRowPreview() {
    GameShelfTheme(dynamicColor = false) {
        Surface { GameSearchResultRow(game = PreviewSearchResult, onClick = {}) }
    }
}
