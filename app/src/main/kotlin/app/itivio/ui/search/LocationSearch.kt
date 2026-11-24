package app.itivio.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SearchBarValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.itivio.R
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import app.itivio.ui.common.BackGestureEasing
import kotlin.coroutines.cancellation.CancellationException
import app.itivio.domain.Location
import app.itivio.domain.LocationKind
import app.itivio.domain.SavedPlaces
import app.itivio.domain.Shortcut
import app.itivio.domain.savedKey
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.FailureState
import app.itivio.ui.common.MessageState
import app.itivio.ui.common.Skeleton
import app.itivio.ui.common.SkeletonBlock
import app.itivio.ui.common.SkeletonText
import app.itivio.ui.common.icon
import app.itivio.ui.planner.Endpoint
import app.itivio.ui.common.shortLabel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import app.itivio.ui.nearby.StopDepartures
import app.itivio.ui.nearby.toLocation
import app.itivio.ui.common.formatDistance

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
    trailing: (@Composable () -> Unit)? = null,
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
            } else {
                trailing?.invoke()
            }
        },
        modifier = modifier,
    )
}

/** How many nearby stops the empty search lists. */
private const val NEARBY_STOPS = 3

/**
 * Full-screen place search. Shows "My location" as the first choice when [offerMyLocation] is
 * set. With an empty query it shows Home, Work, nearby stops, favorites, and recent searches; else autocomplete
 * results. [onPick] receives the choice; the caller collapses the bar. A pick from the results or
 * the recent searches goes first in the recent searches. A non-null [onDepartures] adds a
 * "Show departures" action to the menu of each stop. [nearby] adds the nearest stops of the
 * departures home already shows, so they cost no request.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSearchView(
    searchBarState: SearchBarState,
    textFieldState: TextFieldState,
    placeholder: String,
    offerMyLocation: Boolean,
    onPick: (Endpoint) -> Unit,
    onDepartures: ((Location) -> Unit)? = null,
    anchor: LayoutCoordinates? = null,
    nearby: List<StopDepartures> = emptyList(),
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
    fun PlaceRow(location: Location, icon: Int, title: AnnotatedString, subtitle: String?, shape: Shape, onClick: () -> Unit) {
        SuggestionRow(icon, title, subtitle, shape, onClick = onClick) {
            PlaceMenu(
                location, saved, places,
                onDepartures = onDepartures?.takeIf { location.stopId != null }?.let { show ->
                    {
                        show(location)
                        scope.launch { searchBarState.animateToCollapsed() }
                    }
                },
            )
        }
    }

    // Set while a "Set home" or "Set work" row waits for its place. The pick then fills the
    // shortcut, and the search stays open, so the rider sees the new row.
    var assigning by rememberSaveable { mutableStateOf<Shortcut?>(null) }
    LaunchedEffect(searchBarState) {
        snapshotFlow { searchBarState.currentValue }.collect { if (it == SearchBarValue.Collapsed) assigning = null }
    }
    fun pickPlace(location: Location, recent: Boolean) {
        val shortcut = assigning
        if (shortcut != null) {
            places.setShortcut(shortcut, location)
            assigning = null
            textFieldState.clearText()
        } else if (recent) {
            pickRecent(location)
        } else {
            pick(Endpoint.Place(location))
        }
    }
    val query = textFieldState.text.trim().toString()

    SearchOverlay(
        state = searchBarState,
        anchor = anchor,
        title = placeholder,
        inputField = { fieldModifier ->
            LocationInputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                placeholder = when (assigning) {
                    Shortcut.HOME -> stringResource(R.string.shortcut_search_home)
                    Shortcut.WORK -> stringResource(R.string.shortcut_search_work)
                    null -> placeholder
                },
                onBack = { scope.launch { searchBarState.animateToCollapsed() } },
                modifier = fieldModifier,
            )
        },
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
            val myLocation = offerMyLocation && assigning == null
            if (query.isEmpty()) {
                // My location, Home, and Work are one group. An empty shortcut offers to set it.
                val shortcuts = Shortcut.entries.filter { assigning == null || it == assigning }
                val count = shortcuts.size + if (myLocation) 1 else 0
                if (myLocation) {
                    item(key = "my-location") {
                        SuggestionRow(R.drawable.ic_my_location, stringResource(R.string.search_my_location), null, groupShape(0, count)) {
                            pick(Endpoint.MyLocation)
                        }
                    }
                }
                shortcuts.forEachIndexed { index, shortcut ->
                    val shape = groupShape(index + if (myLocation) 1 else 0, count)
                    val location = saved.shortcut(shortcut)
                    item(key = "shortcut:$shortcut") {
                        if (location != null) {
                            PlaceRow(location, shortcut.iconRes(), AnnotatedString(stringResource(shortcut.labelRes())), location.name, shape) {
                                pickPlace(location, recent = false)
                            }
                        } else {
                            SuggestionRow(
                                icon = shortcut.iconRes(),
                                title = stringResource(shortcut.setLabelRes()),
                                subtitle = stringResource(R.string.search_set_shortcut_hint),
                                shape = shape,
                                outlinedIcon = true,
                            ) { assigning = if (assigning == shortcut) null else shortcut }
                        }
                    }
                }
                // The nearest stops of the departures that home already shows: no new request. A stop
                // that is already a favorite or a recent search has its own row, so it is left out here.
                val listed = (saved.favorites + saved.recents).map { it.id }.toSet()
                val nearbyStops = if (assigning == null) {
                    nearby.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
                        .mapNotNull { stop -> stop.toLocation()?.let { it to stop } }
                        .filter { (location, _) -> location.id !in listed }
                        .take(NEARBY_STOPS)
                } else {
                    emptyList()
                }
                if (nearbyStops.isNotEmpty()) {
                    item(key = "nearby") { SectionHeader(stringResource(R.string.search_nearby_stops)) }
                    itemsIndexed(nearbyStops, key = { index, _ -> "nearby:$index" }) { index, (location, stop) ->
                        val lines = stop.departures.map { it.line.name }.distinct().joinToString(", ")
                        val subtitle = listOfNotNull(lines.ifEmpty { null }, stop.distanceMeters?.let { formatDistance(it) }).joinToString(" · ")
                        PlaceRow(location, location.iconRes(), AnnotatedString(location.name), subtitle, groupShape(index, nearbyStops.size)) {
                            pickPlace(location, recent = false)
                        }
                    }
                }
                if (saved.favorites.isNotEmpty()) {
                    item(key = "favorites") { SectionHeader(stringResource(R.string.saved_favorites)) }
                    itemsIndexed(saved.favorites, key = { _, it -> "favorite:${it.savedKey}" }) { index, location ->
                        PlaceRow(location, R.drawable.ic_star_filled, AnnotatedString(location.name), location.subtitle(), groupShape(index, saved.favorites.size)) {
                            pickPlace(location, recent = false)
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
                    itemsIndexed(saved.recents, key = { _, it -> "recent:${it.savedKey}" }) { index, location ->
                        PlaceRow(location, R.drawable.ic_history, AnnotatedString(location.name), location.subtitle(), groupShape(index, saved.recents.size)) {
                            pickPlace(location, recent = true)
                        }
                    }
                }
                return@LazyColumn
            }
            if (myLocation) {
                item(key = "my-location") {
                    SuggestionRow(R.drawable.ic_my_location, stringResource(R.string.search_my_location), null, groupShape(0, 1)) {
                        pick(Endpoint.MyLocation)
                    }
                }
                item(key = "my-location-gap") { Spacer(Modifier.height(16.dp)) }
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
                LoadState.Loading -> item(key = "loading") {
                    Skeleton { repeat(5) { SuggestionRowSkeleton(wide = it % 2 == 0) } }
                }
                is LoadState.Failed -> item(key = "error") { FailureState(state.failure, onRetry = viewModel::retry) }
                is LoadState.Ready -> if (state.value.isEmpty()) {
                    item(key = "empty") {
                        MessageState(
                            icon = R.drawable.ic_search,
                            title = stringResource(R.string.search_no_results, query),
                            body = "",
                        )
                    }
                } else {
                    // Geocoding IDs can be blank or repeated. These rows use position keys.
                    itemsIndexed(state.value) { index, location ->
                        PlaceRow(
                            location, location.iconRes(), highlightMatch(location.name, query), location.subtitle(),
                            groupShape(index, state.value.size),
                        ) { pickPlace(location, recent = true) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchOverlay(
    state: SearchBarState,
    anchor: LayoutCoordinates?,
    title: String,
    inputField: @Composable (Modifier) -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val open by remember(state) {
        derivedStateOf { state.targetValue == SearchBarValue.Expanded || state.progress > 0f }
    }
    var self by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val insets = WindowInsets.safeDrawing
    val scheme = MaterialTheme.colorScheme
    val from = SearchBarDefaults.colors().containerColor
    val to = scheme.surfaceContainerLow

    LaunchedEffect(state) {
        snapshotFlow { state.targetValue }.collect { if (it == SearchBarValue.Expanded) focus.requestFocus() }
    }
    PredictiveBackHandler(enabled = open) { gesture ->
        try {
            gesture.collect { state.snapTo(1f - SearchBackPreview * BackGestureEasing.transform(it.progress)) }
            scope.launch { state.animateToCollapsed() }
        } catch (cancelled: CancellationException) {
            scope.launch { state.animateToExpanded() }
            throw cancelled
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                if (!open) return@layout layout(0, 0) {}
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .onPlaced { self = it }
            .graphicsLayer {
                val p = state.progress
                val bar = self?.takeIf { it.isAttached }?.let { overlay ->
                    anchor?.takeIf { it.isAttached }?.let { overlay.localBoundingBoxOf(it, clipBounds = false) }
                }
                if (bar == null) {
                    alpha = p
                    translationY = (1f - p) * SearchRise.toPx()
                    return@graphicsLayer
                }
                val top = insets.getTop(this) * (1f - p)
                val width = lerp(bar.width, size.width, p)
                val height = lerp(bar.height, size.height, p)
                translationX = bar.left * (1f - p)
                translationY = (bar.top - insets.getTop(this)) * (1f - p)
                clip = true
                shape = ClipShape(Rect(0f, top, width, top + height), bar.height / 2 * (1f - p))
            }
            .drawBehind { drawRect(lerp(from, to, state.progress)) }
            .pointerInput(Unit) {}
            .semantics { paneTitle = title },
    ) {
        inputField(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .focusRequester(focus),
        )
        Box(
            Modifier
                .weight(1f)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .layout { measurable, constraints ->
                    if (state.progress < SearchListDelay) return@layout layout(constraints.maxWidth, constraints.maxHeight) {}
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .graphicsLayer { alpha = ((state.progress - SearchListDelay) / (1f - SearchListDelay)).coerceIn(0f, 1f) },
        ) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoveredBySearch(vararg states: SearchBarState, content: @Composable () -> Unit) {
    val covered by remember(*states) { derivedStateOf { states.any { it.progress == 1f } } }
    Box(
        if (covered) {
            Modifier.clearAndSetSemantics {}.focusProperties { onEnter = { cancelFocusChange() } }.focusGroup()
        } else {
            Modifier
        },
    ) { content() }
}

private class ClipShape(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
}

private const val SearchListDelay = 0.1f
private const val SearchBackPreview = 0.1f
private val SearchRise = 48.dp

/** The shape of row [index] in a group of [count]: round outer corners, small inner ones. */
private fun groupShape(index: Int, count: Int): Shape {
    val outer = 20.dp
    val inner = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

/** [name] with the first match of [query] in bold, so the eye finds what differs between rows. Case is ignored. */
internal fun highlightMatch(name: String, query: String): AnnotatedString {
    val start = if (query.isBlank()) -1 else name.indexOf(query.trim(), ignoreCase = true)
    if (start < 0) return AnnotatedString(name)
    return buildAnnotatedString {
        append(name)
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, start + query.trim().length)
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
private fun PlaceMenu(location: Location, saved: SavedPlaces, places: SavedPlacesViewModel, onDepartures: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.place_options, location.name),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onDepartures != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.place_show_departures)) },
                    onClick = {
                        open = false
                        onDepartures()
                    },
                )
            }
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
    shape: Shape,
    outlinedIcon: Boolean = false,
    onClick: () -> Unit,
) = SuggestionRow(icon, AnnotatedString(title), subtitle, shape, outlinedIcon, onClick, null)

