package app.itivio.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.itivio.data.boarding.IdfmBoardingRepository
import app.itivio.data.ign.IgnAddressSearch
import app.itivio.data.options.DataStoreJourneyOptionsRepository
import app.itivio.data.places.PlacesDatabase
import app.itivio.data.places.RoomPlacesRepository
import app.itivio.data.settings.DataStoreSettingsRepository
import app.itivio.data.settings.LocalAppData
import app.itivio.data.transitous.TransitousRoutingRepository
import app.itivio.domain.AppData
import app.itivio.domain.BoardingRepository
import app.itivio.domain.JourneyOptionsRepository
import app.itivio.domain.PlacesRepository
import app.itivio.domain.RoutingRepository
import app.itivio.domain.SettingsRepository
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
    // One instance for the file: the journey options and the settings share it. The data
    // extraction rules also keep this file out of backup and device transfer.
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        ) { androidContext().preferencesDataStoreFile("settings") }
    }
    single<JourneyOptionsRepository> { DataStoreJourneyOptionsRepository(get()) }
    single<SettingsRepository> { DataStoreSettingsRepository(get()) }
    single<AppData> { LocalAppData(get<PlacesDatabase>().places(), get()) }
    single<BoardingRepository> { IdfmBoardingRepository { androidContext().assets.open("boarding/idfm.tsv") } }
}
