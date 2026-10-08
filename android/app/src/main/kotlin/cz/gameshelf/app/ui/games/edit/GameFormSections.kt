package cz.gameshelf.app.ui.games.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.CollectionStatus
import cz.gameshelf.app.domain.model.Completeness
import cz.gameshelf.app.domain.model.Condition
import cz.gameshelf.app.domain.model.GameFormat
import cz.gameshelf.app.domain.model.PlayStatus
import cz.gameshelf.app.domain.model.Region
import cz.gameshelf.app.ui.common.UiText
import cz.gameshelf.app.ui.common.asString
import cz.gameshelf.app.ui.common.labelRes
import cz.gameshelf.app.ui.components.AutocompleteTextField
import cz.gameshelf.app.ui.components.DateField
import cz.gameshelf.app.ui.components.DropdownField
import cz.gameshelf.app.ui.components.PlatformDropdownField

private const val CURRENCY_LENGTH = 3

@Composable
fun GameFormContent(
    form: GameForm,
    errors: Map<GameField, UiText>,
    suggestions: FormSuggestions,
    onFormChange: ((GameForm) -> GameForm) -> Unit,
    onScanBarcode: () -> Unit,
    onSearchGameDatabase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun error(field: GameField): UiText? = errors[field]

    Column(modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        FormSection(stringResource(R.string.form_section_basic)) {
            FormTextField(
                value = form.title,
                onValueChange = { v -> onFormChange { it.copy(title = v) } },
                label = stringResource(R.string.field_title_required),
                error = error(GameField.TITLE),
                capitalization = KeyboardCapitalization.Words,
                trailingIcon = {
                    IconButton(onClick = onSearchGameDatabase) {
                        Icon(
                            painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.action_search_game_database),
                        )
                    }
                },
            )
            PlatformDropdownField(
                selected = form.platform,
                onSelect = { p -> onFormChange { it.copy(platform = p) } },
                label = stringResource(R.string.field_platform_required),
                error = error(GameField.PLATFORM)?.asString(),
                modifier = Modifier.fillMaxWidth(),
            )
            FormTextField(
                value = form.edition,
                onValueChange = { v -> onFormChange { it.copy(edition = v) } },
                label = stringResource(R.string.field_edition),
                error = error(GameField.EDITION),
            )
            AutocompleteTextField(
                value = form.genre,
                onValueChange = { v -> onFormChange { it.copy(genre = v) } },
                label = stringResource(R.string.field_genre),
                suggestions = suggestions.genres,
                error = error(GameField.GENRE)?.asString(),
                modifier = Modifier.fillMaxWidth(),
            )
            AutocompleteTextField(
                value = form.developer,
                onValueChange = { v -> onFormChange { it.copy(developer = v) } },
                label = stringResource(R.string.field_developer),
                suggestions = suggestions.developers,
                error = error(GameField.DEVELOPER)?.asString(),
                capitalization = KeyboardCapitalization.Words,
                modifier = Modifier.fillMaxWidth(),
            )
            AutocompleteTextField(
                value = form.publisher,
                onValueChange = { v -> onFormChange { it.copy(publisher = v) } },
                label = stringResource(R.string.field_publisher),
                suggestions = suggestions.publishers,
                error = error(GameField.PUBLISHER)?.asString(),
                capitalization = KeyboardCapitalization.Words,
                modifier = Modifier.fillMaxWidth(),
            )
            FormTextField(
                value = form.releaseYear,
                onValueChange = { v -> onFormChange { it.copy(releaseYear = v) } },
                label = stringResource(R.string.field_release_year),
                error = error(GameField.RELEASE_YEAR),
                keyboardType = KeyboardType.Number,
            )
            FormTextField(
                value = form.coverImageUrl,
                onValueChange = { v -> onFormChange { it.copy(coverImageUrl = v) } },
                label = stringResource(R.string.field_cover_url),
                error = error(GameField.COVER_URL),
                keyboardType = KeyboardType.Uri,
                capitalization = KeyboardCapitalization.None,
            )
        }

        FormSection(stringResource(R.string.form_section_collector)) {
            DropdownField(
                label = stringResource(R.string.field_status),
                selected = form.status,
                options = CollectionStatus.selectable,
                optionLabel = { stringResource(it.labelRes) },
                onSelect = { s -> s?.let { onFormChange { f -> f.copy(status = it) } } },
                error = error(GameField.STATUS)?.asString(),
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = stringResource(R.string.field_format),
                selected = form.format,
                options = GameFormat.selectable,
                optionLabel = { stringResource(it.labelRes) },
                onSelect = { s -> s?.let { onFormChange { f -> f.copy(format = it) } } },
                error = error(GameField.FORMAT)?.asString(),
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = stringResource(R.string.field_region),
                selected = form.region,
                options = Region.entries,
                optionLabel = { stringResource(it.labelRes) },
                onSelect = { r -> onFormChange { it.copy(region = r) } },
                noneLabel = stringResource(R.string.option_none),
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = stringResource(R.string.field_completeness),
                selected = form.completeness,
                options = Completeness.selectable,
                optionLabel = { stringResource(it.labelRes) },
                onSelect = { c -> onFormChange { it.copy(completeness = c) } },
                noneLabel = stringResource(R.string.option_none),
                error = error(GameField.COMPLETENESS)?.asString(),
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = stringResource(R.string.field_condition),
                selected = form.condition,
                options = Condition.selectable,
                optionLabel = { stringResource(it.labelRes) },
                onSelect = { c -> onFormChange { it.copy(condition = c) } },
                noneLabel = stringResource(R.string.option_none),
                error = error(GameField.CONDITION)?.asString(),
                modifier = Modifier.fillMaxWidth(),
            )
            FormTextField(
                value = form.barcode,
                onValueChange = { v -> onFormChange { it.copy(barcode = v) } },
                label = stringResource(R.string.field_barcode),
                error = error(GameField.BARCODE),
                keyboardType = KeyboardType.Number,
                trailingIcon = {
                    IconButton(onClick = onScanBarcode) {
                        Icon(
                            painterResource(R.drawable.ic_barcode_scanner),
                            contentDescription = stringResource(R.string.action_scan_barcode),
                        )
                    }
                },
            )
            FormTextField(
                value = form.productCode,
                onValueChange = { v -> onFormChange { it.copy(productCode = v) } },
                label = stringResource(R.string.field_product_code),
                error = error(GameField.PRODUCT_CODE),
                capitalization = KeyboardCapitalization.Characters,
            )
            FormTextField(
                value = form.quantity,
                onValueChange = { v -> onFormChange { it.copy(quantity = v) } },
                label = stringResource(R.string.field_quantity),
                error = error(GameField.QUANTITY),
                keyboardType = KeyboardType.Number,
            )
            AutocompleteTextField(
                value = form.storageLocation,
                onValueChange = { v -> onFormChange { it.copy(storageLocation = v) } },
                label = stringResource(R.string.field_storage),
                suggestions = suggestions.storageLocations,
                error = error(GameField.STORAGE_LOCATION)?.asString(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FormSection(stringResource(R.string.form_section_purchase)) {
            FormTextField(
                value = form.purchasePrice,
                onValueChange = { v -> onFormChange { it.copy(purchasePrice = v) } },
                label = stringResource(R.string.field_purchase_price),
                error = error(GameField.PURCHASE_PRICE),
                keyboardType = KeyboardType.Decimal,
            )
            FormTextField(
                value = form.estimatedValue,
                onValueChange = { v -> onFormChange { it.copy(estimatedValue = v) } },
                label = stringResource(R.string.field_estimated_value),
                error = error(GameField.ESTIMATED_VALUE),
                keyboardType = KeyboardType.Decimal,
            )
            FormTextField(
                value = form.currency,
                onValueChange = { v -> onFormChange { it.copy(currency = v.uppercase().take(CURRENCY_LENGTH)) } },
                label = stringResource(R.string.field_currency),
                error = error(GameField.CURRENCY),
                supportingText = stringResource(R.string.currency_hint),
                capitalization = KeyboardCapitalization.Characters,
            )
            DateField(
                value = form.purchaseDate,
                onValueChange = { d -> onFormChange { it.copy(purchaseDate = d) } },
                label = stringResource(R.string.field_purchase_date),
                modifier = Modifier.fillMaxWidth(),
            )
            FormTextField(
                value = form.purchasePlace,
                onValueChange = { v -> onFormChange { it.copy(purchasePlace = v) } },
                label = stringResource(R.string.field_purchase_place),
                error = error(GameField.PURCHASE_PLACE),
            )
        }

        FormSection(stringResource(R.string.form_section_rating)) {
            DropdownField(
                label = stringResource(R.string.field_play_status),
                selected = form.playStatus,
                options = PlayStatus.selectable,
                optionLabel = { stringResource(it.labelRes) },
                onSelect = { p -> onFormChange { it.copy(playStatus = p) } },
                noneLabel = stringResource(R.string.option_none),
                error = error(GameField.PLAY_STATUS)?.asString(),
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = stringResource(R.string.field_rating),
                selected = form.rating,
                options = (1..10).toList(),
                optionLabel = { stringResource(R.string.rating_value, it) },
                onSelect = { r -> onFormChange { it.copy(rating = r) } },
                noneLabel = stringResource(R.string.rating_none),
                error = error(GameField.RATING)?.asString(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .toggleable(
                        value = form.favorite,
                        role = Role.Switch,
                        onValueChange = { checked -> onFormChange { it.copy(favorite = checked) } },
                    )
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.field_favorite),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = form.favorite, onCheckedChange = null)
            }
            FormTextField(
                value = form.notes,
                onValueChange = { v -> onFormChange { it.copy(notes = v) } },
                label = stringResource(R.string.field_notes),
                error = error(GameField.NOTES),
                singleLine = false,
                minLines = 3,
            )
        }
    }
}

@Composable
private fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: UiText?,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    singleLine: Boolean = true,
    minLines: Int = 1,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val message = error?.asString() ?: supportingText
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        trailingIcon = trailingIcon,
        isError = error != null,
        supportingText = message?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
