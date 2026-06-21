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
  var SCRUB = cfg.scrubFeedAds !== false;
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

  // -------------------------------------------------------- feed scrubbing ---
  var AD_KEYS = [
    "promotedMetadata", "promoted_metadata", "scribeData", "ad_id", "adMetadata",
    "is_ad", "injected", "sponsored", "is_paid_partnership", "ad_action",
  ];
  function looksLikeAd(obj) {
    if (!obj || typeof obj !== "object") return false;
    for (var i = 0; i < AD_KEYS.length; i++) {
      if (Object.prototype.hasOwnProperty.call(obj, AD_KEYS[i])) return true;
    }
    var eid = obj.entryId || obj.entryid;
    if (typeof eid === "string" && (eid.indexOf("promoted") >= 0 || eid.indexOf("ad-slot") >= 0)) return true;
    return false;
  }

  // Recursively walk JSON: drop ad array entries, capture media. Returns true if mutated.
  function walk(node, depth) {
    if (depth > 40 || !node || typeof node !== "object") return false;
    var changed = false;
    if (Array.isArray(node)) {
      for (var i = node.length - 1; i >= 0; i--) {
        if (SCRUB && looksLikeAd(node[i])) {
          node.splice(i, 1);
          changed = true;
        } else if (walk(node[i], depth + 1)) {
          changed = true;
        }
      }
      return changed;
    }
    // media capture
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
      if (Object.prototype.hasOwnProperty.call(node, k)) {
        if (walk(node[k], depth + 1)) changed = true;
      }
    }
    return changed;
  }

  function interestingUrl(url) {
    if (!url) return false;
    return /graphql|timeline|\/api\/|\/i\/api\/|HomeTimeline|feed|aweme/i.test(url);
  }

  function transformBody(url, text) {
    if (!interestingUrl(url) || !text || text.charAt(0) !== "{") return null;
    var data;
    try {
      data = JSON.parse(text);
    } catch (e) {
      return null;
    }
    var changed = walk(data, 0);
    return changed ? JSON.stringify(data) : null;
  }

  // fetch wrapper
  if (window.fetch) {
    var realFetch = window.fetch;
    window.fetch = function (input, init) {
      return realFetch.call(this, input, init).then(function (resp) {
        var url = typeof input === "string" ? input : input && input.url;
        if (!interestingUrl(url)) return resp;
        return resp
          .clone()
          .text()
          .then(function (text) {
            var t = transformBody(url, text);
            if (t == null) return resp;
            var headers = new Headers(resp.headers);
            return new Response(t, { status: resp.status, statusText: resp.statusText, headers: headers });
          })
          .catch(function () {
            return resp;
          });
      });
    };
  }

  // XHR wrapper — override prototype getters so handler order doesn't matter.
  (function () {
    var proto = XMLHttpRequest.prototype;
    var realOpen = proto.open;
    proto.open = function (method, url) {
      this.__rutaUrl = url;
      return realOpen.apply(this, arguments);
    };
    function override(prop) {
      var desc = Object.getOwnPropertyDescriptor(proto, prop);
      if (!desc || !desc.get) return;
      var realGet = desc.get;
      Object.defineProperty(proto, prop, {
        configurable: true,
        get: function () {
          var raw = realGet.call(this);
          if (this.readyState !== 4 || !this.__rutaUrl) return raw;
          if (this.__rutaCache !== undefined) return this.__rutaCache;
          // Only text responses can be rewritten; leave json/blob/arraybuffer untouched.
          if (typeof raw !== "string") return raw;
          var t = transformBody(this.__rutaUrl, raw);
          if (t == null) {
            this.__rutaCache = raw;
            return raw;
          }
          this.__rutaCache = t;
          return t;
        },
      });
    }
    override("responseText");
    override("response");
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

  // --------------------------------------------------------------- exports ---
  window.__ruta = {
    version: 1,
    applyCosmetic: applyCosmetic,
    applyCustomCss: applyCustomCss,
    resolveMedia: resolveMedia,
    probeElement: probeElement,
    configure: function (next) {
      if (!next) return;
      if (typeof next.scrubFeedAds === "boolean") SCRUB = next.scrubFeedAds;
      if (typeof next.stripTrackingParams === "boolean") STRIP = next.stripTrackingParams;
    },
  };
  post("ready", { host: HOST });
})();
