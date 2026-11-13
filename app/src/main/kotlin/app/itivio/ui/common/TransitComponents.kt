package app.itivio.ui.common

import android.text.TextUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.itivio.R
import app.itivio.domain.Journey
import app.itivio.domain.Leg
import app.itivio.domain.Line
import app.itivio.domain.StopTime
import app.itivio.domain.TransitMode
import java.time.ZoneId

/**
 * Styles a formatted amount such as "45 min" or "1,6 km". The numbers keep the weight of the text
 * style, which the caller sets heavy. The units get a light weight at [unitScale] of the size.
 */
fun amountText(text: String, unitScale: Float = 0.6f): AnnotatedString = buildAnnotatedString {
    val unit = SpanStyle(fontWeight = FontWeight.Light, fontSize = unitScale.em)
    var start = 0
    while (start < text.length) {
        val number = text[start].isAmountDigit()
        var end = start
        while (end < text.length && text[end].isAmountDigit() == number) end++
        if (number) append(text.substring(start, end)) else withStyle(unit) { append(text.substring(start, end)) }
        start = end
    }
}

private fun Char.isAmountDigit() = isDigit() || this == ',' || this == '.'

/**
 * A string resource with `<b>` markup, for text that pairs a bold part with a light part. The
 * markup keeps the word order in the hands of the translator. The arguments are escaped.
 */
@Composable
fun markedStringResource(@StringRes id: Int, vararg args: String): AnnotatedString =
    AnnotatedString.fromHtml(stringResource(id, *args.map(TextUtils::htmlEncode).toTypedArray()))

/** Agency colors when the feed has them, theme colors otherwise. */
@Composable
fun lineColors(line: Line): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val container = line.color?.let(::Color) ?: return scheme.secondaryContainer to scheme.onSecondaryContainer
    val content = line.textColor?.let(::Color) ?: if (container.luminance() > 0.5f) Color.Black else Color.White
    return container to content
}

