# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

ruta is a single-module Android app (Kotlin, Jetpack Compose/Material 3, Hilt, DataStore): a privacy-focused wrapper browser that puts social sites in one app, with ad/tracker blocking, multi-account isolation, and no analytics. minSdk 26, compile/target SDK 35, JDK 17. GPL-3.0-or-later.

## Build & test

There is **no Gradle wrapper jar committed** — `./gradlew` does not work. Use a standalone Gradle (8.11.x) with JDK 17. On the primary dev machine:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # JDK 25 is also installed but too new for Gradle
export ANDROID_HOME=$HOME/android-sdk
~/gradle-8.11.1/bin/gradle :app:assembleDebug --no-daemon --console=plain
~/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --no-daemon --console=plain
# single test class:
~/gradle-8.11.1/bin/gradle :app:testDebugUnitTest --tests "io.github.klppl.ruta.blocking.AbpParserTest" --no-daemon
```

Install & run on a connected device (debug id has a `.debug` suffix):

```bash
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.klppl.ruta.debug/io.github.klppl.ruta.MainActivity
```

Verify UI changes by building, installing, and screenshotting on the device (`adb exec-out screencap -p`). Device screenshots exceed the image-read size limit — downscale first (`ffmpeg -i shot.png -vf scale=800:-1 small.png`); multiply coordinates read from the 800px image by 1.35 for device taps. Compose UI is not readable via `uiautomator dump`.

## Releases

- `versionCode` comes from the `RUTA_VERSION_CODE` env var (defaults to 1 locally). The scheme is the UTC date as an 8-digit int (`20260703`); it must strictly increase per Play upload. Never append a suffix digit — `202606261` would outrank all future dates.
- Preferred path is CI: `gh workflow run release-apk.yml --ref main -f version_code=<YYYYMMDD>` (defaults to today's UTC date if omitted). Repo secrets produce a signed `.aab`, attached to a GitHub Release tagged `build-<n>`.
- Local signed builds: keystore at `~/ruta-release.keystore`, credentials in `~/.ruta-signing.env` (outside the repo — never commit). `versionName` is manual in `app/build.gradle.kts`.
- Play's "native debug symbols" warning is unfixable (the only `.so` files are pre-stripped androidx prebuilts) — ignore it, don't chase it.

## Architecture

Single activity (`MainActivity`) → `RootScreen` (NavHost: home / settings / about, plus the biometric `LockScreen` gate) → `HomeScreen`. **`BrowserViewModel` is the central state holder** — tabs, profiles, settings, block stats, and dock/tab-switcher visibility all flow from it; UI composables are thin.

Three user-facing concepts, each with its own persistence:

- **Sites** — the dashboard grid (`ServiceLauncher`). Built-in services live in `model/AppService.kt` (`Services`); user additions/order in `data/bookmarks/BookmarkRepository`. Once the user has any sites, the grid shows only theirs; a fresh install shows the catalog as suggestions.
- **Tabs** — open pages (`model/Tab`, persisted in `data/tabs/`). The bottom `AccountDock` is the tab switcher (brand glyph per tab, ring color = profile accent). The dock's order is open-order, deliberately independent of the grid order.
- **Profiles** — isolated identities (`profile/ProfileManager`). On API 33+ each non-default profile gets its own `ProfileStore` WebView profile (separate cookies/storage); older devices fall back to the single default profile. Accent colors are derived from the profile id (`accentForProfile`), not stored.

Browser layer (`browser/`): `BrowserEngine` + `WebViewPool` own WebView instances keyed by tab id; `RutaWebViewClient` intercepts requests through the blocking layer; `ContentScriptInjector` injects `app/src/main/assets/content.js` at document-start.

Blocking layer (`blocking/`): `AbpParser` parses ABP-syntax filter lists (EasyList/EasyPrivacy downloaded at runtime, plus bundled `assets/filters/ruta.txt`); `RequestBlocker` does network blocking, `CosmeticRules` element hiding, `BlockStatsRepository` counts, `BlocklistRefreshWorker` refreshes lists daily via WorkManager.

`links/LinkHandling` handles external link interception (the app is a browser-role candidate); `util/UrlSanitizer` strips tracking params on copy.

## Gotchas & conventions

- **`assets/content.js` is a checked-in, hand-authored build output** — the optional esbuild pipeline in `content-src/` is not required (and Node may not be installed). Edit the asset directly; it must stay a single self-contained ES2017 IIFE exposing the `window.__ruta` hooks the native side calls (see `content-src/README.md`).
- UI is Compose-only; no XML layouts. Brand glyphs are drawables mapped in `ui/home/BrandIcons.kt`; unknown hosts fall back to `SiteFavicon` (Google favicon service) then a monogram.
- Settings are flat DataStore prefs: add a field to `AppSettings`, a key + mapping + setter in `SettingsRepository`, and (if user-visible) a row in `SettingsScreen`. Backup/export (`data/backup/`) serializes these prefs — new keys ride along automatically.
- Number formatting uses `util/Format.kt` `formatCount` (comma/`M` style, deliberately locale-independent) — don't introduce `String.format("%,d")`.
- Design ethos: "calm". One way to do a thing, minimal chrome, no dead menu items (the ⋮ sheet gates page actions on `host != null`), quiet one-time hints over persistent labels. Match this when adding UI.
- Unit tests exist only for the filter parser (`AbpParserTest`); there is no lint/format gradle task wired up. Real verification is on-device.
