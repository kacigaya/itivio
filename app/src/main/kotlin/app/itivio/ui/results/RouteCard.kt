package app.itivio.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.itivio.R
import app.itivio.domain.Alert
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.ui.common.LegChain
import app.itivio.ui.common.TimeText
import app.itivio.ui.common.amountText
import app.itivio.ui.common.formatDistance
import app.itivio.ui.common.formatDuration
import app.itivio.ui.common.formatTime

@Composable
fun RouteCard(journey: Journey, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val firstTransit = journey.legs.firstOrNull { it is Leg.Transit } as Leg.Transit?
    val startZone = journey.legs.first().from.zone
    val endZone = journey.legs.last().to.zone
    val scheme = MaterialTheme.colorScheme
    val secondary = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
    Card(
        onClick = onClick,
        // The selected card is the route on the map. Its container shows this, with no outline.
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer)
        } else {
            CardDefaults.cardColors(containerColor = scheme.surfaceContainer)
        },
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.time_range,
                        formatTime(journey.departure.best, startZone),
                        formatTime(journey.arrival.best, endZone),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    amountText(formatDuration(journey.duration), unitScale = 0.7f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            LegChain(journey)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (firstTransit != null) {
                    TimeText(firstTransit.start, firstTransit.from.zone)
                    Text(
                        firstTransit.from.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                color = if (journey.isCancelled) scheme.error else secondary,
            )
            AlertLine(journey.alerts)
        }
    }
}

/**
 * The first service alert of the journey on one line, and the number of the others on a pill.
 * The itinerary shows them all.
 */
@Composable
private fun AlertLine(alerts: List<Alert>) {
    val first = alerts.firstOrNull() ?: return
    val others = alerts.size - 1
    val more = if (others > 0) pluralStringResource(R.plurals.results_more_alerts, others, others) else null
    val description = listOfNotNull(first.header, more).joinToString(" · ")
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Icon(
            painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(
            first.header,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (others > 0) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = CircleShape,
            ) {
                Text(
                    stringResource(R.string.results_more_alerts_count, others),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}
