package cz.gameshelf.app.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cz.gameshelf.app.R

/** Top app bar navigation icon (back arrow by default, or e.g. a close icon for forms). */
@Composable
fun NavigationIconButton(
    onClick: () -> Unit,
    @DrawableRes iconRes: Int = R.drawable.ic_arrow_back,
    @StringRes contentDescriptionRes: Int = R.string.action_back,
) {
    IconButton(onClick = onClick) {
        Icon(painterResource(iconRes), contentDescription = stringResource(contentDescriptionRes))
    }
}
