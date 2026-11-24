package app.itivio.domain

import java.time.Instant

/** Home screen widgets that show the next departures of one stop. */
interface StopWidgets {
    /** False when the launcher cannot add a widget from the app. */
    val canPin: Boolean

    /** Asks the launcher to add a widget for the stop [stopId]. */
    fun pin(stopId: String, name: String)
}

/** The departures a widget shows: the next [max] from [now], in time order. */
fun widgetDepartures(departures: List<Departure>, now: Instant, max: Int = 3): List<Departure> =
    departures.filter { !it.cancelled && !it.time.best.isBefore(now) }.sortedBy { it.time.best }.take(max)
