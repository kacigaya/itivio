package app.itivio.ui.explore

import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Badge
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import app.itivio.ui.disruptions.DisruptionGroups
import app.itivio.ui.disruptions.disruptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.itivio.R
import app.itivio.domain.Location
import app.itivio.domain.SavedPlaces
import app.itivio.domain.Shortcut
import app.itivio.domain.savedKey
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.MapPanel
import app.itivio.ui.nearby.StopDepartures
import app.itivio.ui.nearby.nearbyDepartures
import app.itivio.ui.planner.Endpoint
import app.itivio.ui.search.LocationInputField
import app.itivio.ui.search.LocationSearchView
import app.itivio.ui.search.SavedPlacesViewModel
import app.itivio.ui.search.iconRes
import app.itivio.ui.search.labelRes
import app.itivio.ui.search.rememberLocationSearchBarState
import app.itivio.ui.search.rememberLocationTextState
import java.time.Instant
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

// Drag handle, search row, shortcut chips, title, and the first departures.
val ExplorePeekHeight = 344.dp

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
    /** Null when the window has its own Disruptions page; the sheet then shows only the departures. */
    disruptions: LoadState<DisruptionGroups>? = null,
    onRetryDisruptions: () -> Unit = {},
    onDestination: (Endpoint) -> Unit,
    onStop: (Location) -> Unit,
    onSettings: () -> Unit,
    places: SavedPlacesViewModel = koinViewModel(),
) {
    val searchBarState = rememberLocationSearchBarState()
    val textState = rememberLocationTextState()
    val scope = rememberCoroutineScope()
    val saved by places.saved.collectAsStateWithLifecycle()
    // Set while the search view picks a place for an empty Home or Work chip, not a destination.
    var assigning by rememberSaveable { mutableStateOf<Shortcut?>(null) }
    LaunchedEffect(searchBarState) {
        snapshotFlow { searchBarState.currentValue }.collect { if (it == SearchBarValue.Collapsed) assigning = null }
    }
    val placeholder = stringResource(
        when (assigning) {
            Shortcut.HOME -> R.string.shortcut_search_home
            Shortcut.WORK -> R.string.shortcut_search_work
            null -> R.string.search_where_to
        },
    )

    MapPanel(wide = wide, peekHeight = ExplorePeekHeight) {
        // Search and locate sit on the sheet, in reach of the thumb. They stay in place while the list scrolls.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 16.dp, top = if (wide) 8.dp else 0.dp, end = 16.dp),
        ) {
            SearchBar(
                state = searchBarState,
                inputField = {
                    LocationInputField(textState, searchBarState, placeholder, onBack = null, trailing = {
                        IconButton(onClick = onSettings) {
                            Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
                        }
                    })
                },
                modifier = Modifier.weight(1f),
            )
            LocateButton(locationGranted, onRequestLocation, onRecenter)
        }
        SavedChips(
            saved = saved,
            onOpen = { onDestination(Endpoint.Place(it)) },
            onAssign = { shortcut ->
                assigning = shortcut
                scope.launch { searchBarState.animateToExpanded() }
            },
        )
        if (disruptions == null) {
            LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                if (locationGranted) {
                    nearbyDepartures(nearby, now, onRetryNearby)
                } else {
                    item(key = "intro") { Intro(onRequestLocation) }
                }
            }
        } else {
            // Two pages in place of a navigation bar. A swipe on the list or a tap on a title changes the page.
            val pager = rememberPagerState { HomePage.entries.size }
            BackHandler(enabled = pager.currentPage != 0) { scope.launch { pager.animateScrollToPage(0) } }
            HomeTitles(
                selected = HomePage.entries[pager.currentPage],
                alerts = (disruptions as? LoadState.Ready)?.value?.let { it.saved.size + it.nearby.size } ?: 0,
                onSelect = { scope.launch { pager.animateScrollToPage(it.ordinal) } },
            )
            HorizontalPager(state = pager, verticalAlignment = Alignment.Top) { page ->
                LazyColumn(contentPadding = PaddingValues(bottom = 8.dp), modifier = Modifier.fillMaxSize()) {
                    when (HomePage.entries[page]) {
                        HomePage.DEPARTURES -> if (locationGranted) {
                            nearbyDepartures(nearby, now, onRetryNearby, title = false)
                        } else {
                            item(key = "intro") { Intro(onRequestLocation) }
                        }
                        HomePage.DISRUPTIONS -> disruptions(disruptions, now, onRetryDisruptions)
                    }
                }
            }
        }
    }

    LocationSearchView(
        searchBarState = searchBarState,
        textFieldState = textState,
        placeholder = placeholder,
        offerMyLocation = false,
        onPick = { endpoint ->
            // The bar shows no destination, so a query left in it would read as a stale search.
            textState.clearText()
            val shortcut = assigning
            if (shortcut != null && endpoint is Endpoint.Place) {
                places.setShortcut(shortcut, endpoint.location)
            } else {
                onDestination(endpoint)
            }
        },
        onDepartures = { location ->
            textState.clearText()
            onStop(location)
        },
        places = places,
    )
}

