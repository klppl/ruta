package io.github.klppl.ruta.browser

import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.klppl.ruta.blocking.ProceduralRule
import org.json.JSONArray
import org.json.JSONObject
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/** Page→native message; [data] is the parsed `data` field of the JSON envelope. */
fun interface PageMessageListener {
    fun onMessage(type: String, data: JSONObject?)
}

data class ContentConfig(
    val stripTrackingParams: Boolean,
)

/**
 * Builds the page-side bundle (assets/content.js) with a settings prelude and injects it at
 * document-start, plus wires the page→native message channel. Prefers the modern
 * WebMessageListener + DocumentStartJavaScript APIs with classic fallbacks.
 */
@Singleton
class ContentScriptInjector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val bundle: String by lazy {
        context.assets.open("content.js").bufferedReader().use { it.readText() }
    }

    private val allowedOrigins = setOf("*")
    private val supportsDocStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    private val supportsWebMessage = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    /** A WebView that lacked DOCUMENT_START_SCRIPT must inject the bundle in onPageStarted. */
    val needsManualInjection: Boolean get() = !supportsDocStart

    private fun prelude(config: ContentConfig): String =
        "window.__rutaConfig=" + JSONObject(
            mapOf(
                "stripTrackingParams" to config.stripTrackingParams,
            ),
        ).toString() + ";"

    /** Installs the document-start script and message bridge on a freshly created WebView. */
    fun install(webView: WebView, config: ContentConfig, listener: PageMessageListener) {
        val script = prelude(config) + "\n" + bundle

        if (supportsWebMessage) {
            WebViewCompat.addWebMessageListener(
                webView,
                "rutaNative",
                allowedOrigins,
            ) { _: WebView, message: WebMessageCompat, _: Uri, isMainFrame: Boolean, _: JavaScriptReplyProxy ->
                // Only the top document may talk to us: a third-party iframe could otherwise
                // spoof media/context messages (e.g. trigger a bogus download).
                if (isMainFrame) dispatch(message.data, listener)
            }
        } else {
            webView.addJavascriptInterface(ClassicBridge(listener), "rutaBridge")
        }

        if (supportsDocStart) {
            WebViewCompat.addDocumentStartJavaScript(webView, script, allowedOrigins)
        }
    }

    /** Fallback injection when DOCUMENT_START_SCRIPT is unavailable. */
    fun injectManually(webView: WebView, config: ContentConfig) {
        if (!needsManualInjection) return
        webView.evaluateJavascript(prelude(config) + "\n" + bundle, null)
    }

    fun applyCosmetic(webView: WebView, selectors: List<String>) {
        if (selectors.isEmpty()) return
        val json = JSONArray(selectors).toString()
        webView.evaluateJavascript("window.__ruta&&window.__ruta.applyCosmetic($json)", null)
    }

    fun applyProcedural(webView: WebView, rules: List<ProceduralRule>) {
        if (rules.isEmpty()) return
        val arr = JSONArray()
        rules.forEach { rule ->
            val steps = JSONArray()
            rule.steps.forEach { step -> steps.put(JSONObject(mapOf("op" to step.op, "arg" to step.arg))) }
            arr.put(JSONObject(mapOf("selector" to rule.selector, "steps" to steps)))
        }
        webView.evaluateJavascript("window.__ruta&&window.__ruta.applyProcedural($arr)", null)
    }

    fun applyCustomCss(webView: WebView, css: String) {
        val js = "window.__ruta&&window.__ruta.applyCustomCss(${JSONObject.quote(css)})"
        webView.evaluateJavascript(js, null)
    }

    fun reconfigure(webView: WebView, config: ContentConfig) {
        val obj = JSONObject(
            mapOf(
                "stripTrackingParams" to config.stripTrackingParams,
            ),
        )
        webView.evaluateJavascript("window.__ruta&&window.__ruta.configure($obj)", null)
    }

    fun requestMedia(webView: WebView) {
        webView.evaluateJavascript("window.__ruta&&window.__ruta.resolveMedia()", null)
    }

    fun probe(webView: WebView, x: Float, y: Float) {
        webView.evaluateJavascript("window.__ruta&&window.__ruta.probeElement($x,$y)", null)
    }

    private fun dispatch(raw: String?, listener: PageMessageListener) {
        if (raw.isNullOrEmpty()) return
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val type = obj.optString("type")
        val data = obj.optJSONObject("data")
        if (type.isNotEmpty()) listener.onMessage(type, data)
    }

    private inner class ClassicBridge(private val listener: PageMessageListener) {
        @JavascriptInterface
        fun postMessage(message: String) = dispatch(message, listener)
    }
}
