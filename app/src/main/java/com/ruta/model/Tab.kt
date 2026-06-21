package com.ruta.model

/**
 * A single browsing tab. The WebView itself lives in the [com.ruta.browser.WebViewPool],
 * keyed by [id]; this is the lightweight, serializable state.
 */
data class Tab(
    val id: String,
    val url: String,
    val title: String = "",
    val faviconUrl: String? = null,
    val profileId: String = DEFAULT_PROFILE_ID,
    val desktopMode: Boolean = false,
    /** True until the tab has navigated to a real page (i.e. still on the launcher). */
    val isNewTab: Boolean = url.isBlank(),
    val canGoBack: Boolean = false,
    val progress: Int = 0,
) {
    companion object {
        const val DEFAULT_PROFILE_ID = ""
    }
}
