package app.itivio.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.itivio.R
import app.itivio.domain.Location
import app.itivio.domain.LocationKind
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.LoadingState
import app.itivio.ui.common.FailureState
import app.itivio.ui.common.MessageState
import app.itivio.ui.common.icon
import app.itivio.ui.planner.Endpoint
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * The input field shared by the collapsed bar and the expanded search view. Both must use the
 * same [TextFieldState] so the text survives the expand animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationInputField(
    textFieldState: TextFieldState,
    searchBarState: SearchBarState,
    placeholder: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    SearchBarDefaults.InputField(
        textFieldState = textFieldState,
        searchBarState = searchBarState,
        onSearch = {},
        placeholder = { Text(placeholder, maxLines = 1) },
        leadingIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
            } else {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null)
            }
        },
        trailingIcon = {
            if (textFieldState.text.isNotEmpty() && onBack != null) {
                IconButton(onClick = { textFieldState.clearText() }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        modifier = modifier,
    )
}

/**
 * Full-screen place search. Shows "My location" as the first choice when [offerMyLocation] is
 * set, then autocomplete results. [onPick] receives the choice; the caller collapses the bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSearchView(
    searchBarState: SearchBarState,
    textFieldState: TextFieldState,
    placeholder: String,
    offerMyLocation: Boolean,
    onPick: (Endpoint) -> Unit,
    // Screens with two search views need separate ViewModels in the same store.
    viewModel: SearchViewModel = koinViewModel(key = placeholder),
) {
    val scope = rememberCoroutineScope()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect(viewModel::onQueryChange)
    }

    fun pick(endpoint: Endpoint) {
        onPick(endpoint)
        scope.launch { searchBarState.animateToCollapsed() }
    }

    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = {
            LocationInputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                placeholder = placeholder,
                onBack = { scope.launch { searchBarState.animateToCollapsed() } },
            )
        },
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            if (offerMyLocation) {
                item(key = "my-location") {
                    SuggestionRow(
                        icon = R.drawable.ic_my_location,
                        title = stringResource(R.string.search_my_location),
                        subtitle = null,
                        onClick = { pick(Endpoint.MyLocation) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            when (val state = suggestions) {
                LoadState.Idle -> item(key = "hint") {
                    if (textFieldState.text.isNotBlank()) {
                        Text(
                            stringResource(R.string.search_hint_more),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                LoadState.Loading -> item(key = "loading") { LoadingState() }
                is LoadState.Failed -> item(key = "error") { FailureState(state.failure, onRetry = viewModel::retry) }
                is LoadState.Ready -> if (state.value.isEmpty()) {
                    item(key = "empty") {
                        MessageState(
                            icon = R.drawable.ic_search,
                            title = stringResource(R.string.search_no_results, textFieldState.text.trim()),
                            body = "",
                        )
                    }
                } else {
                    // Geocoding IDs can be blank or repeated. These stateless rows use position keys.
                    items(state.value) { location ->
                        SuggestionRow(
                            icon = location.iconRes(),
                            title = location.name,
                            subtitle = location.area ?: stringResource(location.kindLabel()),
                            onClick = { pick(Endpoint.Place(location)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(icon: Int, title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        leadingContent = {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.size(40.dp),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        },
        supportingContent = subtitle?.let { { Text(it, maxLines = 1) } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Text(title, maxLines = 1)
    }
}

private fun Location.iconRes(): Int = when (kind) {
    LocationKind.STOP -> modes.minByOrNull { it.ordinal }?.icon() ?: R.drawable.ic_directions_bus
    LocationKind.ADDRESS -> R.drawable.ic_location_on
    LocationKind.PLACE -> R.drawable.ic_location_on
}

private fun Location.kindLabel(): Int = when (kind) {
    LocationKind.STOP -> R.string.search_kind_stop
    LocationKind.ADDRESS -> R.string.search_kind_address
    LocationKind.PLACE -> R.string.search_kind_place
}

@Composable
fun rememberLocationTextState(): TextFieldState = rememberTextFieldState()

/**
 * The theme default spring for both directions. The library opens with the slow spring, which is
 * too soft in the expressive scheme for a bar that travels from the sheet to the top of the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberLocationSearchBarState(): SearchBarState {
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    return rememberSearchBarState(animationSpecForExpand = spring, animationSpecForCollapse = spring)
}
