package app.itivio

import app.itivio.ui.common.UserLocation
import app.itivio.ui.planner.PlannerViewModel
import app.itivio.ui.search.SearchViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val uiModule = module {
    single { UserLocation() }
    viewModelOf(::PlannerViewModel)
    viewModelOf(::SearchViewModel)
}
