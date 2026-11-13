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
import app.itivio.ui.stop.StopDeparturesViewModel
import app.itivio.ui.stop.StopDeparturesViewModel.Companion.REFRESH_MILLIS
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StopDeparturesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeRoutingRepository()
    private val viewModel by lazy { StopDeparturesViewModel(repository) }
    private val departure = Departure(
        Stop("Alexanderplatz", GeoPoint(52.5, 13.4), stopId = "s"), Line("U2"), TransitMode.SUBWAY, "Pankow",
        StopTime(Instant.parse("2026-11-09T10:00:00Z")), cancelled = false, tripId = "t",
    )

    @Test
    fun `loads the board of the stop and refreshes once a minute`() = runTest(main.dispatcher) {
        repository.stopResult = { listOf(departure) }
        viewModel.departures.launchIn(backgroundScope)
        viewModel.show("s")
        runCurrent()
        assertEquals(LoadState.Ready(listOf(departure)), viewModel.departures.value)
        assertEquals(listOf("s"), repository.stopCalls)

        advanceTimeBy(REFRESH_MILLIS)
        runCurrent()
        assertEquals(listOf("s", "s"), repository.stopCalls)
    }

    @Test
    fun `only the first load shows an error`() = runTest(main.dispatcher) {
        repository.stopResult = { throw RoutingException.Offline() }
        viewModel.departures.launchIn(backgroundScope)
        viewModel.show("s")
        runCurrent()
        assertEquals(LoadState.Failed(Failure.OFFLINE), viewModel.departures.value)

        repository.stopResult = { listOf(departure) }
        viewModel.retry()
        runCurrent()
        repository.stopResult = { throw RoutingException.Server(503) }
        advanceTimeBy(REFRESH_MILLIS)
        runCurrent()
        assertEquals(LoadState.Ready(listOf(departure)), viewModel.departures.value)
    }
}
