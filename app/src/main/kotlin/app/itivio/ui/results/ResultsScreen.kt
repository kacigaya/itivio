package app.itivio.ui.results

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import app.itivio.R
import app.itivio.domain.JourneyOptions
import app.itivio.ui.common.FailureState
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.MapPanel
import app.itivio.ui.common.MessageState
import app.itivio.ui.common.Skeleton
import app.itivio.ui.planner.Endpoint
import app.itivio.ui.planner.Paging
import app.itivio.ui.planner.PlannerState
import app.itivio.ui.planner.RouteSort
import app.itivio.ui.search.LocationSearchView
import app.itivio.ui.search.rememberLocationSearchBarState
import app.itivio.ui.search.rememberLocationTextState
import java.time.Instant
import kotlinx.coroutines.launch

val ResultsPeekHeight = 360.dp

private val ListMargin = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    wide: Boolean,
    state: PlannerState,
    onBack: () -> Unit,
    onOrigin: (Endpoint) -> Unit,
    onDestination: (Endpoint) -> Unit,
    onSwap: () -> Unit,
    onTime: (Instant?, Boolean) -> Unit,
    onOptions: (JourneyOptions) -> Unit,
    onSort: (RouteSort) -> Unit,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onRetry: () -> Unit,
) {
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var pickingOptions by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val originSearch = rememberLocationSearchBarState()
    val destinationSearch = rememberLocationSearchBarState()
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
        ResultsList(
            state,
            onPickTime = { pickingTime = true },
            onPickOptions = { pickingOptions = true },
            onSort, onSelect, onOpen, onEarlier, onLater, onRetry,
        )
    }

    if (pickingTime) {
        TravelTimeDialog(
            time = state.time,
            arriveBy = state.arriveBy,
            onDismiss = { pickingTime = false },
            onConfirm = { time, arriveBy ->
                pickingTime = false
                onTime(time, arriveBy)
            },
        )
    }

    if (pickingOptions) {
        JourneyOptionsDialog(
            options = state.options,
            onDismiss = { pickingOptions = false },
            onConfirm = { options ->
                pickingOptions = false
                onOptions(options)
            },
        )
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
    onPickTime: () -> Unit,
    onPickOptions: () -> Unit,
    onSort: (RouteSort) -> Unit,
    onSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
    onRetry: () -> Unit,
) {
    val highlighted = state.highlighted?.id
    LazyColumn(
        contentPadding = PaddingValues(horizontal = ListMargin, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "filters") { FilterChips(state, onPickTime, onPickOptions, onSort) }
        when (val results = state.results) {
            LoadState.Idle, LoadState.Loading -> item(key = "loading") {
                Skeleton(spacing = 8.dp) { repeat(3) { RouteCardSkeleton() } }
            }
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
                if (state.canLoadEarlier) {
                    item(key = "earlier") {
                        PageButton(R.drawable.ic_arrow_upward, R.string.results_earlier, state.loadingPage, Paging.EARLIER, onEarlier)
                    }
                }
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
                        PageButton(R.drawable.ic_arrow_downward, R.string.results_later, state.loadingPage, Paging.LATER, onLater)
                    }
                }
            }
        }
    }
}

/**
 * Loads the [page] of journeys before or after the list. A text button, so the route cards stay
 * the main content. The two buttons are disabled while a page loads, and the button of the page
 * in [loading] shows a loading indicator in place of its arrow.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageButton(@DrawableRes icon: Int, @StringRes label: Int, loading: Paging?, page: Paging, onClick: () -> Unit) {
    val busy = loading == page
    val busyLabel = stringResource(R.string.loading)
    TextButton(
        onClick = onClick,
        enabled = loading == null,
        modifier = Modifier.fillMaxWidth().semantics { if (busy) stateDescription = busyLabel },
    ) {
        // The indicator is as large as the arrow, so the label does not move.
        if (busy) {
            LoadingIndicator(Modifier.size(ButtonDefaults.IconSize).clearAndSetSemantics {})
        } else {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        }
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(label))
    }
}

/**
 * The travel time and the options come first: they change the results, while sorting only
 * reorders them. The row scrolls to the two screen edges, past the margin of the list.
 */
@Composable
private fun FilterChips(state: PlannerState, onPickTime: () -> Unit, onPickOptions: () -> Unit, onSort: (RouteSort) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = ListMargin),
        modifier = Modifier.layout { measurable, constraints ->
            val margin = ListMargin.roundToPx()
            val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + margin * 2))
            layout(placeable.width - margin * 2, placeable.height) { placeable.place(-margin, 0) }
        },
    ) {
        item(key = "time") { TravelTimeChip(state.time, state.arriveBy, onPickTime) }
        item(key = "options") { JourneyOptionsChip(state.options, onPickOptions, enabled = state.optionsLoaded) }
        item(key = "divider") { VerticalDivider(Modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant) }
        items(RouteSort.entries) { sort ->
            FilterButton(stringResource(sort.label()), checked = sort == state.sort, onClick = { onSort(sort) })
        }
    }
}

/**
 * A filter of the results as a tonal button, the same as the saved places on home. [checked]
 * shows that the filter is on, or not at its default.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FilterButton(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
) {
    FilledTonalToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        enabled = enabled,
        contentPadding = if (leadingIcon != null) ButtonDefaults.ButtonWithIconContentPadding else ButtonDefaults.ContentPadding,
        modifier = modifier,
    ) {
        if (leadingIcon != null) {
            Icon(painterResource(leadingIcon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (trailingIcon != null) {
            Icon(painterResource(trailingIcon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        }
    }
}

private fun RouteSort.label(): Int = when (this) {
    RouteSort.BEST -> R.string.sort_best
    RouteSort.FASTEST -> R.string.sort_fastest
    RouteSort.FEWEST_TRANSFERS -> R.string.sort_fewest_transfers
    RouteSort.LEAST_WALKING -> R.string.sort_least_walking
}
