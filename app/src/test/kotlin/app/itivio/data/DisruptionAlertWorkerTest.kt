package app.itivio.data

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.itivio.data.alerts.DisruptionAlertWorker
import app.itivio.domain.Alert
import app.itivio.domain.AlertEffect
import app.itivio.domain.Departure
import app.itivio.domain.GeoPoint
import app.itivio.domain.Line
import app.itivio.domain.PlacesRepository
import app.itivio.domain.RoutingRepository
import app.itivio.domain.SavedPlaces
import app.itivio.domain.SettingsRepository
import app.itivio.domain.Stop
import app.itivio.domain.StopTime
import app.itivio.domain.ThemeMode
import app.itivio.domain.TransitMode
import app.itivio.ui.FakePlacesRepository
import app.itivio.ui.FakeRoutingRepository
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DisruptionAlertWorkerTest {
    private class FakeSettings(alerts: Boolean) : SettingsRepository {
        override val theme = MutableStateFlow(ThemeMode.SYSTEM)
        override suspend fun setTheme(mode: ThemeMode) { theme.value = mode }
        override val disruptionAlerts = MutableStateFlow(alerts)
        override suspend fun setDisruptionAlerts(on: Boolean) { disruptionAlerts.value = on }
        override val severeAlertsOnly = MutableStateFlow(true)
        override suspend fun setSevereAlertsOnly(on: Boolean) { severeAlertsOnly.value = on }
        override val notifiedAlerts = MutableStateFlow(emptySet<String>())
        override suspend fun setNotifiedAlerts(keys: Set<String>) { notifiedAlerts.value = keys }
    }

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
    private val closed = Alert("Line 6 closed", "Replacement buses", effect = AlertEffect.NO_SERVICE)
    private val detour = Alert("Bus detour", "", effect = AlertEffect.DETOUR)
    private val routing = FakeRoutingRepository()

    private fun start(alerts: Boolean): FakeSettings {
        val settings = FakeSettings(alerts)
        val stop = Stop("Nation", GeoPoint(48.85, 2.39), stopId = "nation")
        routing.stopResult = {
            listOf(Departure(stop, Line("6"), TransitMode.SUBWAY, "Étoile", StopTime(Instant.EPOCH), false, "t", alerts = listOf(closed, detour)))
        }
        startKoin {
            modules(module {
                single<RoutingRepository> { routing }
                single<PlacesRepository> { FakePlacesRepository(SavedPlaces(home = FakeRoutingRepository.location("Nation", "nation"))) }
                single<SettingsRepository> { settings }
            })
        }
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        return settings
    }

    @After
    fun stop() = stopKoin()

    private fun run(): ListenableWorker.Result =
        runBlocking { TestListenableWorkerBuilder<DisruptionAlertWorker>(context).build().doWork() }

    @Test
    fun `a new severe alert notifies once`() {
        start(alerts = true)
        assertEquals(ListenableWorker.Result.success(), run())
        assertEquals(listOf("Line 6 closed"), notifications.allNotifications.map { it.extras.getString("android.title") })

        context.getSystemService(NotificationManager::class.java).cancelAll()
        run()
        assertEquals(0, notifications.allNotifications.size)
    }

    @Test
    fun `turned off, the worker sends nothing`() {
        start(alerts = false)
        run()
        assertEquals(0, routing.stopCalls.size)
        assertEquals(0, notifications.allNotifications.size)
    }
}
