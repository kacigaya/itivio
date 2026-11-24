package app.itivio.ui.widget

import android.appwidget.AppWidgetManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.itivio.R
import app.itivio.domain.Departure
import app.itivio.domain.RoutingRepository
import app.itivio.domain.StopWidgets
import app.itivio.domain.widgetDepartures
import app.itivio.ui.common.attempt
import app.itivio.ui.common.readableLineText
import app.itivio.ui.theme.DarkColors
import app.itivio.ui.theme.LightColors
import java.time.Instant
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import android.os.Build

private val STOP_ID = stringPreferencesKey("stop_id")
private val STOP_NAME = stringPreferencesKey("stop_name")

/** The last answer that arrived, so a failed update keeps the departures on screen. */
private val LAST_ROWS = stringPreferencesKey("last_rows")
private val LAST_UPDATED = longPreferencesKey("last_updated")

/**
 * One departure as the widget shows it, and as the widget keeps it between updates. It is the
 * rendered row, not a [Departure], so the Glance state needs no domain types and no mapper.
 */
@Serializable
data class WidgetRow(
    val line: String,
    val lineColor: Int? = null,
    val textColor: Int? = null,
    val headsign: String = "",
    /** The departure, in epoch milliseconds. */
    val at: Long,
    /** A live time, which the widget shows in the tertiary color. */
    val live: Boolean = false,
)

/**
 * The next departures of one stop. It asks Transitous when Android updates it, about every 30
 * minutes (`updatePeriodMillis`), and when the rider taps refresh. So it shows clock times, not
 * countdowns, and the time of the update. A failed request keeps the last answer, with its time.
 */
class DeparturesWidget : GlanceAppWidget(), KoinComponent {
    private val repository: RoutingRepository by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val stopId = state[STOP_ID]
        // A slow answer must not hold the update, which Android ends after a few seconds.
        val fetched = stopId?.let { stop ->
            withTimeoutOrNull(FETCH_TIMEOUT_MILLIS) { attempt { repository.stopDepartures(stop, DEPARTURES_SCAN) }.getOrNull() }
        }?.let { departures ->
            val at = Instant.now()
            at to widgetDepartures(departures, at).map(Departure::toWidgetRow)
        }
        if (fetched != null) {
            updateAppWidgetState(context, id) { preferences ->
                preferences[LAST_ROWS] = json.encodeToString(fetched.second)
                preferences[LAST_UPDATED] = fetched.first.toEpochMilli()
            }
        }
        // A failed request keeps the last answer, with the time it arrived.
        val rows = fetched?.second ?: state[LAST_ROWS]?.let(::rowsOf)
        val updated = fetched?.first ?: state[LAST_UPDATED]?.let(Instant::ofEpochMilli)
        val time = DateFormat.getTimeFormat(context)
        provideContent {
            DeparturesWidgetContent(
                name = state[STOP_NAME],
                rows = rows,
                updated = updated?.let { time.format(Date.from(it)) },
                time = { time.format(Date.from(it)) },
            )
        }
    }

    private companion object {
        const val DEPARTURES_SCAN = 12
        const val FETCH_TIMEOUT_MILLIS = 10_000L
        val json = Json { ignoreUnknownKeys = true }

        /** The stored rows, or null when the file holds none: a fresh widget has no answer yet. */
        fun rowsOf(stored: String): List<WidgetRow>? = runCatching { json.decodeFromString<List<WidgetRow>>(stored) }.getOrNull()
    }
}

private fun Departure.toWidgetRow(): WidgetRow =
    WidgetRow(line.name, line.color, line.textColor, headsign.orEmpty(), time.best.toEpochMilli(), time.isRealtime)

/**
 * The widget content: the stop name and the refresh button, then the departures with the line,
 * the destination, and the clock time. Without a stop, it says how to pick one. A tap opens the app.
 * [rows] is null when no answer arrived yet, and [updated] is null with it, or for an answer of a
 * widget that has never loaded.
 */
