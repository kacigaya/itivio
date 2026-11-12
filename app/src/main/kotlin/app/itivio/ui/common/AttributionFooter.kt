package app.itivio.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.itivio.R

/**
 * Transitous requires a visible link to its data sources. Map tile attribution (OpenStreetMap)
 * is drawn by the map's own attribution button.
 */
@Composable
fun AttributionFooter(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val url = stringResource(R.string.attribution_url)
    val ignUrl = stringResource(R.string.attribution_ign_url)
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.attribution_routing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
            TextButton(onClick = { uriHandler.openUri(url) }) {
                Text(stringResource(R.string.attribution_sources), style = MaterialTheme.typography.labelMedium)
            }
        }
        TextButton(onClick = { uriHandler.openUri(ignUrl) }) {
            Text(stringResource(R.string.attribution_ign), style = MaterialTheme.typography.labelMedium)
        }
        Text(
            stringResource(R.string.privacy_ign),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}
