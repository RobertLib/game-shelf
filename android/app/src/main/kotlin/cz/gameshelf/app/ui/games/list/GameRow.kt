package cz.gameshelf.app.ui.games.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Completeness
import cz.gameshelf.app.domain.model.Condition
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.domain.model.GameFormat
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.Region
import cz.gameshelf.app.ui.common.Formatters
import cz.gameshelf.app.ui.common.labelRes
import cz.gameshelf.app.ui.components.GameCover
import cz.gameshelf.app.ui.components.LabelBadge
import cz.gameshelf.app.ui.games.gameSubtitle
import cz.gameshelf.app.ui.theme.GameShelfTheme
import cz.gameshelf.app.ui.theme.GameShelfThemeExtras
import java.math.BigDecimal
import java.time.Instant

@Composable
fun GameRow(
    game: Game,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameCover(
            coverUrl = game.coverImageUrl,
            platform = game.platform,
            title = game.title,
            modifier = Modifier.size(width = 52.dp, height = 68.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = gameSubtitle(game),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            GameBadges(game)
        }
        if (game.favorite || game.estimatedValue != null) {
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (game.favorite) {
                    Icon(
                        painter = painterResource(R.drawable.ic_star_filled),
                        contentDescription = stringResource(R.string.game_favorite),
                        tint = GameShelfThemeExtras.colors.favorite,
                        modifier = Modifier.size(20.dp),
                    )
                }
                game.estimatedValue?.let {
                    Text(
                        text = Formatters.money(it, game.currency),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GameBadges(game: Game) {
    val showStatus = game.status != CollectionStatus.OWNED
    if (!showStatus && game.completeness == null && game.condition == null) return
    Spacer(Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showStatus) {
            LabelBadge(
                text = stringResource(game.status.labelRes),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            )
        }
        game.completeness?.let { LabelBadge(stringResource(it.labelRes)) }
        game.condition?.let {
            LabelBadge(
                text = stringResource(it.labelRes),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

internal val PreviewGame = Game(
    id = "1",
    title = "The Legend of Zelda: Ocarina of Time",
    platform = Platform.N64,
    status = CollectionStatus.WISHLIST,
    format = GameFormat.PHYSICAL,
    region = Region.PAL,
    edition = null,
    completeness = Completeness.CIB,
    condition = Condition.VERY_GOOD,
    playStatus = null,
    genre = "Action-adventure",
    developer = "Nintendo EAD",
    publisher = "Nintendo",
    releaseYear = 1998,
    barcode = null,
    productCode = null,
    quantity = 1,
    purchasePrice = null,
    purchaseDate = null,
    purchasePlace = null,
    estimatedValue = BigDecimal("2500"),
    currency = "CZK",
    storageLocation = null,
    rating = 10,
    favorite = true,
    coverImageUrl = null,
    notes = null,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

@Preview(showBackground = true)
@Composable
private fun GameRowPreview() {
    GameShelfTheme(dynamicColor = false) {
        Surface { GameRow(game = PreviewGame, onClick = {}) }
    }
}
