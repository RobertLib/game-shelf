package cz.gameshelf.app.ui.games.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.ActiveFilter
import cz.gameshelf.app.domain.model.CoverFilter
import cz.gameshelf.app.ui.common.Formatters
import cz.gameshelf.app.ui.common.labelRes
import java.math.BigDecimal

/** Removable chips for the applied filters, followed by "Clear all". */
@Composable
fun ActiveFiltersRow(
    filters: List<ActiveFilter>,
    onRemove: (ActiveFilter) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(filters, key = { it.toString() }) { filter ->
            InputChip(
                selected = true,
                onClick = { onRemove(filter) },
                label = { Text(activeFilterLabel(filter)) },
                trailingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.filter_remove),
                        modifier = Modifier.size(InputChipDefaults.IconSize),
                    )
                },
            )
        }
        item(key = "clear-all") {
            TextButton(onClick = onClearAll) { Text(stringResource(R.string.filters_clear_all)) }
        }
    }
}

@Composable
private fun activeFilterLabel(filter: ActiveFilter): String = when (filter) {
    is ActiveFilter.PlatformValue -> stringResource(filter.platform.labelRes)
    is ActiveFilter.StatusValue -> stringResource(filter.status.labelRes)
    is ActiveFilter.FormatValue -> stringResource(filter.format.labelRes)
    is ActiveFilter.RegionValue -> stringResource(filter.region.labelRes)
    is ActiveFilter.CompletenessValue -> stringResource(filter.completeness.labelRes)
    is ActiveFilter.ConditionValue -> stringResource(filter.condition.labelRes)
    is ActiveFilter.PlayStatusValue -> stringResource(filter.playStatus.labelRes)
    is ActiveFilter.Genre -> filter.genre
    is ActiveFilter.Publisher -> stringResource(R.string.chip_publisher, filter.value)
    is ActiveFilter.Developer -> stringResource(R.string.chip_developer, filter.value)
    is ActiveFilter.StorageLocation -> stringResource(R.string.chip_storage, filter.value)
    ActiveFilter.FavoritesOnly -> stringResource(R.string.chip_favorites)
    is ActiveFilter.Cover -> stringResource(
        if (filter.cover == CoverFilter.WITH_COVER) R.string.chip_with_cover else R.string.chip_without_cover,
    )
    is ActiveFilter.ReleaseYear ->
        stringResource(R.string.chip_release_year, rangeText(filter.from?.toString(), filter.to?.toString()))
    is ActiveFilter.PurchasePrice ->
        stringResource(R.string.chip_purchase_price, rangeText(filter.min.formatted(), filter.max.formatted()))
    is ActiveFilter.EstimatedValue ->
        stringResource(R.string.chip_estimated_value, rangeText(filter.min.formatted(), filter.max.formatted()))
    is ActiveFilter.PurchaseDate -> stringResource(
        R.string.chip_purchase_date,
        rangeText(filter.from?.let(Formatters::date), filter.to?.let(Formatters::date)),
    )
    is ActiveFilter.RatingMin -> stringResource(R.string.chip_rating, filter.min)
}

private fun BigDecimal?.formatted(): String? = this?.let(Formatters::number)

@Composable
private fun rangeText(from: String?, to: String?): String = when {
    from != null && to != null -> stringResource(R.string.range_between, from, to)
    from != null -> stringResource(R.string.range_from, from)
    else -> stringResource(R.string.range_to, to.orEmpty())
}
