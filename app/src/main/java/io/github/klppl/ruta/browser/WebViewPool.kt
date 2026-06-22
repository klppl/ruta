package io.github.klppl.ruta.browser

import android.view.ViewGroup
import android.webkit.WebView
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds at most one live [WebView] per tab id. Only the active tab's WebView is attached to
 * the Compose hierarchy; the rest stay detached but alive so their JS state survives.
 */
@Singleton
class WebViewPool @Inject constructor() {

    private val webViews = LinkedHashMap<String, WebView>()

    fun get(tabId: String): WebView? = webViews[tabId]

    fun contains(tabId: String): Boolean = webViews.containsKey(tabId)

    fun put(tabId: String, webView: WebView) {
        webViews[tabId] = webView
    }

    fun remove(tabId: String) {
        webViews.remove(tabId)?.let(::destroy)
    }

    fun destroyAll() {
        webViews.values.forEach(::destroy)
        webViews.clear()
    }

    val size: Int get() = webViews.size

    private fun destroy(webView: WebView) {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.webChromeClient = null
        webView.loadUrl("about:blank")
        webView.removeAllViews()
        webView.destroy()
    }
}
