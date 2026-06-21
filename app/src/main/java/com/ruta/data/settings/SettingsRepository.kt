package com.ruta.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ruta.ui.theme.ThemeMode
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
        val adBlock = booleanPreferencesKey("ad_block")
        val cosmetic = booleanPreferencesKey("cosmetic")
        val scrubFeed = booleanPreferencesKey("scrub_feed")
        val stripParams = booleanPreferencesKey("strip_params")
        val doubleBack = booleanPreferencesKey("double_back")
        val perSiteProfile = booleanPreferencesKey("per_site_profile")
        val proxyEnabled = booleanPreferencesKey("proxy_enabled")
        val proxyUrl = stringPreferencesKey("proxy_url")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamicColor] ?: true,
            adBlockEnabled = p[Keys.adBlock] ?: true,
            cosmeticFilteringEnabled = p[Keys.cosmetic] ?: true,
            scrubFeedAds = p[Keys.scrubFeed] ?: true,
            stripTrackingParams = p[Keys.stripParams] ?: true,
            doubleBackToExit = p[Keys.doubleBack] ?: true,
            separateProfilePerSite = p[Keys.perSiteProfile] ?: false,
            proxyEnabled = p[Keys.proxyEnabled] ?: false,
            proxyUrl = p[Keys.proxyUrl] ?: "",
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.themeMode] = mode.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.dynamicColor] = value }
    suspend fun setAdBlock(value: Boolean) = edit { it[Keys.adBlock] = value }
    suspend fun setCosmetic(value: Boolean) = edit { it[Keys.cosmetic] = value }
    suspend fun setScrubFeed(value: Boolean) = edit { it[Keys.scrubFeed] = value }
    suspend fun setStripParams(value: Boolean) = edit { it[Keys.stripParams] = value }
    suspend fun setDoubleBack(value: Boolean) = edit { it[Keys.doubleBack] = value }
    suspend fun setPerSiteProfile(value: Boolean) = edit { it[Keys.perSiteProfile] = value }
    suspend fun setProxyEnabled(value: Boolean) = edit { it[Keys.proxyEnabled] = value }
    suspend fun setProxyUrl(value: String) = edit { it[Keys.proxyUrl] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }
}
