# ruta

A clean, minimalist **social browser** for Android. It wraps social-network websites (X,
Instagram, Facebook, TikTok, Reddit, Bluesky, Threads, LinkedIn, Tumblr, Mastodon) in a
Compose + WebView shell with built-in ad/tracker blocking, multi-account isolation, and
in-feed media download.

This is a clean-room implementation. The filter-list parser, content scripts and blocking
engine are original; EasyList / EasyPrivacy are consumed as runtime data assets with
attribution (see the in-app About screen).

<p align="center">
  <img src="screenshot.png" alt="ruta launcher — social service grid, profile chips and dock" width="300">
</p>

## Stack

- Kotlin, single-module Android app (min SDK 26, target SDK 35)
- Jetpack Compose + Material 3 (dynamic color / Material You), edge-to-edge
- `android.webkit.WebView` + **androidx.webkit** (`WebViewCompat`, `ProfileStore`,
  `ProxyController`, document-start scripts, web-message channel)
- Coroutines + StateFlow, Jetpack DataStore (Preferences)
- Hilt for DI, Navigation Compose (Home / Settings / About)
- OkHttp + kotlinx-serialization for fetching filter lists, WorkManager for refresh

## Architecture

```
app/
  RutaApp, MainActivity
  di/            Hilt modules (DataStore, OkHttp)
  ui/
    theme/       Material 3 theme, dynamic color, per-profile accents
    home/        BrowserViewModel, HomeScreen, ServiceLauncher, WebViewHost, UrlEditOverlay
    tabswitcher/ TabSwitcher card grid
    settings/    SettingsScreen, SettingsViewModel, AboutScreen
    components/   AddressPill, ControlsSheet, ContextSheet, ProfileChips
  browser/
    BrowserEngine        creates/configures WebViews, owns clients + bridge
    RutaWebViewClient    network blocking in shouldInterceptRequest
    RutaWebChromeClient  popups, file chooser, permissions, progress/title
    ContentScriptInjector document-start injection + page↔native messaging
    MediaResolver        DownloadManager + MediaStore (blob) saving
    UserAgentProvider    UA derived from installed WebView, desktop override
    WebViewPool          one live WebView per tab id
  blocking/
    AbpParser            clean-room ABP + hostfile parser
    RequestBlocker       longest-suffix host matching (allow wins at deeper label)
    CosmeticRules        ## / #@# element-hiding with domain restriction
    BlocklistRepository  download + cache (ETag / Expires) + parse off-thread
    BlocklistRefreshWorker
  profile/        ProfileManager (ProfileStore multi-account isolation)
  data/           DataStore-backed repositories (settings / styles / tabs)
  util/           UrlSanitizer (tracking-param stripping), Hosts
  assets/content.js  page-side script (feed scrubbing, cosmetic CSS, media, clipboard)
content-src/      esbuild pipeline for the content script (optional)
```

Network blocking is native (in `shouldInterceptRequest`); cosmetic hiding, per-service feed
ad-scrubbing, clipboard cleanup and media resolution run in the injected page script.

## Building

This project targets Android Studio (Ladybug or newer) with the Android SDK installed.

```bash
# from a machine with the Android SDK + a local.properties pointing at it
./gradlew :app:assembleDebug
```

Open the folder in Android Studio and let it sync; it will provision the Gradle wrapper jar
and SDK. The min toolchain: AGP 8.7, Kotlin 2.0.21, JDK 17.

> Note: `gradle/wrapper/gradle-wrapper.jar` is intentionally not committed (binary). Android
> Studio regenerates it on import, or run `gradle wrapper --gradle-version 8.11.1` once.

## Status / roadmap

Implemented in v1: WebView shell + tab pool, native ad/tracker blocking with runtime lists,
cosmetic filtering, per-service feed scrubbing (X / Instagram heuristics), multi-account
profiles, media download (Instagram / TikTok / X + blob fallback), tracking-param stripping,
custom CSS, per-app proxy.

Deferred (v2+): Facebook DASH/progressive extraction, in-WebView Google-OAuth shim,
tab groups / split view, cloud sync.

## License

App code: Apache License 2.0 (see `LICENSE`). EasyList / EasyPrivacy are dual GPLv3 /
CC BY-SA 3.0 data assets used at runtime with attribution.
