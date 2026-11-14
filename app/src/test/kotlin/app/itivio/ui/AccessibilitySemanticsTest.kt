package app.itivio.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import app.itivio.ui.common.LocalTimeFormatter
import java.time.format.DateTimeFormatter
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.itivio.ui.itinerary.ItineraryScreen
import app.itivio.ui.common.Choice
import app.itivio.ui.common.ChoiceButtons
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import app.itivio.domain.Stop
import app.itivio.domain.BoardingHint
import app.itivio.domain.StationExit
import app.itivio.domain.GeoPoint
import app.itivio.domain.Leg
import app.itivio.domain.Journey
import app.itivio.domain.TransitMode
import app.itivio.ui.common.LegChain
import app.itivio.domain.Departure
import app.itivio.domain.Line
import app.itivio.ui.nearby.DeparturesCard
import app.itivio.ui.itinerary.TrainDepartures
import app.itivio.domain.StopTime
import app.itivio.ui.common.LoadingState
import app.itivio.ui.common.Skeleton
import app.itivio.ui.nearby.DeparturesCardSkeleton
import androidx.compose.ui.test.onAllNodesWithContentDescription
import app.itivio.ui.common.TimeText
import app.itivio.ui.theme.ItivioTheme
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS", application = Application::class)
class AccessibilitySemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a delayed time is announced once with both live and scheduled values`() {
        val scheduled = Instant.parse("2026-11-09T10:00:00Z")
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalTimeFormatter provides DateTimeFormatter.ofPattern("HH:mm")) {
                    TimeText(StopTime(scheduled, scheduled.plusSeconds(120)), ZoneId.of("UTC"))
                }
            }
        }
        compose.onNodeWithContentDescription("10:02, Live, Scheduled 10:00").assertIsDisplayed()
        // The semantic label replaces visible child text instead of repeating both times.
        val descriptions = compose.onAllNodesWithText("10:02")
        descriptions.assertCountEquals(0)
    }

    @Test
    fun `loading has one accessible label`() {
        compose.setContent { ItivioTheme(dynamicColor = false) { LoadingState() } }
        compose.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun `a skeleton has one accessible label`() {
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                Skeleton { repeat(3) { DeparturesCardSkeleton(rows = 2, header = true) } }
            }
        }
        compose.onAllNodesWithContentDescription("Loading").assertCountEquals(1)
    }

    @Test
    fun `walking badge speaks minutes once`() {
        val start = StopTime(Instant.parse("2026-11-09T10:00:00Z"))
        val end = StopTime(start.scheduled.plusSeconds(300))
        val stop = Stop("Station", GeoPoint(52.5, 13.4), stopId = "stop")
        val leg = Leg.Street(TransitMode.WALK, stop, stop, start, end, emptyList(), 400.0)
        val journey = Journey("walk", listOf(leg), start, end, 0)
        compose.setContent { ItivioTheme(dynamicColor = false) { LegChain(journey) } }
        compose.onNodeWithContentDescription("Walk, 5 min").assertIsDisplayed()
        compose.onAllNodesWithText("5").assertCountEquals(0)
    }

    @Test
    fun `train arrival label replaces the visual abbreviation`() {
        val now = Instant.parse("2026-11-09T10:00:00Z")
        val stop = Stop("Station", GeoPoint(52.5, 13.4), stopId = "stop", zone = ZoneId.of("UTC"))
        val departure = Departure(
            stop = stop, line = Line("R", id = "line"), mode = TransitMode.RAIL,
            headsign = "Destination", time = StopTime(now.plusSeconds(300)), cancelled = false, tripId = "trip",
            alightTime = StopTime(now.plusSeconds(1500)),
        )
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalTimeFormatter provides DateTimeFormatter.ofPattern("HH:mm")) {
                    TrainDepartures(
                        listOf(departure), null, now,
                        loading = false, canSelect = true, changed = false, failed = false,
                        onSelect = {}, onReset = {},
                    )
                }
            }
        }
        compose.onAllNodesWithText("10:25", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Arrives at 10:25, 20 min on board", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `abbreviated choices speak the full label without repeating the number`() {
        var picked = -1
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                ChoiceButtons(
                    choices = listOf(Choice("1 transfer", text = "1"), Choice("2 transfers", text = "2")),
                    selected = 0,
                    onSelect = { picked = it },
                )
            }
        }
        compose.onAllNodesWithText("2").assertCountEquals(0)
        compose.onNodeWithContentDescription("2 transfers").assertIsNotSelected().performClick()
        org.junit.Assert.assertEquals(1, picked)
    }


    @Test
    fun `a departure due now does not say in now`() {
        val now = Instant.parse("2026-11-09T10:00:00Z")
        val stop = Stop("Station", GeoPoint(52.5, 13.4), zone = ZoneId.of("UTC"))
        val departure = Departure(stop, Line("R"), TransitMode.RAIL, "Destination", StopTime(now, now), false, "trip")
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalTimeFormatter provides DateTimeFormatter.ofPattern("HH:mm")) {
                    DeparturesCard(listOf(departure), now)
                }
            }
        }
        compose.onNodeWithContentDescription("Now, 10:00, Live", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("Now").assertCountEquals(0)
    }


    @Test
    fun `walking directions has one spoken name and keeps its action`() {
        val start = StopTime(Instant.parse("2026-11-09T10:00:00Z"))
        val end = StopTime(start.scheduled.plusSeconds(300))
        val from = Stop("Start", GeoPoint(52.5, 13.4))
        val to = Stop("End", GeoPoint(52.501, 13.4))
        val leg = Leg.Street(TransitMode.WALK, from, to, start, end, listOf(from.point, to.point), 120.0)
        var picked = -1
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                ItineraryScreen(
                    wide = true, journey = Journey("walk", listOf(leg), start, end, 0),
                    now = start.scheduled, lineTimes = emptyMap(), onBack = {}, onWalk = { picked = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Walking directions").performScrollTo().performClick()
        compose.onAllNodesWithText("Directions").assertCountEquals(0)
        org.junit.Assert.assertEquals(0, picked)
    }

    @Test
    fun `a station exit stays visible without a train part`() {
        val start = StopTime(Instant.parse("2026-11-16T09:00:00Z"))
        val end = StopTime(start.scheduled.plusSeconds(960))
        val from = Stop("Nation", GeoPoint(48.8482, 2.3960))
        val to = Stop("La Défense", GeoPoint(48.8916, 2.2383))
        val leg = Leg.Transit(
            TransitMode.RAIL, from, to, start, end, emptyList(), Line("A"), null, null, emptyList(),
        )
        val exit = StationExit("50148846", "6", "Coupole - Regnault", GeoPoint(48.891989, 2.242296))
        compose.setContent {
            ItivioTheme(dynamicColor = false) {
                ItineraryScreen(
                    wide = true, journey = Journey("rer", listOf(leg), start, end, 0),
                    now = start.scheduled, lineTimes = emptyMap(), onBack = {}, onWalk = {},
                    boarding = mapOf(0 to BoardingHint(null, null, exit)),
                )
            }
        }
        compose.onNodeWithContentDescription("Best exit: 6 – Coupole - Regnault").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Where to board").assertCountEquals(0)
    }

}