/**
 * One row of a group: a tile with the icon, the title and the subtitle, and an optional menu. An
 * [outlinedIcon] marks a row that sets something up, such as "Set home", not a place.
 */
@Composable
private fun SuggestionRow(
    icon: Int,
    title: AnnotatedString,
    subtitle: String?,
    shape: Shape,
    outlinedIcon: Boolean = false,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerHigh,
        shape = shape,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp),
    ) {
        ListItem(
            onClick = onClick,
            leadingContent = {
                Surface(
                    color = if (outlinedIcon) Color.Transparent else scheme.secondaryContainer,
                    contentColor = if (outlinedIcon) scheme.onSurfaceVariant else scheme.onSecondaryContainer,
                    border = if (outlinedIcon) BorderStroke(1.dp, scheme.outlineVariant) else null,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.size(40.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            },
            supportingContent = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            trailingContent = trailing,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The placeholder of a [SuggestionRow] with a title and a subtitle. Rows differ in width with [wide]. */
@Composable
private fun SuggestionRowSkeleton(wide: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        // The height and the margins of a two-line list item.
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 32.dp, vertical = 8.dp),
    ) {
        SkeletonBlock(Modifier.size(40.dp))
        Column {
            SkeletonText(MaterialTheme.typography.bodyLarge, Modifier.fillMaxWidth(if (wide) 0.6f else 0.45f))
            SkeletonText(MaterialTheme.typography.bodyMedium, Modifier.fillMaxWidth(if (wide) 0.35f else 0.5f))
        }
    }
}

/** The area, or the kind of place. A stop also names its modes, so stops with one name differ. */
@Composable
private fun Location.subtitle(): String {
    val modes = modes.filter { it.isTransit }.sortedBy { it.ordinal }.map { stringResource(it.shortLabel()) }.distinct()
    return listOfNotNull(area ?: stringResource(kindLabel()).takeIf { modes.isEmpty() }, *modes.toTypedArray()).joinToString(" · ")
}

fun Shortcut.setLabelRes(): Int = when (this) {
    Shortcut.HOME -> R.string.shortcut_set_home
    Shortcut.WORK -> R.string.shortcut_set_work
}

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
