package app.itivio.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.itivio.data.places.PlacesDatabase
import app.itivio.data.places.RoomPlacesRepository
import app.itivio.domain.GeoPoint
import app.itivio.domain.Location
import app.itivio.domain.LocationKind
import app.itivio.domain.PlacesRepository
import app.itivio.domain.Shortcut
import app.itivio.domain.TransitMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoomPlacesRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder<PlacesDatabase>(ApplicationProvider.getApplicationContext())
        .allowMainThreadQueries()
        .build()
    private var now = 0L
    private val repository = RoomPlacesRepository(database.places(), clock = { ++now })

    @After
    fun close() = database.close()

    @Test
    fun `a saved place reads back unchanged`() = runTest {
        val stop = place("Alexanderplatz").copy(
            kind = LocationKind.STOP, stopId = "de:11000:900100003", modes = setOf(TransitMode.SUBWAY, TransitMode.TRAM),
        )
        repository.setFavorite(stop, favorite = true)

        assertEquals(listOf(stop), repository.saved.first().favorites)
    }

    @Test
    fun `favorites keep the order they were added in`() = runTest {
        repository.setFavorite(place("A"), favorite = true)
        repository.setFavorite(place("B"), favorite = true)
        repository.setFavorite(place("A"), favorite = false)
        repository.setFavorite(place("C"), favorite = true)

        assertEquals(listOf("B", "C"), repository.saved.first().favorites.map { it.name })
    }

    @Test
    fun `each shortcut holds one place`() = runTest {
        repository.setShortcut(Shortcut.HOME, place("Old home"))
        repository.setShortcut(Shortcut.HOME, place("New home"))
        repository.setShortcut(Shortcut.WORK, place("Office"))

        val saved = repository.saved.first()
        assertEquals("New home", saved.home?.name)
        assertEquals("Office", saved.work?.name)

        repository.setShortcut(Shortcut.HOME, null)
        assertNull(repository.saved.first().home)
        assertEquals("Office", repository.saved.first().work?.name)
    }

    @Test
    fun `recents are newest first, without duplicates, and capped`() = runTest {
        (1..PlacesRepository.MAX_RECENTS + 2).forEach { repository.addRecent(place("P$it")) }
        repository.addRecent(place("P5"))

        val recents = repository.saved.first().recents.map { it.name }
        assertEquals(PlacesRepository.MAX_RECENTS, recents.size)
        assertEquals(listOf("P5", "P12", "P11"), recents.take(3))
        assertTrue("P1" !in recents && "P2" !in recents)
    }

    @Test
    fun `clearing recents keeps favorites and shortcuts`() = runTest {
        val home = place("Home")
        repository.addRecent(home)
        repository.setFavorite(home, favorite = true)
        repository.setShortcut(Shortcut.HOME, home)

        repository.clearRecents()

        val saved = repository.saved.first()
        assertTrue(saved.recents.isEmpty())
        assertEquals(listOf(home), saved.favorites)
        assertEquals(home, saved.home)
    }

    private fun place(name: String) =
        Location(id = "", name = name, kind = LocationKind.ADDRESS, point = GeoPoint(48.85, 2.35), area = "Paris")
}
