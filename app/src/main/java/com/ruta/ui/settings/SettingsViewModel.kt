package com.ruta.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ruta.blocking.BlocklistRepository
import com.ruta.data.settings.AppSettings
import com.ruta.data.settings.SettingsRepository
import com.ruta.data.styles.StyleRepository
import com.ruta.profile.Profile
import com.ruta.profile.ProfileManager
import com.ruta.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val styleRepository: StyleRepository,
    private val profileManager: ProfileManager,
    blocklistRepository: BlocklistRepository,
) : ViewModel() {

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val globalCss = styleRepository.globalCss
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val blocklistStatus = blocklistRepository.status

    val multiProfileSupported = profileManager.multiProfileSupported

    val profiles = profileManager.userProfiles
        .map { listOf(Profile.Default) + it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(Profile.Default))

    private val blocklist = blocklistRepository

    fun setThemeMode(mode: ThemeMode) = launch { settingsRepository.setThemeMode(mode) }
    fun setDynamicColor(v: Boolean) = launch { settingsRepository.setDynamicColor(v) }
    fun setAdBlock(v: Boolean) = launch { settingsRepository.setAdBlock(v) }
    fun setCosmetic(v: Boolean) = launch { settingsRepository.setCosmetic(v) }
    fun setScrubFeed(v: Boolean) = launch { settingsRepository.setScrubFeed(v) }
    fun setStripParams(v: Boolean) = launch { settingsRepository.setStripParams(v) }
    fun setDoubleBack(v: Boolean) = launch { settingsRepository.setDoubleBack(v) }
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
