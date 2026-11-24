(function () {
  if (window.__pvAB) return;
  window.__pvAB = 1;
  var H = location.hostname || '';
  var isYT = /(^|\.)youtube\.com$|(^|\.)youtube-nocookie\.com$/.test(H);
  var isGoogle = /(^|\.)google\.[a-z.]+$/.test(H);

  /* ---------- 1. Cosmetic filters (hide ad containers) ---------- */
  var sel = [
    'ins.adsbygoogle', '.adsbygoogle', 'iframe[src*="doubleclick.net"]', 'iframe[src*="googlesyndication"]',
    'iframe[id^="google_ads"]', 'div[id^="google_ads"]', 'div[id^="div-gpt-ad"]', '[id^="aswift_"]',
    '.google-auto-placed', '[data-google-query-id]', 'a[href*="googleadservices.com"]',
    /* Google Search */
    '#tads', '#tadsb', '#bottomads', 'div[data-text-ad]', '[data-text-ad]', '.commercial-unit-desktop-top',
    '.commercial-unit-mobile-top', '.cu-container', '.pla-unit', '.pla-unit-container', '.uEierd',
    '[aria-label="Ads"]', '.ads-ad', '[data-ad-slot]', '#tvcap', '.qGXjvb', '[data-text-ad="1"]', 'div[data-ta-slot-pos]', '[data-sokoban-container][data-text-ad]',
    /* Bing */
    '.b_ad', 'li.b_ad', '.sb_adTA', '#b_results > li.b_adLastChild',
    /* Yahoo search */
    '.searchCenterTopAds', '.searchCenterBottomAds', '.searchCenterMiddleAds', '#cols .ads',
    /* YouTube */
    '.ytp-ad-module', '.ytp-ad-overlay-container', '.ytp-ad-image-overlay', '.ytp-paid-content-overlay',
    'ytd-ad-slot-renderer', 'ytd-in-feed-ad-layout-renderer', 'ytd-banner-promo-renderer',
    'ytd-display-ad-renderer', 'ytd-promoted-sparkles-web-renderer', 'ytd-promoted-video-renderer',
    'ytd-player-legacy-desktop-watch-ads-renderer', 'ytd-statement-banner-renderer', '#masthead-ad', '#player-ads',
    '.masthead-ad-control', 'ytm-promoted-sparkles-web-renderer', 'ytm-promoted-video-renderer',
    'ytm-companion-ad-renderer', 'ytm-statement-banner-renderer', 'ytm-banner-promo-renderer',
    'ad-slot-renderer', 'ytm-ad-slot-renderer', '.ytm-ad-slot', 'ytm-in-feed-ad-layout-renderer',
    'ytd-merch-shelf-renderer', 'ytd-ad-slot-renderer ~ *:empty',
    /* Generic ad containers (news / blog / video / Indian sites) */
    'amp-ad', 'amp-embed', 'amp-sticky-ad', '[data-ad-client]', '[data-ad-unit]', '[data-ad-format]',
    'iframe[src*="googleads"]', 'iframe[src*="adservice"]', 'iframe[src*="adsterra"]', 'iframe[src*="popads"]', 'iframe[src*="/ads/"]',
    'iframe[src*="exoclick"]', 'iframe[src*="propeller"]', 'iframe[src*="taboola"]', 'iframe[src*="outbrain"]',
    '[id^="taboola"]', '[id^="outbrain"]', '.OUTBRAIN', '.taboola', '[id^="mgid"]', '[class*="mgid"]', '[id^="ezoic"]',
    '[id^="ads-"]', '[id^="ad-container"]', '[id^="adcontainer"]', '[id="ad"]', '[id="ads"]', '[id="advert"]', '[id="advertisement"]',
    '[class~="ad-banner"]', '[class~="ad-container"]', '[class~="ads-container"]', '[class~="ad-wrapper"]', '[class~="ad-slot"]',
    '[class~="advert"]', '[class~="advertisement"]', '[class~="adsbox"]', '[class~="sticky-ad"]', '[class~="ad-sticky"]',
    '[class~="popup-ad"]', '[class~="ad-popup"]', '[class~="sponsored-content"]', '[aria-label="Advertisement"]', '[aria-label="advertisement"]',
    '[data-testid="ad"]', '[data-component="ad"]', '[data-ad]', '[data-adunit]'
  ].join(',');
  function addCss() {
    try {
      var root = document.head || document.documentElement;
      if (!root) return false;
      if (document.querySelector('style[data-pravah="adblock"]')) return true;
      var st = document.createElement('style');
      st.setAttribute('data-pravah', 'adblock');
      st.textContent = sel + '{display:none!important}';
      root.appendChild(st);
      return true;
    } catch (e) { return false; }
  }
  if (!addCss()) {
    var _tries = 0, _iv = setInterval(function () { if (addCss() || ++_tries > 300) clearInterval(_iv); }, 10);
    document.addEventListener('DOMContentLoaded', addCss);
  }

  /* ---------- 2. YouTube: strip ads from player data + auto-skip ---------- */
  if (isYT) {
    var AD_KEYS = ['adPlacements', 'adSlots', 'playerAds', 'adBreakHeartbeatParams'];
    var strip = function (o) {
      try {
        if (!o || typeof o !== 'object') return o;
        for (var i = 0; i < AD_KEYS.length; i++) if (AD_KEYS[i] in o) delete o[AD_KEYS[i]];
        if (o.playerResponse) strip(o.playerResponse);
        if (o.response) strip(o.response);
      } catch (e) {}
      return o;
    };
    var _parse = JSON.parse;
    var _ipr;
    try {
      Object.defineProperty(window, 'ytInitialPlayerResponse', {
        configurable: true,
        get: function () { return _ipr; },
        set: function (v) { _ipr = strip(v); }
      });
    } catch (e) {}
    /* Only touch the player response, and only when it really carries ad data (keeps playback safe). */
    var _fetch = window.fetch;
    if (_fetch) {
      window.fetch = function () {
        var a = arguments, p = _fetch.apply(this, a), u = '';
        try { u = (a[0] && a[0].url) || String(a[0]); } catch (e) {}
        if (!/\/youtubei\/v1\/player(\?|$)/.test(u)) return p;
        return p.then(function (res) {
          try {
            return res.clone().text().then(function (t) {
              if (t.indexOf('adPlacements') < 0 && t.indexOf('playerAds') < 0 && t.indexOf('adSlots') < 0) return res;
              try {
                var j = _parse(t);
                strip(j);
                return new Response(JSON.stringify(j), { status: res.status, statusText: res.statusText, headers: res.headers });
              } catch (e) { return res; }
            });
          } catch (e) { return res; }
        });
      };
    }
    var adOn = false, prevMuted = false;
    setInterval(function () {
      try {
        var p = document.querySelector('.html5-video-player');
        var v = document.querySelector('video.html5-main-video') || document.querySelector('video');
        var showing = p && (p.classList.contains('ad-showing') || p.classList.contains('ad-interrupting'));
        if (showing && v) {
          if (!adOn) { adOn = true; prevMuted = v.muted; }
          v.muted = true;
          if (isFinite(v.duration) && v.duration > 0) { v.playbackRate = 16; v.currentTime = v.duration - 0.05; }
        } else if (adOn && v) {
          adOn = false; v.muted = prevMuted; v.playbackRate = 1;
        }
        var b = document.querySelector('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button,.ytp-ad-overlay-close-button,.ytp-ad-skip-button-container button');
        if (b) b.click();
      } catch (e) {}
    }, 250);
  }

  /* ---------- 3. Search-result ads (Google, Bing, Yahoo, DuckDuckGo) ----------
     Google keeps renaming its ad containers, so ads are found by what they ARE:
     ad-click links (aclk / googleadservices / doubleclick) and "Sponsored" / "Ad" labels.
     The whole ad block around them is hidden; organic results are never touched. */
  var isSearchSite = isGoogle || /(^|\.)bing\.com$|(^|\.)yahoo\.com$|(^|\.)duckduckgo\.com$/.test(H);
  if (isSearchSite) {
    var AD_A = 'a[data-rw*="aclk"],a[href*="/aclk?"],a[href*="/aclick?"],a[href*="/pagead/aclk"],' +
               'a[href*="googleadservices.com/"],a[href*="doubleclick.net/"],a[href*="/pagead/"],a[href*="syndicatedsearch.goog"]';
    var LABEL = /^(sponsored|ads?|sponsored results?|sponsored links?|sponsored products?|(sponsored|ads?)\s*[·•\-].*|प्रायोजित|विज्ञापन)$/i;
    var STOPID = { rso: 1, tads: 1, tadsb: 1, bottomads: 1, center_col: 1, main: 1, search: 1, res: 1, cols: 1, b_results: 1, b_content: 1, content: 1, web: 1 };
    var isAdA = function (a) { try { return a.matches(AD_A); } catch (e) { return false; } };
    /* is there an ordinary result title (a heading inside a normal link) in p, outside of `skip`? */
    var organicIn = function (p, skip) {
      var hs = p.querySelectorAll('h3,h2,[role="heading"]');
      for (var i = 0; i < hs.length; i++) {
        var h = hs[i];
        if (skip.contains(h)) continue;
        var a = h.closest ? h.closest('a') : null;
        if (a && !isAdA(a)) return true;
      }
      return false;
    };
    var unitFor = function (n) {
      var c = n, prev = n;
      for (var k = 0; k < 14; k++) {
        var p = c.parentElement;
        if (!p || p === document.body || p === document.documentElement || STOPID[p.id] || p.getAttribute('role') === 'main') break;
        if (organicIn(p, c)) break;
        var all = p.getElementsByTagName('a').length;
        if (all > 16 && p.querySelectorAll(AD_A).length * 2 < all) break;   /* too many normal links: that is the result list */
        prev = c; c = p;
        try { if (c.getBoundingClientRect().height > window.innerHeight * 2.2) { c = prev; break; } } catch (e) {}
      }
      return c;
    };
    var hide = function (el) {
      try { el.setAttribute('data-pv-ad', '1'); el.style.setProperty('display', 'none', 'important'); } catch (e) {}
    };
    var onResults = function () {
      return /^\/(search|html|aclk)/.test(location.pathname || '') || /[?&](q|p|query)=/.test(location.search || '');
    };
    var sweep = function () {
      try {
        var as = document.querySelectorAll(AD_A);
        for (var i = 0; i < as.length; i++) {
          if (as[i].closest('[data-pv-ad]')) continue;
          hide(unitFor(as[i]));
        }
        if (!onResults() || !document.body) return;
        var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false), t, hits = [];
        while ((t = w.nextNode())) {
          var v = t.nodeValue;
          if (!v || v.length > 40) continue;
          v = v.trim();
          if (!v || !LABEL.test(v)) continue;
          var el = t.parentElement;
          if (!el || el.closest('[data-pv-ad],nav,header,footer,script,style,title,option,textarea,input,button')) continue;
          hits.push(el);
        }
        for (var j = 0; j < hits.length; j++) hide(unitFor(hits[j]));
      } catch (e) {}
    };
    var timer;
    var kick = function () { clearTimeout(timer); timer = setTimeout(sweep, 120); };
    try { new MutationObserver(kick).observe(document.documentElement, { childList: true, subtree: true }); } catch (e) {}
    document.addEventListener('DOMContentLoaded', sweep);
    window.addEventListener('load', sweep);
    setInterval(sweep, 1500);
    /* an ad link can never be opened by accident */
    document.addEventListener('click', function (e) {
      try {
        var a = e.target && e.target.closest ? e.target.closest(AD_A) : null;
        if (a) { e.preventDefault(); e.stopPropagation(); }
      } catch (err) {}
    }, true);
  }

  /* ---------- 3b. Click-jacking guard: invisible full-screen layers & ad links never fire ---------- */
  var AD_HOST_RE = /(^|\.)(doubleclick|googlesyndication|googleadservices|adsterra|popads|popcash|propellerads|exoclick|juicyads|clickadu|hilltopads|adcash|trafficjunky|bidvertiser|a-ads|richpush|zedo|onclickads|monetag|mondiad|admaven|ad-maven|clickaine|tsyndicate|exdynsrv|exosrv|realsrv|adnxs|taboola|outbrain|mgid|revcontent|adskeeper|profitablecpmrate|highperformanceformat|effectivegatecpm|toprevenuegate|onclasrv|popunder)\./i;
  var hostOf = function (u) { try { return new URL(u, location.href).hostname; } catch (e) { return ''; } };
  var isInvisibleCover = function (el) {
    try {
      if (!el || el === document.body || el === document.documentElement) return false;
      var cs = getComputedStyle(el);
      if (cs.position !== 'fixed' && cs.position !== 'absolute') return false;
      var r = el.getBoundingClientRect();
      if (r.width < window.innerWidth * 0.85 || r.height < window.innerHeight * 0.6) return false;
      var clear = parseFloat(cs.opacity) < 0.06 || cs.visibility === 'hidden' ||
                  ((cs.backgroundColor === 'rgba(0, 0, 0, 0)' || cs.backgroundColor === 'transparent') && cs.backgroundImage === 'none' && !(el.innerText || '').trim() && !el.querySelector('img,video,canvas,input,button,form,p,h1,h2,h3'));
      return clear && parseInt(cs.zIndex || '0', 10) >= 10;
    } catch (e) { return false; }
  };
  var guard = function (e) {
    try {
      var t = e.target;
      var a = t && t.closest ? t.closest('a[href]') : null;
      if (a && AD_HOST_RE.test(hostOf(a.href))) { e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation(); return; }
      var x = (e.touches && e.touches[0] ? e.touches[0].clientX : e.clientX), y = (e.touches && e.touches[0] ? e.touches[0].clientY : e.clientY);
      var el = (typeof x === 'number') ? document.elementFromPoint(x, y) : null;
      if (el && isInvisibleCover(el)) { el.style.setProperty('display', 'none', 'important'); e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation(); }
    } catch (err) {}
  };
  document.addEventListener('click', guard, true);
  document.addEventListener('touchstart', guard, { capture: true, passive: false });
  try {
    var _assign = null;
    document.addEventListener('DOMContentLoaded', function () {
      var kill = function () {
        var n = document.querySelectorAll('div,section,aside,a');
        for (var i = 0; i < n.length && i < 400; i++) {
          if (n[i].getAttribute('data-pv-ad')) continue;
          if (isInvisibleCover(n[i])) n[i].style.setProperty('display', 'none', 'important');
        }
      };
      setTimeout(kill, 1500); setInterval(kill, 4000);
    });
  } catch (e) {}

  /* ---------- 4. Block script-created popups / window.open spam ---------- */
  try {
    var _open = window.open;
    window.open = function () {
      var u = arguments[0] ? String(arguments[0]) : '';
      if (u && AD_HOST_RE.test(hostOf(u))) return null;
      if (navigator.userActivation && navigator.userActivation.isActive) return _open.apply(window, arguments);
      return null;
    };
  } catch (e) {}
})();
