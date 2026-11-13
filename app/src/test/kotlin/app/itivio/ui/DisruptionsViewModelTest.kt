package app.itivio.ui

import app.itivio.domain.Alert
import app.itivio.domain.AlertEffect
import app.itivio.domain.Departure
import app.itivio.domain.GeoPoint
import app.itivio.domain.Line
import app.itivio.domain.RoutingException
import app.itivio.domain.SavedPlaces
import app.itivio.domain.Stop
import app.itivio.domain.StopTime
import app.itivio.domain.TransitMode
import app.itivio.ui.FakeRoutingRepository.Companion.location
import app.itivio.ui.common.Failure
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.UserLocation
import app.itivio.ui.disruptions.DisruptionGroups
import app.itivio.ui.disruptions.DisruptionsViewModel
import app.itivio.ui.disruptions.DisruptionsViewModel.Companion.REFRESH_MILLIS
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DisruptionsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeRoutingRepository()
    private val places = FakePlacesRepository()
    private val userLocation = UserLocation()
    private val viewModel by lazy { DisruptionsViewModel(repository, places, userLocation) }
    private val point = GeoPoint(52.52, 13.405)

    private val closed = Alert("Line closed", "", effect = AlertEffect.NO_SERVICE)
    private val detour = Alert("Detour", "", effect = AlertEffect.DETOUR)

    @Test
    fun `without saved stops and without a position there is nothing to ask`() = runTest(main.dispatcher) {
        // A favorite that is not a stop has no alerts.
        places.saved.value = SavedPlaces(favorites = listOf(location("Rue de Rivoli 12")))
        viewModel.disruptions.launchIn(backgroundScope)
        runCurrent()

        assertEquals(LoadState.Ready(DisruptionGroups()), viewModel.disruptions.value)
        assertTrue(repository.stopCalls.isEmpty())
        assertTrue(repository.nearbyCalls.isEmpty())
    }

    @Test
    fun `saved stops come first and the nearby group does not repeat their alerts`() = runTest(main.dispatcher) {
        places.saved.value = SavedPlaces(
            home = location("Home stop", stopId = "home"),
            favorites = listOf(location("Zoo", stopId = "zoo"), location("Home stop", stopId = "home")),
        )
        repository.stopResult = { stopId -> if (stopId == "zoo") listOf(departure("Zoo", closed)) else emptyList() }
        repository.nearbyResult = { listOf(departure("Zoo", closed), departure("Central", detour)) }
        userLocation.point.value = point
        viewModel.disruptions.launchIn(backgroundScope)
        runCurrent()

        val groups = (viewModel.disruptions.value as LoadState.Ready).value
        assertEquals(listOf(closed), groups.saved.map { it.alert })
        assertEquals(listOf(detour), groups.nearby.map { it.alert })
        // Each saved stop gets one request, and each request asks for alerts.
        assertEquals(listOf("home", "zoo"), repository.stopAlertCalls)
        assertEquals(listOf(point), repository.nearbyAlertCalls)
    }

    @Test
    fun `one failed stop keeps the answers of the others`() = runTest(main.dispatcher) {
        places.saved.value = SavedPlaces(favorites = listOf(location("Zoo", stopId = "zoo"), location("Central", stopId = "central")))
        repository.stopResult = { stopId -> if (stopId == "zoo") throw RoutingException.Server(500) else listOf(departure("Central", detour)) }
        viewModel.disruptions.launchIn(backgroundScope)
        runCurrent()

        assertEquals(listOf(detour), (viewModel.disruptions.value as LoadState.Ready).value.saved.map { it.alert })
    }

    @Test
    fun `a first load with no answer shows the failure and can be retried`() = runTest(main.dispatcher) {
        places.saved.value = SavedPlaces(favorites = listOf(location("Zoo", stopId = "zoo")))
        repository.stopResult = { throw RoutingException.Offline() }
        viewModel.disruptions.launchIn(backgroundScope)
        runCurrent()
        assertEquals(LoadState.Failed(Failure.OFFLINE), viewModel.disruptions.value)

        // A failed first load stops the refresh.
        advanceTimeBy(REFRESH_MILLIS * 2)
        runCurrent()
        assertEquals(1, repository.stopCalls.size)

        repository.stopResult = { listOf(departure("Zoo", closed)) }
        viewModel.retry()
        runCurrent()
        assertEquals(listOf(closed), (viewModel.disruptions.value as LoadState.Ready).value.saved.map { it.alert })
    }

    @Test
    fun `refresh asks again after the interval and a failed refresh keeps the list`() = runTest(main.dispatcher) {
        places.saved.value = SavedPlaces(favorites = listOf(location("Zoo", stopId = "zoo")))
        repository.stopResult = { listOf(departure("Zoo", closed)) }
        viewModel.disruptions.launchIn(backgroundScope)
        runCurrent()
        val ready = viewModel.disruptions.value

        advanceTimeBy(REFRESH_MILLIS - 1)
        runCurrent()
        assertEquals(1, repository.stopCalls.size)

        repository.stopResult = { throw RoutingException.Offline() }
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, repository.stopCalls.size)
        assertEquals(ready, viewModel.disruptions.value)
    }

    @Test
    fun `first nearby request can fail after an empty state without location`() = runTest(main.dispatcher) {
        viewModel.disruptions.launchIn(backgroundScope)
        runCurrent()
        assertEquals(LoadState.Ready(DisruptionGroups()), viewModel.disruptions.value)

        repository.nearbyResult = { throw RoutingException.Offline() }
        userLocation.point.value = point
        runCurrent()
        assertEquals(LoadState.Failed(Failure.OFFLINE), viewModel.disruptions.value)

        repository.nearbyResult = { listOf(departure("Central", detour)) }
        viewModel.retry()
        runCurrent()
        assertEquals(listOf(detour), (viewModel.disruptions.value as LoadState.Ready).value.nearby.map { it.alert })
    }

    private fun departure(stop: String, vararg alerts: Alert) = Departure(
        stop = Stop(stop, point, stopId = stop),
        line = Line("M41"),
        mode = TransitMode.BUS,
        headsign = "Town hall",
        time = StopTime(Instant.parse("2026-11-09T10:00:00Z")),
        cancelled = false,
        tripId = "trip-$stop",
        alerts = alerts.toList(),
    )
}
