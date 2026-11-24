package app.itivio.domain

/** One request goes out for each saved stop, so the number of stops has a limit. */
const val MAX_SAVED_STOPS = 10

/** The IDs of the saved stops: Home, Work, then the favorites. Places that are not stops have no alerts. */
fun SavedPlaces.stopIds(): List<String> =
    (listOfNotNull(home, work) + favorites).mapNotNull { it.stopId }.distinct().take(MAX_SAVED_STOPS)

/** Identity of an alert for the notifications: its text. A provider can resend an alert with new periods. */
fun Alert.notificationKey(): String = "$header\u0000$description".hashCode().toString()

/** The disruptions to notify: severe only when [severeOnly], and not notified before. */
fun alertsToNotify(disruptions: List<Disruption>, severeOnly: Boolean, notified: Set<String>): List<Disruption> =
    disruptions.filter { (!severeOnly || it.alert.level == AlertSeverity.SEVERE) && it.alert.notificationKey() !in notified }

/** Checks the saved stops for new alerts in the background, while the rider wants it. */
fun interface DisruptionWatch {
    fun enable(on: Boolean)
}
