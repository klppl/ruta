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

  // ----------------------------------------------- procedural cosmetics ---
  // uBlock-style procedural filters from the lists (parsed natively into {selector, steps}). CSS
  // can't express these, so we evaluate them page-side: start from `selector`, then apply each
  // step in order — text filters (:has-text/:contains), ancestor traversal (:upward), and a final
  // action (hide by default, or :style / :remove). Covers e.g. the Meta "open in app" modal
  // (matched by CTA text), Facebook Reels/Suggested collapsing, and Reddit AutoModerator removal.
  var procRules = []; // [{ sel: string, steps: [{op, ...}] }]

  function compileNeedle(text) {
    var m = /^\/(.*)\/([a-z]*)$/i.exec(text || "");
    if (m) {
      try {
        var re = new RegExp(m[1], m[2]);
        return function (s) { return re.test(s || ""); };
      } catch (e) {}
    }
    var lower = String(text || "").toLowerCase();
    return function (s) { return String(s || "").toLowerCase().indexOf(lower) >= 0; };
  }

  function bgAlpha(bg) {
    var m = /rgba?\(([^)]+)\)/.exec(bg || "");
    if (!m) return 1;
    var p = m[1].split(",");
    return p.length > 3 ? parseFloat(p[3]) : 1;
  }

  // A modal's dim backdrop is usually a separate portal element — a fixed, viewport-covering,
  // semi-transparent layer with no role or text — so hiding the dialog card leaves the page
  // dimmed and click-blocked. When a procedural rule removes something, drop any such scrim too.
  function removeModalScrims() {
    var w = window.innerWidth, h = window.innerHeight;
    var nodes = document.querySelectorAll("body *");
    for (var i = 0; i < nodes.length; i++) {
      var e = nodes[i];
      if (e.getAttribute("data-ruta-proc") === "1") continue;
      var cs = window.getComputedStyle(e);
      if (cs.display === "none") continue;
      if (cs.position !== "fixed" && cs.position !== "absolute") continue;
      var a = bgAlpha(cs.backgroundColor);
      if (!(a > 0.02 && a < 1)) continue; // only semi-transparent dim layers
      var r = e.getBoundingClientRect();
      if (r.width < w * 0.85 || r.height < h * 0.7) continue;
      if ((e.textContent || "").trim().length >= 2) continue; // scrims carry no content
      e.setAttribute("data-ruta-proc", "1");
      e.style.setProperty("display", "none", "important");
    }
  }

  // :upward(n) -> n-th ancestor; :upward(selector) -> nearest matching ancestor (excluding self).
  function upwardOf(el, arg) {
    if (/^[0-9]+$/.test(arg)) {
      var n = parseInt(arg, 10);
      while (n-- > 0 && el) el = el.parentElement;
      return el;
    }
    if (!el.parentElement) return null;
    try {
      return el.parentElement.closest(arg);
    } catch (e) {
      return null;
    }
  }

  function sweepProcedural() {
    if (!procRules.length) return;
    var removed = false;
    for (var i = 0; i < procRules.length; i++) {
      var r = procRules[i];
      var els;
      try {
        els = [].slice.call(document.querySelectorAll(r.sel));
      } catch (e) {
        continue;
      }
      var action = "hide";
      var styleArg = null;
      for (var s = 0; s < r.steps.length; s++) {
        var step = r.steps[s];
        if (step.op === "filter") {
          els = filterByText(els, step.test);
        } else if (step.op === "upward") {
          els = mapUpward(els, step.arg);
        } else if (step.op === "style") {
          action = "style";
          styleArg = step.arg;
        } else if (step.op === "remove") {
          action = "remove";
        }
      }
      for (var e = 0; e < els.length; e++) {
        var el = els[e];
        if (!el || el.getAttribute("data-ruta-proc") === "1") continue;
        el.setAttribute("data-ruta-proc", "1");
        if (action === "style") {
          try { el.style.cssText += ";" + styleArg; } catch (x) {}
        } else if (action === "remove") {
          try { el.remove(); removed = true; } catch (x) {}
        } else {
          el.style.setProperty("display", "none", "important");
          removed = true;
        }
      }
    }
    if (removed) {
      removeModalScrims();
      // Overlays often lock page scroll (body overflow:hidden); restore it once removed.
      var roots = [document.documentElement, document.body];
      for (var k = 0; k < roots.length; k++) {
        if (roots[k]) roots[k].style.setProperty("overflow", "auto", "important");
      }
    }
  }

  function filterByText(els, test) {
    var out = [];
    for (var i = 0; i < els.length; i++) {
      if (test(els[i].textContent || "")) out.push(els[i]);
    }
    return out;
  }

  function mapUpward(els, arg) {
    var out = [];
    for (var i = 0; i < els.length; i++) {
      var up = upwardOf(els[i], arg);
      if (up && out.indexOf(up) < 0) out.push(up);
    }
    return out;
  }

  var procScheduled = false;
  function scheduleProcSweep() {
    if (procScheduled) return;
    procScheduled = true;
    var run = function () {
      procScheduled = false;
      try {
        sweepProcedural();
      } catch (e) {}
    };
    if (window.requestAnimationFrame) window.requestAnimationFrame(run);
    else window.setTimeout(run, 0);
  }

  var procObserverStarted = false;
  function startProcObserver() {
    if (procObserverStarted) return;
    procObserverStarted = true;
    var obs = new MutationObserver(scheduleProcSweep);
    try {
      obs.observe(document.documentElement, { childList: true, subtree: true });
    } catch (e) {}
  }

  function applyProcedural(rules) {
    if (!rules || !rules.length) return;
    procRules = [];
    for (var i = 0; i < rules.length; i++) {
      var rule = rules[i];
      if (!rule || !rule.selector) continue;
      var raw = rule.steps || [];
      var steps = [];
      for (var k = 0; k < raw.length; k++) {
        var op = raw[k].op;
        var arg = raw[k].arg || "";
        if (op === "has-text" || op === "contains") steps.push({ op: "filter", test: compileNeedle(arg) });
        else if (op === "upward") steps.push({ op: "upward", arg: arg });
        else if (op === "style") steps.push({ op: "style", arg: arg });
        else if (op === "remove") steps.push({ op: "remove" });
      }
      procRules.push({ sel: rule.selector, steps: steps });
    }
    if (document.documentElement) startProcObserver();
    else document.addEventListener("DOMContentLoaded", startProcObserver);
    scheduleProcSweep();
  }

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
    var atTop = true;
    function setChrome(v) {
      if (v !== chromeVisible) {
        chromeVisible = v;
        post("scroll", { visible: v, top: atTop });
      }
    }
    // Report whether the dominant (most recently scrolled) scroller sits at its top, so the
    // native side knows when a downward drag means pull-to-refresh rather than feed scrolling.
    function setTop(v) {
      if (v !== atTop) {
        atTop = v;
        post("scroll", { visible: chromeVisible, top: v });
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
      setTop(y <= 6);
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
    applyProcedural: applyProcedural,
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
