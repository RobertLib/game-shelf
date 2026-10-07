package cz.gameshelf.app.ui.games.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Completeness
import cz.gameshelf.app.domain.model.Condition
import cz.gameshelf.app.domain.model.CoverFilter
import cz.gameshelf.app.domain.model.GameFacets
import cz.gameshelf.app.domain.model.GameFormat
import cz.gameshelf.app.domain.model.PlayStatus
import cz.gameshelf.app.domain.model.Region
import cz.gameshelf.app.ui.common.asString
import cz.gameshelf.app.ui.common.labelRes
import cz.gameshelf.app.ui.components.AutocompleteTextField
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Bottom sheet editing a [FilterDraft]; nothing is applied until "Apply". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    draft: FilterDraft,
    facets: GameFacets,
    onDraftChange: ((FilterDraft) -> FilterDraft) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val errors = draft.errors

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.filters_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                TextButton(onClick = onReset) { Text(stringResource(R.string.filters_reset)) }
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                FilterSheetContent(draft, facets, errors, onDraftChange)
            }
            HorizontalDivider()
            Button(
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onApply() }
                },
                enabled = !errors.hasAny,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) { Text(stringResource(R.string.filters_apply)) }
        }
    }
}

@Composable
private fun FilterSheetContent(
    draft: FilterDraft,
    facets: GameFacets,
    errors: FilterDraftErrors,
    onDraftChange: ((FilterDraft) -> FilterDraft) -> Unit,
) {
    PlatformFilterSection(
        selected = draft.platforms,
        counts = facets.platformCounts,
        onToggle = { p -> onDraftChange { it.copy(platforms = it.platforms.toggle(p)) } },
    )
    val statusCounts = facets.statuses.associate { it.value to it.count }
    ChipFilterSection(
        title = stringResource(R.string.filter_status),
        options = CollectionStatus.selectable,
        selected = draft.statuses,
        label = { stringResource(it.labelRes) },
        count = { statusCounts[it.apiValue] },
        onToggle = { s -> onDraftChange { it.copy(statuses = it.statuses.toggle(s)) } },
    )
    ChipFilterSection(
        title = stringResource(R.string.filter_format),
        options = GameFormat.selectable,
        selected = draft.formats,
        label = { stringResource(it.labelRes) },
        onToggle = { f -> onDraftChange { it.copy(formats = it.formats.toggle(f)) } },
    )
    ChipFilterSection(
        title = stringResource(R.string.filter_region),
        options = Region.entries,
        selected = draft.regions,
        label = { stringResource(it.labelRes) },
        onToggle = { r -> onDraftChange { it.copy(regions = it.regions.toggle(r)) } },
    )
    ChipFilterSection(
        title = stringResource(R.string.filter_completeness),
        options = Completeness.selectable,
        selected = draft.completeness,
        label = { stringResource(it.labelRes) },
        onToggle = { c -> onDraftChange { it.copy(completeness = it.completeness.toggle(c)) } },
    )
    ChipFilterSection(
        title = stringResource(R.string.filter_condition),
        options = Condition.selectable,
        selected = draft.conditions,
        label = { stringResource(it.labelRes) },
        onToggle = { c -> onDraftChange { it.copy(conditions = it.conditions.toggle(c)) } },
    )
    ChipFilterSection(
        title = stringResource(R.string.filter_play_status),
        options = PlayStatus.selectable,
        selected = draft.playStatuses,
        label = { stringResource(it.labelRes) },
        onToggle = { p -> onDraftChange { it.copy(playStatuses = it.playStatuses.toggle(p)) } },
    )
    val genreCounts = facets.genres.associate { it.value to it.count }
    ChipFilterSection(
        title = stringResource(R.string.filter_genre),
        options = facets.genres.map { it.value } + (draft.genres - genreCounts.keys).sorted(),
        selected = draft.genres,
        label = { it },
        count = { genreCounts[it] },
        emptyText = stringResource(R.string.filter_genre_empty),
        onToggle = { g -> onDraftChange { it.copy(genres = it.genres.toggle(g)) } },
    )
    AutocompleteTextField(
        value = draft.publisher,
        onValueChange = { v -> onDraftChange { it.copy(publisher = v) } },
        label = stringResource(R.string.filter_publisher),
        suggestions = facets.publishers.map { it.value },
        capitalization = KeyboardCapitalization.Words,
    )
    AutocompleteTextField(
        value = draft.developer,
        onValueChange = { v -> onDraftChange { it.copy(developer = v) } },
        label = stringResource(R.string.filter_developer),
        suggestions = facets.developers.map { it.value },
        capitalization = KeyboardCapitalization.Words,
    )
    AutocompleteTextField(
        value = draft.storageLocation,
        onValueChange = { v -> onDraftChange { it.copy(storageLocation = v) } },
        label = stringResource(R.string.filter_storage),
        suggestions = facets.storageLocations.map { it.value },
    )
    FavoritesOnlyRow(
        checked = draft.favoritesOnly,
        onCheckedChange = { checked -> onDraftChange { it.copy(favoritesOnly = checked) } },
    )
    CoverSection(draft.cover) { cover -> onDraftChange { it.copy(cover = cover) } }
    RangeInputSection(
        title = stringResource(R.string.filter_release_year),
        from = draft.releaseYearFrom,
        to = draft.releaseYearTo,
        onFromChange = { v -> onDraftChange { it.copy(releaseYearFrom = v) } },
        onToChange = { v -> onDraftChange { it.copy(releaseYearTo = v) } },
        keyboardType = KeyboardType.Number,
        error = errors.releaseYear?.asString(),
        fromPlaceholder = facets.releaseYearMin?.toString(),
        toPlaceholder = facets.releaseYearMax?.toString(),
    )
    RangeInputSection(
        title = stringResource(R.string.filter_purchase_price),
        from = draft.purchasePriceMin,
        to = draft.purchasePriceMax,
        onFromChange = { v -> onDraftChange { it.copy(purchasePriceMin = v) } },
        onToChange = { v -> onDraftChange { it.copy(purchasePriceMax = v) } },
        keyboardType = KeyboardType.Decimal,
        error = errors.purchasePrice?.asString(),
    )
    RangeInputSection(
        title = stringResource(R.string.filter_estimated_value),
        from = draft.estimatedValueMin,
        to = draft.estimatedValueMax,
        onFromChange = { v -> onDraftChange { it.copy(estimatedValueMin = v) } },
        onToChange = { v -> onDraftChange { it.copy(estimatedValueMax = v) } },
        keyboardType = KeyboardType.Decimal,
        error = errors.estimatedValue?.asString(),
    )
    DateRangeSection(
        title = stringResource(R.string.filter_purchase_date),
        from = draft.purchaseDateFrom,
        to = draft.purchaseDateTo,
        onFromChange = { d -> onDraftChange { it.copy(purchaseDateFrom = d) } },
        onToChange = { d -> onDraftChange { it.copy(purchaseDateTo = d) } },
        error = errors.purchaseDate?.asString(),
    )
    RatingSection(draft.ratingMin) { rating -> onDraftChange { it.copy(ratingMin = rating) } }
}

