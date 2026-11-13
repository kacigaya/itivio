package app.itivio.ui.itinerary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.itivio.R
import app.itivio.domain.Alert
import app.itivio.domain.Departure
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.Stop
import app.itivio.domain.TransitMode
import app.itivio.ui.common.AttributionFooter
import app.itivio.ui.common.CountdownText
import app.itivio.ui.common.IconTile
import app.itivio.ui.common.LegChain
import app.itivio.ui.common.LineBadge
import app.itivio.ui.common.MapPanel
import app.itivio.ui.common.Tag
import app.itivio.ui.common.TimeText
import app.itivio.ui.common.amountText
import app.itivio.ui.common.clockMinutesUntil
import app.itivio.ui.common.formatDistance
import app.itivio.ui.common.formatDuration
import app.itivio.ui.common.formatTime
import app.itivio.ui.common.icon
import app.itivio.ui.common.label
import app.itivio.ui.common.lineColors
import app.itivio.ui.common.markedStringResource
import java.time.Duration
import java.time.Instant

val ItineraryPeekHeight = 320.dp
private val RailWidth = 32.dp
private val RailStroke: Dp = 6.dp

/**
 * The steps of [journey]. [now] drives the tracker: the status tag and the outline around the
 * step in progress. [lineTimes] gives the next departures of each transit leg's line, by leg index.
 * [onWalk] opens the walking view of a walk.
 */
@Composable
fun ItineraryScreen(
    wide: Boolean,
    journey: Journey,
    now: Instant,
    lineTimes: Map<Int, List<Departure>>,
    onBack: () -> Unit,
    onWalk: (legIndex: Int) -> Unit,
    selection: TrainSelectionState = TrainSelectionState(),
    onSelectDeparture: (Int, Departure) -> Unit = { _, _ -> },
    onResetTrain: (Int) -> Unit = {},
) {
    val currentLeg = journey.currentLegIndex(now)
    MapPanel(
        wide = wide,
        peekHeight = ItineraryPeekHeight,
        header = {
            FilledTonalIconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
            }
        },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary") { Summary(journey, now) }
            if (journey.missedConnectionIndex != null) {
                item(key = "missed_connection") {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
                        Text(stringResource(R.string.itinerary_missed_connection), style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            }
            itemsIndexed(journey.legs, key = { index, _ -> index }) { index, leg ->
                Step(current = index == currentLeg, card = leg is Leg.Transit) {
                    when (leg) {
                        is Leg.Transit -> TransitLegItem(
                            leg, lineTimes[index].orEmpty(), now,
                            loading = selection.loadingLeg == index,
                            canSelect = selection.loadingLeg == null,
                            changed = index in selection.changedLegs,
                            platformChanged = index in selection.platformChangedLegs,
                            failed = selection.failedLeg == index,
                            onSelect = { onSelectDeparture(index, it) },
                            onReset = { onResetTrain(index) },
                        )
                        is Leg.Street -> StreetLegItem(leg, onWalk = { onWalk(index) }.takeIf { leg.hasWalkingView })
                    }
                }
            }
            item(key = "arrive") {
                val last = journey.legs.last()
                Step(current = false, card = false) { Arrival(last) }
            }
            item(key = "attribution") { AttributionFooter() }
        }
    }
}

/**
 * One step of the journey. A transit step is a card. The step in progress has an outline, like
 * the active card of a trip tracker. Its semantics state the same, so the outline is not the only
 * signal.
 */
@Composable
private fun Step(current: Boolean, card: Boolean, content: @Composable () -> Unit) {
    val label = stringResource(R.string.itinerary_current_step)
    Surface(
        color = if (card) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        shape = MaterialTheme.shapes.largeIncreased,
        border = if (current) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().then(if (current) Modifier.semantics { stateDescription = label } else Modifier),
        content = content,
    )
}

/** Time to the departure before the journey starts, then time to the arrival. Null after the arrival. */
@Composable
private fun trackerStatus(journey: Journey, now: Instant): String? = when {
    now < journey.departure.best -> {
        val wait = clockMinutesUntil(now, journey.departure.best)
        if (wait < 1) stringResource(R.string.itinerary_leave_now)
        else stringResource(R.string.itinerary_leave_in, formatDuration(Duration.ofMinutes(wait)))
    }
    now < journey.arrival.best ->
        stringResource(R.string.itinerary_arrive_in, formatDuration(Duration.ofMinutes(clockMinutesUntil(now, journey.arrival.best))))
    else -> null
}

