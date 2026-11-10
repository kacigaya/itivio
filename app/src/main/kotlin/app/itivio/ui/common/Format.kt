package app.itivio.ui.common

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.itivio.R
import java.text.DecimalFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

internal val LocalTimeFormatter = staticCompositionLocalOf<DateTimeFormatter?> { null }

@Composable
internal fun rememberTimeFormatter(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var use24Hour by remember(context, locale) { mutableStateOf(DateFormat.is24HourFormat(context)) }
    DisposableEffect(context, locale) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                use24Hour = DateFormat.is24HourFormat(context)
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24), false, observer)
        use24Hour = DateFormat.is24HourFormat(context)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return remember(locale, use24Hour) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (use24Hour) "Hm" else "hm"), locale)
    }
}

/** Times show in the stop's own zone, so a trip abroad reads like the local timetable. */
@Composable
fun formatTime(instant: Instant, zone: ZoneId?): String {
    val formatter = LocalTimeFormatter.current ?: rememberTimeFormatter()
    return formatter.withZone(zone ?: ZoneId.systemDefault()).format(instant)
}

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
