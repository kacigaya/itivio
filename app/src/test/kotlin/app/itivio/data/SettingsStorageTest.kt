package app.itivio.data

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.itivio.data.options.DataStoreJourneyOptionsRepository
import app.itivio.data.places.PlacesDatabase
import app.itivio.data.places.RoomPlacesRepository
import app.itivio.data.settings.DataStoreSettingsRepository
import app.itivio.data.settings.LocalAppData
import app.itivio.domain.GeoPoint
import app.itivio.domain.JourneyOptions
import app.itivio.domain.Location
import app.itivio.domain.LocationKind
import app.itivio.domain.SavedPlaces
import app.itivio.domain.Shortcut
import app.itivio.domain.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsStorageTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = TestScope()
    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { folder.root.resolve("settings.preferences_pb") }
    }
    private val database = Room.inMemoryDatabaseBuilder<PlacesDatabase>(ApplicationProvider.getApplicationContext())
        .allowMainThreadQueries()
        .build()
    private val settings by lazy { DataStoreSettingsRepository(store) }

    @After
    fun close() = database.close()

    @Test
    fun `the theme follows the system until the user sets it`() = scope.runTest {
        assertEquals(ThemeMode.SYSTEM, settings.theme.first())

        settings.setTheme(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, settings.theme.first())
    }

    @Test
    fun `an unknown stored theme falls back to the system`() = scope.runTest {
        store.edit { it[stringPreferencesKey("theme")] = "SEPIA" }

        assertEquals(ThemeMode.SYSTEM, settings.theme.first())
    }

    @Test
    fun `delete all removes the saved places, the journey options, and the settings`() = scope.runTest {
        val places = RoomPlacesRepository(database.places())
        val options = DataStoreJourneyOptionsRepository(store)
        val place = Location("id", "Zoo", LocationKind.STOP, GeoPoint(52.5, 13.4), stopId = "zoo")
        places.setFavorite(place, favorite = true)
        places.setShortcut(Shortcut.HOME, place)
        places.addRecent(place)
        options.save(JourneyOptions(wheelchair = true))
        settings.setTheme(ThemeMode.LIGHT)

        LocalAppData(database.places(), store).deleteAll()

        assertEquals(SavedPlaces(), places.saved.first())
        assertEquals(JourneyOptions(), options.options.first())
        assertEquals(ThemeMode.SYSTEM, settings.theme.first())
    }
}
