package app.itivio.ui

import app.itivio.domain.BoundingBox
import app.itivio.domain.GeoPoint
import app.itivio.domain.Location
import app.itivio.domain.LocationKind
import app.itivio.ui.map.MapStop
import app.itivio.ui.map.MapStopsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapStopsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeRoutingRepository()
    private val viewModel by lazy { MapStopsViewModel(repository) }
    private val view = BoundingBox(south = 48.8551, west = 2.3501, north = 48.8589, east = 2.3549)

    @Test
    fun `loads the stops of a rounded area at street zoom`() = runTest(main.dispatcher) {
        val stop = Location("s", "Hôtel de Ville", LocationKind.STOP, GeoPoint(48.857, 2.352), stopId = "s")
        repository.stopsResult = { listOf(stop, stop.copy(stopId = null)) }
        viewModel.onCameraIdle(view, 16.0)
        advanceUntilIdle()

        assertEquals(listOf(MapStop("s", "Hôtel de Ville", GeoPoint(48.857, 2.352))), viewModel.stops.value)
        val box = repository.boxes.single()
        assertEquals(48.85, box.south, 1e-9)
        assertEquals(2.35, box.west, 1e-9)
        assertEquals(48.86, box.north, 1e-9)
        assertEquals(2.36, box.east, 1e-9)
    }

    @Test
    fun `sends nothing when zoomed out, when the view stays in the loaded area, or while the camera moves on`() =
        runTest(main.dispatcher) {
            viewModel.onCameraIdle(view, 13.0)
            advanceUntilIdle()
            assertTrue(repository.boxes.isEmpty())

            viewModel.onCameraIdle(view, 16.0)
            advanceUntilIdle()
            viewModel.onCameraIdle(view.copy(south = 48.8552, north = 48.8590), 16.5)
            advanceUntilIdle()
            assertEquals(1, repository.boxes.size)

            // Quick moves in a row send one request, for the last view.
            val far = BoundingBox(48.8751, 2.3701, 48.8789, 2.3749)
            viewModel.onCameraIdle(far.copy(south = 48.8651, north = 48.8689), 16.0)
            viewModel.onCameraIdle(far, 16.0)
            advanceUntilIdle()
            assertEquals(2, repository.boxes.size)
            assertEquals(48.87, repository.boxes.last().south, 1e-9)
        }
}
