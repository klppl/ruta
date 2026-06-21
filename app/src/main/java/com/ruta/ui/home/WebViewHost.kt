package com.ruta.ui.home

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Attaches a pooled [WebView] into the composition. A per-instance [FrameLayout] container
 * lets the same WebView move between tabs without "already has a parent" crashes.
 */
@Composable
fun WebViewHost(webView: WebView, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context -> FrameLayout(context) },
        update = { container ->
            if (webView.parent !== container) {
                (webView.parent as? ViewGroup)?.removeView(webView)
                container.removeAllViews()
                container.addView(webView)
            }
        },
    )
}
