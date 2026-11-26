package app.itivio.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import app.itivio.ui.widget.DeparturesWidgetContent
import app.itivio.ui.widget.WidgetRow
import androidx.glance.material3.ColorProviders
import app.itivio.ui.theme.DarkColors
import app.itivio.ui.theme.LightColors
import app.itivio.domain.Departure
import app.itivio.domain.GeoPoint
import app.itivio.domain.Line
import app.itivio.domain.Stop
import app.itivio.domain.StopTime
import app.itivio.domain.TransitMode
import app.itivio.domain.widgetDepartures
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The departures widget, rendered to its RemoteViews, as the launcher shows it. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = Application::class)
class WidgetScreenshotTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val now = Instant.parse("2026-11-24T07:54:00Z")
    private val stop = Stop("S+U Alexanderplatz", GeoPoint(52.52, 13.41), stopId = "a")
    private val u2 = Line("U2", color = 0xFFDA421E.toInt(), textColor = 0xFFFFFFFF.toInt())
    private val s5 = Line("S5", color = 0xFFEB7405.toInt())
    private fun at(minutes: Long, live: Boolean = false) =
        StopTime(now.plusSeconds(minutes * 60), if (live) now.plusSeconds(minutes * 60) else null)

    private val departures = listOf(
        Departure(stop, u2, TransitMode.SUBWAY, "Ruhleben", at(3, live = true), false, "1"),
        Departure(stop, u2, TransitMode.SUBWAY, "Pankow", at(4), false, "2"),
        Departure(stop, s5, TransitMode.SUBURBAN, "Strausberg Nord", at(7, live = true), false, "3"),
        Departure(stop, u2, TransitMode.SUBWAY, "Ruhleben", at(8), false, "4"),
        Departure(stop, s5, TransitMode.SUBURBAN, "Westkreuz", at(-2), false, "5"),
    )

    private fun capture(name: String, stopName: String?) {
        val format = DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(ZoneId.of("Europe/Berlin"))
        val size = DpSize(330.dp, 180.dp)
        val rows = widgetDepartures(departures, now).map { departure ->
            WidgetRow(
                line = departure.line.name,
                lineColor = departure.line.color,
                textColor = departure.line.textColor,
                headsign = departure.headsign.orEmpty(),
                at = departure.time.best.toEpochMilli(),
                live = departure.time.isRealtime,
            )
        }
        val result = runBlocking {
            GlanceRemoteViews().compose(context, size) {
                DeparturesWidgetContent(stopName, rows, format.format(now), { format.format(it) }, ColorProviders(LightColors, DarkColors))
            }
        }
        val host = FrameLayout(context)
        val view = result.remoteViews.apply(context, host)
        val density = context.resources.displayMetrics.density
        val width = (size.width.value * density).toInt()
        val height = (size.height.value * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        bitmap.captureRoboImage(
            "src/test/screenshots/$name.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(outputDirectoryPath = "build/outputs/roborazzi")),
        )
    }

    @Test fun widget() = capture("widget_departures", "S+U Alexanderplatz")

    @Test fun widgetWithoutStop() = capture("widget_pick_stop", null)

    @Test fun `the widget shows the next three departures from now`() {
        assertEquals(listOf("1", "2", "3"), widgetDepartures(departures, now).map { it.tripId })
    }

    @Test fun `the widget leaves out a cancelled departure and one that left`() {
        val stop = Stop("S+U Alexanderplatz", GeoPoint(52.52, 13.41), stopId = "a")
        fun departure(tripId: String, minutes: Long, cancelled: Boolean = false) =
            Departure(stop, u2, TransitMode.SUBWAY, "Ruhleben", at(minutes), cancelled, tripId)

        val shown = widgetDepartures(
            listOf(departure("gone", -1), departure("cancelled", 2, cancelled = true), departure("soon", 5), departure("later", 1)),
            now,
        )

        assertEquals(listOf("later", "soon"), shown.map { it.tripId })
    }

    @Test fun `an empty stop shows no departures`() {
        assertEquals(emptyList<Departure>(), widgetDepartures(emptyList(), now))
    }
}
