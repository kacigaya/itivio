package app.itivio.ui.stop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.itivio.R
import app.itivio.domain.Departure
import app.itivio.ui.common.FailureState
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.MapPanel
import app.itivio.ui.common.MessageState
import app.itivio.ui.common.Skeleton
import app.itivio.ui.common.clockMinutesUntil
import app.itivio.ui.nearby.DeparturesCard
import app.itivio.ui.nearby.DeparturesCardSkeleton
import java.time.Instant

val StopPeekHeight = 360.dp

/**
 * The next departures from one stop. [onDirections] plans a journey to the stop. [favorite] is
 * null while saved places load; the toggle then waits, so it never shows a wrong state.
 */
@Composable
fun StopScreen(
    wide: Boolean,
    name: String,
    departures: LoadState<List<Departure>>,
    now: Instant,
    favorite: Boolean?,
    onBack: () -> Unit,
    onDirections: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRetry: () -> Unit,
    /** Adds a home screen widget for the stop. Null when the launcher cannot. */
    onAddWidget: (() -> Unit)? = null,
) {
    MapPanel(
        wide = wide,
        peekHeight = StopPeekHeight,
        header = {
            FilledTonalIconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
            }
        },
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
            item(key = "title") {
                Text(
                    name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
                )
            }
            item(key = "actions") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) {
                    Button(onClick = onDirections) {
                        Icon(painterResource(R.drawable.ic_directions), contentDescription = null)
                        Text(stringResource(R.string.stop_directions), modifier = Modifier.padding(start = 8.dp))
                    }
                    FavoriteToggle(favorite, onToggleFavorite)
                    onAddWidget?.let { add ->
                        FilledTonalIconButton(onClick = add, modifier = Modifier.size(48.dp)) {
                            Icon(painterResource(R.drawable.ic_widgets), contentDescription = stringResource(R.string.widget_add))
                        }
                    }
                }
            }
            item(key = "departures-title") {
                Text(
                    stringResource(R.string.stop_departures),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
                )
            }
            when (departures) {
                LoadState.Idle, LoadState.Loading -> item(key = "loading") {
                    Skeleton(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { DeparturesCardSkeleton(rows = 4, header = false) }
                }
                is LoadState.Failed -> item(key = "error") { FailureState(departures.failure, onRetry) }
                is LoadState.Ready -> {
                    // The board refreshes once a minute; a departure from a past minute must not read "Now".
                    val upcoming = departures.value.filter { clockMinutesUntil(now, it.time.best) >= 0 }
                    if (upcoming.isEmpty()) {
                        item(key = "empty") {
                            MessageState(
                                icon = R.drawable.ic_info,
                                title = stringResource(R.string.stop_empty_title),
                                body = stringResource(R.string.stop_empty_body),
                            )
                        }
                    } else {
                        item(key = "departures") { DeparturesCard(upcoming, now, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                    }
                }
            }
        }
    }
}

/** Saved: a filled star on secondary, the checked color of the app. Not saved: an outlined button. */
@Composable
private fun FavoriteToggle(favorite: Boolean?, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    OutlinedIconToggleButton(
        checked = favorite == true,
        onCheckedChange = { onToggle() },
        enabled = favorite != null,
        colors = IconButtonDefaults.outlinedIconToggleButtonColors(
            checkedContainerColor = scheme.secondary,
            checkedContentColor = scheme.onSecondary,
        ),
        modifier = Modifier.size(48.dp),
    ) {
        Icon(painterResource(R.drawable.ic_star_filled), contentDescription = stringResource(R.string.stop_favorite))
    }
}
