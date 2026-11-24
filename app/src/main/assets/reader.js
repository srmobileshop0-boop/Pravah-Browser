(function () {
  var old = document.getElementById('__pv_reader');
  if (old) { old.remove(); document.documentElement.style.overflow = ''; return; }
  var paras = document.querySelectorAll('p'), groups = new Map(), i;
  for (i = 0; i < paras.length; i++) {
    var t = (paras[i].innerText || '').trim();
    if (t.length < 40) continue;
    var par = paras[i].parentElement;
    groups.set(par, (groups.get(par) || 0) + t.length);
  }
  var best = null, bs = 0;
  groups.forEach(function (v, k) { if (v > bs) { bs = v; best = k; } });
  var root = document.querySelector('article') || best;
  if (best && root && root !== best && !root.contains(best)) root = best;
  var box = document.createElement('div');
  box.id = '__pv_reader';
  var sh = box.attachShadow({ mode: 'open' });
  var title = (document.querySelector('h1') || {}).innerText || document.title || '';
  var body = document.createElement('div'); body.className = 'c';
  var h = document.createElement('h1'); h.textContent = title.trim(); body.appendChild(h);
  var any = false;
  if (root) {
    var nodes = root.querySelectorAll('h2,h3,h4,p,ul,ol,blockquote,pre,img,figure img');
    for (i = 0; i < nodes.length; i++) {
      var n = nodes[i], tag = n.tagName.toLowerCase(), c;
      if (n.closest('nav,aside,footer,header,form,script,style,[class*="comment"],[class*="share"],[class*="related"]') && tag !== 'img') continue;
      if (tag === 'img') {
        var src = n.currentSrc || n.src;
        if (!src || (n.naturalWidth && n.naturalWidth < 200)) continue;
        c = document.createElement('img'); c.src = src;
      } else {
        var tx = (n.innerText || '').trim();
        if (!tx || (tag === 'p' && tx.length < 3)) continue;
        c = document.createElement(tag === 'h4' ? 'h3' : tag);
        c.textContent = tx;
      }
      body.appendChild(c); any = true;
    }
  }
  if (!any) { var m = document.createElement('p'); m.textContent = 'Reader view is not available for this page.'; body.appendChild(m); }
  sh.innerHTML = '<style>' +
    ':host{all:initial}.w{position:fixed;inset:0;z-index:2147483647;background:#fbf8f1;color:#1d1d1b;overflow-y:auto;-webkit-overflow-scrolling:touch;font-family:Georgia,"Times New Roman",serif}' +
    '.w.dk{background:#121316;color:#e6e6e6}.bar{position:sticky;top:0;display:flex;gap:8px;align-items:center;padding:10px 12px;background:inherit;border-bottom:1px solid rgba(128,128,128,.25);font-family:system-ui,sans-serif}' +
    '.bar b{flex:1;font-size:15px}.bar button{height:36px;min-width:40px;border-radius:18px;border:1px solid rgba(128,128,128,.4);background:transparent;color:inherit;font-size:15px;font-weight:700;padding:0 10px}' +
    '.c{max-width:680px;margin:0 auto;padding:18px 20px 80px;font-size:19px;line-height:1.75}.c h1{font-size:1.5em;line-height:1.25}.c img{max-width:100%;height:auto;border-radius:10px;margin:10px 0}' +
    '.c pre{white-space:pre-wrap;background:rgba(128,128,128,.15);padding:10px;border-radius:8px;font-size:.8em}.c blockquote{border-left:4px solid #FF9933;margin-left:0;padding-left:14px;opacity:.9}</style>' +
    '<div class="w"><div class="bar"><b>Reader</b><button id="m">A-</button><button id="p">A+</button><button id="d">Dark</button><button id="x">Close</button></div></div>';
  var w = sh.querySelector('.w'); w.appendChild(body);
  var fs = 19;
  sh.getElementById('p').onclick = function () { fs = Math.min(30, fs + 2); body.style.fontSize = fs + 'px'; };
  sh.getElementById('m').onclick = function () { fs = Math.max(13, fs - 2); body.style.fontSize = fs + 'px'; };
  sh.getElementById('d').onclick = function () { w.classList.toggle('dk'); };
  sh.getElementById('x').onclick = function () { box.remove(); document.documentElement.style.overflow = ''; };
  document.documentElement.appendChild(box);
  document.documentElement.style.overflow = 'hidden';
})();
