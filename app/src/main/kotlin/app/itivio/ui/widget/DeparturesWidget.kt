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
import androidx.glance.layout.height
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
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import app.itivio.ui.theme.DarkColors
import app.itivio.ui.theme.LightColors
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import android.os.Build

private val STOP_ID = stringPreferencesKey("stop_id")
private val STOP_NAME = stringPreferencesKey("stop_name")

/**
 * The next departures of one stop. It asks Transitous when Android updates it, about every 30
 * minutes (`updatePeriodMillis`), and when the rider taps refresh. So it shows clock times, not
 * countdowns, and the time of the update.
 */
class DeparturesWidget : GlanceAppWidget(), KoinComponent {
    private val repository: RoutingRepository by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val stopId = state[STOP_ID]
        val now = Instant.now()
        val departures = stopId?.let { runCatching { repository.stopDepartures(it, DEPARTURES_SCAN) }.getOrNull() }
        val time = DateFormat.getTimeFormat(context)
        provideContent {
            DeparturesWidgetContent(
                name = state[STOP_NAME],
                departures = departures?.let { widgetDepartures(it, now) },
                updated = time.format(Date.from(now)),
                time = { time.format(Date.from(it)) },
            )
        }
    }

    private companion object {
        const val DEPARTURES_SCAN = 12
    }
}

/**
 * The widget content: the stop name and the refresh button, then the departures with the line,
 * the destination, and the clock time. Without a stop, it says how to pick one. A tap opens the app.
 */
@Composable
fun DeparturesWidgetContent(
    name: String?,
    departures: List<Departure>?,
    updated: String,
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
                departures == null -> Message(context.getString(R.string.widget_failed))
                departures.isEmpty() -> Message(context.getString(R.string.stop_empty_title))
                else -> departures.forEach { DepartureRow(it, time(it.time.best)) }
            }
            Spacer(GlanceModifier.defaultWeight())
            if (name != null) {
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
private fun DepartureRow(departure: Departure, time: String) {
    val colors = GlanceTheme.colors
    val line = departure.line
    val background = line.color?.let { ColorProvider(Color(it)) } ?: colors.secondaryContainer
    val text = line.color?.let { ColorProvider(readableOn(it, line.textColor)) } ?: colors.onSecondaryContainer
    Row(GlanceModifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.background(background).cornerRadius(12.dp).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(line.name, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = text), maxLines = 1)
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(
            departure.headsign.orEmpty(),
            style = TextStyle(fontSize = 14.sp, color = colors.onSurface),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            time,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (departure.time.isRealtime) colors.tertiary else colors.onSurface,
            ),
        )
    }
}

/** The text color of the line when it reads on the line color, else black or white: the one with more contrast. */
private fun readableOn(background: Int, text: Int?): Color {
    fun luminance(color: Int): Double = Color(color).let { 0.2126 * it.red + 0.7152 * it.green + 0.0722 * it.blue }
    fun contrast(a: Int, b: Int): Double = (maxOf(luminance(a), luminance(b)) + 0.05) / (minOf(luminance(a), luminance(b)) + 0.05)
    if (text != null && contrast(background, text) >= 4.5) return Color(text)
    return if (contrast(background, 0xFF000000.toInt()) >= contrast(background, 0xFFFFFFFF.toInt())) Color.Black else Color.White
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
    override val canPin: Boolean get() = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    override fun pin(stopId: String, name: String) {
        val callback = PendingIntent.getBroadcast(
            context,
            stopId.hashCode(),
            Intent(context, WidgetPinnedReceiver::class.java).putExtra(STOP_ID.name, stopId).putExtra(STOP_NAME.name, name),
            // Mutable: the launcher adds the ID of the new widget.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        CoroutineScope(Dispatchers.Default).launch {
            GlanceAppWidgetManager(context).requestPinGlanceAppWidget(DeparturesWidgetReceiver::class.java, successCallback = callback)
        }
    }
}

