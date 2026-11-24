package app.itivio.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.AppData
import app.itivio.domain.JourneyOptions
import app.itivio.domain.JourneyOptionsRepository
import app.itivio.domain.PlacesRepository
import app.itivio.domain.SettingsRepository
import app.itivio.domain.ThemeMode
import app.itivio.ui.common.attempt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.itivio.domain.DisruptionWatch

/**
 * The settings and the data the app keeps. A storage failure, such as a full disk, must not stop
 * the app: a failed read shows the defaults, and a failed write changes nothing.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val journeyOptions: JourneyOptionsRepository,
    private val places: PlacesRepository,
    private val appData: AppData,
    private val watch: DisruptionWatch,
) : ViewModel() {
    val theme: StateFlow<ThemeMode> = settings.theme.catch { emit(ThemeMode.SYSTEM) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    /** Null until the stored options are read. */
    val options: StateFlow<JourneyOptions?> = journeyOptions.options.catch { emit(JourneyOptions()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val disruptionAlerts: StateFlow<Boolean> = settings.disruptionAlerts.catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val severeAlertsOnly: StateFlow<Boolean> = settings.severeAlertsOnly.catch { emit(true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setTheme(mode: ThemeMode) = write { settings.setTheme(mode) }

    fun setDisruptionAlerts(on: Boolean) = write {
        settings.setDisruptionAlerts(on)
        watch.enable(on)
    }

    fun setSevereAlertsOnly(on: Boolean) = write { settings.setSevereAlertsOnly(on) }

    fun setOptions(options: JourneyOptions) = write { journeyOptions.save(options) }

    fun clearRecents() = write { places.clearRecents() }

    fun deleteAll() = write {
        watch.enable(false)
        appData.deleteAll()
    }

    private fun write(action: suspend () -> Unit) {
        viewModelScope.launch {
            attempt { action() }
        }
    }
}
