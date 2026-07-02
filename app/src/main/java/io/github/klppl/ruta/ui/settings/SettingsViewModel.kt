package io.github.klppl.ruta.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klppl.ruta.data.backup.BackupManager
import io.github.klppl.ruta.blocking.BlocklistRepository
import io.github.klppl.ruta.blocking.FilterListEntry
import io.github.klppl.ruta.blocking.FilterListRepository
import io.github.klppl.ruta.data.settings.AppSettings
import io.github.klppl.ruta.data.settings.SettingsRepository
import io.github.klppl.ruta.data.styles.StyleRepository
import io.github.klppl.ruta.links.LinkHandling
import io.github.klppl.ruta.profile.Profile
import io.github.klppl.ruta.profile.ProfileManager
import io.github.klppl.ruta.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val styleRepository: StyleRepository,
    private val profileManager: ProfileManager,
    private val filterListRepository: FilterListRepository,
    private val linkHandling: LinkHandling,
    private val backupManager: BackupManager,
    blocklistRepository: BlocklistRepository,
) : ViewModel() {

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val globalCss = styleRepository.globalCss
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val blocklistStatus = blocklistRepository.status

    val filterLists: StateFlow<List<FilterListEntry>> = filterListRepository.lists
        .stateIn(viewModelScope, SharingStarted.Eagerly, FilterListRepository.DEFAULTS)

    fun addFilterList(name: String, url: String) = launch { filterListRepository.add(name, url) }
    fun removeFilterList(id: String) = launch { filterListRepository.remove(id) }
    fun setFilterListEnabled(id: String, enabled: Boolean) =
        launch { filterListRepository.setEnabled(id, enabled) }

    val multiProfileSupported = profileManager.multiProfileSupported

    val profiles = profileManager.userProfiles
        .map { listOf(Profile.Default) + it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(Profile.Default))

    private val blocklist = blocklistRepository

    init {
        // Make sure every wrapped site's domain is offered to Android, so the user can manage them
        // all from the system "open by default" screen (link handling is no longer toggled in-app).
        linkHandling.ensureAllEnabled()
    }

    fun openLinkSystemSettings() = linkHandling.openSystemSettings()

    fun setThemeMode(mode: ThemeMode) = launch { settingsRepository.setThemeMode(mode) }
    fun setDynamicColor(v: Boolean) = launch { settingsRepository.setDynamicColor(v) }
    fun setForceDarkWebsites(v: Boolean) = launch { settingsRepository.setForceDarkWebsites(v) }
    fun setShowAddressBar(v: Boolean) = launch { settingsRepository.setShowAddressBar(v) }
    fun setAddressBarAtTop(v: Boolean) = launch { settingsRepository.setAddressBarAtTop(v) }
    fun setAutoHideDock(v: Boolean) = launch { settingsRepository.setAutoHideDock(v) }
    fun setAdBlock(v: Boolean) = launch { settingsRepository.setAdBlock(v) }
    fun setCosmetic(v: Boolean) = launch { settingsRepository.setCosmetic(v) }
    fun setStripParams(v: Boolean) = launch { settingsRepository.setStripParams(v) }
    fun setThirdPartyCookies(v: Boolean) = launch { settingsRepository.setThirdPartyCookies(v) }
    fun setDoubleBack(v: Boolean) = launch { settingsRepository.setDoubleBack(v) }
    fun setPullToRefresh(v: Boolean) = launch { settingsRepository.setPullToRefresh(v) }
    fun setSearchEngine(id: String) = launch { settingsRepository.setSearchEngine(id) }
    fun setOpenLinksExternally(v: Boolean) = launch { settingsRepository.setOpenLinksExternally(v) }
    fun setPerSiteProfile(v: Boolean) = launch { settingsRepository.setPerSiteProfile(v) }
    fun setProxyEnabled(v: Boolean) = launch { settingsRepository.setProxyEnabled(v) }
    fun setProxyUrl(v: String) = launch { settingsRepository.setProxyUrl(v) }

    /** Enables the app lock only when the device can actually authenticate the user. */
    fun setAppLock(enabled: Boolean) {
        if (enabled) {
            val can = BiometricManager.from(appContext).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            if (can != BiometricManager.BIOMETRIC_SUCCESS) {
                toast("Set up a screen lock or fingerprint in Android settings first")
                return
            }
        }
        launch { settingsRepository.setAppLock(enabled) }
    }
    fun setGlobalCss(css: String) = launch { styleRepository.setGlobalCss(css) }
    fun refreshLists() = launch { blocklist.refresh(force = true) }
    fun deleteProfile(id: String) = launch { profileManager.deleteProfile(id) }

    /** Wipes cookies + web storage for [id] ("log out of everything" for that profile). */
    fun clearProfileData(id: String) {
        profileManager.wipe(id)
        toast("Data cleared — open sites sign out on reload")
    }

    fun exportSettings(uri: Uri) = launch {
        toast(if (backupManager.exportTo(uri)) "Backup saved" else "Export failed")
    }

    fun importSettings(uri: Uri) = launch {
        toast(if (backupManager.importFrom(uri)) "Backup restored" else "Import failed — not a ruta backup?")
    }

    private fun toast(msg: String) = Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
