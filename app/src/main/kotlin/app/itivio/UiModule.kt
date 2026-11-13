package app.itivio

import app.itivio.ui.common.UserLocation
import app.itivio.ui.itinerary.LineTimesViewModel
import app.itivio.ui.itinerary.TripTrackerViewModel
import app.itivio.ui.map.MapStopsViewModel
import app.itivio.ui.nearby.NearbyViewModel
import app.itivio.ui.planner.PlannerViewModel
import app.itivio.ui.search.SavedPlacesViewModel
import app.itivio.ui.search.SearchViewModel
import app.itivio.ui.stop.StopDeparturesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val uiModule = module {
    single { UserLocation() }
    // The SavedStateHandle keeps the planning request across process death.
    viewModel { PlannerViewModel(get(), get(), get(), get()) }
    viewModelOf(::SearchViewModel)
    viewModelOf(::SavedPlacesViewModel)
    viewModelOf(::NearbyViewModel)
    viewModel { LineTimesViewModel(get()) }
    viewModel { TripTrackerViewModel(get()) }
    viewModelOf(::StopDeparturesViewModel)
    viewModelOf(::MapStopsViewModel)
}
