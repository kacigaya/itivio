package app.itivio.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.itivio.R
import app.itivio.ui.common.AttributionFooter
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.MapPanel
import app.itivio.ui.nearby.StopDepartures
import app.itivio.ui.nearby.nearbyDepartures
import app.itivio.ui.planner.Endpoint
import app.itivio.ui.search.LocationInputField
import app.itivio.ui.search.LocationSearchView
import app.itivio.ui.search.rememberLocationTextState
import java.time.Instant

val ExplorePeekHeight = 232.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    wide: Boolean,
    locationGranted: Boolean,
    nearby: LoadState<List<StopDepartures>>,
    now: Instant,
    onRequestLocation: () -> Unit,
    onRecenter: () -> Unit,
    onRetryNearby: () -> Unit,
    onDestination: (Endpoint) -> Unit,
) {
    val searchBarState = rememberSearchBarState()
    val textState = rememberLocationTextState()
    val placeholder = stringResource(R.string.search_where_to)

    MapPanel(
        wide = wide,
        peekHeight = ExplorePeekHeight,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchBar(
                    state = searchBarState,
                    inputField = { LocationInputField(textState, searchBarState, placeholder, onBack = null) },
                    shadowElevation = 3.dp,
                    modifier = Modifier.weight(1f),
                )
                if (!wide) LocateButton(locationGranted, onRequestLocation, onRecenter)
            }
            if (wide) LocateButton(locationGranted, onRequestLocation, onRecenter)
        },
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
            if (locationGranted) {
                nearbyDepartures(nearby, now, onRetryNearby)
            } else {
                item(key = "intro") { Intro(onRequestLocation) }
            }
            item(key = "attribution") { AttributionFooter() }
        }
    }

    LocationSearchView(
        searchBarState = searchBarState,
        textFieldState = textState,
        placeholder = placeholder,
        offerMyLocation = false,
        onPick = onDestination,
    )
}

@Composable
private fun LocateButton(locationGranted: Boolean, onRequestLocation: () -> Unit, onRecenter: () -> Unit) {
    SmallFloatingActionButton(
        onClick = if (locationGranted) onRecenter else onRequestLocation,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Icon(painterResource(R.drawable.ic_my_location), contentDescription = stringResource(R.string.locate_me))
    }
}

/** Without location the sheet explains the app and asks for access; nearby departures need it. */
@Composable
private fun Intro(onRequestLocation: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(stringResource(R.string.explore_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.explore_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = onRequestLocation, modifier = Modifier.padding(top = 8.dp)) {
            Icon(painterResource(R.drawable.ic_my_location), contentDescription = null)
            Text(stringResource(R.string.allow_location), modifier = Modifier.padding(start = 8.dp))
        }
    }
}
