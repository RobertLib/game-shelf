package cz.gameshelf.app.ui.games.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.GameSort
import cz.gameshelf.app.domain.model.GameSortField
import cz.gameshelf.app.domain.model.SortOrder
import cz.gameshelf.app.ui.common.labelRes

@Composable
fun SortMenuButton(
    sort: GameSort,
    onFieldSelect: (GameSortField) -> Unit,
    onOrderSelect: (SortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.action_sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                text = stringResource(R.string.sort_header),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            GameSortField.entries.forEach { field ->
                SortMenuItem(stringResource(field.labelRes), isSelected = field == sort.field) {
                    onFieldSelect(field)
                    expanded = false
                }
            }
            HorizontalDivider()
            SortMenuItem(
                text = stringResource(R.string.sort_ascending),
                isSelected = sort.order == SortOrder.ASC,
                leadingIconRes = R.drawable.ic_arrow_upward,
            ) {
                onOrderSelect(SortOrder.ASC)
                expanded = false
            }
            SortMenuItem(
                text = stringResource(R.string.sort_descending),
                isSelected = sort.order == SortOrder.DESC,
                leadingIconRes = R.drawable.ic_arrow_downward,
            ) {
                onOrderSelect(SortOrder.DESC)
                expanded = false
            }
        }
    }
}

@Composable
private fun SortMenuItem(
    text: String,
    isSelected: Boolean,
    leadingIconRes: Int? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = leadingIconRes?.let { { Icon(painterResource(it), contentDescription = null) } },
        trailingIcon = if (isSelected) {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
        } else {
            null
        },
        modifier = Modifier.semantics { selected = isSelected },
    )
}

@Composable
fun FilterActionButton(activeCount: Int, onClick: () -> Unit) {
    val description = buildString {
        append(stringResource(R.string.action_filter))
        if (activeCount > 0) {
            append(", ")
            append(pluralStringResource(R.plurals.active_filters, activeCount, activeCount))
        }
    }
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (activeCount > 0) Badge { Text(activeCount.toString()) } }) {
            Icon(painterResource(R.drawable.ic_filter_list), contentDescription = description)
        }
    }
}
