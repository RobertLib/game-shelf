package cz.gameshelf.app.ui.games.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.ui.common.labelRes

/**
 * Platforms present in the collection (with counts) and already selected ones come first, grouped
 * by manufacturer; the remaining platforms are behind "Show all platforms".
 */
@Composable
fun PlatformFilterSection(
    selected: Set<Platform>,
    counts: Map<Platform, Int>,
    onToggle: (Platform) -> Unit,
) {
    // Selection at the time the sheet opened, so chips do not jump between the lists while toggling.
    val initiallySelected = remember { selected }
    val prominent = remember(counts) { counts.keys + initiallySelected }
    val others = remember(prominent) { Platform.entries.filterNot { it in prominent } }
    var showAll by rememberSaveable { mutableStateOf(false) }

    FilterSection(stringResource(R.string.filter_platforms)) {
        if (counts.isEmpty()) {
            Text(
                stringResource(R.string.filter_platforms_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PlatformGroups(prominent, counts, selected, onToggle)
        if (others.isNotEmpty()) {
            TextButton(onClick = { showAll = !showAll }) {
                Text(stringResource(if (showAll) R.string.filter_hide_other_platforms else R.string.filter_show_all_platforms))
            }
            if (showAll) PlatformGroups(others.toSet(), counts, selected, onToggle)
        }
    }
}

@Composable
private fun PlatformGroups(
    platforms: Set<Platform>,
    counts: Map<Platform, Int>,
    selected: Set<Platform>,
    onToggle: (Platform) -> Unit,
) {
    platforms
        .groupBy { it.group }
        .toSortedMap(compareBy { it.ordinal })
        .forEach { (group, groupPlatforms) ->
            Text(
                text = stringResource(group.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groupPlatforms
                    .sortedWith(compareByDescending<Platform> { counts[it] ?: 0 }.thenBy { it.ordinal })
                    .forEach { platform ->
                        SelectableChip(
                            label = stringResource(platform.labelRes),
                            selected = platform in selected,
                            count = counts[platform],
                            onClick = { onToggle(platform) },
                        )
                    }
            }
        }
}
