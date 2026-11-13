package app.itivio.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.LaunchedEffect
import app.itivio.ui.search.LocationSearchView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import app.itivio.data.transitous.PlanResponse
import app.itivio.data.transitous.toDomain
import app.itivio.data.transitousJson
import app.itivio.domain.Alert
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
import app.itivio.domain.ThemeMode
import app.itivio.domain.TransitMode
import app.itivio.domain.disruptionsOf
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.UserLocation
import app.itivio.ui.disruptions.DisruptionGroups
import app.itivio.ui.disruptions.DisruptionsScreen
import app.itivio.ui.explore.ExploreScreen
import app.itivio.ui.settings.AboutScreen
import app.itivio.ui.settings.SettingsScreen
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
import app.itivio.ui.stop.StopScreen
import app.itivio.ui.walk.WalkScreen
import app.itivio.ui.theme.ItivioTheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import java.time.Clock
import java.time.Instant
import app.itivio.ui.results.LocalTravelClock
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compares panels without the native map in light and dark, at normal and 200% text size.
 * Reference images live in src/test/screenshots; differences go to build/outputs/roborazzi/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = Application::class)
open class ScreenshotTest {
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

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, content: @Composable () -> Unit) {
        var dark by mutableStateOf(false)
        val fontScale = RuntimeEnvironment.getFontScale()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ItivioTheme(darkTheme = dark, dynamicColor = false) {
                // Stands in for the map so sheet edges stay visible.
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale),
                    LocalTravelClock provides Clock.fixed(t0, berlin),
                ) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceDim)) { content() }
                }
            }
        }
        for (theme in listOf("light", "dark")) {
            dark = theme == "dark"
            val variant = theme + if (fontScale >= 2f) "_large" else ""
            Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeBy(1_000)
            assertNamedActions()
            compose.onRoot().captureRoboImage(
                "src/test/screenshots/${name}_$variant.png",
                roborazziOptions = RoborazziOptions(
                    compareOptions = RoborazziOptions.CompareOptions(
                        outputDirectoryPath = "build/outputs/roborazzi",
                        // ARM64 and x86_64 differ at a few antialiased clock-hand edge pixels.
                        changeThreshold = if (name == "travel_time") 0.00005f else 0f,
                    ),
                ),
            )
        }
    }

    private fun assertNamedActions() {
        fun named(node: SemanticsNode): Boolean =
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.isNotBlank() } == true ||
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.isNotBlank() } == true ||
                node.config.getOrNull(SemanticsProperties.EditableText) != null ||
                node.children.any(::named)
        val actions = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()
        actions.forEach { node -> org.junit.Assert.assertTrue("Unnamed action: ${node.config}", named(node)) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun search() = capture("search") {
        val state = rememberSearchBarState()
        val text = rememberTextFieldState()
        LaunchedEffect(Unit) { state.animateToExpanded() }
        LocationSearchView(state, text, "Destination", offerMyLocation = true, onPick = {})
    }

    @Test
    fun explore() = capture("explore") {
        ExploreScreen(
            wide = false, locationGranted = false, nearby = LoadState.Idle, now = t0,
            onRequestLocation = {}, onRecenter = {}, onRetryNearby = {}, onDestination = {}, onStop = {}, onSettings = {},
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
                onRequestLocation = {}, onRecenter = {}, onRetryNearby = {}, onDestination = {}, onStop = {}, onSettings = {},
            )
        }
    }

    @Test
    fun nearby() = capture("nearby") {
        ExploreScreen(
            wide = false, locationGranted = true, nearby = LoadState.Ready(sampleDepartures), now = t0,
            onRequestLocation = {}, onRecenter = {}, onRetryNearby = {}, onDestination = {}, onStop = {}, onSettings = {},
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
            onWalk = {},
        )
    }

    @Test
    fun stopBoard() = capture("stop") {
        StopScreen(
            wide = false,
            name = "S+U Alexanderplatz",
            departures = LoadState.Ready(sampleDepartures.flatMap { it.departures }),
            now = t0,
            onBack = {}, onDirections = {}, onRetry = {},
        )
    }

    @Test
    fun disruptions() = capture("disruptions") {
        val departures = transitousJson.decodeFromString<app.itivio.data.transitous.StoptimesResponse>(
            requireNotNull(javaClass.getResource("/transitous/stoptimes-alerts.json")).readText(),
        ).stopTimes.map { it.toDomain() }
        val saved = disruptionsOf(departures.filter { it.line.name != "57" })
        DisruptionsScreen(
            state = LoadState.Ready(DisruptionGroups(saved, disruptionsOf(departures, shown = saved.map { it.alert }.toSet()))),
            now = t0,
            onRetry = {},
        )
    }

    @Test
    fun disruptionsEmpty() = capture("disruptions_empty") {
        DisruptionsScreen(state = LoadState.Ready(DisruptionGroups()), now = t0, onRetry = {})
    }

    @Test
    fun trainChoices() = capture("train_choices") {
        app.itivio.ui.itinerary.TrainDepartures(
            departures = sampleDepartures.first().departures,
            selectedTripId = "d1", now = t0,
            loading = false, canSelect = true, changed = true, failed = false,
            onSelect = {}, onReset = {},
        )
    }

    @Test
    fun trainChoicesFailed() = capture("train_choices_failed") {
        app.itivio.ui.itinerary.TrainDepartures(
            departures = sampleDepartures.first().departures,
            selectedTripId = "d1", now = t0,
            loading = false, canSelect = true, changed = false, failed = true,
            onSelect = {}, onReset = {},
        )
    }

    @Test
    fun settings() = capture("settings") {
        SettingsScreen(
            theme = ThemeMode.SYSTEM, options = JourneyOptions(),
            onBack = {}, onTheme = {}, onOptions = {}, onClearRecents = {}, onDeleteAll = {}, onAbout = {},
        )
    }

    @Test
    fun about() = capture("about") { AboutScreen(version = "0.8.0", onBack = {}) }

    @Test
    fun walk() = capture("walk") { Walk(position = { it.points[it.points.size / 3] }) }

    @Test
    fun walkOffRoute() = capture("walk_off_route") { Walk(position = { GeoPoint(52.5275, 13.372) }) }

    @Test
    fun walkWide() = capture("walk_wide") { Walk(position = { it.points[it.points.size / 3] }, wide = true) }

    /** The first walk of the Berlin fixture, from Invalidenstraße to the platforms of the main station. */
    @Composable
    private fun Walk(position: (app.itivio.domain.WalkRoute) -> GeoPoint, wide: Boolean = false) {
        val leg = transitousJson.decodeFromString<PlanResponse>(
            requireNotNull(javaClass.getResource("/transitous/plan.json")).readText(),
        ).toDomain(toName = "Berlin Hbf").journeys.first().legs.first() as Leg.Street
        WalkScreen(
            wide = wide,
            leg = leg,
            position = position(app.itivio.domain.WalkRoute.of(leg.steps, leg.geometry)),
            locationGranted = true,
            following = false,
            now = t0,
            onClose = {}, onRecenter = {}, onRequestLocation = {}, onReplan = {},
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

    @Test
    fun singleLongAlertIsFullyReadable() {
        val header = "The station is closed for maintenance. Use the temporary bus stop on the other side of the main road. ".repeat(4)
        compose.setContent {
            ItivioTheme { app.itivio.ui.itinerary.AlertBanner(listOf(Alert(header, ""))) }
        }
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(header).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        org.junit.Assert.assertTrue(layouts.single().lineCount > 2)
        org.junit.Assert.assertFalse(layouts.single().hasVisualOverflow)
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
            from = GeoPoint(52.5013, 13.4),
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
                        alerts = listOf(
                            Alert("Diversion at Potsdamer Platz because of road works", "The stop moves 50 m."),
                            Alert("Lift out of service at U Stadtmitte", ""),
                        ),
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

/** A separate Android configuration also scales text in dialog windows. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = Application::class)
class LargeScreenshotTest : ScreenshotTest() {
    @Before
    fun useLargeText() = RuntimeEnvironment.setFontScale(2f)

    @After
    fun restoreTextSize() = RuntimeEnvironment.setFontScale(1f)
}
