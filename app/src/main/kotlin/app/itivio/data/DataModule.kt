package app.itivio.data

import app.itivio.data.transitous.TransitousRoutingRepository
import app.itivio.data.ign.IgnAddressSearch
import app.itivio.domain.RoutingRepository
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dataModule = module {
    single { createHttpClient() }
    single(named("ign")) { createHttpClient(contact = "", baseUrl = "https://data.geopf.fr/geocodage/") }
    single { IgnAddressSearch(get(named("ign"))) }
    single<RoutingRepository> { TransitousRoutingRepository(get(), frenchAddresses = get()) }
}
