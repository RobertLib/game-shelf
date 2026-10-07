package cz.gameshelf.app.ui.games.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One labelled value of the detail screen; `null` values are not shown at all. */
data class DetailField(val label: String, val value: String, val multiline: Boolean = false)

@Composable
fun DetailSection(title: String, fields: List<DetailField>, modifier: Modifier = Modifier) {
    if (fields.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .semantics { heading() },
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                fields.forEach { DetailRow(it) }
            }
        }
    }
}

@Composable
private fun ColumnScope.DetailRow(field: DetailField) {
    val labelStyle = MaterialTheme.typography.bodyMedium
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    if (field.multiline) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(field.label, style = labelStyle, color = labelColor)
            Text(field.value, style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                field.label,
                style = labelStyle,
                color = labelColor,
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline(),
            )
            Text(
                field.value,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1.3f)
                    .alignByBaseline(),
            )
        }
    }
}
