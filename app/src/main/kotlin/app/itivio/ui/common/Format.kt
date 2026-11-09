package app.itivio.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.itivio.R
import java.text.DecimalFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.roundToInt

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** Times show in the stop's own zone, so a trip abroad reads like the local timetable. */
fun formatTime(instant: Instant, zone: ZoneId?): String =
    timeFormatter.withZone(zone ?: ZoneId.systemDefault()).format(instant)

/** Rounds to whole minutes; a journey under one minute still shows as 1 min. */
@Composable
fun formatDuration(duration: Duration): String {
    val totalMinutes = ((duration.seconds + 30) / 60).toInt().coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> stringResource(R.string.duration_min, minutes)
        minutes == 0 -> stringResource(R.string.duration_h, hours)
        else -> stringResource(R.string.duration_h_min, hours, minutes)
    }
}

@Composable
fun formatDistance(meters: Double): String = when {
    meters < 1000 -> stringResource(R.string.distance_m, ((meters / 10).roundToInt() * 10).coerceAtLeast(10))
    else -> stringResource(R.string.distance_km, DecimalFormat("0.#").format(meters / 1000))
}

/** Null when the delay is under a minute, which riders treat as on time. */
@Composable
fun formatDelay(delay: Duration): String? {
    val minutes = (delay.seconds / 60).toInt()
    if (minutes == 0) return null
    val amount = formatDuration(Duration.ofMinutes(abs(minutes).toLong()))
    return if (minutes > 0) stringResource(R.string.delay_late, amount) else stringResource(R.string.delay_early, amount)
}
