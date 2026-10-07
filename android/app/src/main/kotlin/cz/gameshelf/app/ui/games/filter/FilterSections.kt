package cz.gameshelf.app.ui.games.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cz.gameshelf.app.R
import cz.gameshelf.app.ui.components.DateField
import java.time.LocalDate

@Composable
fun FilterSection(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            trailing?.invoke()
        }
        content()
    }
}

@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    count: Int? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(if (count != null) stringResource(R.string.facet_with_count, label, count) else label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}

/** Multi-select of [options] as filter chips; [count] adds the number of games from facets. */
@Composable
fun <T> ChipFilterSection(
    title: String,
    options: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
    count: (T) -> Int? = { null },
    emptyText: String? = null,
) {
    FilterSection(title) {
        if (options.isEmpty() && emptyText != null) {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                SelectableChip(
                    label = label(option),
                    selected = option in selected,
                    count = count(option),
                    onClick = { onToggle(option) },
                )
            }
        }
    }
}

@Composable
fun RangeInputSection(
    title: String,
    from: String,
    to: String,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    keyboardType: KeyboardType,
    error: String?,
    fromPlaceholder: String? = null,
    toPlaceholder: String? = null,
) {
    FilterSection(title) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RangeTextField(
                label = stringResource(R.string.field_from),
                value = from,
                onValueChange = onFromChange,
                keyboardType = keyboardType,
                isError = error != null,
                placeholder = fromPlaceholder,
                modifier = Modifier.weight(1f),
            )
            RangeTextField(
                label = stringResource(R.string.field_to),
                value = to,
                onValueChange = onToChange,
                keyboardType = keyboardType,
                isError = error != null,
                placeholder = toPlaceholder,
                modifier = Modifier.weight(1f),
            )
        }
        FieldError(error)
    }
}

@Composable
private fun RangeTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    isError: Boolean,
    placeholder: String?,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}

@Composable
fun DateRangeSection(
    title: String,
    from: LocalDate?,
    to: LocalDate?,
    onFromChange: (LocalDate?) -> Unit,
    onToChange: (LocalDate?) -> Unit,
    error: String?,
) {
    FilterSection(title) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateField(from, onFromChange, stringResource(R.string.field_from), Modifier.weight(1f), error = error?.let { "" })
            DateField(to, onToChange, stringResource(R.string.field_to), Modifier.weight(1f), error = error?.let { "" })
        }
        FieldError(error)
    }
}

@Composable
private fun FieldError(error: String?) {
    if (error != null) {
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
