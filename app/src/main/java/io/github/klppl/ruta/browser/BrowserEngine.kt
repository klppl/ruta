package io.github.klppl.ruta.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ContextThemeWrapper
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import io.github.klppl.ruta.BuildConfig
import io.github.klppl.ruta.R
import io.github.klppl.ruta.blocking.BlockStatsRepository
import io.github.klppl.ruta.blocking.BlocklistRepository
import io.github.klppl.ruta.blocking.RequestBlocker
import io.github.klppl.ruta.data.settings.AppSettings
import io.github.klppl.ruta.data.settings.SettingsRepository
import io.github.klppl.ruta.data.styles.StyleRepository
import io.github.klppl.ruta.model.ContextTarget
import io.github.klppl.ruta.model.Tab
import io.github.klppl.ruta.profile.ProfileManager
import io.github.klppl.ruta.util.Hosts
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns all WebView creation and configuration. Composables never touch WebView APIs directly;
 * they ask the engine for the active tab's view and forward UI actions here.
 */
@Singleton
class BrowserEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pool: WebViewPool,
    private val requestBlocker: RequestBlocker,
    private val blockStatsRepository: BlockStatsRepository,
    private val blocklistRepository: BlocklistRepository,
    private val contentScriptInjector: ContentScriptInjector,
    private val mediaResolver: MediaResolver,
    private val profileManager: ProfileManager,
    private val userAgentProvider: UserAgentProvider,
    private val settingsRepository: SettingsRepository,
    private val styleRepository: StyleRepository,
) {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // WebViews must be created with an Activity-backed context so the system Autofill
    // Framework (Bitwarden, Google, etc.) and other window-scoped features work. The
    // singleton can't hold an Activity directly, so we point this wrapper at the current
    // Activity while it's alive and fall back to the app context otherwise.
    private val webViewHostContext = MutableContextWrapper(context)

    @Volatile private var currentSettings: AppSettings = AppSettings()
    private val desktopByTab = HashMap<String, Boolean>()
    private val darkByTab = HashMap<String, Boolean>()
    // Popups awaiting their first navigation so we can route external links out to the browser.
    private val pendingPopups = HashMap<String, PendingPopup>()

    /** Set by MainActivity to bridge file-chooser and permission flows to the Activity. */
    var fileChooserHandler: ((android.webkit.ValueCallback<Array<Uri>>, android.webkit.WebChromeClient.FileChooserParams) -> Boolean)? = null
    var permissionHandler: ((android.webkit.PermissionRequest) -> Unit)? = null

    /** Called from MainActivity so new WebViews are bound to the Activity (enables autofill). */
    fun attachActivity(activity: Context) {
        webViewHostContext.baseContext = activity
    }

    fun detachActivity() {
        webViewHostContext.baseContext = context
    }

    init {
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        scope.launch {
            settingsRepository.settings.collect { settings ->
                val proxyChanged = settings.proxyEnabled != currentSettings.proxyEnabled ||
                    settings.proxyUrl != currentSettings.proxyUrl
                currentSettings = settings
                if (proxyChanged) applyProxy(settings)
            }
        }
    }

    fun getOrCreateWebView(tab: Tab, events: TabEvents): WebView {
        val existing = pool.get(tab.id)
        if (existing != null) {
            if (darkByTab[tab.id] == currentSettings.forceDarkWebsites) return existing
            // "Force dark websites" toggled: rebuild this tab in the correctly-themed context.
            pool.remove(tab.id)
        }
        val webView = createWebView(tab, events)
        pool.put(tab.id, webView)
        desktopByTab[tab.id] = tab.desktopMode
        darkByTab[tab.id] = currentSettings.forceDarkWebsites
        if (tab.url.isNotBlank()) webView.loadUrl(tab.url)
        return webView
    }

    fun loadUrl(tabId: String, url: String) {
        pool.get(tabId)?.loadUrl(url)
    }

    fun reload(tabId: String) = pool.get(tabId)?.reload() ?: Unit
    fun stopLoading(tabId: String) = pool.get(tabId)?.stopLoading() ?: Unit
    fun goBack(tabId: String) = pool.get(tabId)?.goBack() ?: Unit
    fun canGoBack(tabId: String): Boolean = pool.get(tabId)?.canGoBack() == true

    fun destroyTab(tabId: String) {
        pool.remove(tabId)
        desktopByTab.remove(tabId)
        darkByTab.remove(tabId)
        pendingPopups.remove(tabId)
    }

    fun setDesktopMode(tabId: String, desktop: Boolean) {
        val webView = pool.get(tabId) ?: return
        desktopByTab[tabId] = desktop
        webView.settings.userAgentString = userAgentProvider.forMode(desktop)
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = desktop
        webView.reload()
    }

    fun requestMediaDownload(tabId: String) {
        val webView = pool.get(tabId) ?: return
        contentScriptInjector.requestMedia(webView)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(tab: Tab, events: TabEvents): WebView {
        val forceDark = currentSettings.forceDarkWebsites
        val webView = WebView(webViewContext(forceDark))
        webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        // Bind the profile before any load so cookies/storage land in the right store.
        profileManager.applyProfile(webView, tab.profileId)

        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = tab.desktopMode
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = userAgentProvider.forMode(tab.desktopMode)
        }

        // Let WebView OAuth flows pass Google's "disallowed_useragent" check.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(webView.settings, emptySet())
        }

        applyForceDark(webView, forceDark)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        contentScriptInjector.install(webView, contentConfig()) { type, data ->
            main.post { handlePageMessage(tab.id, type, data, events) }
        }

        webView.webViewClient = RutaWebViewClient(
            tabId = tab.id,
            requestBlocker = requestBlocker,
            onBlocked = blockStatsRepository::record,
            onStarted = { url, _ ->
                // A deferred popup's first URL decides external-vs-in-app; if it was handed to the
                // system browser, skip the rest (the WebView is being torn down).
                if (!handlePopupFirstUrl(tab.id, url, events)) {
                    events.onProgress(tab.id, 5)
                    if (contentScriptInjector.needsManualInjection) {
                        contentScriptInjector.injectManually(webView, contentConfig())
                    }
                    events.onUrlChanged(tab.id, url, webView.canGoBack())
                }
            },
            onFinished = { url -> handlePageFinished(tab.id, webView, url, events) },
            onUrl = { url, canGoBack -> events.onUrlChanged(tab.id, url, canGoBack) },
            onExternalScheme = { uri -> openExternal(uri) },
            onExternalLink = { current, target, mainFrame, gesture ->
                maybeOpenExternally(current, target, mainFrame, gesture)
            },
        )

        webView.webChromeClient = RutaWebChromeClient(
            onProgress = { p -> events.onProgress(tab.id, p) },
            onTitle = { title -> events.onTitle(tab.id, title) },
            onCloseWindow = { events.onCloseTab(tab.id) },
            onCreateNewWindow = { source, gesture, msg -> handleCreateWindow(tab, events, source, gesture, msg) },
            onFileChooser = { cb, params -> fileChooserHandler?.invoke(cb, params) ?: false },
            onPermission = { req -> permissionHandler?.invoke(req) ?: req.deny() },
        )

        webView.setDownloadListener { url, _, _, _, _ ->
            mediaResolver.downloadUrl(url, webView.url, desktopByTab[tab.id] == true)
        }
        // Dock collapse/reveal is driven by the page script's scroll detection (it catches
        // single-page-app inner scrollers that the native scroll listener misses).

        attachLongPress(webView, tab, events)
        return webView
    }

    private fun attachLongPress(webView: WebView, tab: Tab, events: TabEvents) {
        var lastX = 0f
        var lastY = 0f
        webView.setOnTouchListener { _, ev ->
            lastX = ev.x
            lastY = ev.y
            false
        }
        webView.setOnLongClickListener {
            val hit = webView.hitTestResult
            val extra = hit.extra
            when (hit.type) {
                WebView.HitTestResult.IMAGE_TYPE,
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    // Richer probe (catches <video>); posts a "context" message handled below.
                    contentScriptInjector.probe(webView, lastX, lastY)
                    events.onContextTarget(ContextTarget(tab.id, image = extra))
                    true
                }
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                    contentScriptInjector.probe(webView, lastX, lastY)
                    events.onContextTarget(ContextTarget(tab.id, link = extra))
                    true
                }
                else -> {
                    // Not on a plain image/link. On sites where double-tap means "like" (Instagram,
                    // TikTok), translate the long-press into a synthetic double-tap so the user can
                    // heart posts/messages. Everywhere else, fall back to the context probe.
                    val domain = Hosts.registrable(Hosts.hostOf(webView.url))
                    if (domain != null && domain in LIKE_GESTURE_HOSTS) {
                        synthesizeDoubleTap(webView, lastX, lastY)
                        true
                    } else {
                        contentScriptInjector.probe(webView, lastX, lastY)
                        false
                    }
                }
            }
        }
    }

    /**
     * Dispatches a synthetic double-tap at ([x], [y]) — the native "like"/heart gesture on
     * Instagram and similar apps. Two quick tap (down/up) pairs are fed straight to the WebView's
     * touch pipeline so the page sees real touch events. Coordinates are the WebView's own touch
     * space (captured in [attachLongPress]), so they land exactly where the user pressed.
     */
    private fun synthesizeDoubleTap(webView: WebView, x: Float, y: Float) {
        dispatchTap(webView, x, y, delay = 0)
        dispatchTap(webView, x, y, delay = 160)
    }

    private fun dispatchTap(webView: WebView, x: Float, y: Float, delay: Long) {
        main.postDelayed({
            val t = SystemClock.uptimeMillis()
            MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0).let {
                webView.dispatchTouchEvent(it)
                it.recycle()
            }
            main.postDelayed({
                val up = SystemClock.uptimeMillis()
                MotionEvent.obtain(t, up, MotionEvent.ACTION_UP, x, y, 0).let {
                    webView.dispatchTouchEvent(it)
                    it.recycle()
                }
            }, 40)
        }, delay)
    }

    /**
     * A page asked to open a new window (target="_blank" / window.open). We can't decide here
     * whether the destination is external — on SPA sites (Bluesky, X, …) the link isn't a plain
     * anchor, so the hit-test result is empty and no URL is available yet. Instead we mint the popup
     * WebView but keep it hidden, and let its first committed navigation ([handlePopupFirstUrl])
     * decide: hand cross-site links to the system browser, or surface same-site/auth popups as a
     * real in-app tab. Deferring the tab means the user never sees an in-app flash for links that
     * leave the app.
     */
    private fun handleCreateWindow(
        parent: Tab,
        events: TabEvents,
        source: WebView,
        userGesture: Boolean,
        resultMsg: Message,
    ): Boolean {
        val childId = "tab:" + UUID.randomUUID().toString().take(8)
        val childTab = Tab(
            id = childId,
            url = "",
            profileId = parent.profileId, // popups inherit the parent's profile
            desktopMode = parent.desktopMode,
        )
        val child = createWebView(childTab, events)
        pool.put(childId, child)
        desktopByTab[childId] = parent.desktopMode
        darkByTab[childId] = currentSettings.forceDarkWebsites
        pendingPopups[childId] = PendingPopup(
            parentUrl = source.url,
            profileId = parent.profileId,
            desktopMode = parent.desktopMode,
            userGesture = userGesture,
        )

        val transport = resultMsg.obj as WebView.WebViewTransport
        transport.webView = child
        resultMsg.sendToTarget()
        return true
    }

    /**
     * First committed URL of a deferred popup (see [handleCreateWindow]). Returns true if the popup
     * was consumed (handed to the external browser); false if it's a normal tab or was promoted to
     * an in-app tab, in which case the caller continues normal page-start handling.
     */
    private fun handlePopupFirstUrl(childId: String, url: String, events: TabEvents): Boolean {
        val pending = pendingPopups.remove(childId) ?: return false
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        val external = pending.userGesture && uri != null && (scheme == "http" || scheme == "https") &&
            maybeOpenExternally(pending.parentUrl, uri, isMainFrame = true, userGesture = true)
        if (external) {
            // Opened in the system browser — tear down the throwaway popup once this page-start
            // callback unwinds (destroying a WebView mid-callback is unsafe).
            main.post { pool.remove(childId) }
            return true
        }
        // Staying in-app: surface the tab now that we know the destination.
        events.onOpenPopup(childId, pending.profileId, pending.desktopMode)
        return false
    }

    private data class PendingPopup(
        val parentUrl: String?,
        val profileId: String,
        val desktopMode: Boolean,
        val userGesture: Boolean,
    )

    private fun handlePageMessage(tabId: String, type: String, data: JSONObject?, events: TabEvents) {
        val webView = pool.get(tabId) ?: return
        when (type) {
            "ready" -> {
                val host = data?.optString("host").orEmpty().ifEmpty { Hosts.hostOf(webView.url).orEmpty() }
                pushCosmetic(webView, host)
                pushCustomCss(webView)
            }
            "media" -> mediaResolver.onMediaMessage(data, webView.url, desktopByTab[tabId] == true)
            "scroll" -> events.onChromeVisibility(data?.optBoolean("visible", true) ?: true)
            "context" -> {
                if (data == null) return
                val target = ContextTarget(
                    tabId = tabId,
                    link = data.optString("link").ifBlankNull(),
                    image = data.optString("image").ifBlankNull(),
                    video = data.optString("video").ifBlankNull(),
                    text = data.optString("text").ifBlankNull(),
                )
                if (!target.isEmpty) events.onContextTarget(target)
            }
        }
    }

    private fun handlePageFinished(tabId: String, webView: WebView, url: String, events: TabEvents) {
        events.onProgress(tabId, 100)
        events.onUrlChanged(tabId, url, webView.canGoBack())
        Hosts.hostOf(url)?.let { host ->
            events.onFaviconResolved(tabId, "https://$host/favicon.ico")
            pushCosmetic(webView, host)
        }
        pushCustomCss(webView)
    }

    private fun pushCosmetic(webView: WebView, host: String) {
        if (host.isEmpty() || !currentSettings.cosmeticFilteringEnabled) return
        val rules = blocklistRepository.cosmetic.value
        contentScriptInjector.applyCosmetic(webView, rules.selectorsFor(host))
        contentScriptInjector.applyProcedural(webView, rules.proceduralFor(host))
    }

    private fun pushCustomCss(webView: WebView) {
        val url = webView.url ?: return
        scope.launch {
            val css = withContext(Dispatchers.IO) { styleRepository.cssFor(url) }
            if (css.isNotBlank()) contentScriptInjector.applyCustomCss(webView, css)
        }
    }

    private fun openExternal(uri: Uri): Boolean {
        val intent = when (uri.scheme?.lowercase()) {
            "intent" -> runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()
            else -> Intent(Intent.ACTION_VIEW, uri)
        } ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * Decides whether a clicked link should be handed off to the system browser. Only main-frame,
     * user-initiated navigations that leave the current site's registrable domain qualify; auth
     * domains (Google/Apple/etc. sign-in) are kept in-app so logins don't break.
     */
    private fun maybeOpenExternally(
        currentUrl: String?,
        target: Uri,
        isMainFrame: Boolean,
        userGesture: Boolean,
    ): Boolean {
        if (!currentSettings.openLinksExternally || !isMainFrame || !userGesture) return false
        val targetHost = target.host?.lowercase() ?: return false
        // Outbound-link shims (l.instagram.com, l.facebook.com, out.reddit.com, …) are same-domain
        // redirectors that bounce the user to an external site. They'd pass the same-domain check
        // below and load in-app, so the real destination never leaves the app. Hand the shim URL
        // straight to the system browser and let it follow the redirect.
        if (REDIRECT_SHIMS.any { targetHost == it || targetHost.endsWith(".$it") }) {
            return openInExternalBrowser(target)
        }
        val currentDomain = Hosts.registrable(Hosts.hostOf(currentUrl)) ?: return false
        val targetDomain = Hosts.registrable(targetHost) ?: return false
        if (targetDomain == currentDomain) return false
        if (KEEP_IN_APP.any { targetHost == it || targetHost.endsWith(".$it") }) return false
        return openInExternalBrowser(target)
    }

    /** Opens [uri] in an external browser, excluding ruta itself to avoid looping back in. */
    private fun openInExternalBrowser(uri: Uri): Boolean {
        val pm = context.packageManager
        val self = context.packageName
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val default = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val browser = default?.takeIf { it != self && it != "android" }
            ?: pm.queryIntentActivities(probe, 0).map { it.activityInfo.packageName }.firstOrNull { it != self }
            ?: return false // no other browser available — let the WebView load it
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setPackage(browser)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    /**
     * Context used to create a WebView. Always Activity-backed (via [webViewHostContext]) so
     * autofill works. For force-dark we wrap it in a non-light theme — a [ContextThemeWrapper]
     * keeps the Activity in the base chain (unlike createConfigurationContext), so the page
     * sees prefers-color-scheme: dark while autofill still functions.
     */
    private fun webViewContext(forceDark: Boolean): Context =
        if (forceDark) ContextThemeWrapper(webViewHostContext, R.style.Theme_Ruta_WebDark)
        else webViewHostContext

    private fun applyForceDark(webView: WebView, enabled: Boolean) {
        runCatching {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, enabled)
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(
                    webView.settings,
                    if (enabled) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF,
                )
            }
        }
    }

    private fun applyProxy(settings: AppSettings) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        val controller = ProxyController.getInstance()
        if (!settings.proxyEnabled || settings.proxyUrl.isBlank()) {
            controller.clearProxyOverride({ it.run() }, {})
            return
        }
        val rule = normalizeProxy(settings.proxyUrl)
        val config = ProxyConfig.Builder()
            .addProxyRule(rule)
            .addDirect()
            .build()
        controller.setProxyOverride(config, { it.run() }, {})
    }

    private fun normalizeProxy(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.contains("://")) trimmed else "http://$trimmed"
    }

    private fun contentConfig() = ContentConfig(
        stripTrackingParams = currentSettings.stripTrackingParams,
    )

    private fun String?.ifBlankNull(): String? = if (this.isNullOrBlank()) null else this

    private companion object {
        // Cross-domain navigations to these (sign-in / SSO providers) stay in-app so logging
        // into a wrapped site doesn't get bounced out to the external browser.
        val KEEP_IN_APP = listOf(
            "google.com", "apple.com", "facebook.com", "microsoft.com", "microsoftonline.com",
            "live.com", "okta.com", "auth0.com", "twitter.com", "x.com",
        )

        // Same-domain outbound redirectors social apps wrap external links in. Tapping such a
        // link otherwise stays in-app (same registrable domain) and the redirect lands inside ruta.
        val REDIRECT_SHIMS = listOf(
            "l.instagram.com",
            "l.facebook.com", "lm.facebook.com",
            "out.reddit.com",
            "l.threads.net", "l.threads.com",
            "t.umblr.com",
        )

        // Sites where a long-press should act as a double-tap ("like"/heart) instead of opening
        // the image/link context menu. Registrable domains.
        val LIKE_GESTURE_HOSTS = setOf("instagram.com", "tiktok.com")
    }
}
