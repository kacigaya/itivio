package app.itivio.ui.results

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.itivio.ui.common.JourneyStatusTag
import java.time.Duration
import java.time.Instant
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import app.itivio.ui.common.SkeletonBlock
import app.itivio.ui.common.SkeletonText
import app.itivio.ui.common.TimeText
import app.itivio.ui.common.amountText
import app.itivio.ui.common.formatDistance
import app.itivio.ui.common.formatDuration
import app.itivio.ui.common.formatTime

@Composable
fun RouteCard(journey: Journey, selected: Boolean, now: Instant, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    JourneyStatusTag(journey, now)
                    Text(
                        stringResource(
                            R.string.time_range,
                            formatTime(journey.departure.best, startZone),
                            formatTime(journey.arrival.best, endZone),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Text(
                    amountText(formatDuration(journey.duration), unitScale = 0.7f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            LegBar(journey, walkColor = secondary)
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

/** The placeholder of a [RouteCard]. It goes in a [Skeleton]. */
@Composable
fun RouteCardSkeleton(modifier: Modifier = Modifier) {
    val type = MaterialTheme.typography
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                SkeletonText(type.titleMedium, Modifier.fillMaxWidth(0.4f))
                SkeletonText(type.titleLarge, Modifier.width(72.dp))
            }
            SkeletonBlock(Modifier.fillMaxWidth().height(LegBarHeight))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBlock(Modifier.size(width = 40.dp, height = 28.dp))
                SkeletonBlock(Modifier.size(width = 64.dp, height = 28.dp))
                SkeletonBlock(Modifier.size(width = 56.dp, height = 28.dp))
            }
            SkeletonText(type.bodyMedium, Modifier.fillMaxWidth(0.6f))
            SkeletonText(type.bodySmall, Modifier.fillMaxWidth(0.45f))
        }
    }
}

private val LegBarHeight = 8.dp

/** One leg on the [LegBar], as fractions of the journey time from its departure. */
internal data class BarSegment(val leg: Leg, val start: Float, val end: Float)

/** The legs placed on a 0..1 time axis. A wait between two legs stays empty. Legs with no time are left out. */
internal fun barSegments(journey: Journey): List<BarSegment> {
    val total = journey.duration.toMillis().toFloat()
    if (total <= 0f) return emptyList()
    fun at(time: Instant) = (Duration.between(journey.departure.best, time).toMillis() / total).coerceIn(0f, 1f)
    return journey.legs.mapNotNull { leg ->
        val start = at(leg.start.best)
        val end = at(leg.end.best)
        if (end > start) BarSegment(leg, start, end) else null
    }
}

/**
 * The legs on a bar in proportion to their time: a line in its color, a walk as dots, a wait as
 * a gap. It is for the eye only; the leg chain below names each line for a screen reader.
 */
@Composable
private fun LegBar(journey: Journey, walkColor: Color) {
    val segments = barSegments(journey)
    if (segments.isEmpty()) return
    // A line without a color gets secondary, which shows on both card colors.
    val fallback = MaterialTheme.colorScheme.secondary
    val colors = segments.map { (it.leg as? Leg.Transit)?.let { leg -> leg.line.color?.let(::Color) ?: fallback } }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.fillMaxWidth().height(LegBarHeight)) {
        val gap = 2.dp.toPx()
        val dot = 1.5.dp.toPx()
        val step = 6.dp.toPx()
        segments.forEachIndexed { index, segment ->
            var left = segment.start * size.width + gap
            var right = segment.end * size.width - gap
            if (rtl) left = size.width - right.also { right = size.width - left }
            val color = colors[index]
            if (color != null) {
                val width = (right - left).coerceAtLeast(size.height)
                drawRoundRect(color, Offset(left, 0f), Size(width, size.height), CornerRadius(size.height / 2))
            } else {
                var x = left + dot
                while (x <= right - dot) {
                    drawCircle(walkColor, dot, Offset(x, size.height / 2))
                    x += step
                }
            }
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
