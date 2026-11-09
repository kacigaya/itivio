package app.itivio.ui.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.itivio.R
import app.itivio.domain.TransitMode

@DrawableRes
fun TransitMode.icon(): Int = when (this) {
    TransitMode.WALK -> R.drawable.ic_directions_walk
    TransitMode.BIKE -> R.drawable.ic_directions_bike
    TransitMode.CAR -> R.drawable.ic_directions_car
    TransitMode.BUS, TransitMode.COACH -> R.drawable.ic_directions_bus
    TransitMode.TRAM -> R.drawable.ic_tram
    TransitMode.SUBWAY -> R.drawable.ic_subway
    TransitMode.SUBURBAN, TransitMode.RAIL, TransitMode.HIGHSPEED_RAIL, TransitMode.FUNICULAR -> R.drawable.ic_train
    TransitMode.FERRY -> R.drawable.ic_directions_boat
    TransitMode.AIRPLANE -> R.drawable.ic_flight
    TransitMode.AERIAL_LIFT -> R.drawable.ic_gondola_lift
    TransitMode.ON_DEMAND -> R.drawable.ic_local_taxi
    TransitMode.OTHER -> R.drawable.ic_commute
}

@StringRes
fun TransitMode.label(): Int = when (this) {
    TransitMode.WALK -> R.string.mode_walk
    TransitMode.BIKE -> R.string.mode_bike
    TransitMode.CAR -> R.string.mode_car
    TransitMode.BUS -> R.string.mode_bus
    TransitMode.COACH -> R.string.mode_coach
    TransitMode.TRAM -> R.string.mode_tram
    TransitMode.SUBWAY -> R.string.mode_subway
    TransitMode.SUBURBAN -> R.string.mode_suburban
    TransitMode.RAIL -> R.string.mode_rail
    TransitMode.HIGHSPEED_RAIL -> R.string.mode_highspeed_rail
    TransitMode.FERRY -> R.string.mode_ferry
    TransitMode.AIRPLANE -> R.string.mode_airplane
    TransitMode.AERIAL_LIFT -> R.string.mode_aerial_lift
    TransitMode.FUNICULAR -> R.string.mode_funicular
    TransitMode.ON_DEMAND -> R.string.mode_on_demand
    TransitMode.OTHER -> R.string.mode_other
}
