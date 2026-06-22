/*
 * ruta content script (page-side).
 * Built artifact — see content-src/content.ts for the authored source.
 * Injected at document-start. Exposes window.__ruta hooks the native layer calls,
 * installs feed ad-scrubbing (XHR + fetch), cosmetic/custom CSS, clipboard cleanup,
 * media resolution and an element probe for the long-press menu.
 */
(function () {
  "use strict";
  if (window.__ruta) return;

  var cfg = window.__rutaConfig || {};
  var STRIP = cfg.stripTrackingParams !== false;
  var HOST = (location.hostname || "").toLowerCase();

  // ---------------------------------------------------------------- bridge ---
  function post(type, data) {
    try {
      var msg = JSON.stringify({ type: type, data: data == null ? null : data });
      if (window.rutaNative && window.rutaNative.postMessage) window.rutaNative.postMessage(msg);
      else if (window.rutaBridge && window.rutaBridge.postMessage) window.rutaBridge.postMessage(msg);
    } catch (e) {}
  }
  function log() {
    /* no-op in release; uncomment to debug: post("log", Array.prototype.join.call(arguments, " ")); */
  }

  // -------------------------------------------------------------- cosmetic ---
  var cosmeticStyle = null;
  var customStyle = null;

  function ensureStyle(ref, id) {
    if (ref && ref.isConnected) return ref;
    var el = document.createElement("style");
    el.id = id;
    el.type = "text/css";
    (document.head || document.documentElement).appendChild(el);
    return el;
  }

  function applyCosmetic(selectors) {
    if (!selectors || !selectors.length) return;
    cosmeticStyle = ensureStyle(cosmeticStyle, "ruta-cosmetic");
    // One hide rule. Chunk to keep individual selector groups bounded.
    var out = [];
    for (var i = 0; i < selectors.length; i += 1000) {
      out.push(selectors.slice(i, i + 1000).join(",") + "{display:none!important}");
    }
    cosmeticStyle.textContent = out.join("\n");
  }

  function applyCustomCss(css) {
    customStyle = ensureStyle(customStyle, "ruta-custom");
    customStyle.textContent = css || "";
  }

  // Re-insert our style nodes if the page tears them out.
  var observer = new MutationObserver(function () {
    if (cosmeticStyle && !cosmeticStyle.isConnected) (document.head || document.documentElement).appendChild(cosmeticStyle);
    if (customStyle && !customStyle.isConnected) (document.head || document.documentElement).appendChild(customStyle);
  });
  function startObserver() {
    try {
      observer.observe(document.documentElement, { childList: true, subtree: true });
    } catch (e) {}
  }
  if (document.documentElement) startObserver();
  else document.addEventListener("DOMContentLoaded", startObserver);

  // ------------------------------------------------------------- clipboard ---
  var TRACK_PARAMS = /^(utm_|_branch)|^(fbclid|gclid|igsh|igshid|xmt|si|mc_eid|mc_cid|ref_src|ref_url|s|t|rdt|correlation_id|share_id)$/i;
  function cleanUrl(u) {
    try {
      var url = new URL(u);
      var del = [];
      url.searchParams.forEach(function (_v, k) {
        if (TRACK_PARAMS.test(k)) del.push(k);
      });
      del.forEach(function (k) {
        url.searchParams.delete(k);
      });
      return url.toString();
    } catch (e) {
      return u;
    }
  }
  function cleanText(text) {
    if (typeof text !== "string") return text;
    return text.replace(/https?:\/\/[^\s]+/g, function (m) {
      return cleanUrl(m);
    });
  }
  if (STRIP && navigator.clipboard && navigator.clipboard.writeText) {
    var realWrite = navigator.clipboard.writeText.bind(navigator.clipboard);
    navigator.clipboard.writeText = function (text) {
      return realWrite(cleanText(text));
    };
  }

  // ------------------------------------------------------ media capture buf ---
  var captured = []; // {url, kind, bitrate}
  function remember(url, kind, bitrate) {
    if (!url || typeof url !== "string") return;
    var decoded = url.replace(/\\u0026/g, "&").replace(/\\u002F/gi, "/");
    captured.push({ url: decoded, kind: kind || "video", bitrate: bitrate || 0 });
    if (captured.length > 200) captured.shift();
  }

  // ----------------------------------------------- media capture (read-only) ---
  // IMPORTANT: we never rewrite response bodies. Meta/X APIs use 64-bit integer IDs that
  // exceed JS's safe integer range, so JSON.parse->JSON.stringify silently corrupts them and
  // breaks the app ("page can't load"). We only read a throwaway copy to harvest media URLs
  // for the download feature; promoted-post hiding is left to cosmetic filters.
  function interestingUrl(url) {
    if (!url) return false;
    return /graphql|timeline|\/api\/|\/i\/api\/|HomeTimeline|feed|aweme/i.test(url);
  }

  function scanMedia(node, depth) {
    if (depth > 40 || !node || typeof node !== "object") return;
    if (Array.isArray(node)) {
      for (var i = 0; i < node.length; i++) scanMedia(node[i], depth + 1);
      return;
    }
    if (node.video_info && node.video_info.variants) {
      node.video_info.variants.forEach(function (v) {
        if (v && v.content_type === "video/mp4") remember(v.url, "video", v.bitrate || 0);
      });
    }
    if (node.video_versions && node.video_versions.length) {
      remember(node.video_versions[0].url, "video", node.video_versions[0].width || 0);
    }
    if (typeof node.playAddr === "string") remember(node.playAddr, "video", 1);
    if (typeof node.downloadAddr === "string") remember(node.downloadAddr, "video", 2);
    for (var k in node) {
      if (Object.prototype.hasOwnProperty.call(node, k)) scanMedia(node[k], depth + 1);
    }
  }

  // Parse a throwaway copy purely to capture media URLs. Never returns/replaces anything.
  function captureMedia(url, text) {
    if (!interestingUrl(url) || !text || text.charAt(0) !== "{") return;
    var data;
    try {
      data = JSON.parse(text);
    } catch (e) {
      return;
    }
    try {
      scanMedia(data, 0);
    } catch (e) {}
  }

  // fetch: read a clone for media capture, always return the original untouched response.
  if (window.fetch) {
    var realFetch = window.fetch;
    window.fetch = function (input, init) {
      return realFetch.call(this, input, init).then(function (resp) {
        var url = typeof input === "string" ? input : input && input.url;
        if (interestingUrl(url)) {
          resp.clone().text().then(function (text) { captureMedia(url, text); }).catch(function () {});
        }
        return resp;
      });
    };
  }

  // XHR: observe completed responses for media capture; never modify the response.
  (function () {
    var proto = XMLHttpRequest.prototype;
    var realOpen = proto.open;
    var realSend = proto.send;
    proto.open = function (method, url) {
      this.__rutaUrl = url;
      return realOpen.apply(this, arguments);
    };
    proto.send = function () {
      var xhr = this;
      if (xhr.__rutaUrl && interestingUrl(xhr.__rutaUrl)) {
        xhr.addEventListener("load", function () {
          try {
            if (xhr.responseType === "" || xhr.responseType === "text") {
              captureMedia(xhr.__rutaUrl, xhr.responseText);
            }
          } catch (e) {}
        });
      }
      return realSend.apply(this, arguments);
    };
  })();

  // ------------------------------------------------------- media resolution ---
  function decodeUrl(u) {
    return u ? u.replace(/\\u0026/g, "&").replace(/\\u002F/gi, "/").replace(/&amp;/g, "&") : u;
  }
  function bestVideo() {
    var best = null;
    captured.forEach(function (m) {
      if (!best || m.bitrate > best.bitrate) best = m;
    });
    return best ? best.url : null;
  }
  function scanScripts(regex) {
    var scripts = document.querySelectorAll("script");
    for (var i = 0; i < scripts.length; i++) {
      var m = regex.exec(scripts[i].textContent || "");
      if (m) return decodeUrl(m[1]);
    }
    return null;
  }
  function visibleVideoSrc() {
    var vids = document.querySelectorAll("video");
    var chosen = null;
    for (var i = 0; i < vids.length; i++) {
      var v = vids[i];
      var src = v.currentSrc || v.src;
      if (!src) continue;
      if (!v.paused) return src;
      if (!chosen) chosen = src;
    }
    return chosen;
  }
  function resolveByHost() {
    if (/instagram\.com/.test(HOST)) {
      return scanScripts(/"video_versions":\[\{"[^}]*?"url":"([^"]+)"/) || visibleVideoSrc();
    }
    if (/tiktok\.com/.test(HOST)) {
      return (
        scanScripts(/"(?:playAddr|downloadAddr)":"([^"]+)"/) ||
        bestVideo() ||
        visibleVideoSrc()
      );
    }
    if (/x\.com|twitter\.com/.test(HOST)) {
      return bestVideo() || visibleVideoSrc();
    }
    return bestVideo() || visibleVideoSrc();
  }

  function emitMedia(url) {
    if (!url) {
      post("media", { ok: false });
      return;
    }
    if (url.indexOf("blob:") === 0) {
      fetch(url)
        .then(function (r) {
          return r.blob();
        })
        .then(function (blob) {
          var reader = new FileReader();
          reader.onloadend = function () {
            var s = String(reader.result);
            var comma = s.indexOf(",");
            post("media", {
              ok: true,
              kind: "blob",
              mime: blob.type || "video/mp4",
              base64: comma >= 0 ? s.substring(comma + 1) : s,
            });
          };
          reader.readAsDataURL(blob);
        })
        .catch(function () {
          post("media", { ok: false });
        });
    } else {
      post("media", { ok: true, kind: "url", url: url });
    }
  }

  function resolveMedia() {
    try {
      emitMedia(resolveByHost());
    } catch (e) {
      post("media", { ok: false });
    }
  }

  // ----------------------------------------------------------- element probe ---
  function probeElement(px, py) {
    var dpr = window.devicePixelRatio || 1;
    var x = px / dpr;
    var y = py / dpr;
    var el = document.elementFromPoint(x, y);
    var result = { link: null, image: null, video: null, text: null };
    var node = el;
    var hops = 0;
    while (node && hops < 8) {
      var tag = (node.tagName || "").toLowerCase();
      if (!result.image && tag === "img" && node.src) result.image = node.src;
      if (!result.video && tag === "video") result.video = node.currentSrc || node.src || "blob";
      if (!result.link && tag === "a" && node.href) {
        result.link = node.href;
        result.text = (node.textContent || "").trim().slice(0, 120);
      }
      node = node.parentElement;
      hops++;
    }
    post("context", result);
  }

  // ------------------------------------------------------- chrome on scroll ---
  // Report scroll direction so the native dock can collapse on scroll-down / reveal on
  // scroll-up. A capture-phase listener catches single-page-app inner scrollers (X,
  // Instagram) that don't move the document's own scroll position.
  (function setupScrollWatch() {
    var tops = new WeakMap();
    var accum = 0;
    var chromeVisible = true;
    function setChrome(v) {
      if (v !== chromeVisible) {
        chromeVisible = v;
        post("scroll", { visible: v });
      }
    }
    function onScroll(e) {
      var t = e.target || window;
      var isDoc = t === window || t === document || t === document.documentElement || t === document.body;
      var y, scrollable;
      if (isDoc) {
        var se = document.scrollingElement || document.documentElement;
        y = window.scrollY || (se ? se.scrollTop : 0);
        scrollable = se ? se.scrollHeight - se.clientHeight : 9999;
      } else {
        y = t.scrollTop || 0;
        scrollable = (t.scrollHeight || 0) - (t.clientHeight || 0);
      }
      // Ignore elements that aren't really scrollable (avoids fixed bars firing spurious events).
      if (scrollable < 80) return;
      var key = isDoc ? document : t;
      var prev = tops.has(key) ? tops.get(key) : y;
      tops.set(key, y);
      var dy = y - prev;
      if (Math.abs(dy) < 2 || Math.abs(dy) > 1500) return; // noise / jumps (tab switch)
      if (y <= 6) {
        accum = 0;
        setChrome(true);
        return;
      }
      if ((dy > 0) !== (accum > 0)) accum = 0;
      accum += dy;
      if (accum > 56) setChrome(false);
      else if (accum < -56) setChrome(true);
    }
    window.addEventListener("scroll", onScroll, true);
  })();

  // --------------------------------------------------------------- exports ---
  window.__ruta = {
    version: 1,
    applyCosmetic: applyCosmetic,
    applyCustomCss: applyCustomCss,
    resolveMedia: resolveMedia,
    probeElement: probeElement,
    configure: function (next) {
      if (!next) return;
      if (typeof next.stripTrackingParams === "boolean") STRIP = next.stripTrackingParams;
    },
  };
  post("ready", { host: HOST });
})();
