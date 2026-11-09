package app.itivio.data

import app.itivio.data.transitous.TransitousRoutingRepository
import app.itivio.domain.RoutingRepository
import org.koin.dsl.module

val dataModule = module {
    single { createHttpClient() }
    single<RoutingRepository> { TransitousRoutingRepository(get()) }
}
