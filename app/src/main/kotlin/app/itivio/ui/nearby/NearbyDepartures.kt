package app.itivio.ui.nearby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
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
                groups.forEach { group ->
                    item(key = "stop-${group.name}") { StopHeader(group.name) }
                    items(group.departures, key = { "${group.name}-${it.tripId}-${it.time.scheduled}" }) { departure ->
                        DepartureRow(departure, now)
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

@Composable
private fun StopHeader(name: String) {
    Text(
        name,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
internal fun DepartureRow(departure: Departure, now: Instant) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        LineBadge(departure.line, departure.mode)
        Column(Modifier.weight(1f)) {
            Text(
                departure.headsign ?: departure.line.longName.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (departure.cancelled) TextDecoration.LineThrough else null,
            )
            val detail = when {
                departure.cancelled -> stringResource(R.string.cancelled)
                departure.stop.track != null -> stringResource(R.string.itinerary_track, departure.stop.track)
                else -> null
            }
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (departure.cancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val minutes = clockMinutesUntil(now, departure.time.best)
        if (minutes < 60) {
            Countdown(departure, minutes)
        } else {
            TimeText(departure.time, departure.stop.zone, emphasized = true)
        }
    }
}

/** Minutes until departure over the clock time. */
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
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.clearAndSetSemantics { contentDescription = description }) {
        CountdownText(minutes, live)
        Text(clock, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
