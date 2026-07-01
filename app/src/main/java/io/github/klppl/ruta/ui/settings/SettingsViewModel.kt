package io.github.klppl.ruta.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.klppl.ruta.blocking.BlocklistRepository
import io.github.klppl.ruta.blocking.FilterListEntry
import io.github.klppl.ruta.blocking.FilterListRepository
import io.github.klppl.ruta.data.settings.AppSettings
import io.github.klppl.ruta.data.settings.SettingsRepository
import io.github.klppl.ruta.data.styles.StyleRepository
import io.github.klppl.ruta.links.LinkHandling
import io.github.klppl.ruta.model.AppService
import io.github.klppl.ruta.profile.Profile
import io.github.klppl.ruta.profile.ProfileManager
import io.github.klppl.ruta.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val styleRepository: StyleRepository,
    private val profileManager: ProfileManager,
    private val filterListRepository: FilterListRepository,
    private val linkHandling: LinkHandling,
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

    /** Built-in sites that can be registered as link handlers. */
    val linkServices: List<AppService> = linkHandling.services

    private val _linkHandlers = MutableStateFlow(linkHandling.snapshot())
    /** Map of service id → whether ruta currently handles that site's links. */
    val linkHandlers: StateFlow<Map<String, Boolean>> = _linkHandlers.asStateFlow()

    fun setLinkHandling(serviceId: String, enabled: Boolean) {
        linkHandling.setEnabled(serviceId, enabled)
        _linkHandlers.value = _linkHandlers.value + (serviceId to enabled)
    }

    /** Re-read state in case the user changed it from the system settings screen. */
    fun refreshLinkHandling() {
        _linkHandlers.value = linkHandling.snapshot()
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
    fun setDoubleBack(v: Boolean) = launch { settingsRepository.setDoubleBack(v) }
    fun setPullToRefresh(v: Boolean) = launch { settingsRepository.setPullToRefresh(v) }
    fun setOpenLinksExternally(v: Boolean) = launch { settingsRepository.setOpenLinksExternally(v) }
    fun setPerSiteProfile(v: Boolean) = launch { settingsRepository.setPerSiteProfile(v) }
    fun setProxyEnabled(v: Boolean) = launch { settingsRepository.setProxyEnabled(v) }
    fun setProxyUrl(v: String) = launch { settingsRepository.setProxyUrl(v) }
    fun setGlobalCss(css: String) = launch { styleRepository.setGlobalCss(css) }
    fun refreshLists() = launch { blocklist.refresh(force = true) }
    fun deleteProfile(id: String) = launch { profileManager.deleteProfile(id) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
