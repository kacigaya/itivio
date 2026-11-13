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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.itivio.R
import app.itivio.domain.CompassDirection
import app.itivio.domain.GeoPoint
import app.itivio.domain.Leg
import app.itivio.domain.Maneuver
import app.itivio.domain.WalkInstruction
import app.itivio.domain.WalkRoute
import app.itivio.ui.common.IconTile
import app.itivio.ui.common.MapPanel
import app.itivio.ui.common.amountText
import app.itivio.ui.common.formatDistance
import app.itivio.ui.common.formatDuration
import app.itivio.ui.common.formatTime
import app.itivio.ui.common.markedStringResource
import java.time.Duration
import java.time.Instant

// Drag handle, the summary row, and the progress bar.
val WalkPeekHeight = 156.dp

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
        val done = if (route.lengthMeters > 0) (along / route.lengthMeters).toFloat().coerceIn(0f, 1f) else 0f
        Summary(left, remaining, now.plus(left), leg, done, onClose)
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(route.instructions) { position, instruction ->
                InstructionRow(instruction, destination, current = position == index)
            }
        }
    }
}

@Composable
private fun InstructionCard(instruction: WalkInstruction, meters: Double, destination: String, then: WalkInstruction?) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                // The next instruction is read out when it changes.
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                Surface(color = scheme.primary, contentColor = scheme.onPrimary, shape = MaterialTheme.shapes.largeIncreased) {
                    Icon(painterResource(instruction.maneuver.icon()), contentDescription = null, modifier = Modifier.padding(14.dp).size(44.dp))
                }
                Column(Modifier.weight(1f)) {
                    if (instruction.maneuver != Maneuver.ARRIVE || meters > 0) {
                        // The distance is the largest text on the screen: a walker reads it at a glance.
                        Text(
                            amountText(formatDistance(meters.coerceAtLeast(0.0)), unitScale = 0.55f),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Text(instructionText(instruction, destination), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                }
            }
            if (then != null) {
                Surface(
                    color = scheme.onPrimaryContainer.copy(alpha = 0.12f),
                    contentColor = scheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.semantics(mergeDescendants = true) {}.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(stringResource(R.string.walk_then), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Light)
                        Icon(painterResource(then.maneuver.icon()), contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            instructionText(then, destination),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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

/**
 * Remaining time, remaining distance, and arrival time, with the button that closes the view.
 * The wavy bar shows the part of the walk that is done ([done], 0 to 1).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Summary(left: Duration, meters: Double, arrival: Instant, leg: Leg.Street, done: Float, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    amountText(formatDuration(left)),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.semantics { heading() },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(painterResource(R.drawable.ic_directions_walk), contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
                    Text(formatDistance(meters), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Icon(
                        painterResource(R.drawable.ic_flag),
                        contentDescription = null,
                        tint = muted,
                        modifier = Modifier.padding(start = 8.dp).size(16.dp),
                    )
                    Text(
                        markedStringResource(R.string.walk_arrival, formatTime(arrival, leg.to.zone ?: leg.from.zone)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Light,
                        color = muted,
                    )
                }
            }
            FilledIconButton(
                onClick = onClose,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.walk_close))
            }
        }
        val progressLabel = stringResource(R.string.walk_progress)
        LinearWavyProgressIndicator(
            progress = { done },
            modifier = Modifier.fillMaxWidth().padding(end = 8.dp).semantics { contentDescription = progressLabel },
        )
    }
}

@Composable
private fun InstructionRow(instruction: WalkInstruction, destination: String, current: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (current) {
            IconTile(instruction.maneuver.icon(), color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        } else {
            IconTile(instruction.maneuver.icon(), color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            instructionText(instruction, destination),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (instruction.lengthMeters > 0) {
            Text(
                formatDistance(instruction.lengthMeters),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Light,
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
