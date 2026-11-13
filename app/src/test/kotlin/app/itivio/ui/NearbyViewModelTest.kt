package app.itivio.ui

import app.itivio.domain.Departure
import app.itivio.domain.GeoPoint
import app.itivio.domain.Line
import app.itivio.domain.RoutingException
import app.itivio.domain.Stop
import app.itivio.domain.StopTime
import app.itivio.domain.TransitMode
import app.itivio.ui.common.Failure
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.UserLocation
import app.itivio.ui.nearby.NearbyViewModel
import app.itivio.ui.nearby.NearbyViewModel.Companion.REFRESH_MILLIS
import app.itivio.ui.nearby.StopDepartures
import app.itivio.ui.nearby.byLine
import app.itivio.ui.nearby.upcoming
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
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
class NearbyViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeRoutingRepository()
    private val userLocation = UserLocation()
    private val point = GeoPoint(52.52, 13.405)
    // Lazy: stateIn reads Dispatchers.Main after the rule installs it.
    private val viewModel by lazy { NearbyViewModel(repository, userLocation) }

    @Test
    fun `without a location fix stays idle and makes no request`() = runTest(main.dispatcher) {
        viewModel.departures.launchIn(backgroundScope)
        runCurrent()
        advanceTimeBy(REFRESH_MILLIS * 2)
        runCurrent()

        assertEquals(LoadState.Idle, viewModel.departures.value)
        assertTrue(repository.nearbyCalls.isEmpty())
    }

    @Test
    fun `first fix loads departures grouped by stop name in time order`() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<List<Departure>>()
        repository.nearbyResult = { gate.await() }
        viewModel.departures.launchIn(backgroundScope)
        runCurrent()

        userLocation.point.value = point
        runCurrent()
        assertEquals(LoadState.Loading, viewModel.departures.value)
        assertEquals(listOf(point), repository.nearbyCalls)

        val zooLate = departure("Zoo", "zoo-1", scheduledMinutes = 5, estimatedMinutes = 20)
        val central = departure("Central", "central", scheduledMinutes = 10)
        val zooEarly = departure("Zoo", "zoo-2", scheduledMinutes = 15, estimatedMinutes = 8)
        gate.complete(listOf(zooLate, central, zooEarly))
        runCurrent()

        assertEquals(
            LoadState.Ready(
                listOf(
                    // The stops of the fixture are at the device position.
                    StopDepartures("Zoo", listOf(zooEarly, zooLate), distanceMeters = 0.0),
                    StopDepartures("Central", listOf(central), distanceMeters = 0.0),
                ),
            ),
            viewModel.departures.value,
        )
    }

    @Test
    fun `refresh requests again only after the refresh interval`() = runTest(main.dispatcher) {
        userLocation.point.value = point
        viewModel.departures.launchIn(backgroundScope)
        runCurrent()
        assertEquals(listOf(point), repository.nearbyCalls)

        advanceTimeBy(REFRESH_MILLIS - 1)
        runCurrent()
        assertEquals(1, repository.nearbyCalls.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(point, point), repository.nearbyCalls)
    }

    @Test
    fun `small moves keep the request and large moves request the new point immediately`() = runTest(main.dispatcher) {
        userLocation.point.value = point
        viewModel.departures.launchIn(backgroundScope)
        runCurrent()

        // Latitude changes are about 50 m and 500 m, away from the 150 m threshold.
        userLocation.point.value = point.copy(latitude = point.latitude + 0.00045)
        runCurrent()
        assertEquals(listOf(point), repository.nearbyCalls)

        val moved = point.copy(latitude = point.latitude + 0.0045)
        userLocation.point.value = moved
        runCurrent()
        assertEquals(listOf(point, moved), repository.nearbyCalls)
    }

    @Test
    fun `first load failure maps offline stops polling and can be retried`() = runTest(main.dispatcher) {
        repository.nearbyResult = { throw RoutingException.Offline() }
        userLocation.point.value = point
        viewModel.departures.launchIn(backgroundScope)
        runCurrent()
        assertEquals(LoadState.Failed(Failure.OFFLINE), viewModel.departures.value)

        advanceTimeBy(REFRESH_MILLIS * 2)
        runCurrent()
        assertEquals(listOf(point), repository.nearbyCalls)
        assertEquals(LoadState.Failed(Failure.OFFLINE), viewModel.departures.value)

        val departure = departure("Zoo", "zoo", scheduledMinutes = 10)
        repository.nearbyResult = { listOf(departure) }
        viewModel.retry()
        runCurrent()

        assertEquals(listOf(point, point), repository.nearbyCalls)
        assertEquals(
            LoadState.Ready(listOf(StopDepartures("Zoo", listOf(departure), distanceMeters = 0.0))),
            viewModel.departures.value,
        )
    }

    @Test
    fun `background refresh failure keeps the previous ready list`() = runTest(main.dispatcher) {
        val departure = departure("Zoo", "zoo", scheduledMinutes = 10)
        repository.nearbyResult = { listOf(departure) }
        userLocation.point.value = point
        viewModel.departures.launchIn(backgroundScope)
        runCurrent()
        val ready = LoadState.Ready(listOf(StopDepartures("Zoo", listOf(departure), distanceMeters = 0.0)))
        assertEquals(ready, viewModel.departures.value)

        repository.nearbyResult = { throw RoutingException.Offline() }
        advanceTimeBy(REFRESH_MILLIS)
        runCurrent()

        assertEquals(2, repository.nearbyCalls.size)
        assertEquals(ready, viewModel.departures.value)
    }

    @Test
    fun `requests stop after the last collector leaves and the subscription timeout passes`() = runTest(main.dispatcher) {
        userLocation.point.value = point
        val collector = viewModel.departures.launchIn(backgroundScope)
        runCurrent()
        assertEquals(listOf(point), repository.nearbyCalls)

        collector.cancel()
        runCurrent()
        advanceTimeBy(5_000 + REFRESH_MILLIS * 3)
        runCurrent()

        assertEquals(listOf(point), repository.nearbyCalls)
    }

    @Test
    fun `upcoming drops departures from past minutes and empty stops`() {
        val groups = listOf(
            StopDepartures("Aubert", listOf(departure("Aubert", "a", 0), departure("Aubert", "a", 1))),
            StopDepartures("Tourelle", listOf(departure("Tourelle", "t", 0))),
        )
        // 10:01:30: the 10:01 bus is still in the current minute and stays; the 10:00 buses are gone.
        val upcoming = groups.upcoming(Instant.parse("2026-11-09T10:01:30Z"))
        assertEquals(listOf("Aubert"), upcoming.map { it.name })
        assertEquals(listOf(Instant.parse("2026-11-09T10:01:00Z")), upcoming.single().departures.map { it.time.best })
    }

    @Test
    fun `group gives the distance to the nearest platform of each stop`() {
        val far = departure("Zoo", "zoo-1", 0).let { it.copy(stop = it.stop.copy(point = GeoPoint(48.8610, 2.35))) }
        val near = departure("Zoo", "zoo-2", 1).let { it.copy(stop = it.stop.copy(point = GeoPoint(48.8601, 2.35))) }

        val withPosition = NearbyViewModel.group(listOf(far, near), from = GeoPoint(48.86, 2.35)).single()
        // 0.0001 degrees of latitude is about 11 m.
        assertEquals(11.1, withPosition.distanceMeters!!, 0.1)
        assertEquals(null, NearbyViewModel.group(listOf(far, near)).single().distanceMeters)
    }

    @Test
    fun `byLine gives one list for each line and direction in order of the first departure`() {
        val toTownHall1 = departure("Zoo", "zoo-1", 1)
        val toDepot = departure("Zoo", "zoo-2", 2).copy(headsign = "Depot")
        val toTownHall2 = departure("Zoo", "zoo-1", 5)
        val otherLine = departure("Zoo", "zoo-1", 6).copy(line = Line("N5"))

        assertEquals(
            listOf(listOf(toTownHall1, toTownHall2), listOf(toDepot), listOf(otherLine)),
            listOf(toTownHall1, toDepot, toTownHall2, otherLine).byLine(),
        )
    }

    private fun departure(
        name: String,
        stopId: String,
        scheduledMinutes: Long,
        estimatedMinutes: Long? = null,
    ): Departure {
        val base = Instant.parse("2026-11-09T10:00:00Z")
        return Departure(
            stop = Stop(name, point, stopId = stopId),
            line = Line("M41"),
            mode = TransitMode.BUS,
            headsign = "Town hall",
            time = StopTime(
                scheduled = base.plusSeconds(scheduledMinutes * 60),
                estimated = estimatedMinutes?.let { base.plusSeconds(it * 60) },
            ),
            cancelled = false,
            tripId = "trip-$stopId",
        )
    }
}
