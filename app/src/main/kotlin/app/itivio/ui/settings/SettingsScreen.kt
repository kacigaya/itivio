package app.itivio.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.itivio.R
import app.itivio.domain.JourneyOptions
import app.itivio.domain.ThemeMode
import app.itivio.ui.common.Page
import app.itivio.ui.results.JourneyOptionsDialog

/**
 * The settings: theme, language, journey options, stored data, and the way to the about screen.
 * [options] is null until the stored options are read; the row is off until then.
 */
@Composable
fun SettingsScreen(
    theme: ThemeMode,
    options: JourneyOptions?,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onOptions: (JourneyOptions) -> Unit,
    onClearRecents: () -> Unit,
    onDeleteAll: () -> Unit,
    onAbout: () -> Unit,
) {
    var editingOptions by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Page(title = stringResource(R.string.settings_title), onBack = onBack) {
        section("appearance", R.string.settings_appearance) {
            ThemePicker(theme, onTheme)
            // Android 13 and later have a language setting for each app.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SettingsRow(
                    icon = R.drawable.ic_language,
                    title = stringResource(R.string.settings_language),
                    body = stringResource(R.string.settings_language_body),
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        } catch (_: ActivityNotFoundException) {
                            // Some devices have no such screen. The language of the device then applies.
                        }
                    },
                )
            }
        }
        section("journeys", R.string.settings_journeys) {
            SettingsRow(
                icon = R.drawable.ic_tune,
                title = stringResource(R.string.options_title),
                body = stringResource(R.string.settings_options_body),
                enabled = options != null,
                onClick = { editingOptions = true },
            )
        }
        section("data", R.string.settings_data) {
            SettingsRow(
                icon = R.drawable.ic_history,
                title = stringResource(R.string.saved_clear_recents),
                body = stringResource(R.string.settings_clear_recents_body),
                onClick = onClearRecents,
            )
            SettingsRow(
                icon = R.drawable.ic_delete,
                title = stringResource(R.string.settings_delete_all),
                body = stringResource(R.string.settings_delete_all_body),
                color = MaterialTheme.colorScheme.error,
                onClick = { confirmingDelete = true },
            )
        }
        section("about", R.string.about_title) {
            SettingsRow(
                icon = R.drawable.ic_info,
                title = stringResource(R.string.settings_about),
                body = stringResource(R.string.settings_about_body),
                onClick = onAbout,
            )
        }
    }

    if (editingOptions && options != null) {
        JourneyOptionsDialog(
            options = options,
            onDismiss = { editingOptions = false },
            onConfirm = {
                editingOptions = false
                onOptions(it)
            },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            title = { Text(stringResource(R.string.settings_delete_all_question)) },
            text = { Text(stringResource(R.string.settings_delete_all_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDeleteAll()
                }) { Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A titled group of rows on one card. */
internal fun LazyListScope.section(key: String, title: Int, content: @Composable () -> Unit) {
    item(key = key) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp).semantics { heading() },
            )
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.largeIncreased) {
                Column(Modifier.padding(vertical = 4.dp)) { content() }
            }
        }
    }
}

@Composable
internal fun SettingsRow(
    @DrawableRes icon: Int,
    title: String,
    body: String?,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = body?.let { { Text(it, fontWeight = FontWeight.Light) } },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent, headlineColor = color, leadingIconColor = color),
        modifier = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier,
    )
}

@Composable
private fun ThemePicker(theme: ThemeMode, onTheme: (ThemeMode) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_theme), fontWeight = FontWeight.SemiBold) },
        leadingContent = { Icon(painterResource(R.drawable.ic_palette), contentDescription = null) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = mode == theme,
                        onClick = { onTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        label = { Text(stringResource(mode.label()), maxLines = 1) },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}