@Composable
fun DeparturesWidgetContent(
    name: String?,
    rows: List<WidgetRow>?,
    updated: String?,
    time: (Instant) -> String,
    colors: ColorProviders = widgetColors(),
) {
    GlanceTheme(colors = colors) {
        val colors = GlanceTheme.colors
        Column(
            GlanceModifier.fillMaxSize().background(colors.widgetBackground).cornerRadius(24.dp).padding(14.dp)
                .clickable(actionStartActivity(launchIntent())),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name ?: "itivio",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.onSurface),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (name != null) {
                    CircleIconButton(
                        imageProvider = ImageProvider(R.drawable.ic_refresh),
                        contentDescription = androidx.glance.LocalContext.current.getString(R.string.widget_refresh),
                        onClick = actionRunCallback<RefreshAction>(),
                        backgroundColor = null,
                        contentColor = colors.primary,
                    )
                }
            }
            val context = androidx.glance.LocalContext.current
            when {
                name == null -> Message(context.getString(R.string.widget_pick_stop))
                rows == null -> Message(context.getString(R.string.widget_failed))
                rows.isEmpty() -> Message(context.getString(R.string.stop_empty_title))
                else -> rows.forEach { DepartureRow(it, time(Instant.ofEpochMilli(it.at))) }
            }
            Spacer(GlanceModifier.defaultWeight())
            if (name != null && updated != null) {
                Text(
                    context.getString(R.string.widget_updated, updated),
                    style = TextStyle(fontSize = 12.sp, color = colors.onSurfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant), modifier = GlanceModifier.padding(top = 6.dp))
}

@Composable
private fun DepartureRow(row: WidgetRow, time: String) {
    val colors = GlanceTheme.colors
    val textColor = row.lineColor?.let { ColorProvider(readableLineText(Color(it), row.textColor?.let(::Color))) }
    Row(GlanceModifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier.background(row.lineColor?.let { ColorProvider(Color(it)) } ?: colors.secondaryContainer)
                .cornerRadius(12.dp).padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(
                row.line,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textColor ?: colors.onSecondaryContainer),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(
            row.headsign,
            style = TextStyle(fontSize = 14.sp, color = colors.onSurface),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            time,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (row.live) colors.tertiary else colors.onSurface,
            ),
        )
    }
}

@Composable
private fun launchIntent(): Intent {
    val context = androidx.glance.LocalContext.current
    return context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
}

/** Dynamic color on Android 12 and later, as in the app; else the app's own scheme. */
@Composable
internal fun widgetColors(): ColorProviders =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceTheme.colors else ColorProviders(light = LightColors, dark = DarkColors)

/** Asks for the departures again. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        DeparturesWidget().update(context, glanceId)
    }
}

class DeparturesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DeparturesWidget()
}

/** Stores the stop of a widget the launcher just added from the app, and shows its departures. */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val stopId = intent.getStringExtra(STOP_ID.name) ?: return
        val name = intent.getStringExtra(STOP_NAME.name) ?: return
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)
                updateAppWidgetState(context, glanceId) { state: androidx.datastore.preferences.core.MutablePreferences ->
                    state[STOP_ID] = stopId
                    state[STOP_NAME] = name
                    // A widget is not bound to a stop until now, or is re-pinned with another one.
                    state.remove(LAST_ROWS)
                    state.remove(LAST_UPDATED)
                }
                DeparturesWidget().update(context, glanceId)
            } finally {
                result.finish()
            }
        }
    }
}

/** Pins a [DeparturesWidget] with the launcher's own dialog. The stop arrives with the callback. */
internal class GlanceStopWidgets(private val context: Context) : StopWidgets {
    /** No stop ID is unique enough for a request code, so each pin takes the next one. */
    private val lastRequestCode = AtomicInteger()

    override val canPin: Boolean get() = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    override fun pin(stopId: String, name: String) {
        // A request code per pin: with one code for all, a second pin would overwrite the extras of
        // the first, which is still waiting for the launcher.
        val callback = PendingIntent.getBroadcast(
            context,
            lastRequestCode.incrementAndGet(),
            Intent(context, WidgetPinnedReceiver::class.java).putExtra(STOP_ID.name, stopId).putExtra(STOP_NAME.name, name),
            // Mutable: the launcher adds the ID of the new widget.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        CoroutineScope(Dispatchers.Default).launch {
            GlanceAppWidgetManager(context).requestPinGlanceAppWidget(DeparturesWidgetReceiver::class.java, successCallback = callback)
        }
    }
}
