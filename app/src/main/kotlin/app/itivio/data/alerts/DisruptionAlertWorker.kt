package app.itivio.data.alerts

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.itivio.R
import app.itivio.domain.Disruption
import app.itivio.domain.DisruptionWatch
import app.itivio.domain.PlacesRepository
import app.itivio.domain.RoutingRepository
import app.itivio.domain.SettingsRepository
import app.itivio.domain.alertsToNotify
import app.itivio.domain.disruptionsOf
import app.itivio.domain.notificationKey
import app.itivio.domain.stopIds
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Checks the saved stops every [INTERVAL_MINUTES], when a network is there. */
internal class WorkManagerDisruptionWatch(private val context: Context) : DisruptionWatch {
    override fun enable(on: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!on) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<DisruptionAlertWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME = "disruption-alerts"
        const val INTERVAL_MINUTES = 30L
    }
}

/**
 * Asks for the alerts of the saved stops, the same requests as the Disruptions page, and notifies
 * the new ones. It sends only the stop IDs. It remembers the alerts it saw, so an alert notifies
 * once, and an alert that ends is forgotten.
 */
class DisruptionAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val repository: RoutingRepository by inject()
    private val places: PlacesRepository by inject()
    private val settings: SettingsRepository by inject()

    override suspend fun doWork(): Result {
        if (!settings.disruptionAlerts.first()) return Result.success()
        val stops = places.saved.first().stopIds()
        if (stops.isEmpty()) return Result.success()
        val departures = coroutineScope {
            stops.map { stopId -> async { runCatching { repository.stopDepartures(stopId, STOP_SCAN, withAlerts = true) }.getOrNull() } }
                .awaitAll()
        }
        if (departures.all { it == null }) return Result.retry()
        val disruptions = disruptionsOf(departures.filterNotNull().flatten())
        val fresh = alertsToNotify(disruptions, settings.severeAlertsOnly.first(), settings.notifiedAlerts.first())
        fresh.take(MAX_NOTIFICATIONS).forEach(::notify)
        settings.setNotifiedAlerts(disruptions.map { it.alert.notificationKey() }.toSet())
        return Result.success()
    }

    private fun notify(disruption: Disruption) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.alerts_channel))
                .build(),
        )
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val open = launch?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        val lines = disruption.lines.joinToString(", ") { it.line.name }
        val text = listOf(lines, disruption.stops.joinToString(", ")).filter { it.isNotEmpty() }.joinToString(" · ")
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(disruption.alert.header)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(disruption.alert.notificationKey(), NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL = "disruption_alerts"
        const val NOTIFICATION_ID = 2
        const val STOP_SCAN = 20
        const val MAX_NOTIFICATIONS = 3
    }
}