@Composable
private fun FavoritesOnlyRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.filter_favorites_only),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun CoverSection(selected: CoverFilter, onSelect: (CoverFilter) -> Unit) {
    val options = listOf(
        CoverFilter.ANY to R.string.filter_cover_any,
        CoverFilter.WITH_COVER to R.string.filter_cover_with,
        CoverFilter.WITHOUT_COVER to R.string.filter_cover_without,
    )
    FilterSection(stringResource(R.string.filter_cover)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (cover, labelRes) ->
                SegmentedButton(
                    selected = cover == selected,
                    onClick = { onSelect(cover) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(stringResource(labelRes)) }
            }
        }
    }
}

@Composable
private fun RatingSection(ratingMin: Int?, onChange: (Int?) -> Unit) {
    val valueText = ratingMin?.let { stringResource(R.string.filter_min_rating_value, it) }
        ?: stringResource(R.string.filter_min_rating_any)
    FilterSection(
        title = stringResource(R.string.filter_min_rating),
        trailing = { Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
    ) {
        // 0 stands for "any rating".
        Slider(
            value = (ratingMin ?: 0).toFloat(),
            onValueChange = { value -> onChange(value.roundToInt().takeIf { it > 0 }) },
            valueRange = 0f..10f,
            steps = 9,
            modifier = Modifier.semantics { stateDescription = valueText },
        )
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
