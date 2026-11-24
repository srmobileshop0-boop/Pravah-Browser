(function () {
  if (!window.__pvDev) {
    var D = window.__pvDev = { logs: [] };
    ['log', 'info', 'warn', 'error', 'debug'].forEach(function (k) {
      var o = console[k];
      console[k] = function () {
        try { D.logs.push([k, Array.prototype.slice.call(arguments).map(function (a) { try { return typeof a === 'object' ? JSON.stringify(a) : String(a); } catch (e) { return String(a); } }).join(' ')]); if (D.logs.length > 500) D.logs.shift(); } catch (e) {}
        return o.apply(console, arguments);
      };
    });
    window.addEventListener('error', function (e) { D.logs.push(['error', (e.message || 'Error') + ' (' + (e.filename || '') + ':' + (e.lineno || 0) + ')']); });
  }
  var old = document.getElementById('__pv_dev');
  if (old) { old.remove(); return; }
  var host = document.createElement('div'); host.id = '__pv_dev';
  var sh = host.attachShadow({ mode: 'open' });
  sh.innerHTML = '<style>.w{position:fixed;left:0;right:0;bottom:0;height:62vh;z-index:2147483647;background:#14161c;color:#e8e8ee;font:12.5px/1.45 ui-monospace,Menlo,monospace;display:flex;flex-direction:column;border-radius:16px 16px 0 0;box-shadow:0 -8px 30px rgba(0,0,0,.4)}' +
    '.t{display:flex;gap:4px;padding:8px;border-bottom:1px solid #2a2d38;font-family:system-ui,sans-serif}.t button{border:0;border-radius:14px;padding:7px 12px;background:#242733;color:#cfd2e4;font-size:13px;font-weight:700}.t button.on{background:#5b6cff;color:#fff}.t .x{margin-left:auto}' +
    '.p{flex:1;overflow:auto;padding:8px;white-space:pre-wrap;word-break:break-all}.l{padding:2px 0;border-bottom:1px solid #20232e}.l.error{color:#ff7b7b}.l.warn{color:#ffd166}' +
    '.i{display:flex;gap:6px;padding:8px;border-top:1px solid #2a2d38}.i input{flex:1;height:36px;border-radius:10px;border:1px solid #333745;background:#1d2029;color:#fff;padding:0 10px;font:inherit}.i button{border:0;border-radius:10px;background:#5b6cff;color:#fff;padding:0 14px;font-weight:700}</style>' +
    '<div class="w"><div class="t"><button data-k="c" class="on">Console</button><button data-k="n">Network</button><button data-k="s">Source</button><button data-k="a">Storage</button><button class="x" data-k="x">Close</button></div><div class="p"></div><div class="i"><input placeholder="Run JavaScript..." autocomplete="off" autocapitalize="off"><button>Run</button></div></div>';
  var P = sh.querySelector('.p'), inp = sh.querySelector('input'), tab = 'c';
  function esc(s) { return String(s).replace(/[&<>]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]; }); }
  function draw() {
    var h = '';
    if (tab === 'c') {
      h = window.__pvDev.logs.map(function (l) { return '<div class="l ' + l[0] + '">' + esc(l[1]) + '</div>'; }).join('') || 'No console output yet (logs are captured from when Developer Tools was first opened).';
    } else if (tab === 'n') {
      var r = performance.getEntriesByType('resource');
      h = r.slice(-200).map(function (e) { return '<div class="l">' + esc(e.initiatorType) + '  ' + Math.round(e.duration) + 'ms  ' + (e.transferSize ? Math.round(e.transferSize / 1024) + 'KB  ' : '') + esc(e.name) + '</div>'; }).join('') || 'No requests recorded.';
    } else if (tab === 's') {
      h = esc(document.documentElement.outerHTML.slice(0, 200000));
    } else {
      var parts = [];
      try { parts.push('Cookies:\n' + (document.cookie || '(none)')); } catch (e) {}
      try { parts.push('localStorage (' + localStorage.length + '):\n' + Object.keys(localStorage).slice(0, 80).map(function (k) { return k + ' = ' + String(localStorage.getItem(k)).slice(0, 120); }).join('\n')); } catch (e) {}
      try { parts.push('sessionStorage (' + sessionStorage.length + '):\n' + Object.keys(sessionStorage).slice(0, 80).map(function (k) { return k + ' = ' + String(sessionStorage.getItem(k)).slice(0, 120); }).join('\n')); } catch (e) {}
      h = esc(parts.join('\n\n'));
    }
    P.innerHTML = h; P.scrollTop = P.scrollHeight;
  }
  sh.querySelector('.t').onclick = function (e) {
    var b = e.target.closest('button'); if (!b) return;
    if (b.dataset.k === 'x') { host.remove(); return; }
    tab = b.dataset.k; sh.querySelectorAll('.t button').forEach(function (x) { x.classList.toggle('on', x === b); }); draw();
  };
  function run() {
    var c = inp.value.trim(); if (!c) return; inp.value = '';
    try { var v = (0, eval)(c); window.__pvDev.logs.push(['log', '> ' + c + '\n' + (typeof v === 'object' ? JSON.stringify(v) : String(v))]); }
    catch (e) { window.__pvDev.logs.push(['error', '> ' + c + '\n' + e]); }
    tab = 'c'; sh.querySelectorAll('.t button').forEach(function (x) { x.classList.toggle('on', x.dataset.k === 'c'); }); draw();
  }
  sh.querySelector('.i button').onclick = run;
  inp.addEventListener('keydown', function (e) { if (e.key === 'Enter') run(); });
  document.documentElement.appendChild(host); draw();
})();