@Composable
fun LineBadge(line: Line, mode: TransitMode, modifier: Modifier = Modifier) {
    val (container, content) = lineColors(line)
    val description = stringResource(R.string.line_description, stringResource(mode.label()), line.name)
    Surface(
        color = container,
        contentColor = content,
        shape = CircleShape,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.heightIn(min = 28.dp).padding(start = 8.dp, end = if (line.name.isEmpty()) 8.dp else 10.dp),
        ) {
            Icon(painterResource(mode.icon()), contentDescription = null, modifier = Modifier.size(16.dp))
            if (line.name.isNotEmpty()) {
                Text(line.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
        }
    }
}

/**
 * A small label with an icon, such as "1 transfer". The default colors come from the content
 * color, so the tag works on each container.
 */
@Composable
fun Tag(
    @DrawableRes icon: Int,
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current.copy(alpha = 0.12f),
    contentColor: Color = LocalContentColor.current,
) {
    Surface(color = color, contentColor = contentColor, shape = CircleShape, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.heightIn(min = 28.dp).padding(start = 8.dp, end = 10.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** An icon on a tonal tile. It marks rows that have no line color, such as walks. */
@Composable
fun IconTile(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(color = color, contentColor = contentColor, shape = MaterialTheme.shapes.medium, modifier = modifier.size(36.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** Walks shorter than this are noise in the leg chain; the itinerary still lists them. */
private const val MIN_SHOWN_WALK_SECONDS = 120

/** The legs of [journey] in order: a line badge for each transit leg, and the minutes of each walk. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LegChain(journey: Journey, modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        val shown = journey.legs.filter { it is Leg.Transit || it.duration.seconds >= MIN_SHOWN_WALK_SECONDS }
        shown.forEachIndexed { index, leg ->
            if (index > 0) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = LocalContentColor.current.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }
            when (leg) {
                is Leg.Transit -> LineBadge(leg.line, leg.mode)
                is Leg.Street -> {
                    val label = stringResource(leg.mode.label())
                    if (leg.mode == TransitMode.WALK) {
                        Tag(
                            icon = leg.mode.icon(),
                            text = ((leg.duration.seconds + 30) / 60).toString(),
                            // The icon alone names the mode; a screen reader needs the word.
                            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label },
                        )
                    } else {
                        Tag(icon = leg.mode.icon(), text = label)
                    }
                }
            }
        }
    }
}

/**
 * A departure or arrival time. Live times use the tertiary role with a live icon; a delayed time
 * also shows the scheduled time struck through. Scheduled-only times stay neutral, so riders can
 * tell timetable data from real-time data.
 */
@Composable
fun TimeText(time: StopTime, zone: ZoneId?, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    val style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    val best = formatTime(time.best, zone)
    val scheduled = formatTime(time.scheduled, zone)
    val delayed = time.isRealtime && best != scheduled
    val liveLabel = stringResource(R.string.live)
    val scheduledLabel = stringResource(R.string.scheduled)
    val wasLabel = stringResource(R.string.scheduled_time_was, scheduled)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = buildString {
                append(best)
                append(", ")
                append(if (time.isRealtime) liveLabel else scheduledLabel)
                if (delayed) append(", ").append(wasLabel)
            }
        },
    ) {
        if (delayed) {
            Text(
                scheduled,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
            )
        }
        if (time.isRealtime) {
            Icon(
                painterResource(R.drawable.ic_sensors),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            best,
            style = style,
            fontWeight = if (emphasized) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (time.isRealtime) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Minutes until a departure, with a heavy number and a light unit. Live countdowns use the same
 * tertiary role and icon as [TimeText], so riders can tell live data from the timetable. A live
 * "Now" is a tertiary pill: it is the departure that needs the rider's attention.
 */
@Composable
fun CountdownText(
    minutes: Long,
    live: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
) {
    val now = minutes <= 0
    val countdown = if (now) stringResource(R.string.nearby_now) else stringResource(R.string.duration_min, minutes.toInt())
    val status = stringResource(if (live) R.string.live else R.string.scheduled)
    val scheme = MaterialTheme.colorScheme
    val pill = now && live
    Surface(
        color = if (pill) scheme.tertiary else Color.Transparent,
        contentColor = when {
            pill -> scheme.onTertiary
            live -> scheme.tertiary
            else -> scheme.onSurface
        },
        // A surface clips to its shape; only the pill has the padding for round ends.
        shape = if (pill) CircleShape else RectangleShape,
        modifier = modifier.clearAndSetSemantics { contentDescription = "$countdown, $status" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = if (pill) Modifier.padding(start = 8.dp, end = 10.dp, top = 2.dp, bottom = 2.dp) else Modifier,
        ) {
            if (live) Icon(painterResource(R.drawable.ic_sensors), contentDescription = null, modifier = Modifier.size(16.dp))
            if (now) {
                Text(countdown, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
            } else {
                Text(amountText(countdown, unitScale = 0.7f), style = style, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.loading)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth().padding(32.dp).semantics { contentDescription = label },
    ) {
        LoadingIndicator()
    }
}

@Composable
fun MessageState(
    icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        // Explicit color: some hosts, such as the full-screen search bar, do not set a content color.
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) { Text(actionLabel) }
        }
    }
}

@Composable
fun FailureState(failure: Failure, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val (icon, title, body) = when (failure) {
        Failure.OFFLINE -> Triple(R.drawable.ic_cloud_off, R.string.error_offline_title, R.string.error_offline_body)
        Failure.SERVER -> Triple(R.drawable.ic_warning, R.string.error_server_title, R.string.error_server_body)
        Failure.UNEXPECTED -> Triple(R.drawable.ic_error, R.string.error_unexpected_title, R.string.error_unexpected_body)
        Failure.NO_LOCATION -> Triple(R.drawable.ic_my_location, R.string.error_no_location_title, R.string.error_no_location_body)
    }
    MessageState(
        icon = icon,
        title = stringResource(title),
        body = stringResource(body),
        actionLabel = stringResource(R.string.retry),
        onAction = onRetry,
        modifier = modifier,
    )
}
