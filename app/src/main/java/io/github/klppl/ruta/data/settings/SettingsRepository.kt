package io.github.klppl.ruta.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.klppl.ruta.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val forceDarkWeb = booleanPreferencesKey("force_dark_web")
        val showAddressBar = booleanPreferencesKey("show_address_bar")
        val addressBarTop = booleanPreferencesKey("address_bar_top")
        val autoHideDock = booleanPreferencesKey("auto_hide_dock")
        val adBlock = booleanPreferencesKey("ad_block")
        val cosmetic = booleanPreferencesKey("cosmetic")
        val stripParams = booleanPreferencesKey("strip_params")
        val thirdPartyCookies = booleanPreferencesKey("third_party_cookies")
        val doubleBack = booleanPreferencesKey("double_back")
        val pullToRefresh = booleanPreferencesKey("pull_to_refresh")
        val openLinksExternal = booleanPreferencesKey("open_links_external")
        val perSiteProfile = booleanPreferencesKey("per_site_profile")
        val proxyEnabled = booleanPreferencesKey("proxy_enabled")
        val proxyUrl = stringPreferencesKey("proxy_url")
        val appLock = booleanPreferencesKey("app_lock")
        val searchEngine = stringPreferencesKey("search_engine")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamicColor] ?: true,
            forceDarkWebsites = p[Keys.forceDarkWeb] ?: true,
            showAddressBar = p[Keys.showAddressBar] ?: true,
            addressBarAtTop = p[Keys.addressBarTop] ?: false,
            autoHideDock = p[Keys.autoHideDock] ?: true,
            adBlockEnabled = p[Keys.adBlock] ?: true,
            cosmeticFilteringEnabled = p[Keys.cosmetic] ?: true,
            stripTrackingParams = p[Keys.stripParams] ?: true,
            thirdPartyCookies = p[Keys.thirdPartyCookies] ?: false,
            doubleBackToExit = p[Keys.doubleBack] ?: true,
            pullToRefresh = p[Keys.pullToRefresh] ?: true,
            openLinksExternally = p[Keys.openLinksExternal] ?: true,
            separateProfilePerSite = p[Keys.perSiteProfile] ?: false,
            proxyEnabled = p[Keys.proxyEnabled] ?: false,
            proxyUrl = p[Keys.proxyUrl] ?: "",
            appLock = p[Keys.appLock] ?: false,
            searchEngine = p[Keys.searchEngine] ?: "duckduckgo",
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.themeMode] = mode.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.dynamicColor] = value }
    suspend fun setForceDarkWebsites(value: Boolean) = edit { it[Keys.forceDarkWeb] = value }
    suspend fun setShowAddressBar(value: Boolean) = edit { it[Keys.showAddressBar] = value }
    suspend fun setAddressBarAtTop(value: Boolean) = edit { it[Keys.addressBarTop] = value }
    suspend fun setAutoHideDock(value: Boolean) = edit { it[Keys.autoHideDock] = value }
    suspend fun setAdBlock(value: Boolean) = edit { it[Keys.adBlock] = value }
    suspend fun setCosmetic(value: Boolean) = edit { it[Keys.cosmetic] = value }
    suspend fun setStripParams(value: Boolean) = edit { it[Keys.stripParams] = value }
    suspend fun setThirdPartyCookies(value: Boolean) = edit { it[Keys.thirdPartyCookies] = value }
    suspend fun setDoubleBack(value: Boolean) = edit { it[Keys.doubleBack] = value }
    suspend fun setPullToRefresh(value: Boolean) = edit { it[Keys.pullToRefresh] = value }
    suspend fun setOpenLinksExternally(value: Boolean) = edit { it[Keys.openLinksExternal] = value }
    suspend fun setPerSiteProfile(value: Boolean) = edit { it[Keys.perSiteProfile] = value }
    suspend fun setProxyEnabled(value: Boolean) = edit { it[Keys.proxyEnabled] = value }
    suspend fun setProxyUrl(value: String) = edit { it[Keys.proxyUrl] = value }
    suspend fun setAppLock(value: Boolean) = edit { it[Keys.appLock] = value }
    suspend fun setSearchEngine(value: String) = edit { it[Keys.searchEngine] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }
}
