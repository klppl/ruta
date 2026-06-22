package io.github.klppl.ruta.browser

import android.content.Context
import android.webkit.WebSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Derives the User-Agent from the installed WebView's default UA rather than hard-coding a
 * Chrome version, then strips the app/"wv" token (which some sites use to refuse logins).
 * Also produces a desktop-mode override for per-site requests.
 */
@Singleton
class UserAgentProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val mobileUserAgent: String by lazy { stripWebViewToken(WebSettings.getDefaultUserAgent(context)) }

    val desktopUserAgent: String by lazy { buildDesktop(mobileUserAgent) }

    fun forMode(desktop: Boolean): String = if (desktop) desktopUserAgent else mobileUserAgent

    /** Removes the "; wv" token and the "Version/x.x Chrome" duplication that marks a WebView. */
    private fun stripWebViewToken(ua: String): String =
        ua.replace("; wv", "")
            .replace(Regex("""Version/\d+\.\d+\s+"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

    /** Turn a mobile UA into a desktop one: drop "Mobile"/"Android" platform hints. */
    private fun buildDesktop(mobile: String): String {
        val chrome = Regex("""Chrome/(\d+)""").find(mobile)?.groupValues?.get(1) ?: "120"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$chrome.0.0.0 Safari/537.36"
    }
}
