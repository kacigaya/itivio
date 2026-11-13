package app.itivio.ui.results

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.itivio.R
import app.itivio.domain.Alert
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.TransitMode
import app.itivio.ui.common.LineBadge
import app.itivio.ui.common.TimeText
import app.itivio.ui.common.formatDistance
import app.itivio.ui.common.formatDuration
import app.itivio.ui.common.formatTime
import app.itivio.ui.common.icon
import app.itivio.ui.common.label

/** Walks shorter than this are noise in the leg chain; the itinerary still lists them. */
private const val MIN_SHOWN_WALK_SECONDS = 120

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteCard(journey: Journey, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val firstTransit = journey.legs.firstOrNull { it is Leg.Transit } as Leg.Transit?
    val startZone = journey.legs.first().from.zone
    val endZone = journey.legs.last().to.zone
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        R.string.time_range,
                        formatTime(journey.departure.best, startZone),
                        formatTime(journey.arrival.best, endZone),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatDuration(journey.duration),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                val shown = journey.legs.filter { it is Leg.Transit || it.duration.seconds >= MIN_SHOWN_WALK_SECONDS }
                shown.forEachIndexed { index, leg ->
                    if (index > 0) {
                        Icon(
                            painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    when (leg) {
                        is Leg.Transit -> LineBadge(leg.line, leg.mode)
                        is Leg.Street -> StreetChip(leg)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (firstTransit != null) {
                    TimeText(firstTransit.start, firstTransit.from.zone)
                    Text(
                        firstTransit.from.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else {
                    Text(stringResource(R.string.results_walk_only), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                listOfNotNull(
                    if (journey.isCancelled) stringResource(R.string.cancelled) else null,
                    if (journey.transfers == 0) stringResource(R.string.no_transfers)
                    else pluralStringResource(R.plurals.transfers, journey.transfers, journey.transfers),
                    if (journey.walkingMeters > 0) stringResource(R.string.walk_distance, formatDistance(journey.walkingMeters)) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (journey.isCancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AlertLine(journey.alerts)
        }
    }
}

/** The first service alert of the journey, and the number of the others. The itinerary shows them all. */
@Composable
private fun AlertLine(alerts: List<Alert>) {
    val first = alerts.firstOrNull() ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Icon(
            painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(
            listOfNotNull(
                first.header,
                (alerts.size - 1).takeIf { it > 0 }?.let { pluralStringResource(R.plurals.results_more_alerts, it, it) },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StreetChip(leg: Leg.Street) {
    val label = stringResource(leg.mode.label())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(leg.mode.icon()),
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        if (leg.mode == TransitMode.WALK) {
            Text(
                ((leg.duration.seconds + 30) / 60).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
