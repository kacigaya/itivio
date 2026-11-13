package app.itivio.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.itivio.R
import app.itivio.domain.JourneyOptions
import app.itivio.domain.TransitMode
import app.itivio.domain.WalkingSpeed
import app.itivio.ui.common.Choice
import app.itivio.ui.common.ChoiceButtons
import app.itivio.ui.common.icon
import app.itivio.ui.common.label

/** Opens the journey options. Selected when an option is not at its default. */
@Composable
fun JourneyOptionsChip(options: JourneyOptions, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilterButton(
        label = stringResource(R.string.options_chip),
        checked = !options.isDefault,
        onClick = onClick,
        enabled = enabled,
        leadingIcon = R.drawable.ic_tune,
        modifier = modifier,
    )
}

/**
 * Edits a copy of [options]. [onConfirm] gets the copy, so the changes cause one new search and
 * not one for each control.
 */
@Composable
fun JourneyOptionsDialog(options: JourneyOptions, onDismiss: () -> Unit, onConfirm: (JourneyOptions) -> Unit) {
    var draft by rememberSaveable(stateSaver = JourneyOptionsSaver) { mutableStateOf(options) }

    PickerDialog(
        title = stringResource(R.string.options_title),
        onDismiss = onDismiss,
        buttons = {
            TextButton(onClick = { draft = JourneyOptions() }, enabled = !draft.isDefault) {
                Text(stringResource(R.string.options_reset))
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = { onConfirm(draft) }) { Text(stringResource(R.string.done)) }
        },
        fieldsArrangement = Arrangement.spacedBy(8.dp),
    ) { JourneyOptionsFields(draft, onChange = { draft = it }) }
}

@Composable
private fun JourneyOptionsFields(options: JourneyOptions, onChange: (JourneyOptions) -> Unit) {
    val chipSpacing = Arrangement.spacedBy(8.dp)

    SectionTitle(R.string.options_modes)
    FlowRow(horizontalArrangement = chipSpacing) {
        JourneyOptions.SELECTABLE_MODES.forEach { mode ->
            val allowed = mode !in options.excludedModes
            FilterChip(
                selected = allowed,
                onClick = { onChange(options.withMode(mode, !allowed)) },
                label = { Text(stringResource(mode.label())) },
                leadingIcon = { Icon(painterResource(mode.icon()), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
    // Tells why a tap on the last selected mode changes nothing.
    if (options.excludedModes.size == JourneyOptions.SELECTABLE_MODES.size - 1) {
        SupportingText(stringResource(R.string.options_modes_last), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }

    SectionTitle(R.string.options_transfers)
    val limits = listOf<Int?>(null) + (0..JourneyOptions.MAX_TRANSFER_LIMIT)
    // One row of short buttons. The line below gives the choice in words.
    ChoiceButtons(
        choices = limits.map { limit ->
            if (limit == null) Choice(transferLimitLabel(limit), icon = R.drawable.ic_all_inclusive, text = null)
            else Choice(transferLimitLabel(limit), text = limit.toString())
        },
        selected = limits.indexOf(options.maxTransfers),
        onSelect = { onChange(options.copy(maxTransfers = limits[it])) },
        stackLargeText = false,
    )
    SupportingText(transferLimitLabel(options.maxTransfers), Modifier.clearAndSetSemantics {})

    SectionTitle(R.string.options_walking)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(options.wheelchair, role = Role.Switch, onValueChange = { onChange(options.copy(wheelchair = it)) })
            .padding(vertical = 8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_accessible), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.options_wheelchair), style = MaterialTheme.typography.bodyLarge)
            SupportingText(stringResource(R.string.options_wheelchair_body))
        }
        Switch(checked = options.wheelchair, onCheckedChange = null)
    }
    Text(stringResource(R.string.options_walking_speed), style = MaterialTheme.typography.bodyLarge)
    ChoiceButtons(
        // More arrows for more speed. The arrows point in the direction of travel.
        choices = WalkingSpeed.entries.map { Choice(stringResource(it.label()), icon = it.icon(), iconRotation = 90f) },
        selected = options.walkingSpeed.ordinal,
        onSelect = { onChange(options.copy(walkingSpeed = WalkingSpeed.entries[it])) },
        enabled = !options.wheelchair,
    )
    if (options.wheelchair) SupportingText(stringResource(R.string.options_walking_speed_unused))
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun SupportingText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun transferLimitLabel(limit: Int?): String = when (limit) {
    null -> stringResource(R.string.options_no_limit)
    0 -> stringResource(R.string.no_transfers)
    else -> pluralStringResource(R.plurals.transfers, limit, limit)
}

private fun WalkingSpeed.label(): Int = when (this) {
    WalkingSpeed.SLOW -> R.string.options_speed_slow
    WalkingSpeed.NORMAL -> R.string.options_speed_normal
    WalkingSpeed.FAST -> R.string.options_speed_fast
}

private fun WalkingSpeed.icon(): Int = when (this) {
    WalkingSpeed.SLOW -> R.drawable.ic_stat_1
    WalkingSpeed.NORMAL -> R.drawable.ic_stat_2
    WalkingSpeed.FAST -> R.drawable.ic_stat_3
}

/** Strings only, so the draft survives a configuration change without a cast. */
private val JourneyOptionsSaver = listSaver<JourneyOptions, String>(
    save = {
        listOf(
            it.excludedModes.joinToString(",") { mode -> mode.name },
            it.maxTransfers?.toString().orEmpty(),
            it.wheelchair.toString(),
            it.walkingSpeed.name,
        )
    },
    restore = { (modes, transfers, wheelchair, speed) ->
        JourneyOptions(
            excludedModes = modes.split(',').filter { it.isNotEmpty() }.map(TransitMode::valueOf).toSet(),
            maxTransfers = transfers.toIntOrNull(),
            wheelchair = wheelchair.toBoolean(),
            walkingSpeed = WalkingSpeed.valueOf(speed),
        )
    },
)
