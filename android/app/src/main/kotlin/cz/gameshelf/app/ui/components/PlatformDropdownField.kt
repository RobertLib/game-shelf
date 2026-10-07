package cz.gameshelf.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cz.gameshelf.app.R
import cz.gameshelf.app.domain.model.Platform
import cz.gameshelf.app.domain.model.PlatformGroup
import cz.gameshelf.app.ui.common.labelRes

/** Platform picker grouped by manufacturer; typing into the field narrows the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformDropdownField(
    selected: Platform?,
    onSelect: (Platform) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    val resources = LocalResources.current
    val labels = remember(resources) { Platform.entries.associateWith { resources.getString(it.labelRes) } }
    val selectedLabel = selected?.let(labels::getValue).orEmpty()

    var expanded by remember { mutableStateOf(false) }
    var text by rememberSaveable(selected) { mutableStateOf(selectedLabel) }
    // Until the user types, the field shows the selection and the menu lists every platform.
    var isFiltering by rememberSaveable(selected) { mutableStateOf(false) }

    val groups: List<Pair<PlatformGroup, List<Platform>>> = remember(text, isFiltering, labels) {
        val needle = text.trim()
        Platform.entries
            .filter { !isFiltering || labels.getValue(it).contains(needle, ignoreCase = true) }
            .groupBy { it.group }
            .toList()
            .sortedBy { (group, _) -> group.ordinal }
    }

    fun close() {
        expanded = false
        text = selectedLabel
        isFiltering = false
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (it) expanded = true else close() },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                isFiltering = true
                expanded = true
            },
            singleLine = true,
            label = { Text(label) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable),
                )
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = ::close) {
            if (groups.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.platform_no_match)) },
                    onClick = {},
                    enabled = false,
                )
            }
            groups.forEach { (group, platforms) ->
                Text(
                    text = stringResource(group.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { heading() },
                )
                platforms.forEach { platform ->
                    MenuOption(labels.getValue(platform), isSelected = platform == selected) {
                        onSelect(platform)
                        text = labels.getValue(platform)
                        isFiltering = false
                        expanded = false
                    }
                }
            }
        }
    }
}
