package app.itivio.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.itivio.R
import app.itivio.ui.common.Page

/** The version, the license, the credits for data and fonts, and the privacy text. */
@Composable
fun AboutScreen(version: String, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Page(title = stringResource(R.string.about_title), onBack = onBack) {
        item(key = "app") {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
                Text(
                    stringResource(R.string.about_version, version),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        section("license", R.string.about_license) {
            Link(R.drawable.ic_info, R.string.about_license_name, R.string.about_license_body, R.string.about_license_url, uriHandler::openUri)
            Link(R.drawable.ic_open_in_new, R.string.about_source, R.string.about_source_body, R.string.about_source_url, uriHandler::openUri)
        }
        section("data", R.string.about_data) {
            Link(R.drawable.ic_commute, R.string.about_transitous, R.string.about_transitous_body, R.string.attribution_url, uriHandler::openUri)
            Link(R.drawable.ic_map, R.string.about_map, R.string.about_map_body, R.string.about_map_url, uriHandler::openUri)
            Link(R.drawable.ic_info, R.string.about_maplibre, R.string.about_maplibre_body, R.string.about_maplibre_url, uriHandler::openUri)
            Link(R.drawable.ic_location_on, R.string.about_ign, R.string.about_ign_body, R.string.attribution_ign_url, uriHandler::openUri)
        }
        section("design", R.string.about_design) {
            Link(R.drawable.ic_palette, R.string.about_font, R.string.about_font_body, R.string.about_font_url, uriHandler::openUri)
            Link(R.drawable.ic_star_filled, R.string.about_icons, R.string.about_icons_body, R.string.about_icons_url, uriHandler::openUri)
        }
        section("privacy", R.string.about_privacy) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    R.string.about_privacy_none,
                    R.string.about_privacy_requests,
                    R.string.privacy_ign,
                    R.string.about_privacy_device,
                ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

/** A credit that opens its web page. The URLs are fixed resources of the app. */
@Composable
private fun Link(icon: Int, title: Int, body: Int, url: Int, open: (String) -> Unit) {
    val target = stringResource(url)
    SettingsRow(icon = icon, title = stringResource(title), body = stringResource(body), onClick = { open(target) })
}
