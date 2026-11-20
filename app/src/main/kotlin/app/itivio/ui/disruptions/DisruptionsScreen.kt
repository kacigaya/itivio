package app.itivio.ui.disruptions

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
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
import app.itivio.ui.common.MessageState
import app.itivio.ui.common.Page
import app.itivio.ui.common.Skeleton
import app.itivio.ui.common.SkeletonBlock
import app.itivio.ui.common.SkeletonText
import app.itivio.ui.common.Tag
import app.itivio.ui.common.formatTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/**
 * The service disruptions on the saved stops and on the stops near the device. [now] picks the
 * time period that each disruption shows.
 */
@Composable
fun DisruptionsScreen(state: LoadState<DisruptionGroups>, now: Instant, onRetry: () -> Unit, onBack: (() -> Unit)? = null) {
    Page(title = stringResource(R.string.disruptions_title), onBack = onBack) {
        item(key = "scope") {
            Text(
                stringResource(R.string.disruptions_scope),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
        }
        disruptions(state, now, onRetry)
    }
}

/**
 * Adds the disruptions to a list: the two groups, or the loading, empty, or error state. The
 * page and the home sheet show the same list.
 */
fun LazyListScope.disruptions(state: LoadState<DisruptionGroups>, now: Instant, onRetry: () -> Unit) {
    when (state) {
        LoadState.Idle, LoadState.Loading -> item(key = "loading") {
            Skeleton {
                SkeletonText(
                    MaterialTheme.typography.titleMedium,
                    Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp).width(144.dp),
                )
                repeat(2) { DisruptionCardSkeleton(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            }
        }
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

/** The placeholder of a [DisruptionCard]: the tag, the header, the time period, and the description. */
@Composable
private fun DisruptionCardSkeleton(modifier: Modifier = Modifier) {
    val type = MaterialTheme.typography
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.largeIncreased,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBlock(Modifier.size(width = 120.dp, height = 28.dp))
                SkeletonBlock(Modifier.size(width = 56.dp, height = 28.dp))
            }
            SkeletonText(type.titleMedium, Modifier.fillMaxWidth(0.8f))
            SkeletonText(type.bodySmall, Modifier.fillMaxWidth(0.5f))
            Column {
                SkeletonText(type.bodyMedium, Modifier.fillMaxWidth())
                SkeletonText(type.bodyMedium, Modifier.fillMaxWidth())
                SkeletonText(type.bodyMedium, Modifier.fillMaxWidth(0.6f))
            }
        }
    }
}

private val BlankLines = Regex("\\n\\s*\\n")

/** How many lines of the description show before the user opens the card. */
private const val CLOSED_LINES = 3

/**
 * One disruption: its effect, the lines, the header, the time period and the cause, the stops, the
 * description, and the link. A severe disruption has the error container only on its header band;
 * the body stays on the card color, so the long text is easy to read. A tap shows the full
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
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        shape = MaterialTheme.shapes.largeIncreased,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.clickable(
                enabled = description.isNotEmpty(),
                onClickLabel = stringResource(if (open) R.string.disruption_show_less else R.string.disruption_show_more),
            ) { open = !open },
        ) {
            Surface(
                color = if (severe) scheme.errorContainer else Color.Transparent,
                contentColor = if (severe) scheme.onErrorContainer else scheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (severe) 16.dp else 0.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        val effect = stringResource(alert.effect.label())
                        when {
                            severe -> Tag(R.drawable.ic_warning, effect, color = scheme.error, contentColor = scheme.onError)
                            alert.level >= AlertSeverity.WARNING -> Tag(R.drawable.ic_warning, effect)
                            else -> Tag(R.drawable.ic_info, effect)
                        }
                        disruption.lines.forEach { LineBadge(it.line, it.mode) }
                    }
                    Text(alert.header, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (severe) 12.dp else 8.dp, bottom = 16.dp),
            ) {
                val muted = scheme.onSurfaceVariant
                val period = periodText(alert.impactPeriods, now)
                val cause = alert.cause?.let { stringResource(it.label()) }
                listOfNotNull(period, cause).takeIf { it.isNotEmpty() }?.let {
                    Detail(if (period != null) R.drawable.ic_schedule else R.drawable.ic_info, it.joinToString(" · "), muted)
                }
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
                    TextButton(onClick = { uriHandler.openUri(url) }) {
                        Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.disruption_more_information), modifier = Modifier.padding(start = 8.dp))
                    }
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
        start != null && end != null -> stringResource(R.string.disruption_from_to, formatMoment(start, now), formatMoment(end, now))
        start != null -> stringResource(R.string.disruption_from, formatMoment(start, now))
        end != null -> stringResource(R.string.disruption_until, formatMoment(end, now))
        else -> null
    }
}

/**
 * A date and a time in the zone of the device. Within the next week the day is its name, such as
 * "Sunday, 14:30". Later dates this year have no year. A weekday reads well in the "From" and
 * "Until" sentences of each language, where "today" would not.
 */
@Composable
private fun formatMoment(instant: Instant, now: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val date = instant.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val skeleton = when {
        ChronoUnit.DAYS.between(today, date) in 0..6 -> "EEEE"
        date.year == today.year -> "EEEdMMM"
        else -> null
    }
    val formatter = remember(locale, skeleton) {
        if (skeleton == null) DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        else DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
    return stringResource(R.string.travel_day_time, formatter.format(date), formatTime(instant, zone))
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
