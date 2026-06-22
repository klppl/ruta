package io.github.klppl.ruta.browser

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.klppl.ruta.blocking.RequestBlocker
import java.io.ByteArrayInputStream

/**
 * Per-tab client. Network blocking happens in [shouldInterceptRequest] for non-main-frame
 * requests; everything else is forwarded to the engine via the supplied callbacks.
 */
class RutaWebViewClient(
    private val tabId: String,
    private val requestBlocker: RequestBlocker,
    private val onStarted: (url: String, favicon: Bitmap?) -> Unit,
    private val onFinished: (url: String) -> Unit,
    private val onUrl: (url: String, canGoBack: Boolean) -> Unit,
    private val onExternalScheme: (Uri) -> Boolean,
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        if (request.isForMainFrame) return null
        val host = request.url.host ?: return null
        return if (requestBlocker.shouldBlock(host)) BLOCKED else null
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "blob" ||
            scheme == "data" || scheme == "javascript"
        ) {
            return false // let the WebView handle it
        }
        return onExternalScheme(uri)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        onStarted(url, favicon)
        onUrl(url, view.canGoBack())
    }

    override fun onPageFinished(view: WebView, url: String) {
        onFinished(url)
        onUrl(url, view.canGoBack())
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        // Single-page apps (X, Instagram) navigate without full page loads.
        onUrl(url, view.canGoBack())
    }

    companion object {
        private val EMPTY = ByteArray(0)
        private val BLOCKED: WebResourceResponse
            get() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(EMPTY))
    }
}
