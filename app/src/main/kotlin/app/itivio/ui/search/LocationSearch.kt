package app.itivio.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.itivio.R
import app.itivio.domain.Location
import app.itivio.domain.LocationKind
import app.itivio.domain.SavedPlaces
import app.itivio.domain.Shortcut
import app.itivio.domain.savedKey
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
 * set. With an empty query it shows Home, Work, favorites, and recent searches; else autocomplete
 * results. [onPick] receives the choice; the caller collapses the bar. A pick from the results or
 * the recent searches goes first in the recent searches.
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
    places: SavedPlacesViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val saved = places.saved.collectAsStateWithLifecycle().value ?: SavedPlaces()

    LaunchedEffect(textFieldState, viewModel) {
        snapshotFlow { textFieldState.text.toString() }.collect(viewModel::onQueryChange)
    }

    fun pick(endpoint: Endpoint) {
        onPick(endpoint)
        scope.launch { searchBarState.animateToCollapsed() }
    }

    fun pickRecent(location: Location) {
        places.addRecent(location)
        pick(Endpoint.Place(location))
    }

    @Composable
    fun PlaceRow(location: Location, icon: Int, title: String, subtitle: String?, onClick: () -> Unit) {
        SuggestionRow(icon, title, subtitle, onClick) { PlaceMenu(location, saved, places) }
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
            if (textFieldState.text.isBlank()) {
                Shortcut.entries.forEach { shortcut ->
                    val location = saved.shortcut(shortcut) ?: return@forEach
                    item(key = "shortcut:$shortcut") {
                        PlaceRow(location, shortcut.iconRes(), stringResource(shortcut.labelRes()), location.name) {
                            pick(Endpoint.Place(location))
                        }
                    }
                }
                if (saved.favorites.isNotEmpty()) {
                    item(key = "favorites") { SectionHeader(stringResource(R.string.saved_favorites)) }
                    items(saved.favorites, key = { "favorite:${it.savedKey}" }) { location ->
                        PlaceRow(location, R.drawable.ic_star_filled, location.name, location.subtitle()) {
                            pick(Endpoint.Place(location))
                        }
                    }
                }
                if (saved.recents.isNotEmpty()) {
                    item(key = "recents") {
                        val clearLabel = stringResource(R.string.saved_clear_recents)
                        SectionHeader(stringResource(R.string.saved_recents)) {
                            TextButton(
                                onClick = places::clearRecents,
                                modifier = Modifier.semantics { contentDescription = clearLabel },
                            ) { Text(stringResource(R.string.search_clear)) }
                        }
                    }
                    items(saved.recents, key = { "recent:${it.savedKey}" }) { location ->
                        PlaceRow(location, R.drawable.ic_history, location.name, location.subtitle()) { pickRecent(location) }
                    }
                }
                return@LazyColumn
            }
            when (val state = suggestions) {
                LoadState.Idle -> item(key = "hint") {
                    Text(
                        stringResource(R.string.search_hint_more),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
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
                    // Geocoding IDs can be blank or repeated. These rows use position keys.
                    items(state.value) { location ->
                        PlaceRow(location, location.iconRes(), location.name, location.subtitle()) { pickRecent(location) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp, top = 8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action()
    }
}

/** Favorite, Home, and Work actions for one place. Each item adds the place, or removes it when it is already there. */
@Composable
private fun PlaceMenu(location: Location, saved: SavedPlaces, places: SavedPlacesViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.place_options, location.name),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val favorite = saved.isFavorite(location)
            DropdownMenuItem(
                text = { Text(stringResource(if (favorite) R.string.place_remove_favorite else R.string.place_add_favorite)) },
                onClick = {
                    open = false
                    places.toggleFavorite(location)
                },
            )
            Shortcut.entries.forEach { shortcut ->
                val current = saved.isShortcut(shortcut, location)
                DropdownMenuItem(
                    text = { Text(stringResource(if (current) shortcut.removeRes() else shortcut.setRes())) },
                    onClick = {
                        open = false
                        places.toggleShortcut(shortcut, location)
                    },
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    icon: Int,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
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
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Text(title, maxLines = 1)
    }
}

@Composable
private fun Location.subtitle(): String = area ?: stringResource(kindLabel())

fun Shortcut.iconRes(): Int = when (this) {
    Shortcut.HOME -> R.drawable.ic_home
    Shortcut.WORK -> R.drawable.ic_work
}

fun Shortcut.labelRes(): Int = when (this) {
    Shortcut.HOME -> R.string.shortcut_home
    Shortcut.WORK -> R.string.shortcut_work
}

private fun Shortcut.setRes(): Int = when (this) {
    Shortcut.HOME -> R.string.place_set_home
    Shortcut.WORK -> R.string.place_set_work
}

private fun Shortcut.removeRes(): Int = when (this) {
    Shortcut.HOME -> R.string.place_remove_home
    Shortcut.WORK -> R.string.place_remove_work
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
