package cz.gameshelf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.ui.common.shortLabelRes

/**
 * Cover image, drawn over a placeholder with the platform's short name (a game controller icon without
 * a [platform]). The placeholder stays visible while loading, when there is no URL, and when the image
 * fails to load.
 */
@Composable
fun GameCover(
    coverUrl: String?,
    platform: Platform?,
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    contentScale: ContentScale = ContentScale.Crop,
    placeholderStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (platform != null) {
            Text(
                text = stringResource(platform.shortLabelRes),
                style = placeholderStyle,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(4.dp),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_sports_esports),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = stringResource(R.string.cover_description, title),
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
