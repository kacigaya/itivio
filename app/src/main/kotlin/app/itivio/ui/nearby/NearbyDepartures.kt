package app.itivio.ui.nearby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.itivio.R
import app.itivio.domain.Departure
import app.itivio.ui.common.CountdownText
import app.itivio.ui.common.FailureState
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.LoadingState
import app.itivio.ui.common.LineBadge
import app.itivio.ui.common.MessageState
import app.itivio.ui.common.TimeText
import app.itivio.ui.common.clockMinutesUntil
import app.itivio.ui.common.formatDistance
import app.itivio.ui.common.formatTime
import java.time.Instant
import kotlinx.coroutines.delay

/** The current time, updated often enough for minute countdowns. */
@Composable
fun rememberNow(): State<Instant> = produceState(Instant.now()) {
    while (true) {
        delay(15_000)
        value = Instant.now()
    }
}

/** Adds the nearby departures section to a list. [now] drives the countdowns. */
fun LazyListScope.nearbyDepartures(state: LoadState<List<StopDepartures>>, now: Instant, onRetry: () -> Unit) {
    item(key = "nearby-title") {
        Text(
            stringResource(R.string.nearby_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp).semantics { heading() },
        )
    }
    when (state) {
        // Idle means the first location fix has not arrived yet.
        LoadState.Idle, LoadState.Loading -> item(key = "nearby-loading") { LoadingState() }
        is LoadState.Failed -> item(key = "nearby-error") { FailureState(state.failure, onRetry) }
        is LoadState.Ready -> {
            val groups = state.value.upcoming(now)
            if (groups.isEmpty()) {
                item(key = "nearby-empty") {
                    MessageState(
                        icon = R.drawable.ic_info,
                        title = stringResource(R.string.nearby_empty_title),
                        body = stringResource(R.string.nearby_empty_body),
                    )
                }
            } else {
                items(groups, key = { "stop-${it.name}" }) { group ->
                    DeparturesCard(group.departures, now, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        StopHeader(group.name, group.distanceMeters)
                    }
                }
            }
        }
    }
}

/**
 * Drops departures from past clock minutes, and stops left without any. The list refreshes once a
 * minute, and the provider can return a departure that just left; neither may read "Now".
 */
internal fun List<StopDepartures>.upcoming(now: Instant): List<StopDepartures> = mapNotNull { group ->
    group.copy(departures = group.departures.filter { clockMinutesUntil(now, it.time.best) >= 0 })
        .takeIf { it.departures.isNotEmpty() }
}

/**
 * Splits departures in time order into one list for each line and direction. The lists keep the
 * time order, and are in order of their first departure.
 */
internal fun List<Departure>.byLine(): List<List<Departure>> =
    groupBy { Triple(it.line.id ?: it.line.name, it.mode, it.headsign ?: it.line.longName) }.values.toList()

/** How many later departures a row names after the next one. */
private const val LATER_DEPARTURES = 3

/**
 * The departures of one stop on a card: one row for each line and direction. [header] names the
 * stop when the screen shows more than one.
 */
@Composable
internal fun DeparturesCard(
    departures: List<Departure>,
    now: Instant,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val lines = remember(departures) { departures.byLine() }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.largeIncreased,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            header?.invoke()
            lines.forEachIndexed { index, line ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LineRow(line, now)
            }
        }
    }
}

@Composable
private fun StopHeader(name: String, distanceMeters: Double?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (distanceMeters != null) {
            val distance = formatDistance(distanceMeters)
            val description = stringResource(R.string.walk_distance, distance)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            ) {
                Icon(
                    painterResource(R.drawable.ic_directions_walk),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    distance,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The next departure of one line in one direction, and the times of the departures after it. */
@Composable
private fun LineRow(departures: List<Departure>, now: Instant) {
    val next = departures.first()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 10.dp),
    ) {
        LineBadge(next.line, next.mode)
        Column(Modifier.weight(1f)) {
            Text(
                next.headsign ?: next.line.longName.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (next.cancelled) TextDecoration.LineThrough else null,
            )
            val detail = when {
                next.cancelled -> stringResource(R.string.cancelled)
                next.stop.track != null -> stringResource(R.string.itinerary_track, next.stop.track)
                else -> null
            }
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (next.cancelled) FontWeight.Bold else FontWeight.Light,
                    color = if (next.cancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            val minutes = clockMinutesUntil(now, next.time.best)
            if (minutes < 60) Countdown(next, minutes) else TimeText(next.time, next.stop.zone, emphasized = true)
            laterText(departures, now)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The departures after the first one: "then 4 · 8 min". Departures an hour or more away have no
 * countdown, so the text gives the clock time of the first of them.
 */
@Composable
private fun laterText(departures: List<Departure>, now: Instant): String? {
    val later = departures.drop(1).filterNot { it.cancelled }.take(LATER_DEPARTURES)
    val minutes = later.map { clockMinutesUntil(now, it.time.best) }.filter { it < 60 }
    return when {
        minutes.isNotEmpty() -> stringResource(R.string.nearby_then_minutes, minutes.joinToString(" · "))
        later.isNotEmpty() -> stringResource(R.string.nearby_then, formatTime(later.first().time.best, later.first().stop.zone))
        else -> null
    }
}

/** Minutes until departure. A screen reader also gets the clock time. */
@Composable
private fun Countdown(departure: Departure, minutes: Long) {
    val live = departure.time.isRealtime
    val countdown = if (minutes <= 0) stringResource(R.string.nearby_now) else stringResource(R.string.duration_min, minutes.toInt())
    val clock = formatTime(departure.time.best, departure.stop.zone)
    val description = stringResource(
        R.string.nearby_countdown_description,
        countdown,
        clock,
        stringResource(if (live) R.string.live else R.string.scheduled),
    )
    CountdownText(minutes, live, Modifier.clearAndSetSemantics { contentDescription = description })
}