/** The pages of the home sheet, in swipe order. */
private enum class HomePage(@StringRes val title: Int) {
    DEPARTURES(R.string.home_departures),
    DISRUPTIONS(R.string.disruptions_title),
}

/**
 * The titles of the two home pages, as tabs. The title of the page on screen has the heavy
 * color. A badge on "Disruptions" gives the number of [alerts]. The row scrolls, so large text
 * does not clip a title.
 */
@Composable
private fun HomeTitles(selected: HomePage, alerts: Int, onSelect: (HomePage) -> Unit) {
    val scroll = rememberScrollState()
    // With large text the row is wider than the sheet; keep the title of the page on screen in view.
    LaunchedEffect(selected) { scroll.animateScrollTo(if (selected == HomePage.entries.first()) 0 else scroll.maxValue) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 16.dp).selectableGroup(),
    ) {
        HomePage.entries.forEach { page ->
            val count = if (page == HomePage.DISRUPTIONS && alerts > 0) alerts else null
            val countLabel = count?.let { pluralStringResource(R.plurals.home_alerts, it, it) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .selectable(selected = page == selected, role = Role.Tab, onClick = { onSelect(page) })
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    stringResource(page.title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (page == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                )
                if (count != null) {
                    Badge(Modifier.clearAndSetSemantics { contentDescription = countLabel.orEmpty() }) { Text(count.toString()) }
                }
            }
        }
    }
}

/**
 * One tap to plan a journey to Home, Work, or a favorite. An empty Home or Work chip opens the
 * search to set it. The row keeps its height while storage loads, so the sheet does not jump.
 */
@Composable
private fun SavedChips(saved: SavedPlaces?, onOpen: (Location) -> Unit, onAssign: (Shortcut) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 16.dp),
        // A minimum, not a fixed height: chips grow with large text.
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
    ) {
        if (saved == null) return@LazyRow
        items(Shortcut.entries, key = { "shortcut:$it" }) { shortcut ->
            val location = saved.shortcut(shortcut)
            SavedChip(
                icon = shortcut.iconRes(),
                label = stringResource(if (location != null) shortcut.labelRes() else shortcut.setLabelRes()),
                onClick = { if (location != null) onOpen(location) else onAssign(shortcut) },
            )
        }
        items(saved.favorites, key = { "favorite:${it.savedKey}" }) { location ->
            SavedChip(icon = R.drawable.ic_star_filled, label = location.name, onClick = { onOpen(location) })
        }
    }
}

/** A saved place as a tonal button: bolder than a chip, because it starts a journey with one tap. */
@Composable
private fun SavedChip(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing).widthIn(max = 160.dp),
        )
    }
}

private fun Shortcut.setLabelRes(): Int = when (this) {
    Shortcut.HOME -> R.string.shortcut_set_home
    Shortcut.WORK -> R.string.shortcut_set_work
}

/**
 * A button with the height of the search bar. Its shape and its primary container color set it
 * apart from the bar, so the two do not read as one field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocateButton(locationGranted: Boolean, onRequestLocation: () -> Unit, onRecenter: () -> Unit) {
    FilledIconButton(
        onClick = if (locationGranted) onRecenter else onRequestLocation,
        shape = MaterialTheme.shapes.largeIncreased,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.size(SearchBarDefaults.InputFieldHeight),
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
        Text(stringResource(R.string.explore_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
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
