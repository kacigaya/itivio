package app.itivio.ui.disruptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.itivio.R

import app.itivio.domain.AlertCause
import app.itivio.domain.AlertEffect
import app.itivio.domain.AlertSeverity
import app.itivio.domain.Disruption
import app.itivio.domain.TimeRange
import app.itivio.ui.common.FailureState
import app.itivio.ui.common.LineBadge
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.LoadingState
import app.itivio.ui.common.MessageState
import app.itivio.ui.common.Page
import app.itivio.ui.common.Tag
import app.itivio.ui.common.formatTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The service disruptions on the saved stops and on the stops near the device. [now] picks the
 * time period that each disruption shows.
 */
@Composable
fun DisruptionsScreen(state: LoadState<DisruptionGroups>, now: Instant, onRetry: () -> Unit) {
    Page(title = stringResource(R.string.disruptions_title), onBack = null) {
        item(key = "scope") {
            Text(
                stringResource(R.string.disruptions_scope),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
        }
        when (state) {
            LoadState.Idle, LoadState.Loading -> item(key = "loading") { LoadingState() }
            is LoadState.Failed -> item(key = "error") { FailureState(state.failure, onRetry) }
            is LoadState.Ready -> if (state.value.isEmpty) {
                item(key = "empty") {
                    MessageState(
                        icon = R.drawable.ic_info,
                        title = stringResource(R.string.disruptions_empty_title),
                        body = stringResource(R.string.disruptions_empty_body),
                    )
                }
            } else {
                group("saved", R.string.disruptions_saved, state.value.saved, now)
                group("nearby", R.string.disruptions_nearby, state.value.nearby, now)
            }
        }
    }
}

private fun LazyListScope.group(key: String, title: Int, disruptions: List<Disruption>, now: Instant) {
    if (disruptions.isEmpty()) return
    item(key = "$key-title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
        )
    }
    items(disruptions, key = { "$key-${it.alert.hashCode()}" }) { disruption ->
        DisruptionCard(disruption, now, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
}

private val BlankLines = Regex("\\n\\s*\\n")

/** How many lines of the description show before the user opens the card. */
private const val CLOSED_LINES = 3

/**
 * One disruption: its effect and cause, the lines, the header, the time period, the stops, the
 * description, and the link. A severe disruption uses the error container. A tap shows the full
 * description.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisruptionCard(disruption: Disruption, now: Instant, modifier: Modifier = Modifier) {
    val alert = disruption.alert
    val scheme = MaterialTheme.colorScheme
    val severe = alert.level == AlertSeverity.SEVERE
    var open by rememberSaveable { mutableStateOf(false) }
    // The provider can send HTML. The text is shown, and no link in it is followed. Paragraphs
    // become single line breaks, so the closed card does not spend its lines on blank ones.
    val description = remember(alert.description) {
        AnnotatedString.fromHtml(alert.description).text.trim().replace(BlankLines, "\n")
    }
    Surface(
        color = if (severe) scheme.errorContainer else scheme.surfaceContainerHigh,
        contentColor = if (severe) scheme.onErrorContainer else scheme.onSurface,
        shape = MaterialTheme.shapes.largeIncreased,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clickable(
                    enabled = description.isNotEmpty(),
                    onClickLabel = stringResource(if (open) R.string.disruption_show_less else R.string.disruption_show_more),
                ) { open = !open }
                .padding(16.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Tag(
                    icon = if (alert.level >= AlertSeverity.WARNING) R.drawable.ic_warning else R.drawable.ic_info,
                    text = listOfNotNull(stringResource(alert.effect.label()), alert.cause?.let { stringResource(it.label()) }).joinToString(" · "),
                )
                disruption.lines.forEach { LineBadge(it.line, it.mode) }
            }
            Text(alert.header, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val muted = LocalContentColor.current.copy(alpha = 0.8f)
            periodText(alert.impactPeriods, now)?.let { Detail(R.drawable.ic_schedule, it, muted) }
            if (disruption.stops.isNotEmpty()) Detail(R.drawable.ic_location_on, disruption.stops.joinToString(", "), muted)
            if (description.isNotEmpty() && description != alert.header) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (open) Int.MAX_VALUE else CLOSED_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // The link comes from the provider: only web links open.
            alert.url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { url ->
                val uriHandler = LocalUriHandler.current
                // The content color of the card, so the link is readable on the error container too.
                TextButton(
                    onClick = { uriHandler.openUri(url) },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
                ) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.disruption_more_information), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun Detail(icon: Int, text: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Light, color = color)
    }
}

/**
 * The time period that applies at [now], or the next one. A period in progress shows its end. A
 * period that has not started shows its start, and its end when it has one. Null when no period
 * gives a limit.
 */
@Composable
private fun periodText(periods: List<TimeRange>, now: Instant): String? {
    val period = periods.firstOrNull { (it.start == null || it.start <= now) && (it.end == null || it.end > now) }
        ?: periods.filter { it.start != null && it.start > now }.minByOrNull { it.start!! }
        ?: return null
    val start = period.start?.takeIf { it > now }
    // Some feeds give a period that ends when it starts. Only its start has a meaning.
    val end = period.end?.takeIf { period.start == null || it > period.start }
    return when {
        start != null && end != null -> stringResource(R.string.disruption_from_to, formatMoment(start), formatMoment(end))
        start != null -> stringResource(R.string.disruption_from, formatMoment(start))
        end != null -> stringResource(R.string.disruption_until, formatMoment(end))
        else -> null
    }
}

/** A date and a time in the zone of the device, such as "12 Nov 2026, 14:30". */
@Composable
private fun formatMoment(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val date = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    return stringResource(R.string.travel_day_time, date.withZone(zone).format(instant), formatTime(instant, zone))
}

private fun AlertEffect?.label(): Int = when (this) {
    AlertEffect.NO_SERVICE -> R.string.disruption_effect_no_service
    AlertEffect.SIGNIFICANT_DELAYS -> R.string.disruption_effect_delays
    AlertEffect.REDUCED_SERVICE -> R.string.disruption_effect_reduced
    AlertEffect.DETOUR -> R.string.disruption_effect_detour
    AlertEffect.MODIFIED_SERVICE -> R.string.disruption_effect_modified
    AlertEffect.STOP_MOVED -> R.string.disruption_effect_stop_moved
    AlertEffect.ACCESSIBILITY_ISSUE -> R.string.disruption_effect_accessibility
    AlertEffect.ADDITIONAL_SERVICE -> R.string.disruption_effect_additional
    AlertEffect.NO_EFFECT, null -> R.string.disruption_effect_information
}

private fun AlertCause.label(): Int = when (this) {
    AlertCause.TECHNICAL_PROBLEM -> R.string.disruption_cause_technical
    AlertCause.STRIKE -> R.string.disruption_cause_strike
    AlertCause.DEMONSTRATION -> R.string.disruption_cause_demonstration
    AlertCause.ACCIDENT -> R.string.disruption_cause_accident
    AlertCause.HOLIDAY -> R.string.disruption_cause_holiday
    AlertCause.WEATHER -> R.string.disruption_cause_weather
    AlertCause.MAINTENANCE -> R.string.disruption_cause_maintenance
    AlertCause.CONSTRUCTION -> R.string.disruption_cause_construction
    AlertCause.POLICE_ACTIVITY -> R.string.disruption_cause_police
    AlertCause.MEDICAL_EMERGENCY -> R.string.disruption_cause_medical
    AlertCause.SPECIAL_EVENT -> R.string.disruption_cause_event
}
