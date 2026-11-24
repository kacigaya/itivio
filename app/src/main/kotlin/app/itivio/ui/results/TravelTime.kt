package app.itivio.ui.results

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.SuggestionChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.TimeScroll
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.itivio.R
import app.itivio.ui.common.Choice
import app.itivio.ui.common.ChoiceButtons
import app.itivio.ui.common.formatTime
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Trips can be planned this many days ahead; longer ranges rarely have reliable timetables. */
private const val DAYS_AHEAD = 7
private const val MINUTES_PER_DAY = 24 * 60
private val TIME_STEPS = listOf(15, 30, 60)

/** The day and the minute of the day [minutes] after [minuteOfDay] on [day], or null past the last day of the picker. */
internal fun shiftTime(day: Int, minuteOfDay: Int, minutes: Int): Pair<Int, Int>? {
    val total = day * MINUTES_PER_DAY + minuteOfDay + minutes
    return (total / MINUTES_PER_DAY to total % MINUTES_PER_DAY).takeIf { it.first < DAYS_AHEAD }
}

/** The device clock in the app; screenshot tests supply a fixed date and time. */
internal val LocalTravelClock = staticCompositionLocalOf<Clock?> { null }

/** Shows when the trip starts or ends. Selected when the user picked a time other than now. */
@Composable
fun TravelTimeChip(time: Instant?, arriveBy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterButton(
        label = travelTimeLabel(time, arriveBy),
        checked = time != null,
        onClick = onClick,
        leadingIcon = R.drawable.ic_schedule,
        trailingIcon = R.drawable.ic_arrow_drop_down,
        modifier = modifier,
    )
}

@Composable
private fun travelTimeLabel(time: Instant?, arriveBy: Boolean): String {
    if (time == null) return stringResource(R.string.travel_now)
    val wallClock = LocalTravelClock.current ?: Clock.systemDefaultZone()
    val zone = wallClock.zone
    val days = ChronoUnit.DAYS.between(LocalDate.now(wallClock), time.atZone(zone).toLocalDate()).toInt()
    val clock = formatTime(time, zone)
    val moment = if (days == 0) clock else stringResource(R.string.travel_day_time, dayLabel(days), clock)
    return stringResource(if (arriveBy) R.string.travel_arrive_by else R.string.travel_leave_at, moment)
}

@Composable
private fun dayLabel(offset: Int): String = when (offset) {
    0 -> stringResource(R.string.travel_today)
    1 -> stringResource(R.string.travel_tomorrow)
    else -> {
        val locale = LocalConfiguration.current.locales[0]
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEd")
        DateTimeFormatter.ofPattern(pattern, locale).format(LocalDate.now(LocalTravelClock.current ?: Clock.systemDefaultZone()).plusDays(offset.toLong()))
    }
}

/**
 * Picks "leave at" or "arrive by", a day in the next week, and a time. Times are in the device
 * zone. [onConfirm] gets a null time for "leave now".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelTimeDialog(
    time: Instant?,
    arriveBy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (time: Instant?, arriveBy: Boolean) -> Unit,
) {
    val wallClock = LocalTravelClock.current ?: Clock.systemDefaultZone()
    val zone = wallClock.zone
    val today = LocalDate.now(wallClock)
    val initial = (time ?: Instant.now(wallClock)).atZone(zone)
    var arrive by rememberSaveable { mutableStateOf(arriveBy) }
    var day by rememberSaveable {
        mutableIntStateOf(ChronoUnit.DAYS.between(today, initial.toLocalDate()).toInt().coerceIn(0, DAYS_AHEAD - 1))
    }
    var displayMode by rememberSaveable(stateSaver = TimePickerDisplayModeSaver) { mutableStateOf(TimePickerDisplayMode.Scroll) }
    val clock = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )

    PickerDialog(
        title = stringResource(R.string.travel_title),
        onDismiss = onDismiss,
        buttons = { close ->
            TimePickerDialogDefaults.ScrollDisplayModeToggle(
                onDisplayModeChange = {
                    displayMode = if (displayMode == TimePickerDisplayMode.Scroll) TimePickerDisplayMode.Input else TimePickerDisplayMode.Scroll
                },
                displayMode = displayMode,
            )
            TextButton(onClick = { close { onConfirm(null, false) } }) { Text(stringResource(R.string.travel_now)) }
            TextButton(onClick = { close(onDismiss) }) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick = {
                val picked = today.plusDays(day.toLong()).atTime(clock.hour, clock.minute).atZone(zone).toInstant()
                close { onConfirm(picked, arrive) }
            }) { Text(stringResource(R.string.done)) }
        },
        fieldsAlignment = Alignment.CenterHorizontally,
    ) { TravelTimeFields(arrive, { arrive = it }, day, { day = it }, displayMode, clock) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TravelTimeFields(
    arrive: Boolean,
    onArrive: (Boolean) -> Unit,
    day: Int,
    onDay: (Int) -> Unit,
    displayMode: TimePickerDisplayMode,
    clock: TimePickerState,
) {
    ChoiceButtons(
        choices = listOf(Choice(stringResource(R.string.travel_leave)), Choice(stringResource(R.string.travel_arrive))),
        selected = if (arrive) 1 else 0,
        onSelect = { onArrive(it == 1) },
        modifier = Modifier.padding(bottom = 12.dp),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        items(DAYS_AHEAD) { offset ->
            FilterChip(selected = day == offset, onClick = { onDay(offset) }, label = { Text(dayLabel(offset)) })
        }
    }
    val shapes = TimePickerDefaults.shapes()
    if (displayMode == TimePickerDisplayMode.Scroll) TimeScroll(clock, shapes) else TimeInput(clock, shapes)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        TIME_STEPS.forEach { step ->
            val next = shiftTime(day, clock.hour * 60 + clock.minute, step)
            val description = if (step % 60 == 0) {
                pluralStringResource(R.plurals.travel_add_hours, step / 60, step / 60)
            } else {
                pluralStringResource(R.plurals.travel_add_minutes, step, step)
            }
            SuggestionChip(
                onClick = {
                    next?.let { (nextDay, minute) ->
                        onDay(nextDay)
                        clock.hour = minute / 60
                        clock.minute = minute % 60
                    }
                },
                enabled = next != null,
                label = {
                    Text(
                        if (step % 60 == 0) stringResource(R.string.travel_plus_hours, step / 60)
                        else stringResource(R.string.travel_plus_minutes, step),
                    )
                },
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
    }
}

private val TimePickerDisplayModeSaver = Saver<TimePickerDisplayMode, Boolean>(
    save = { it == TimePickerDisplayMode.Scroll },
    restore = { if (it) TimePickerDisplayMode.Scroll else TimePickerDisplayMode.Input },
)
