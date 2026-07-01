package io.github.klppl.ruta.ui.home

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Attaches a pooled [WebView] into the composition, wrapped in a [SwipeRefreshLayout] for
 * pull-to-refresh. A per-instance [FrameLayout] container lets the same WebView move between
 * tabs without "already has a parent" crashes.
 *
 * The refresh gesture engages only when the page is at its top: the WebView's own scroll
 * position covers document-scrolling sites, and [pageAtTop] (content-script reported) covers
 * single-page apps whose feeds scroll an inner element the WebView can't see.
 */
@Composable
fun WebViewHost(
    webView: WebView,
    loading: Boolean,
    pullToRefreshEnabled: Boolean,
    pageAtTop: () -> Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val frame = FrameLayout(context)
            SwipeRefreshLayout(context).apply {
                addView(frame)
                // SwipeRefreshLayout inserts internal children (the spinner); keep a stable
                // handle to our container instead of relying on child indices.
                tag = frame
            }
        },
        update = { swipe ->
            val container = swipe.tag as FrameLayout
            swipe.isEnabled = pullToRefreshEnabled
            swipe.setOnRefreshListener { onRefresh() }
            swipe.setOnChildScrollUpCallback { _, _ ->
                webView.canScrollVertically(-1) || !pageAtTop()
            }
            if (!loading) swipe.isRefreshing = false
            if (webView.parent !== container) {
                (webView.parent as? ViewGroup)?.removeView(webView)
                container.removeAllViews()
                container.addView(webView)
            }
        },
    )
}
