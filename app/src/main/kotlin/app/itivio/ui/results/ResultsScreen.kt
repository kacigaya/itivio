package app.itivio.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import app.itivio.R
import app.itivio.ui.common.AttributionFooter
import app.itivio.ui.common.FailureState
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.LoadingState
import app.itivio.ui.common.MapPanel
import app.itivio.ui.common.MessageState
import app.itivio.ui.planner.Endpoint
import app.itivio.ui.planner.PlannerState
import app.itivio.ui.planner.RouteSort
import app.itivio.ui.search.LocationSearchView
import app.itivio.ui.search.rememberLocationTextState
import kotlinx.coroutines.launch

val ResultsPeekHeight = 360.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    wide: Boolean,
    state: PlannerState,
    onBack: () -> Unit,
    onOrigin: (Endpoint) -> Unit,
    onDestination: (Endpoint) -> Unit,
    onSwap: () -> Unit,
    onSort: (RouteSort) -> Unit,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    onLater: () -> Unit,
    onRetry: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val originSearch = rememberSearchBarState()
    val destinationSearch = rememberSearchBarState()
    val originText = rememberLocationTextState()
    val destinationText = rememberLocationTextState()

    MapPanel(
        wide = wide,
        peekHeight = ResultsPeekHeight,
        header = {
            EndpointsCard(
                origin = state.origin.label(),
                destination = state.destination?.label().orEmpty(),
                onBack = onBack,
                onEditOrigin = { scope.launch { originSearch.animateToExpanded() } },
                onEditDestination = { scope.launch { destinationSearch.animateToExpanded() } },
                onSwap = onSwap,
            )
        },
    ) {
        ResultsList(state, onSort, onSelect, onOpen, onLater, onRetry)
    }

    LocationSearchView(originSearch, originText, stringResource(R.string.search_from), offerMyLocation = true, onPick = onOrigin)
    LocationSearchView(destinationSearch, destinationText, stringResource(R.string.search_to), offerMyLocation = true, onPick = onDestination)
}

@Composable
private fun Endpoint.label(): String = when (this) {
    Endpoint.MyLocation -> stringResource(R.string.search_my_location)
    is Endpoint.Place -> location.name
}

@Composable
private fun EndpointsCard(
    origin: String,
    destination: String,
    onBack: () -> Unit,
    onEditOrigin: () -> Unit,
    onEditDestination: () -> Unit,
    onSwap: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
            }
            Column(Modifier.weight(1f)) {
                EndpointField(stringResource(R.string.search_from), origin, R.drawable.ic_trip_origin, onEditOrigin)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                EndpointField(stringResource(R.string.search_to), destination, R.drawable.ic_location_on, onEditDestination)
            }
            FilledTonalIconButton(onClick = onSwap) {
                Icon(painterResource(R.drawable.ic_swap_vert), contentDescription = stringResource(R.string.search_swap))
            }
        }
    }
}

@Composable
private fun EndpointField(label: String, value: String, icon: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        }
    }
}

@Composable
private fun ResultsList(
    state: PlannerState,
    onSort: (RouteSort) -> Unit,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    onLater: () -> Unit,
    onRetry: () -> Unit,
) {
    val highlighted = state.highlighted?.id
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "sort") { SortChips(state.sort, onSort) }
        when (val results = state.results) {
            LoadState.Idle, LoadState.Loading -> item(key = "loading") { LoadingState() }
            is LoadState.Failed -> item(key = "error") { FailureState(results.failure, onRetry) }
            is LoadState.Ready -> if (results.value.isEmpty()) {
                item(key = "empty") {
                    MessageState(
                        icon = R.drawable.ic_info,
                        title = stringResource(R.string.results_empty_title),
                        body = stringResource(R.string.results_empty_body),
                        actionLabel = stringResource(R.string.retry),
                        onAction = onRetry,
                    )
                }
            } else {
                items(state.sortedJourneys, key = { it.id }) { journey ->
                    RouteCard(
                        journey = journey,
                        selected = journey.id == highlighted,
                        onClick = {
                            onSelect(journey.id)
                            onOpen(journey.id)
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (state.canLoadLater) {
                    item(key = "later") {
                        OutlinedButton(
                            onClick = onLater,
                            enabled = !state.loadingLater,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.results_later)) }
                    }
                }
            }
        }
        item(key = "attribution") { AttributionFooter() }
    }
}

@Composable
private fun SortChips(selected: RouteSort, onSort: (RouteSort) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(RouteSort.entries) { sort ->
            FilterChip(
                selected = sort == selected,
                onClick = { onSort(sort) },
                label = { Text(stringResource(sort.label())) },
            )
        }
    }
}

private fun RouteSort.label(): Int = when (this) {
    RouteSort.BEST -> R.string.sort_best
    RouteSort.FASTEST -> R.string.sort_fastest
    RouteSort.FEWEST_TRANSFERS -> R.string.sort_fewest_transfers
    RouteSort.LEAST_WALKING -> R.string.sort_least_walking
}
