package io.github.klppl.ruta.browser

import io.github.klppl.ruta.model.ContextTarget

/** Engine → host (ViewModel) callbacks for a tab's WebView. */
interface TabEvents {
    fun onTitle(tabId: String, title: String)
    fun onProgress(tabId: String, progress: Int)
    fun onUrlChanged(tabId: String, url: String, canGoBack: Boolean)
    fun onFaviconResolved(tabId: String, faviconUrl: String?)
    /** A popup/new-window WebView was minted and registered in the pool under [childTabId]. */
    fun onOpenPopup(childTabId: String, profileId: String, desktopMode: Boolean)
    fun onCloseTab(tabId: String)
    fun onContextTarget(target: ContextTarget)
    /** Page scrolled: false = scrolling down (collapse the dock), true = up/at top (reveal). */
    fun onChromeVisibility(visible: Boolean)
    /** Find-in-page match state: [activeMatch] is 0-based, [matches] the total (0 = none). */
    fun onFindResult(tabId: String, activeMatch: Int, matches: Int)
    /** Whether the page's dominant scroller is at (or near) its top — gates pull-to-refresh. */
    fun onPageAtTop(tabId: String, atTop: Boolean)
}
