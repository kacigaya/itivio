package app.itivio.ui.walk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.itivio.R
import app.itivio.domain.CompassDirection
import app.itivio.domain.GeoPoint
import app.itivio.domain.Leg
import app.itivio.domain.Maneuver
import app.itivio.domain.WalkInstruction
import app.itivio.domain.WalkRoute
import app.itivio.ui.common.MapPanel
import app.itivio.ui.common.formatDistance
import app.itivio.ui.common.formatDuration
import app.itivio.ui.common.formatTime
import java.time.Duration
import java.time.Instant

val WalkPeekHeight = 112.dp

/** The provider default for walks, used when a leg has no distance or no duration. */
private const val DEFAULT_METERS_PER_SECOND = 1.2

/**
 * Turn-by-turn directions for one walk. [position] is the device position, or null without a
 * fix. The view never plans a new route by itself: [onReplan] runs only from its button.
 */
@Composable
fun WalkScreen(
    wide: Boolean,
    leg: Leg.Street,
    position: GeoPoint?,
    locationGranted: Boolean,
    following: Boolean,
    now: Instant,
    onClose: () -> Unit,
    onRecenter: () -> Unit,
    onRequestLocation: () -> Unit,
    onReplan: () -> Unit,
) {
    val route = remember(leg) { WalkRoute.of(leg.steps, leg.geometry) }
    val progress = position?.let(route::progress)
    // Off the route, the position on it means nothing; the view keeps the start then.
    val along = progress?.takeUnless { it.isOffRoute }?.alongMeters ?: 0.0
    val index = route.nextInstructionIndex(along)
    val destination = leg.to.name.ifBlank { stringResource(R.string.walk_destination) }

    // Turn-by-turn use needs the screen; the view runs only while the app is on screen.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    MapPanel(
        wide = wide,
        peekHeight = WalkPeekHeight,
        header = {
            route.instructions.getOrNull(index)?.let { instruction ->
                val distance = if (instruction.maneuver == Maneuver.DEPART) instruction.lengthMeters else instruction.startMeters - along
                InstructionCard(instruction, distance, destination, route.instructions.getOrNull(index + 1))
            }
            when {
                !locationGranted -> Banner(stringResource(R.string.walk_no_location), stringResource(R.string.allow_location), onRequestLocation)
                progress?.isOffRoute == true -> Banner(stringResource(R.string.walk_off_route), stringResource(R.string.walk_replan), onReplan)
            }
            if (locationGranted && !following) {
                FilledTonalButton(onClick = onRecenter, modifier = Modifier.align(Alignment.End)) {
                    Icon(painterResource(R.drawable.ic_my_location), contentDescription = null)
                    Text(stringResource(R.string.walk_recenter), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
    ) {
        val remaining = (route.lengthMeters - along).coerceAtLeast(0.0)
        val speed = (leg.distanceMeters / leg.duration.seconds.coerceAtLeast(1)).takeIf { leg.distanceMeters > 0 } ?: DEFAULT_METERS_PER_SECOND
        val left = Duration.ofSeconds((remaining / speed).toLong())
        Summary(left, remaining, now.plus(left), leg, onClose)
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "divider") { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            itemsIndexed(route.instructions) { position, instruction ->
                InstructionRow(instruction, destination, current = position == index)
            }
        }
    }
}

@Composable
private fun InstructionCard(instruction: WalkInstruction, meters: Double, destination: String, then: WalkInstruction?) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                // The next instruction is read out when it changes.
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                Icon(painterResource(instruction.maneuver.icon()), contentDescription = null, modifier = Modifier.size(48.dp))
                Column(Modifier.weight(1f)) {
                    if (instruction.maneuver != Maneuver.ARRIVE || meters > 0) {
                        Text(formatDistance(meters.coerceAtLeast(0.0)), style = MaterialTheme.typography.headlineMedium)
                    }
                    Text(instructionText(instruction, destination), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (then != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                ) {
                    Text(stringResource(R.string.walk_then), style = MaterialTheme.typography.labelLarge)
                    Icon(painterResource(then.maneuver.icon()), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(instructionText(then, destination), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Banner(message: String, action: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            Button(onClick = onAction) { Text(action) }
        }
    }
}

/** Remaining time, remaining distance, and arrival time, with the button that closes the view. */
@Composable
private fun Summary(left: Duration, meters: Double, arrival: Instant, leg: Leg.Street, onClose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    ) {
        FilledTonalIconButton(onClick = onClose, shape = CircleShape) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.walk_close))
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(
                formatDuration(left),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                listOf(formatDistance(meters), stringResource(R.string.walk_arrival, formatTime(arrival, leg.to.zone ?: leg.from.zone))).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InstructionRow(instruction: WalkInstruction, destination: String, current: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Icon(
            painterResource(instruction.maneuver.icon()),
            contentDescription = null,
            tint = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            instructionText(instruction, destination),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (instruction.lengthMeters > 0) {
            Text(
                formatDistance(instruction.lengthMeters),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The instruction in words. Without a street name, the text names only the maneuver. */
@Composable
internal fun instructionText(instruction: WalkInstruction, destination: String): String {
    val street = instruction.streetName
    return when (instruction.maneuver) {
        Maneuver.DEPART -> {
            val heading = instruction.heading?.let { stringResource(it.label()) }
            when {
                heading != null && street != null -> stringResource(R.string.walk_depart_heading_street, heading, street)
                heading != null -> stringResource(R.string.walk_depart_heading, heading)
                street != null -> stringResource(R.string.walk_depart_street, street)
                else -> stringResource(R.string.walk_depart)
            }
        }
        Maneuver.ARRIVE -> stringResource(R.string.walk_arrive, destination)
        Maneuver.STAIRS -> stringResource(R.string.walk_stairs)
        Maneuver.ELEVATOR -> stringResource(R.string.walk_elevator)
        else -> {
            val (plain, onto) = instruction.maneuver.texts()
            if (street != null) stringResource(onto, street) else stringResource(plain)
        }
    }
}

private fun Maneuver.texts(): Pair<Int, Int> = when (this) {
    Maneuver.STRAIGHT -> R.string.walk_continue to R.string.walk_continue_street
    Maneuver.SLIGHT_LEFT -> R.string.walk_slight_left to R.string.walk_slight_left_street
    Maneuver.LEFT -> R.string.walk_left to R.string.walk_left_street
    Maneuver.SHARP_LEFT -> R.string.walk_sharp_left to R.string.walk_sharp_left_street
    Maneuver.SLIGHT_RIGHT -> R.string.walk_slight_right to R.string.walk_slight_right_street
    Maneuver.RIGHT -> R.string.walk_right to R.string.walk_right_street
    Maneuver.SHARP_RIGHT -> R.string.walk_sharp_right to R.string.walk_sharp_right_street
    else -> R.string.walk_u_turn to R.string.walk_u_turn_street
}

private fun Maneuver.icon(): Int = when (this) {
    Maneuver.DEPART -> R.drawable.ic_navigation
    Maneuver.STRAIGHT -> R.drawable.ic_straight
    Maneuver.SLIGHT_LEFT -> R.drawable.ic_turn_slight_left
    Maneuver.LEFT -> R.drawable.ic_turn_left
    Maneuver.SHARP_LEFT -> R.drawable.ic_turn_sharp_left
    Maneuver.SLIGHT_RIGHT -> R.drawable.ic_turn_slight_right
    Maneuver.RIGHT -> R.drawable.ic_turn_right
    Maneuver.SHARP_RIGHT -> R.drawable.ic_turn_sharp_right
    Maneuver.U_TURN -> R.drawable.ic_u_turn_left
    Maneuver.STAIRS -> R.drawable.ic_stairs
    Maneuver.ELEVATOR -> R.drawable.ic_elevator
    Maneuver.ARRIVE -> R.drawable.ic_flag
}

private fun CompassDirection.label(): Int = when (this) {
    CompassDirection.NORTH -> R.string.walk_north
    CompassDirection.NORTHEAST -> R.string.walk_northeast
    CompassDirection.EAST -> R.string.walk_east
    CompassDirection.SOUTHEAST -> R.string.walk_southeast
    CompassDirection.SOUTH -> R.string.walk_south
    CompassDirection.SOUTHWEST -> R.string.walk_southwest
    CompassDirection.WEST -> R.string.walk_west
    CompassDirection.NORTHWEST -> R.string.walk_northwest
}
