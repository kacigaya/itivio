package app.itivio.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.itivio.domain.AppData
import app.itivio.domain.JourneyOptions
import app.itivio.domain.JourneyOptionsRepository
import app.itivio.domain.PlacesRepository
import app.itivio.domain.SettingsRepository
import app.itivio.domain.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The settings and the data the app keeps. A storage failure, such as a full disk, must not stop
 * the app: a failed read shows the defaults, and a failed write changes nothing.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val journeyOptions: JourneyOptionsRepository,
    private val places: PlacesRepository,
    private val appData: AppData,
) : ViewModel() {
    val theme: StateFlow<ThemeMode> = settings.theme.catch { emit(ThemeMode.SYSTEM) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    /** Null until the stored options are read. */
    val options: StateFlow<JourneyOptions?> = journeyOptions.options.catch { emit(JourneyOptions()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTheme(mode: ThemeMode) = write { settings.setTheme(mode) }

    fun setOptions(options: JourneyOptions) = write { journeyOptions.save(options) }

    fun clearRecents() = write { places.clearRecents() }

    fun deleteAll() = write { appData.deleteAll() }

    private fun write(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Nothing to show: the stored data stays as it was.
            }
        }
    }
}
