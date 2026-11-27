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

  /* ---------- 3c. Ads with an "Ad" / "Sponsored" label, native ad strips and cam / affiliate widgets ---------- */
  var LABEL_RE = /^(ad|ads|advertisement|advertisements|sponsored|promoted|sponsor|ad choices|ads by .*|sponsored by .*|विज्ञापन|प्रायोजित)$/i;
  var WIDGET_HOST_RE = /(stripchat|strpst|strpjmp|xhamsterlive|xlivrdr|chaturbate|bongacams|bongacash|cam4|livejasmin|awempire|awltovhc|exoclick|exosrv|magsrv|pemsrv|trafficjunky|juicyads|clickadilla|plugrush|popcash|popads|propellerads|hilltopads|onclickads|adsterra|doubleclick|googlesyndication|taboola|outbrain|mgid|revcontent|adnxs|criteo)\./i;
  var vw = function () { return Math.max(window.innerWidth || 0, 320); };
  var safeToHide = function (el) {
    if (!el || el === document.body || el === document.documentElement) return false;
    var tag = el.tagName;
    if (tag === 'MAIN' || tag === 'ARTICLE' || tag === 'BODY' || tag === 'HTML') return false;
    if (el.querySelector && el.querySelector('video,h1,[role="main"],form[role="search"],input[type="search"]')) return false;
    var r = el.getBoundingClientRect();
    return r.height >= 40 && r.height <= 760 && r.width >= 60;
  };
  var hideBox = function (el) { try { el.style.setProperty('display', 'none', 'important'); el.setAttribute('data-pv-ad', '1'); } catch (e) {} };
  var sweepLabels = function () {
    try {
      var cand = document.querySelectorAll('span,div,small,b,i,p,a,label');
      var n = 0;
      for (var i = 0; i < cand.length && n < 1500; i++, n++) {
        var el = cand[i];
        if (el.getAttribute('data-pv-ad') || el.children.length > 1) continue;
        var tx = (el.textContent || '').trim();
        if (tx.length < 2 || tx.length > 22 || !LABEL_RE.test(tx)) continue;
        var up = el, best = null;
        for (var d = 0; d < 7 && up && up.parentElement; d++) {
          up = up.parentElement;
          if (!safeToHide(up)) { if (best) break; else continue; }
          var rw = up.getBoundingClientRect().width;
          best = up;
          if (rw >= vw() * 0.9) break;
        }
        if (best) hideBox(best);
      }
      var links = document.querySelectorAll('a[href],iframe[src]');
      for (var k = 0; k < links.length && k < 800; k++) {
        var a = links[k];
        if (a.getAttribute('data-pv-ad')) continue;
        var h = a.href || a.src || '';
        if (!h || !WIDGET_HOST_RE.test(hostOf(h))) continue;
        var c = a, box = a;
        for (var q = 0; q < 4 && c.parentElement; q++) {
          c = c.parentElement;
          if (safeToHide(c) && (c.innerText || '').length < 400) box = c; else break;
        }
        hideBox(box === a ? a : box);
      }
      var heads = document.querySelectorAll('div,section,h2,h3,p,span');
      for (var m = 0; m < heads.length && m < 1500; m++) {
        var hd = heads[m];
        if (hd.getAttribute('data-pv-ad') || hd.children.length > 2) continue;
        var t2 = (hd.textContent || '').trim();
        if (t2.length > 70 || t2.length < 12) continue;
        if (/^chat with .{0,30}(girls|models|live).{0,20}now!?$/i.test(t2) || /^(live cams?|webcams?|meet .* online)$/i.test(t2)) {
          var p2 = hd, bx = null;
          for (var z = 0; z < 4 && p2.parentElement; z++) { p2 = p2.parentElement; if (safeToHide(p2)) bx = p2; }
          if (bx) hideBox(bx);
        }
      }
    } catch (e) {}
  };
  var sweepT = 0;
  var scheduleSweep = function () { clearTimeout(sweepT); sweepT = setTimeout(sweepLabels, 350); };
  document.addEventListener('DOMContentLoaded', function () {
    sweepLabels();
    try { new MutationObserver(scheduleSweep).observe(document.documentElement, { childList: true, subtree: true }); } catch (e) {}
    setInterval(sweepLabels, 3000);
  });
  window.addEventListener('load', sweepLabels);
  window.addEventListener('scroll', scheduleSweep, { passive: true });

  /* ---------- 3d. Google: the x next to a recent search must remove it ---------- */
  if (/(^|\.)google\.[a-z.]+$/i.test(location.hostname)) {
    var HK = 'pv_hidden_recent';
    var readHidden = function () { try { return JSON.parse(localStorage.getItem(HK) || '[]'); } catch (e) { return []; } };
    var addHidden = function (t) { try { var a = readHidden(); if (a.indexOf(t) < 0) { a.push(t); localStorage.setItem(HK, JSON.stringify(a.slice(-80))); } } catch (e) {} };
    var closeCtl = function (el) {
      var n = el;
      for (var i = 0; i < 5 && n && n !== document.body; i++, n = n.parentElement) {
        var al = (n.getAttribute && (n.getAttribute('aria-label') || n.getAttribute('title'))) || '';
        if (/delete|remove|clear|dismiss|हटाएं/i.test(al)) return n;
        var pth = n.querySelector ? n.querySelector('path') : null;
        var d = pth ? (pth.getAttribute('d') || '') : '';
        if (/^M19 6\.41L17\.59 5|^M18\.3 5\.71|^M6 18L18 6/.test(d) && n.querySelectorAll('path').length === 1) return n;
        var tx = (n.textContent || '').trim();
        if (tx === '\u00d7' || tx === '\u2715' || tx === '\u2716' || tx === 'x') return n;
      }
      return null;
    };
    var recentRow = function (el) {
      var r = el;
      for (var i = 0; i < 7 && r && r.parentElement && r !== document.body; i++, r = r.parentElement) {
        var b = r.getBoundingClientRect();
        if (b.height >= 34 && b.height <= 120 && b.width >= vw() * 0.7 && (r.innerText || '').trim().length > 0) return r;
      }
      return null;
    };
    document.addEventListener('click', function (e) {
      try {
        var c = closeCtl(e.target);
        if (!c) return;
        var row = recentRow(c);
        if (!row) return;
        var label = (row.innerText || '').trim().split('\n')[0].trim();
        if (!label) return;
        setTimeout(function () {
          try {
            if (row.isConnected && row.offsetParent !== null && getComputedStyle(row).display !== 'none') {
              row.style.setProperty('display', 'none', 'important');
              addHidden(label);
            }
          } catch (er) {}
        }, 600);
      } catch (err) {}
    }, true);
    var sweepRecent = function () {
      try {
        var hid = readHidden();
        if (!hid.length) return;
        var cand = document.querySelectorAll('li,[role="option"],[role="row"],[role="listitem"],div[jsaction],div[class]');
        for (var i = 0; i < cand.length && i < 1200; i++) {
          var el = cand[i];
          if (el.style.display === 'none') continue;
          var t = (el.innerText || '').trim().split('\n')[0].trim();
          if (!t || hid.indexOf(t) < 0) continue;
          var b = el.getBoundingClientRect();
          if (b.height >= 34 && b.height <= 120 && el.querySelectorAll('svg').length >= 2) el.style.setProperty('display', 'none', 'important');
        }
      } catch (e) {}
    };
    document.addEventListener('DOMContentLoaded', function () {
      sweepRecent(); setInterval(sweepRecent, 2500);
      try { new MutationObserver(function () { clearTimeout(window.__pvRecT); window.__pvRecT = setTimeout(sweepRecent, 250); }).observe(document.documentElement, { childList: true, subtree: true }); } catch (e) {}
    });
  }

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
