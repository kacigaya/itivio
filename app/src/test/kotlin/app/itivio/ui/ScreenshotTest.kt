package app.itivio.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.itivio.domain.Departure
import app.itivio.domain.GeoPoint
import app.itivio.domain.Journey
import app.itivio.domain.JourneyOptions
import app.itivio.domain.Leg
import app.itivio.domain.Line
import app.itivio.domain.PlacesRepository
import app.itivio.domain.SavedPlaces
import app.itivio.domain.Stop
import app.itivio.domain.StopTime
import app.itivio.domain.TransitMode
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.UserLocation
import app.itivio.ui.explore.ExploreScreen
import app.itivio.ui.itinerary.ItineraryScreen
import app.itivio.ui.nearby.NearbyViewModel
import app.itivio.ui.nearby.StopDepartures
import app.itivio.ui.planner.Endpoint
import app.itivio.ui.planner.PlannerState
import app.itivio.ui.results.JourneyOptionsDialog
import app.itivio.ui.results.ResultsScreen
import app.itivio.ui.results.TravelTimeDialog
import app.itivio.ui.search.SavedPlacesViewModel
import app.itivio.ui.search.SearchViewModel
import app.itivio.ui.theme.ItivioTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Instant
import java.time.ZoneId
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each screen's panel (without the native map) in light and dark for design review.
 * Images land in app/build/outputs/roborazzi/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val places = FakePlacesRepository()

    @Before
    fun koin() {
        startKoin {
            modules(module {
                single { UserLocation() }
                single<app.itivio.domain.RoutingRepository> { FakeRoutingRepository() }
                single<PlacesRepository> { places }
                viewModelOf(::SearchViewModel)
                viewModelOf(::SavedPlacesViewModel)
            })
        }
    }

    @After
    fun stop() = stopKoin()

    private fun capture(name: String, content: @Composable () -> Unit) {
        var dark by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ItivioTheme(darkTheme = dark, dynamicColor = false) {
                // Stands in for the map so sheet edges stay visible.
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceDim)) { content() }
            }
        }
        for (theme in listOf("light", "dark")) {
            dark = theme == "dark"
            Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeBy(1_000)
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_$theme.png")
        }
    }

    @Test
    fun explore() = capture("explore") {
        ExploreScreen(
            wide = false, locationGranted = false, nearby = LoadState.Idle, now = t0,
            onRequestLocation = {}, onRecenter = {}, onRetryNearby = {}, onDestination = {},
        )
    }

    @Test
    fun exploreSaved() {
        places.saved.value = SavedPlaces(
            home = FakeRoutingRepository.location("Rue de Rivoli 12"),
            favorites = listOf(FakeRoutingRepository.location("S+U Alexanderplatz"), FakeRoutingRepository.location("Tempelhofer Feld")),
        )
        capture("explore_saved") {
            ExploreScreen(
                wide = false, locationGranted = true, nearby = LoadState.Ready(sampleDepartures), now = t0,
                onRequestLocation = {}, onRecenter = {}, onRetryNearby = {}, onDestination = {},
            )
        }
    }

    @Test
    fun nearby() = capture("nearby") {
        ExploreScreen(
            wide = false, locationGranted = true, nearby = LoadState.Ready(sampleDepartures), now = t0,
            onRequestLocation = {}, onRecenter = {}, onRetryNearby = {}, onDestination = {},
        )
    }

    @Test
    fun resultsArriveBy() = capture("results_arrive_by") {
        ResultsScreen(
            wide = false,
            state = PlannerState(
                optionsLoaded = true,
                destination = Endpoint.Place(FakeRoutingRepository.location("Potsdamer Platz")),
                time = t0.plusSeconds(26 * 3600),
                arriveBy = true,
                results = LoadState.Ready(sampleJourneys),
            ),
            onBack = {}, onOrigin = {}, onDestination = {}, onSwap = {}, onTime = { _, _ -> }, onOptions = {}, onSort = {},
            onSelect = {}, onOpen = {}, onEarlier = {}, onLater = {}, onRetry = {},
        )
    }

    @Test
    fun travelTimeDialog() = capture("travel_time") {
        TravelTimeDialog(time = null, arriveBy = false, onDismiss = {}, onConfirm = { _, _ -> })
    }

    @Test
    fun journeyOptionsDialog() = capture("journey_options") {
        JourneyOptionsDialog(
            options = JourneyOptions(excludedModes = setOf(TransitMode.COACH, TransitMode.AIRPLANE), maxTransfers = 1),
            onDismiss = {},
            onConfirm = {},
        )
    }

    @Test
    fun results() = capture("results") {
        ResultsScreen(
            wide = false,
            state = PlannerState(
                optionsLoaded = true,
                origin = Endpoint.MyLocation,
                destination = Endpoint.Place(FakeRoutingRepository.location("Potsdamer Platz")),
                options = JourneyOptions(maxTransfers = 1),
                results = LoadState.Ready(sampleJourneys),
                canLoadEarlier = true,
                canLoadLater = true,
            ),
            onBack = {}, onOrigin = {}, onDestination = {}, onSwap = {}, onTime = { _, _ -> }, onOptions = {}, onSort = {},
            onSelect = {}, onOpen = {}, onEarlier = {}, onLater = {}, onRetry = {},
        )
    }

    @Test
    fun resultsOffline() = capture("results_offline") {
        ResultsScreen(
            wide = false,
            state = PlannerState(
                optionsLoaded = true,
                destination = Endpoint.Place(FakeRoutingRepository.location("Potsdamer Platz")),
                results = LoadState.Failed(app.itivio.ui.common.Failure.OFFLINE),
            ),
            onBack = {}, onOrigin = {}, onDestination = {}, onSwap = {}, onTime = { _, _ -> }, onOptions = {}, onSort = {},
            onSelect = {}, onOpen = {}, onEarlier = {}, onLater = {}, onRetry = {},
        )
    }

    @Test
    fun itinerary() = capture("itinerary") { Itinerary(wide = false) }

    // The pane shows the steps below the sheet fold: the line times and the later legs.
    @Test
    fun itineraryWide() = capture("itinerary_wide") { Itinerary(wide = true) }

    @Composable
    private fun Itinerary(wide: Boolean) {
        val line = Line("U2", color = 0xFFDA421E.toInt(), textColor = 0xFFFFFFFF.toInt())
        fun u2(min: Long, liveDelay: Long?, trip: String) =
            Departure(stop("S+U Alexanderplatz", "2"), line, TransitMode.SUBWAY, "Ruhleben", at(min, liveDelay), false, trip)
        ItineraryScreen(
            wide = wide,
            journey = sampleJourneys.first(),
            // Two minutes after the departure: the walk is the step in progress.
            now = t0.plusSeconds(120),
            lineTimes = mapOf(1 to listOf(u2(6, 2, "t1"), u2(11, null, "t4"), u2(16, null, "t5"))),
            onBack = {},
        )
    }

    @Test
    fun resultsWide() = capture("results_wide") {
        ResultsScreen(
            wide = true,
            state = PlannerState(
                optionsLoaded = true,
                destination = Endpoint.Place(FakeRoutingRepository.location("Potsdamer Platz")),
                results = LoadState.Ready(sampleJourneys),
            ),
            onBack = {}, onOrigin = {}, onDestination = {}, onSwap = {}, onTime = { _, _ -> }, onOptions = {}, onSort = {},
            onSelect = {}, onOpen = {}, onEarlier = {}, onLater = {}, onRetry = {},
        )
    }

    private companion object {
        val berlin: ZoneId = ZoneId.of("Europe/Berlin")
        val t0: Instant = Instant.parse("2026-11-09T07:58:00Z")
        fun at(min: Long, liveDelay: Long? = null) =
            StopTime(t0.plusSeconds(min * 60), liveDelay?.let { t0.plusSeconds((min + it) * 60) })
        fun stop(name: String, track: String? = null) = Stop(name, GeoPoint(52.5, 13.4), track = track, zone = berlin)

        val sampleDepartures: List<StopDepartures> = NearbyViewModel.group(
            listOf(
                Departure(stop("S+U Alexanderplatz", "2"), Line("U2", color = 0xFFDA421E.toInt(), textColor = 0xFFFFFFFF.toInt()),
                    TransitMode.SUBWAY, "Ruhleben", at(2, 1), false, "d1"),
                Departure(stop("S+U Alexanderplatz", "1"), Line("U2", color = 0xFFDA421E.toInt(), textColor = 0xFFFFFFFF.toInt()),
                    TransitMode.SUBWAY, "Pankow", at(4), false, "d2"),
                Departure(stop("S+U Alexanderplatz", "12"), Line("S5", color = 0xFFEB7405.toInt()),
                    TransitMode.SUBURBAN, "Strausberg Nord", at(7, 0), false, "d3"),
                Departure(stop("Alexanderplatz/Memhardstr."), Line("M2", color = 0xFFBE1414.toInt()),
                    TransitMode.TRAM, "Heinersdorf", at(3, 2), false, "d4"),
                Departure(stop("Alexanderplatz/Memhardstr."), Line("M4", color = 0xFFBE1414.toInt()),
                    TransitMode.TRAM, "Hackescher Markt", at(9), true, "d5"),
                Departure(stop("Alexanderplatz/Memhardstr."), Line("N5"),
                    TransitMode.BUS, "Hellersdorf", at(75), false, "d6"),
            ),
        )

        val sampleJourneys: List<Journey> = listOf(
            Journey(
                id = "1",
                legs = listOf(
                    Leg.Street(TransitMode.WALK, stop("Start"), stop("S+U Alexanderplatz"), at(0), at(4), emptyList(), 320.0),
                    Leg.Transit(
                        TransitMode.SUBWAY, stop("S+U Alexanderplatz", "2"), stop("U Stadtmitte"), at(6, 2), at(13, 2),
                        emptyList(), Line("U2", color = 0xFFDA421E.toInt(), textColor = 0xFFFFFFFF.toInt(), agency = "BVG"),
                        "Ruhleben", "t1", listOf(stop("U Klosterstr."), stop("U Märkisches Museum"), stop("U Spittelmarkt")),
                    ),
                    Leg.Street(TransitMode.WALK, stop("U Stadtmitte"), stop("U Stadtmitte (U6)"), at(15), at(18), emptyList(), 180.0),
                    Leg.Transit(
                        TransitMode.BUS, stop("U Stadtmitte (U6)", "B"), stop("Potsdamer Platz"), at(19), at(26),
                        emptyList(), Line("M41", color = 0xFFA5027D.toInt(), agency = "BVG"), "Sonnenallee", "t2", emptyList(),
                    ),
                ),
                departure = at(0), arrival = at(26), transfers = 1,
            ),
            Journey(
                id = "2",
                legs = listOf(
                    Leg.Street(TransitMode.WALK, stop("Start"), stop("Alexanderplatz"), at(3), at(8), emptyList(), 420.0),
                    Leg.Transit(
                        TransitMode.SUBURBAN, stop("S Alexanderplatz", "1"), stop("S Potsdamer Platz"), at(10), at(22),
                        emptyList(), Line("S1"), "Wannsee", "t3", emptyList(),
                    ),
                ),
                departure = at(3), arrival = at(22), transfers = 0,
            ),
        )
    }
}
