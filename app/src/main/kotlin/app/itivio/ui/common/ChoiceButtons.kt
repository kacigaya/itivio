package app.itivio.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One choice of [ChoiceButtons]. [label] is the full name. [text] is the text on the button: a
 * short form such as "1", or null for a button that shows only its icon. A screen reader gets
 * [label] in the two cases.
 */
class Choice(
    val label: String,
    @DrawableRes val icon: Int? = null,
    val text: String? = label,
    val iconRotation: Float = 0f,
)

/** Text this large does not fit beside an icon in a third of a row. */
private const val STACK_FONT_SCALE = 1.5f

/**
 * A single choice as a row of connected toggle buttons. The selected button has the secondary
 * color and a round shape, the same as a checked filter button, so it needs no check mark.
 * With large text and [stackLargeText], the choices are a vertical radio list, so each label
 * stays readable.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChoiceButtons(
    choices: List<Choice>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stackLargeText: Boolean = true,
) {
    if (stackLargeText && LocalDensity.current.fontScale >= STACK_FONT_SCALE) {
        Column(modifier.selectableGroup()) {
            choices.forEachIndexed { index, choice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(selected = index == selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(index) })
                        .padding(vertical = 4.dp)
                        .alpha(if (enabled) 1f else 0.38f),
                ) {
                    RadioButton(selected = index == selected, onClick = null, enabled = enabled)
                    ChoiceIcon(choice)
                    Text(choice.label, modifier = Modifier.weight(1f))
                }
            }
        }
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = modifier.fillMaxWidth().selectableGroup(),
    ) {
        choices.forEachIndexed { index, choice ->
            FilledTonalToggleButton(
                checked = index == selected,
                onCheckedChange = { onSelect(index) },
                enabled = enabled,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f).semantics {
                    role = Role.RadioButton
                    this.selected = index == selected
                    if (choice.text != choice.label) contentDescription = choice.label
                },
            ) {
                ChoiceIcon(choice)
                if (choice.text != null) {
                    if (choice.icon != null) Spacer(Modifier.width(6.dp))
                    Text(
                        choice.text,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (choice.text != choice.label) Modifier.clearAndSetSemantics {} else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceIcon(choice: Choice) {
    if (choice.icon == null) return
    Icon(
        painterResource(choice.icon),
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize).rotate(choice.iconRotation),
    )
}
