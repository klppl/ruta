# content-src

Source for the page-side content script that ships as `app/src/main/assets/content.js`.

The native layer (`ContentScriptInjector`) reads the built asset as a single string and injects
it at document-start. The asset is a plain self-contained IIFE — it has **no imports** so it can
be authored directly or bundled from modules.

## Current state

`app/src/main/assets/content.js` is the authoritative, hand-authored build output. It is checked
in directly so the app builds without a Node toolchain.

## Optional: rebuild with esbuild

If you want to split the script into TypeScript modules (`src/scrub-x.ts`, `src/scrub-instagram.ts`,
`src/media.ts`, …) and bundle them, use the provided pipeline:

```bash
cd content-src
npm install
npm run build      # bundles src/content.ts -> ../app/src/main/assets/content.js
```

The bundle must remain a single IIFE that defines `window.__ruta` with the hooks the native code
calls (`applyCosmetic`, `applyCustomCss`, `resolveMedia`, `probeElement`, `configure`) and posts
messages via `window.rutaNative` / `window.rutaBridge`. Keep `format: "iife"` and `target` low
enough for the System WebView baseline (ES2017).
