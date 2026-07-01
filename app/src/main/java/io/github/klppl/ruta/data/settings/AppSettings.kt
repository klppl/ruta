package io.github.klppl.ruta.data.settings

import io.github.klppl.ruta.ui.theme.ThemeMode

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val forceDarkWebsites: Boolean = true,
    val showAddressBar: Boolean = true,
    val addressBarAtTop: Boolean = false,
    val autoHideDock: Boolean = true,
    val adBlockEnabled: Boolean = true,
    val cosmeticFilteringEnabled: Boolean = true,
    val stripTrackingParams: Boolean = true,
    val doubleBackToExit: Boolean = true,
    val pullToRefresh: Boolean = true,
    val openLinksExternally: Boolean = true,
    val separateProfilePerSite: Boolean = false,
    val proxyEnabled: Boolean = false,
    val proxyUrl: String = "",
)
