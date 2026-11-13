package app.itivio.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * A dialog that changes how the results are planned. The fields scroll when the window is short
 * or the text is large, so the title and the buttons stay visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickerDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable FlowRowScope.() -> Unit,
    fieldsAlignment: Alignment.Horizontal = Alignment.Start,
    fieldsArrangement: Arrangement.Vertical = Arrangement.Top,
    fields: @Composable ColumnScope.() -> Unit,
) {
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(
                modifier = Modifier.heightIn(max = (height - 48.dp).coerceAtLeast(0.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Column(
                    horizontalAlignment = fieldsAlignment,
                    verticalArrangement = fieldsArrangement,
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    content = fields,
                )
                FlowRow(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth(), content = buttons)
            }
        }
    }
}
