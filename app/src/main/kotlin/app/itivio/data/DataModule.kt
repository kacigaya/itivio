package app.itivio.data

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.itivio.data.ign.IgnAddressSearch
import app.itivio.data.options.DataStoreJourneyOptionsRepository
import app.itivio.data.places.PlacesDatabase
import app.itivio.data.places.RoomPlacesRepository
import app.itivio.data.transitous.TransitousRoutingRepository
import app.itivio.domain.JourneyOptionsRepository
import app.itivio.domain.PlacesRepository
import app.itivio.domain.RoutingRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dataModule = module {
    single { createHttpClient() }
    single(named("ign")) { createHttpClient(contact = "", baseUrl = "https://data.geopf.fr/geocodage/") }
    single { IgnAddressSearch(get(named("ign"))) }
    single<RoutingRepository> { TransitousRoutingRepository(get(), frenchAddresses = get()) }
    // The data extraction rules keep this database out of backup and device transfer.
    single { Room.databaseBuilder<PlacesDatabase>(androidContext(), "places.db").build() }
    single<PlacesRepository> { RoomPlacesRepository(get<PlacesDatabase>().places()) }
    // The data extraction rules also keep this file out of backup and device transfer.
    single<JourneyOptionsRepository> {
        DataStoreJourneyOptionsRepository(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            ) { androidContext().preferencesDataStoreFile("settings") },
        )
    }
}
