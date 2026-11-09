package app.itivio.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.itivio.R
import app.itivio.domain.Line
import app.itivio.domain.StopTime
import app.itivio.domain.TransitMode
import java.time.ZoneId

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
        shape = MaterialTheme.shapes.small,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Icon(painterResource(mode.icon()), contentDescription = null, modifier = Modifier.size(16.dp))
            if (line.name.isNotEmpty()) {
                Text(line.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
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
            color = if (time.isRealtime) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        )
        if (delayed) {
            Text(
                scheduled,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
            )
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
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
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
