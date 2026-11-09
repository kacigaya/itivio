package app.itivio.ui.itinerary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.itivio.R
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.Stop
import app.itivio.ui.common.AttributionFooter
import app.itivio.ui.common.LineBadge
import app.itivio.ui.common.MapPanel
import app.itivio.ui.common.TimeText
import app.itivio.ui.common.formatDistance
import app.itivio.ui.common.formatDuration
import app.itivio.ui.common.formatTime
import app.itivio.ui.common.icon
import app.itivio.ui.common.label
import app.itivio.ui.common.lineColors

val ItineraryPeekHeight = 320.dp
private val RailWidth = 40.dp

@Composable
fun ItineraryScreen(wide: Boolean, journey: Journey, onBack: () -> Unit) {
    MapPanel(
        wide = wide,
        peekHeight = ItineraryPeekHeight,
        header = {
            FilledTonalIconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
            }
        },
    ) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            item(key = "summary") { Summary(journey) }
            itemsIndexed(journey.legs, key = { index, _ -> index }) { _, leg ->
                when (leg) {
                    is Leg.Transit -> TransitLegItem(leg)
                    is Leg.Street -> StreetLegItem(leg)
                }
            }
            item(key = "arrive") {
                val last = journey.legs.last()
                StopRow(
                    stop = last.to,
                    time = { TimeText(last.end, last.to.zone, emphasized = true) },
                    rail = { RailDot(MaterialTheme.colorScheme.primary, top = true, bottom = false) },
                    caption = stringResource(R.string.itinerary_arrive),
                )
            }
            item(key = "attribution") { AttributionFooter() }
        }
    }
}

@Composable
private fun Summary(journey: Journey) {
    val start = journey.legs.first().from.zone
    val end = journey.legs.last().to.zone
    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatDuration(journey.duration),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(
                stringResource(R.string.time_range, formatTime(journey.departure.best, start), formatTime(journey.arrival.best, end)),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            listOfNotNull(
                if (journey.transfers == 0) stringResource(R.string.no_transfers)
                else pluralStringResource(R.plurals.transfers, journey.transfers, journey.transfers),
                if (journey.walkingMeters > 0) stringResource(R.string.walk_distance, formatDistance(journey.walkingMeters)) else null,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransitLegItem(leg: Leg.Transit) {
    val (lineColor, _) = lineColors(leg.line)
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        StopRow(
            stop = leg.from,
            time = { TimeText(leg.start, leg.from.zone, emphasized = true) },
            rail = { RailDot(lineColor, top = false, bottom = true) },
            caption = leg.from.track?.let { stringResource(R.string.itinerary_track, it) },
        )
        Row(Modifier.height(IntrinsicSize.Min)) {
            Rail(lineColor)
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LineBadge(leg.line, leg.mode)
                    leg.headsign?.let {
                        Text(
                            stringResource(R.string.itinerary_towards, it),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                        )
                    }
                }
                if (leg.cancelled) {
                    Text(stringResource(R.string.cancelled), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                }
                leg.alerts.forEach { alert ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Text(alert.header, style = MaterialTheme.typography.bodySmall)
                    }
                }
                val stops = leg.intermediateStops
                Text(
                    listOfNotNull(
                        formatDuration(leg.duration),
                        if (stops.isNotEmpty()) pluralStringResource(R.plurals.itinerary_stops, stops.size + 1, stops.size + 1) else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (stops.isNotEmpty()) {
                    TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(stringResource(if (expanded) R.string.itinerary_hide_stops else R.string.itinerary_show_stops))
                    }
                }
                AnimatedVisibility(expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        stops.forEach { stop ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (stop.departure ?: stop.arrival)?.let { TimeText(it, stop.zone) }
                                Text(stop.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                leg.line.agency?.let {
                    Text(
                        stringResource(R.string.itinerary_operated_by, it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        StopRow(
            stop = leg.to,
            time = { TimeText(leg.end, leg.to.zone) },
            rail = { RailDot(lineColor, top = true, bottom = false) },
            caption = leg.to.track?.let { stringResource(R.string.itinerary_track, it) },
            emphasized = false,
        )
    }
}

@Composable
private fun StreetLegItem(leg: Leg.Street) {
    val label = stringResource(leg.mode.label())
    Row(Modifier.height(IntrinsicSize.Min).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(RailWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Icon(painterResource(leg.mode.icon()), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            if (leg.distanceMeters > 0) {
                "$label ${formatDistance(leg.distanceMeters)} · ${formatDuration(leg.duration)}"
            } else {
                "$label · ${formatDuration(leg.duration)}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun StopRow(
    stop: Stop,
    time: @Composable () -> Unit,
    rail: @Composable () -> Unit,
    caption: String?,
    emphasized: Boolean = true,
) {
    Row(Modifier.height(IntrinsicSize.Min).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(RailWidth).fillMaxHeight(), contentAlignment = Alignment.Center) { rail() }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                stop.name,
                style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
            )
            caption?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        time()
    }
}

@Composable
private fun Rail(color: Color) {
    Box(Modifier.width(RailWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(Modifier.width(RailStroke).fillMaxHeight().background(color))
    }
}

private val RailStroke: Dp = 6.dp

/** A stop marker on the rail, with rail segments above and/or below it. */
@Composable
private fun RailDot(color: Color, top: Boolean, bottom: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
        Box(Modifier.width(RailStroke).weight(1f).background(if (top) color else Color.Transparent))
        Box(
            Modifier
                .size(16.dp)
                .background(color, CircleShape)
                .padding(4.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
        )
        Box(Modifier.width(RailStroke).weight(1f).background(if (bottom) color else Color.Transparent))
    }
}
