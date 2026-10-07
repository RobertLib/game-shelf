package cz.gameshelf.app.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cz.gameshelf.app.domain.model.Game
import cz.gameshelf.app.ui.common.labelRes

/** "Platform · Region · Year" */
@Composable
fun gameSubtitle(game: Game): String = listOfNotNull(
    stringResource(game.platform.labelRes),
    game.region?.let { stringResource(it.labelRes) },
    game.releaseYear?.toString(),
).joinToString(separator = " · ")
