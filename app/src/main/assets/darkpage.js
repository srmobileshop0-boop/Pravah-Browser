/* Pravah: optional dark mode for web pages (only injected when the user turns it on). */
(function () {
  if (window.__pvDK || window !== window.top) return;
  window.__pvDK = 1;
  var css =
    'html{background:#fff!important;filter:invert(1) hue-rotate(180deg)!important}' +
    'img,picture,video,canvas,iframe,embed,object,[style*="background-image"],svg image{filter:invert(1) hue-rotate(180deg)!important}' +
    'html{color-scheme:light!important}';
  function add() {
    try {
      if (document.getElementById('pv-dark')) return;
      var st = document.createElement('style');
      st.id = 'pv-dark';
      st.textContent = css;
      (document.head || document.documentElement).appendChild(st);
    } catch (e) {}
  }
  add();
  document.addEventListener('readystatechange', add);
})();