/** The hero card: the tracker status, the duration, the times, and the legs of the journey. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(journey: Journey, now: Instant) {
    val scheme = MaterialTheme.colorScheme
    val departure = formatTime(journey.departure.best, journey.legs.first().from.zone)
    val arrival = formatTime(journey.arrival.best, journey.legs.last().to.zone)
    Surface(
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                trackerStatus(journey, now)?.let {
                    // The status follows live times only when the journey has them.
                    if (journey.hasRealtime) {
                        Tag(R.drawable.ic_sensors, it, color = scheme.tertiary, contentColor = scheme.onTertiary)
                    } else {
                        Tag(R.drawable.ic_schedule, it)
                    }
                }
                Tag(
                    R.drawable.ic_sync_alt,
                    if (journey.transfers == 0) stringResource(R.string.no_transfers)
                    else pluralStringResource(R.plurals.transfers, journey.transfers, journey.transfers),
                )
                if (journey.walkingMeters > 0) {
                    val distance = formatDistance(journey.walkingMeters)
                    val description = stringResource(R.string.walk_distance, distance)
                    Tag(R.drawable.ic_directions_walk, distance, Modifier.clearAndSetSemantics { contentDescription = description })
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    amountText(formatDuration(journey.duration), unitScale = 0.5f),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                val range = stringResource(R.string.time_range, departure, arrival)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 8.dp).clearAndSetSemantics { contentDescription = range },
                ) {
                    Text(departure, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(arrival, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            LegChain(journey)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransitLegItem(
    leg: Leg.Transit, departures: List<Departure>, now: Instant,
    loading: Boolean, canSelect: Boolean, changed: Boolean, platformChanged: Boolean, failed: Boolean,
    onSelect: (Departure) -> Unit, onReset: () -> Unit,
) {
    val (lineColor, _) = lineColors(leg.line)
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
        StopRow(
            stop = leg.from,
            time = { TimeText(leg.start, leg.from.zone, emphasized = true) },
            rail = { RailDot(lineColor, top = false, bottom = true) },
            caption = leg.from.track?.let { stringResource(R.string.itinerary_track, it) },
        )
        Row(Modifier.height(IntrinsicSize.Min)) {
            Rail(lineColor)
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LineBadge(leg.line, leg.mode)
                    Column(Modifier.weight(1f)) {
                        leg.tripCode?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        leg.headsign?.let {
                            Text(
                                markedStringResource(R.string.itinerary_towards, it),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Light,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (leg.cancelled) {
                    Text(
                        stringResource(R.string.cancelled),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (leg.alerts.isNotEmpty()) AlertBanner(leg.alerts)
                if (platformChanged) {
                    Text(stringResource(R.string.itinerary_platform_changed), style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                // Line times refresh once a minute; drop the ones that left since.
                val upcoming = departures.filter { clockMinutesUntil(now, it.time.best) >= 0 }
                if (upcoming.isNotEmpty() || loading || failed || changed) {
                    TrainDepartures(upcoming, leg.tripId, now, loading, canSelect, changed, failed, onSelect, onReset)
                }
                val stops = leg.intermediateStops
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Tag(R.drawable.ic_schedule, formatDuration(leg.duration), color = MaterialTheme.colorScheme.surfaceContainerHighest)
                    if (stops.isNotEmpty()) {
                        StopsToggle(count = stops.size + 1, expanded = expanded, onClick = { expanded = !expanded })
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
                        fontWeight = FontWeight.Light,
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

/** The number of stops of a leg. A tap shows or hides the stops between boarding and alighting. */
@Composable
private fun StopsToggle(count: Int, expanded: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.heightIn(min = 28.dp).padding(start = 10.dp, end = 6.dp),
        ) {
            Text(
                pluralStringResource(R.plurals.itinerary_stops, count, count),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = stringResource(if (expanded) R.string.itinerary_hide_stops else R.string.itinerary_show_stops),
                modifier = Modifier.size(18.dp).rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

/**
 * The service alerts of a leg in one banner. It shows the first alert and the number of the
 * others; a tap shows them all.
 */
@Composable
internal fun AlertBanner(alerts: List<Alert>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val more = alerts.size - 1
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .clickable(
                    enabled = more > 0,
                    onClickLabel = stringResource(if (expanded) R.string.itinerary_hide_alerts else R.string.itinerary_show_alerts),
                ) { expanded = !expanded }
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (if (expanded) alerts else alerts.take(1)).forEach { alert ->
                    Text(
                        alert.header,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded || more == 0) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (more > 0) {
                val description = pluralStringResource(R.plurals.results_more_alerts, more, more)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                ) {
                    Text(
                        stringResource(R.string.itinerary_more_alerts, more),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Icon(
                        painterResource(R.drawable.ic_expand_more),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
                    )
                }
            }
        }
    }
}

/**
 * The next departures of the leg's line at the boarding stop. The planned trip is on a tertiary
 * container. A live departure in the next hour shows as a countdown; other departures show the
 * clock time.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TrainDepartures(
    departures: List<Departure>, selectedTripId: String?, now: Instant,
    loading: Boolean, canSelect: Boolean, changed: Boolean, failed: Boolean,
    onSelect: (Departure) -> Unit, onReset: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainerHighest, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp).selectableGroup()) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.itinerary_next_departures), style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (loading) LoadingIndicator(Modifier.size(32.dp).clearAndSetSemantics {})
                if (changed) TextButton(onClick = onReset, enabled = canSelect) { Text(stringResource(R.string.itinerary_reset_train)) }
            }
            if (loading || failed) {
                Text(stringResource(if (failed) R.string.itinerary_change_train_failed else R.string.itinerary_changing_train),
                    style = MaterialTheme.typography.bodySmall, color = if (failed) scheme.error else scheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            departures.forEach { departure ->
                val selected = selectedTripId != null && departure.tripId == selectedTripId
                Surface(
                    selected = selected,
                    onClick = { onSelect(departure) },
                    enabled = canSelect && !departure.cancelled && !departure.tripId.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton },
                    color = if (selected) scheme.tertiaryContainer else Color.Transparent,
                    contentColor = if (selected) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 8.dp)) {
                        RadioButton(selected = selected, onClick = null,
                            enabled = canSelect && !departure.cancelled && !departure.tripId.isNullOrBlank())
                        Column(Modifier.weight(1f)) {
                            val name = departure.headsign ?: departure.line.name
                            Text(departure.tripCode?.let { stringResource(R.string.itinerary_trip_code, it, name) } ?: name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                textDecoration = if (departure.cancelled) TextDecoration.LineThrough else null,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            departure.stop.track?.let { Text(stringResource(R.string.itinerary_track, it), style = MaterialTheme.typography.bodySmall) }
                            if (departure.cancelled) Text(stringResource(R.string.cancelled), style = MaterialTheme.typography.bodySmall)
                        }
                        val minutes = clockMinutesUntil(now, departure.time.best)
                        if (departure.time.isRealtime && minutes < 60) {
                            CountdownText(minutes, live = true, style = MaterialTheme.typography.titleMedium)
                        } else {
                            TimeText(departure.time, departure.stop.zone, emphasized = true)
                        }
                    }
                }
            }
        }
    }
}

/** A walk with a route on the map can open the walking view. */
private val Leg.Street.hasWalkingView: Boolean get() = mode == TransitMode.WALK && geometry.size >= 2

@Composable
private fun StreetLegItem(leg: Leg.Street, onWalk: (() -> Unit)?) {
    val label = stringResource(leg.mode.label())
    val duration = formatDuration(leg.duration)
    val distance = if (leg.distanceMeters > 0) formatDistance(leg.distanceMeters) else null
    // The icon names the mode on screen; a screen reader gets the word.
    val description = if (distance != null) {
        stringResource(R.string.street_leg, label, distance, duration)
    } else {
        stringResource(R.string.street_leg_no_distance, label, duration)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        IconTile(leg.mode.icon())
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(distance ?: duration) }
                if (distance != null) append(" · ").also { append(duration) }
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Light,
            modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
        )
        if (onWalk != null) {
            val open = stringResource(R.string.walk_open)
            Button(
                onClick = onWalk,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.semantics { contentDescription = open },
            ) {
                Icon(painterResource(R.drawable.ic_navigation), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.walk_open_short), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing).clearAndSetSemantics {})
            }
        }
    }
}

/** The last stop of the journey, with the arrival time. */
@Composable
private fun Arrival(last: Leg) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        IconTile(R.drawable.ic_flag, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        Column(Modifier.weight(1f)) {
            Text(
                last.to.name.ifBlank { stringResource(R.string.search_my_location) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.itinerary_arrive),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TimeText(last.end, last.to.zone, emphasized = true)
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
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                // "My location" has no name.
                stop.name.ifBlank { stringResource(R.string.search_my_location) },
                style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            )
            caption?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

/** A stop marker on the rail, with rail segments above and/or below it. */
@Composable
private fun RailDot(color: Color, top: Boolean, bottom: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) {
        Box(Modifier.width(RailStroke).weight(1f).background(if (top) color else Color.Transparent))
        Box(
            Modifier
                .size(18.dp)
                .background(color, CircleShape)
                .padding(5.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
        )
        Box(Modifier.width(RailStroke).weight(1f).background(if (bottom) color else Color.Transparent))
    }
}
