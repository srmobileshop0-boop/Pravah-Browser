/* Pravah page features: privacy guard, background play, video toolbar/menu, touch to search, YouTube options.
   Configuration is injected by the app as window.__pvCfg before this file runs. */
(function () {
  if (window.__pvPriv) return;
  window.__pvPriv = 1;
  var C = window.__pvCfg || {};
  var H = location.hostname || '';
  var isYT = /(^|\.)youtube\.com$/.test(H);
  var top_ = window.top === window;

  function def(o, k, v) { try { Object.defineProperty(o, k, { get: function () { return v; }, configurable: true }); } catch (e) {} }
  function act(name, params) {
    var q = [];
    for (var k in params) q.push(k + '=' + encodeURIComponent(params[k] == null ? '' : params[k]));
    try { location.href = 'pravah-act://' + name + '?' + q.join('&'); } catch (e) {}
  }

  /* ---------- Privacy: fingerprint, DNT / GPC, WebRTC, de-AMP ---------- */
  if (C.fp) {
    def(Navigator.prototype, 'hardwareConcurrency', 4);
    def(Navigator.prototype, 'deviceMemory', 8);
    try {
      var oGet = CanvasRenderingContext2D.prototype.getImageData;
      var oTDU = HTMLCanvasElement.prototype.toDataURL;
      var oBlob = HTMLCanvasElement.prototype.toBlob;
      var jitter = function (c) {
        try {
          var w = c.width, h = c.height;
          if (!w || !h || w * h > 4000000) return c;
          var t = document.createElement('canvas');
          t.width = w; t.height = h;
          var x = t.getContext('2d');
          x.drawImage(c, 0, 0);
          var d = oGet.call(x, 0, 0, w, h), a = d.data, step = Math.max(4, Math.floor(a.length / 48 / 4) * 4);
          for (var i = 0; i < a.length; i += step) a[i] = a[i] ^ 1;
          x.putImageData(d, 0, 0);
          return t;
        } catch (e) { return c; }
      };
      HTMLCanvasElement.prototype.toDataURL = function () { return oTDU.apply(jitter(this), arguments); };
      HTMLCanvasElement.prototype.toBlob = function () { return oBlob.apply(jitter(this), arguments); };
      CanvasRenderingContext2D.prototype.getImageData = function () {
        var d = oGet.apply(this, arguments);
        try { var a = d.data; for (var i = 0; i < a.length; i += 997 * 4) a[i] = a[i] ^ 1; } catch (e) {}
        return d;
      };
    } catch (e) {}
  }
  if (C.dnt) def(Navigator.prototype, 'doNotTrack', '1');
  if (C.gpc) def(Navigator.prototype, 'globalPrivacyControl', true);
  if (C.rtc) {
    ['RTCPeerConnection', 'webkitRTCPeerConnection', 'RTCSessionDescription', 'RTCIceCandidate'].forEach(function (k) {
      try { delete window[k]; } catch (e) {}
      try { window[k] = undefined; } catch (e) {}
    });
  }
  if (C.deamp && top_) {
    var m = location.href.match(/^https?:\/\/(?:www\.)?google\.[a-z.]+\/amp\/s\/(.+)$/i);
    if (m) { location.replace('https://' + m[1]); }
    else {
      document.addEventListener('DOMContentLoaded', function () {
        try {
          var el = document.documentElement;
          if (el.hasAttribute('amp') || el.hasAttribute('⚡')) {
            var l = document.querySelector('link[rel="canonical"]');
            if (l && l.href && l.href !== location.href && /^https?:/.test(l.href)) location.replace(l.href);
          }
        } catch (e) {}
      });
    }
  }

  /* ---------- Listen in background: pages never learn they were hidden ---------- */
  if (C.bg) {
    try {
      def(Document.prototype, 'hidden', false);
      def(Document.prototype, 'webkitHidden', false);
      def(Document.prototype, 'visibilityState', 'visible');
      def(Document.prototype, 'webkitVisibilityState', 'visible');
      var stop = function (e) { e.stopImmediatePropagation(); };
      document.addEventListener('visibilitychange', stop, true);
      document.addEventListener('webkitvisibilitychange', stop, true);
    } catch (e) {}
  }

  /* ---------- small in-page toast ---------- */
  var toastT, toastEl;
  function toast(msg) {
    try {
      if (!toastEl) {
        toastEl = document.createElement('div');
        toastEl.style.cssText = 'position:fixed;left:50%;bottom:90px;transform:translateX(-50%);z-index:2147483647;background:rgba(20,20,28,.92);color:#fff;font:600 13px system-ui,sans-serif;padding:10px 16px;border-radius:18px;max-width:80vw;text-align:center;pointer-events:none';
        document.documentElement.appendChild(toastEl);
      }
      toastEl.textContent = msg;
      toastEl.style.display = 'block';
      clearTimeout(toastT);
      toastT = setTimeout(function () { toastEl.style.display = 'none'; }, 1800);
    } catch (e) {}
  }
  function srcOf(v) {
    var s = v.currentSrc || v.src || '';
    if (!s) { var so = v.querySelector && v.querySelector('source'); if (so) s = so.src || ''; }
    return /^https?:/i.test(s) ? s : '';
  }

  /* ---------- Video toolbar (download / fullscreen / speed) ---------- */
  if (C.vtool && !isYT) {
    var tb, SP = [1, 1.25, 1.5, 2, 0.5], si = 0, cur = null;
    var ic = function (d, extra) { return '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' + d + '</svg>'; };
    var build = function () {
      tb = document.createElement('div');
      tb.id = '__pvtb';
      tb.style.cssText = 'position:fixed;z-index:2147483646;display:none;align-items:center;gap:4px;padding:3px 8px;border-radius:22px;background:linear-gradient(135deg,#ff5a6e,#e0115f);box-shadow:0 4px 14px rgba(0,0,0,.35);-webkit-tap-highlight-color:transparent';
      var b = function (a, svg) { return '<span data-a="' + a + '" style="width:38px;height:36px;display:flex;align-items:center;justify-content:center">' + svg + '</span>'; };
      tb.innerHTML = b('pl', ic('<path d="M8 5.5v13l11-6.5z" fill="#fff"/>')) + b('dl', ic('<path d="M12 4v11m0 0l-4-4m4 4l4-4M5 19h14"/>')) +
        b('fs', ic('<path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/>')) +
        b('sp', ic('<circle cx="12" cy="5" r="1.3" fill="#fff"/><circle cx="12" cy="12" r="1.3" fill="#fff"/><circle cx="12" cy="19" r="1.3" fill="#fff"/>'));
      tb.addEventListener('click', function (e) {
        var s = e.target.closest && e.target.closest('[data-a]');
        if (!s || !cur) return;
        e.preventDefault(); e.stopPropagation();
        var a = s.getAttribute('data-a');
        if (a === 'pl') {
          act('play', { u: srcOf(cur) || '', t: document.title || '' });
        } else if (a === 'dl') {
          var u = srcOf(cur);
          act('download', { u: u });
          toast(u && !/\.(m3u8|mpd)(\?|$)/i.test(u) ? 'Download started' : 'Getting the video...');
        } else if (a === 'fs') {
          try { (cur.requestFullscreen || cur.webkitRequestFullscreen || cur.webkitEnterFullscreen).call(cur); } catch (er) {}
        } else {
          si = (si + 1) % SP.length;
          try { cur.playbackRate = SP[si]; } catch (er) {}
          toast('Speed ' + SP[si] + 'x');
        }
      }, true);
      document.documentElement.appendChild(tb);
    };
    var place = function () {
      try {
        var vs = document.getElementsByTagName('video'), pick = null;
        for (var i = 0; i < vs.length; i++) {
          var v = vs[i];
          if (v.paused || v.ended) continue;
          var r = v.getBoundingClientRect();
          if (r.width < 180 || r.height < 100 || r.bottom < 40 || r.top > innerHeight - 40) continue;
          pick = v; break;
        }
        if (!pick) { if (tb) tb.style.display = 'none'; cur = null; return; }
        if (!tb) build();
        cur = pick;
        var rr = pick.getBoundingClientRect();
        tb.style.display = 'flex';
        var w = tb.offsetWidth || 130;
        tb.style.left = Math.max(6, Math.min(innerWidth - w - 8, rr.right - w - 10)) + 'px';
        tb.style.top = Math.max(50, Math.min(innerHeight - 100, rr.bottom - 64)) + 'px';
      } catch (e) {}
    };
    setInterval(place, 700);
    window.addEventListener('scroll', function () { if (tb && tb.style.display !== 'none') place(); }, { passive: true });
  }

  /* ---------- Video menu: long-press a video ---------- */
  if (C.vmenu) {
    var lp, menu;
    var hideMenu = function () { if (menu) menu.style.display = 'none'; };
    var showMenu = function (v, x, y) {
      if (!menu) {
        menu = document.createElement('div');
        menu.style.cssText = 'position:fixed;z-index:2147483647;background:#fff;color:#1b1a17;border-radius:18px;box-shadow:0 10px 30px rgba(0,0,0,.35);padding:10px 6px;min-width:210px;font:600 15px system-ui,sans-serif';
        menu.innerHTML = '<div style="padding:4px 14px 8px;font-size:12px;color:#777;letter-spacing:.6px">VIDEO</div>' +
          '<div data-a="pl" style="padding:12px 14px;display:flex;justify-content:space-between;gap:20px">Add to Playlist<span>➕</span></div>' +
          '<div data-a="pd" style="padding:12px 14px;display:flex;justify-content:space-between;gap:20px">Add &amp; Download<span>⬇</span></div>';
        menu.addEventListener('click', function (e) {
          var s = e.target.closest && e.target.closest('[data-a]');
          if (!s) return;
          e.preventDefault(); e.stopPropagation();
          var v2 = menu.__v, u = v2 ? srcOf(v2) : '', dl = s.getAttribute('data-a') === 'pd';
          hideMenu();
          if (dl && !u) toast('Getting the video...');
          act('playlist', { u: location.href, t: document.title || location.hostname, v: u, dl: dl ? '1' : '0' });
        }, true);
        document.documentElement.appendChild(menu);
      }
      menu.__v = v;
      menu.style.display = 'block';
      menu.style.left = Math.max(8, Math.min(innerWidth - 230, x - 100)) + 'px';
      menu.style.top = Math.max(60, Math.min(innerHeight - 160, y - 40)) + 'px';
    };
    document.addEventListener('touchstart', function (e) {
      var t = e.target;
      if (menu && menu.contains && menu.contains(t)) return;
      hideMenu();
      var v = t && t.closest ? t.closest('video') : null;
      if (!v) return;
      var p = e.touches[0];
      clearTimeout(lp);
      lp = setTimeout(function () { showMenu(v, p.clientX, p.clientY); }, 600);
    }, { passive: true });
    ['touchend', 'touchmove', 'touchcancel'].forEach(function (n) { document.addEventListener(n, function () { clearTimeout(lp); }, { passive: true }); });
  }

  /* ---------- Touch to search ---------- */
  if (C.touch && top_) {
    var bub, bt, last = '';
    var hide = function () { if (bub) bub.style.display = 'none'; };
    document.addEventListener('selectionchange', function () {
      clearTimeout(bt);
      bt = setTimeout(function () {
        try {
          var s = window.getSelection(), t = s ? String(s).trim() : '';
          if (!t || t.length > 120 || s.rangeCount === 0) { hide(); return; }
          var ae = document.activeElement;
          if (ae && /^(input|textarea)$/i.test(ae.tagName)) { hide(); return; }
          var r = s.getRangeAt(0).getBoundingClientRect();
          if (!bub) {
            bub = document.createElement('div');
            bub.style.cssText = 'position:fixed;z-index:2147483647;background:#1b1a17;color:#fff;border-radius:20px;padding:9px 16px;font:600 14px system-ui,sans-serif;box-shadow:0 6px 18px rgba(0,0,0,.4);max-width:78vw;white-space:nowrap;overflow:hidden;text-overflow:ellipsis';
            bub.addEventListener('click', function (e) { e.preventDefault(); e.stopPropagation(); hide(); act('search', { q: last }); }, true);
            document.documentElement.appendChild(bub);
          }
          last = t;
          bub.textContent = 'Search “' + (t.length > 28 ? t.slice(0, 28) + '…' : t) + '”';
          bub.style.display = 'block';
          var top = r.top > 90 ? r.top - 54 : r.bottom + 56;
          bub.style.top = Math.max(8, Math.min(innerHeight - 60, top)) + 'px';
          bub.style.left = Math.max(8, Math.min(innerWidth - 220, r.left)) + 'px';
        } catch (e) {}
      }, 400);
    });
  }

  /* ---------- YouTube options ---------- */
  if (isYT && (C.ytr || C.yts)) {
    var css = '';
    if (C.ytr) css += 'ytm-watch-next-secondary-results-renderer,ytd-watch-next-secondary-results-renderer,#related,ytm-autonav-endscreen-renderer,.ytp-ce-element,.ytp-endscreen-content,.ytp-videowall-still,.ytp-suggestion-set,.ytp-pause-overlay{display:none!important}';
    if (C.yts) {
      css += 'ytm-reel-shelf-renderer,ytd-reel-shelf-renderer,ytm-shorts-lockup-view-model,ytm-shorts-lockup-view-model-v2,' +
        'grid-shelf-view-model:has(a[href*="/shorts/"]),ytm-rich-item-renderer:has(a[href^="/shorts/"]),ytm-video-with-context-renderer:has(a[href^="/shorts/"]),' +
        'ytd-rich-item-renderer:has(a[href^="/shorts/"]),ytd-video-renderer:has(a[href^="/shorts/"]),ytm-compact-video-renderer:has(a[href^="/shorts/"]),' +
        'ytm-pivot-bar-item-renderer:has(a[href="/shorts"]),ytd-guide-entry-renderer:has(a[title="Shorts"]),ytd-mini-guide-entry-renderer[aria-label="Shorts"]{display:none!important}';
      var mm = location.pathname.match(/^\/shorts\/([\w-]{6,})/);
      if (mm) location.replace('/watch?v=' + mm[1]);
    }
    var addCss = function () {
      try {
        var st = document.createElement('style');
        st.setAttribute('data-pravah', 'yt');
        st.textContent = css;
        (document.head || document.documentElement).appendChild(st);
      } catch (e) {}
    };
    addCss();
  }
})();
