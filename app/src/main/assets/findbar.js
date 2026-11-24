(function () {
  var old = document.getElementById('__pv_find');
  if (old) { old.remove(); try { window.getSelection().removeAllRanges(); } catch (e) {} return; }
  var host = document.createElement('div');
  host.id = '__pv_find';
  host.style.cssText = 'position:fixed;left:8px;right:8px;bottom:12px;z-index:2147483647';
  var sh = host.attachShadow({ mode: 'open' });
  sh.innerHTML = '<style>.b{display:flex;align-items:center;gap:4px;padding:6px 8px;border-radius:26px;background:#fff;box-shadow:0 6px 24px rgba(0,0,0,.28);font-family:system-ui,sans-serif;border:1px solid rgba(0,0,0,.1)}' +
    'input{flex:1;min-width:0;height:40px;border:0;outline:0;background:transparent;font-size:16px;color:#111;padding:0 8px}' +
    'span{font-size:12.5px;color:#666;min-width:46px;text-align:center}button{width:38px;height:38px;border-radius:50%;border:0;background:#f1f2f6;color:#111;font-size:15px}</style>' +
    '<div class="b"><input id="q" type="text" placeholder="Find on page" autocomplete="off"><span id="c"></span><button id="u">&#9650;</button><button id="d">&#9660;</button><button id="x">&#10005;</button></div>';
  document.documentElement.appendChild(host);
  var q = sh.getElementById('q'), c = sh.getElementById('c'), idx = 0, total = 0;
  function count(s) {
    if (!s) return 0;
    var t = (document.body.innerText || '').toLowerCase(), n = 0, p = 0; s = s.toLowerCase();
    while ((p = t.indexOf(s, p)) >= 0) { n++; p += s.length; if (n > 9999) break; }
    return n;
  }
  function go(back) {
    var s = q.value;
    if (!s) { c.textContent = ''; return; }
    total = count(s);
    var ok = false;
    try { ok = window.find(s, false, !!back, true, false, false, false); } catch (e) {}
    if (!ok || !total) { c.textContent = '0/0'; idx = 0; return; }
    idx = back ? (idx <= 1 ? total : idx - 1) : (idx >= total ? 1 : idx + 1);
    c.textContent = idx + '/' + total;
  }
  q.addEventListener('input', function () { idx = 0; try { window.getSelection().removeAllRanges(); } catch (e) {} go(false); });
  q.addEventListener('keydown', function (e) { if (e.key === 'Enter') go(false); });
  sh.getElementById('d').onclick = function () { go(false); };
  sh.getElementById('u').onclick = function () { go(true); };
  sh.getElementById('x').onclick = function () { host.remove(); try { window.getSelection().removeAllRanges(); } catch (e) {} };
  setTimeout(function () { q.focus(); }, 60);
})();
