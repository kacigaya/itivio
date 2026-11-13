package app.itivio.ui.stop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.itivio.R
import app.itivio.domain.Departure
import app.itivio.ui.common.AttributionFooter
import app.itivio.ui.common.FailureState
import app.itivio.ui.common.LoadState
import app.itivio.ui.common.LoadingState
import app.itivio.ui.common.MapPanel
import app.itivio.ui.common.MessageState
import app.itivio.ui.common.clockMinutesUntil
import app.itivio.ui.nearby.DeparturesCard
import java.time.Instant

val StopPeekHeight = 360.dp

/** The next departures from one stop. [onDirections] plans a journey to the stop. */
@Composable
fun StopScreen(
    wide: Boolean,
    name: String,
    departures: LoadState<List<Departure>>,
    now: Instant,
    onBack: () -> Unit,
    onDirections: () -> Unit,
    onRetry: () -> Unit,
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
            item(key = "directions") {
                FilledTonalButton(onClick = onDirections, modifier = Modifier.padding(horizontal = 24.dp)) {
                    Icon(painterResource(R.drawable.ic_directions), contentDescription = null)
                    Text(stringResource(R.string.stop_directions), modifier = Modifier.padding(start = 8.dp))
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
                LoadState.Idle, LoadState.Loading -> item(key = "loading") { LoadingState() }
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
            item(key = "attribution") { AttributionFooter() }
        }
    }
}
