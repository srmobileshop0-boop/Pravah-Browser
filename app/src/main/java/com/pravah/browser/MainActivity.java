package com.pravah.browser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pravah Browser.
 *
 * Layout: [top address bar WebView (top.html)] [tab WebViews] and a bottom UI WebView (ui.html).
 * ui.html draws the bottom bar, home page, tab switcher, history, bookmarks, settings, search and menu.
 * It is 76dp tall while a web page is shown, and full screen for every other screen.
 */
public class MainActivity extends Activity {

    static final int FILE_REQ = 77, PERM_REQ = 78;
    static final String[][] THEMES = {
            {"daylight", "#F6F1E9", "0"}, {"sky", "#EAF4FF", "0"},
            {"midnight", "#0F1226", "1"}, {"amoled", "#000000", "1"}};
    static final String[] ENGINE_URLS = {
            "google", "https://www.google.com/search?q=",
            "bing", "https://www.bing.com/search?q=",
            "yahoo", "https://search.yahoo.com/search?p="};

    static class Tab {
        int id;
        WebView wv;
        String url = "", title = "";
        boolean priv, desktop, loading;
        int progress;
        String thumb;
        ScriptHandler script;
    }

    SharedPreferences sp;
    final Handler main = new Handler(Looper.getMainLooper());
    FrameLayout root, content;
    LinearLayout column;
    WebView ui, top;
    final ArrayList<Tab> tabs = new ArrayList<>();
    Tab cur;
    int nextId = 1;
    String mode = "home", modeArg = "";
    boolean menuOpen = false, uiReady = false, topReady = false;
    JSONArray history = new JSONArray(), bookmarks = new JSONArray();
    JSONObject settings = new JSONObject();
    String adJs = "", darkJs = "", pageScript = "", baseUa = "", chromeVer = "120.0.0.0";
    boolean introActive = true;
    JSONArray dls = new JSONArray();
    Runnable dlPoll;
    View customView;
    WebChromeClient.CustomViewCallback customCb;
    ValueCallback<Uri[]> fileCb;
    boolean saveQueued = false;

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        sp = getSharedPreferences("pravah", MODE_PRIVATE);
        loadData();
        adJs = readAsset("adblock.js");
        darkJs = readAsset("darkpage.js");
        rebuildPageScript();
        String d = WebSettings.getDefaultUserAgent(this);
        baseUa = d.replace("; wv", "").replace(" Version/4.0", "");
        Matcher m = Pattern.compile("Chrome/([\\d.]+)").matcher(d);
        if (m.find()) chromeVer = m.group(1);

        buildLayout();
        applyTheme();
        restoreTabs();
        handleIntent(getIntent());
        introStyle();
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (introActive) {
                    ui.evaluateJavascript("window.Intro&&Intro.skip()", null);
                    endIntro();
                }
            }
        }, 6000);

        if (Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERM_REQ);
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleIntent(i);
    }

    void handleIntent(Intent i) {
        if (i == null || !Intent.ACTION_VIEW.equals(i.getAction()) || i.getData() == null) return;
        Tab t = newTab(i.getData().toString(), false);
        activate(t);
        i.setData(null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveNow();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        for (Tab t : tabs) if (t.wv != null) t.wv.destroy();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (setting("themeMode", "system").equals("system")) {
            applyTheme();
            pushState();
        }
    }

    @Override
    public void onBackPressed() {
        if (introActive) {
            ui.evaluateJavascript("window.Intro&&Intro.skip()", null);
            endIntro();
            return;
        }
        if (customView != null) {
            hideCustom();
            return;
        }
        if (menuOpen) {
            ui.evaluateJavascript("document.getElementById('scrim').click()", null);
            return;
        }
        if (mode.equals("about")) {
            show("settings", "");
            return;
        }
        if (!mode.equals("web") && !mode.equals("home")) {
            activate(cur);
            return;
        }
        if (mode.equals("web") && cur != null && cur.wv != null) {
            if (cur.wv.canGoBack()) {
                cur.wv.goBack();
                return;
            }
            goHome();
            return;
        }
        finish();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == FILE_REQ && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
        } else super.onActivityResult(req, res, data);
    }

    // ------------------------------------------------------------------ layout

    void buildLayout() {
        root = new FrameLayout(this);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        top = makeUiWebView();
        top.loadUrl("file:///android_asset/top.html");
        top.setVisibility(View.GONE);
        column.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

        content = new FrameLayout(this);
        column.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View spacer = new View(this);
        column.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));

        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ui = makeUiWebView();
        ui.loadUrl("file:///android_asset/ui.html");
        root.addView(ui, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM));
        setContentView(root);
    }

    WebView makeUiWebView() {
        final WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        w.setBackgroundColor(Color.TRANSPARENT);
        noDark(w);
        w.setOverScrollMode(View.OVER_SCROLL_NEVER);
        w.setVerticalScrollBarEnabled(false);
        w.setHorizontalScrollBarEnabled(false);
        w.addJavascriptInterface(new Bridge(), "Native");
        w.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                if (v == ui) {
                    uiReady = true;
                    pushState();
                } else {
                    topReady = true;
                    top.evaluateJavascript("Top.setTheme('" + effectiveTheme() + "')", null);
                    pushTop();
                }
            }
        });
        return w;
    }

    void setExpanded(boolean e) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) ui.getLayoutParams();
        int h = (e || introActive) ? ViewGroup.LayoutParams.MATCH_PARENT : dp(76);
        if (lp.height != h) {
            lp.height = h;
            ui.setLayoutParams(lp);
        }
    }

    // ------------------------------------------------------------------ theme

    String[] themeRow() {
        String n = effectiveTheme();
        for (String[] t : THEMES) if (t[0].equals(n)) return t;
        return THEMES[0];
    }

    String effectiveTheme() {
        String m = setting("themeMode", "system");
        boolean dark;
        if (m.equals("dark")) dark = true;
        else if (m.equals("light")) dark = false;
        else dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return dark ? setting("darkStyle", "midnight") : setting("lightStyle", "daylight");
    }

    void introStyle() {
        if (!introActive) return;
        int bg = Color.parseColor("#070B24");
        getWindow().setBackgroundDrawable(new ColorDrawable(bg));
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(0);
        if (root != null) root.setBackgroundColor(bg);
    }

    void endIntro() {
        if (!introActive) return;
        introActive = false;
        applyTheme();
        setExpanded(!mode.equals("web") || menuOpen);
    }

    @SuppressWarnings("deprecation")
    static void noDark(WebView w) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(w.getSettings(), false);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(w.getSettings(), WebSettingsCompat.FORCE_DARK_OFF);
            }
        } catch (Throwable ignored) {
        }
    }

    void rebuildPageScript() {
        StringBuilder sb = new StringBuilder();
        if (flag("adblock", true) && !adJs.isEmpty()) sb.append(adJs).append("\n;\n");
        if (flag("darkPages", false) && !darkJs.isEmpty()) sb.append(darkJs).append("\n;\n");
        pageScript = sb.toString();
    }

    void applyTheme() {
        String[] t = themeRow();
        int bg = Color.parseColor(t[1]);
        boolean dark = t[2].equals("1");
        getWindow().setBackgroundDrawable(new ColorDrawable(bg));
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        int flags = 0;
        if (!dark) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
        if (root != null) root.setBackgroundColor(bg);
        if (topReady) top.evaluateJavascript("Top.setTheme('" + t[0] + "')", null);
    }

    // ------------------------------------------------------------------ data

    String setting(String k, String def) {
        return settings.optString(k, def);
    }

    boolean flag(String k, boolean def) {
        return "true".equals(setting(k, def ? "true" : "false"));
    }

    void loadData() {
        try {
            history = new JSONArray(sp.getString("history", "[]"));
            bookmarks = new JSONArray(sp.getString("bookmarks", "[]"));
            settings = new JSONObject(sp.getString("settings", "{}"));
            dls = new JSONArray(sp.getString("downloads", "[]"));
        } catch (JSONException e) {
            history = new JSONArray();
            bookmarks = new JSONArray();
            settings = new JSONObject();
            dls = new JSONArray();
        }
    }

    void saveSoon() {
        if (saveQueued) return;
        saveQueued = true;
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                saveQueued = false;
                saveNow();
            }
        }, 1500);
    }

    void saveNow() {
        try {
            JSONArray ta = new JSONArray();
            int idx = 0, n = 0;
            for (Tab t : tabs) {
                if (t.priv) continue;
                JSONObject o = new JSONObject();
                String u = t.wv != null && t.wv.getUrl() != null && t.wv.getUrl().startsWith("http") ? t.wv.getUrl() : t.url;
                o.put("url", u == null ? "" : u);
                o.put("title", t.title);
                if (t == cur) idx = n;
                ta.put(o);
                n++;
            }
            JSONObject all = new JSONObject();
            all.put("cur", idx);
            all.put("tabs", ta);
            sp.edit()
                    .putString("history", history.toString())
                    .putString("bookmarks", bookmarks.toString())
                    .putString("settings", settings.toString())
                    .putString("downloads", dls.toString())
                    .putString("tabs", all.toString())
                    .apply();
        } catch (JSONException ignored) {
        }
    }

    void restoreTabs() {
        try {
            JSONObject all = new JSONObject(sp.getString("tabs", "{}"));
            JSONArray ta = all.optJSONArray("tabs");
            if (ta != null) {
                for (int i = 0; i < ta.length(); i++) {
                    JSONObject o = ta.getJSONObject(i);
                    Tab t = new Tab();
                    t.id = nextId++;
                    t.url = o.optString("url", "");
                    t.title = o.optString("title", "");
                    t.desktop = flag("desktop", false);
                    tabs.add(t);
                }
            }
            if (tabs.isEmpty()) {
                tabs.add(blankTab(false));
            }
            int idx = Math.max(0, Math.min(all.optInt("cur", 0), tabs.size() - 1));
            activate(tabs.get(idx));
        } catch (JSONException e) {
            tabs.clear();
            tabs.add(blankTab(false));
            activate(tabs.get(0));
        }
    }

    Tab blankTab(boolean priv) {
        Tab t = new Tab();
        t.id = nextId++;
        t.priv = priv;
        t.desktop = flag("desktop", false);
        return t;
    }

    String readAsset(String name) {
        try (InputStream is = getAssets().open(name)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ tabs

    boolean isBlank(Tab t) {
        return (t.url == null || t.url.isEmpty()) && t.wv == null;
    }

    Tab findTab(int id) {
        for (Tab t : tabs) if (t.id == id) return t;
        return null;
    }

    Tab newTab(String url, boolean priv) {
        Tab t = blankTab(priv);
        tabs.add(t);
        if (url != null && !url.isEmpty()) {
            t.url = url;
            ensureWebView(t);
            t.wv.loadUrl(url);
        }
        saveSoon();
        return t;
    }

    void activate(Tab t) {
        if (t == null) {
            t = blankTab(false);
            tabs.add(t);
        }
        if (cur != null && cur != t && mode.equals("web")) captureThumb(cur);
        cur = t;
        content.removeAllViews();
        if (isBlank(t)) {
            show("home", "");
        } else {
            ensureWebView(t);
            if (t.wv.getParent() != null) ((ViewGroup) t.wv.getParent()).removeView(t.wv);
            content.addView(t.wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (t.wv.getUrl() == null && !t.url.isEmpty()) t.wv.loadUrl(t.url);
            show("web", "");
        }
        saveSoon();
    }

    void destroyWebView(Tab t) {
        if (t.wv == null) return;
        ViewGroup p = (ViewGroup) t.wv.getParent();
        if (p != null) p.removeView(t.wv);
        t.wv.stopLoading();
        t.wv.destroy();
        t.wv = null;
        t.script = null;
    }

    void closeTab(int id) {
        Tab t = findTab(id);
        if (t == null) return;
        int i = tabs.indexOf(t);
        destroyWebView(t);
        tabs.remove(t);
        if (tabs.isEmpty()) {
            cur = null; // no tabs left: the tab screen shows its empty state
        } else if (t == cur) {
            cur = tabs.get(Math.min(i, tabs.size() - 1));
        }
        if (mode.equals("tabs")) pushState();
        else activate(cur);
        saveSoon();
    }

    void goHome() {
        if (cur == null) return;
        destroyWebView(cur);
        cur.url = "";
        cur.title = "";
        cur.thumb = null;
        activate(cur);
    }

    void captureThumb(Tab t) {
        if (t.wv == null || t.priv || t.wv.getWidth() == 0 || t.wv.getHeight() == 0) return;
        try {
            int w = t.wv.getWidth(), h = t.wv.getHeight();
            float sc = 240f / w;
            Bitmap b = Bitmap.createBitmap(240, Math.max(1, (int) (h * sc)), Bitmap.Config.RGB_565);
            Canvas c = new Canvas(b);
            c.drawColor(Color.WHITE);
            c.scale(sc, sc);
            t.wv.draw(c);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 60, bo);
            t.thumb = "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
            b.recycle();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ screens

    void show(String m, String arg) {
        mode = m;
        modeArg = arg == null ? "" : arg;
        boolean web = m.equals("web");
        top.setVisibility(web ? View.VISIBLE : View.GONE);
        setExpanded(!web || menuOpen);
        if (web && cur != null && cur.wv != null) cur.wv.requestFocus();
        pushState();
        pushTop();
        if (m.equals("downloads")) startDlPoll();
        else stopDlPoll();
        if (m.equals("search")) {
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    ui.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(ui, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 150);
        } else {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        }
    }

    static String js(Object o) {
        return o.toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }

    void pushState() {
        if (!uiReady) return;
        try {
            JSONObject o = new JSONObject();
            o.put("mode", mode);
            o.put("arg", modeArg);
            o.put("theme", effectiveTheme());
            JSONObject st = new JSONObject();
            st.put("engine", setting("engine", "google"));
            st.put("adblock", setting("adblock", "true"));
            st.put("popups", setting("popups", "true"));
            st.put("darkPages", setting("darkPages", "false"));
            st.put("desktop", setting("desktop", "false"));
            st.put("themeMode", setting("themeMode", "system"));
            st.put("lightStyle", setting("lightStyle", "daylight"));
            st.put("darkStyle", setting("darkStyle", "midnight"));
            o.put("settings", st);
            JSONObject ab = new JSONObject();
            String ver = "1.1";
            try {
                ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception ignored) {
            }
            ab.put("version", ver);
            ab.put("engine", chromeVer);
            ab.put("android", Build.VERSION.RELEASE);
            o.put("about", ab);
            JSONArray ta = new JSONArray();
            for (Tab t : tabs) {
                JSONObject j = new JSONObject();
                j.put("id", t.id);
                j.put("title", t.title);
                j.put("url", t.url);
                j.put("priv", t.priv);
                j.put("active", t == cur);
                j.put("blank", isBlank(t));
                if (mode.equals("tabs") && t.thumb != null && !t.priv) j.put("thumb", t.thumb);
                ta.put(j);
            }
            o.put("tabs", ta);
            o.put("bookmarks", bookmarks);
            if (mode.equals("history") || mode.equals("search")) {
                JSONArray h = new JSONArray();
                for (int i = 0; i < Math.min(history.length(), 600); i++) h.put(history.get(i));
                o.put("history", h);
            }
            boolean web = cur != null && cur.wv != null;
            o.put("canBack", web && cur.wv.canGoBack());
            o.put("canForward", web && cur.wv.canGoForward());
            o.put("blank", cur == null || isBlank(cur));
            o.put("bookmarked", cur != null && isBookmarked(cur.url));
            o.put("desktop", cur != null && cur.desktop);
            ui.evaluateJavascript("App.setState(" + js(o) + ")", null);
        } catch (JSONException ignored) {
        }
    }

    void pushTop() {
        if (!topReady || cur == null) return;
        try {
            JSONObject o = new JSONObject();
            String u = cur.url == null ? "" : cur.url;
            o.put("url", u);
            o.put("title", cur.title);
            o.put("secure", u.startsWith("https"));
            o.put("loading", cur.loading);
            o.put("progress", cur.progress);
            o.put("bookmarked", isBookmarked(u));
            top.evaluateJavascript("Top.set(" + js(o) + ")", null);
        } catch (JSONException ignored) {
        }
    }

    // ------------------------------------------------------------------ navigation helpers

    static boolean looksLikeUrl(String s) {
        if (s.contains(" ")) return false;
        return s.matches("^([\\w-]+\\.)+[a-zA-Z]{2,}(:\\d+)?([/?#].*)?$")
                || s.matches("^localhost(:\\d+)?([/?#].*)?$")
                || s.matches("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?([/?#].*)?$");
    }

    String resolve(String in, String engine) {
        in = in.trim();
        if (in.isEmpty()) return null;
        if (in.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*") || in.startsWith("about:")) return in;
        if (looksLikeUrl(in)) return "https://" + in;
        String base = ENGINE_URLS[1];
        for (int i = 0; i < ENGINE_URLS.length; i += 2) if (ENGINE_URLS[i].equals(engine)) base = ENGINE_URLS[i + 1];
        try {
            return base + URLEncoder.encode(in, "UTF-8");
        } catch (Exception e) {
            return base + in;
        }
    }

    void loadInCur(String url) {
        if (url == null || cur == null) return;
        Tab t = cur;
        ensureWebView(t);
        t.url = url;
        if (t.wv.getParent() != content) {
            if (t.wv.getParent() != null) ((ViewGroup) t.wv.getParent()).removeView(t.wv);
            content.removeAllViews();
            content.addView(t.wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        t.wv.loadUrl(url);
        show("web", "");
    }

    boolean isBookmarked(String url) {
        if (url == null || url.isEmpty()) return false;
        for (int i = 0; i < bookmarks.length(); i++) {
            JSONObject o = bookmarks.optJSONObject(i);
            if (o != null && url.equals(o.optString("url"))) return true;
        }
        return false;
    }

    void toggleBookmark() {
        if (cur == null || isBlank(cur)) return;
        String url = cur.wv != null && cur.wv.getUrl() != null ? cur.wv.getUrl() : cur.url;
        if (url == null || !url.startsWith("http")) return;
        removeBookmark(url, true);
    }

    void removeBookmark(String url, boolean addIfMissing) {
        try {
            JSONArray n = new JSONArray();
            boolean found = false;
            for (int i = 0; i < bookmarks.length(); i++) {
                JSONObject o = bookmarks.getJSONObject(i);
                if (url.equals(o.optString("url"))) found = true;
                else n.put(o);
            }
            if (!found && addIfMissing) {
                JSONObject o = new JSONObject();
                o.put("url", url);
                o.put("title", cur != null && cur.title != null && !cur.title.isEmpty() ? cur.title : url);
                o.put("t", System.currentTimeMillis());
                JSONArray w = new JSONArray();
                w.put(o);
                for (int i = 0; i < n.length(); i++) w.put(n.get(i));
                n = w;
                toast("Bookmark added");
            } else if (found && addIfMissing) {
                toast("Bookmark removed");
            }
            bookmarks = n;
        } catch (JSONException ignored) {
        }
        saveSoon();
        pushState();
        pushTop();
    }

    void addHistory(Tab t, String url, String title) {
        if (t.priv || url == null || !url.startsWith("http")) return;
        try {
            long now = System.currentTimeMillis();
            if (history.length() > 0) {
                JSONObject f = history.getJSONObject(0);
                if (url.equals(f.optString("url"))) {
                    f.put("t", now);
                    if (title != null && !title.isEmpty()) f.put("title", title);
                    saveSoon();
                    return;
                }
            }
            JSONObject o = new JSONObject();
            o.put("url", url);
            o.put("title", title == null ? "" : title);
            o.put("t", now);
            JSONArray n = new JSONArray();
            n.put(o);
            for (int i = 0; i < Math.min(history.length(), 1499); i++) n.put(history.get(i));
            history = n;
            saveSoon();
        } catch (JSONException ignored) {
        }
    }

    void deleteHistory(String t, String url) {
        try {
            JSONArray n = new JSONArray();
            for (int i = 0; i < history.length(); i++) {
                JSONObject o = history.getJSONObject(i);
                if (String.valueOf(o.optLong("t")).equals(t) && url.equals(o.optString("url"))) continue;
                n.put(o);
            }
            history = n;
        } catch (JSONException ignored) {
        }
        saveSoon();
        pushState();
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ WebView per tab

    void ensureWebView(Tab t) {
        if (t.wv == null) t.wv = makeWeb(t);
    }

    String userAgent(boolean desktop) {
        if (desktop) return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chromeVer + " Safari/537.36";
        return baseUa;
    }

    void applyDesktop(Tab t) {
        if (t.wv == null) return;
        WebSettings s = t.wv.getSettings();
        s.setUserAgentString(userAgent(t.desktop));
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(t.desktop);
    }

    void applyScripts(Tab t) {
        if (t.wv == null) return;
        try {
            if (t.script != null) {
                t.script.remove();
                t.script = null;
            }
            if (!pageScript.isEmpty() && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                t.script = WebViewCompat.addDocumentStartJavaScript(t.wv, pageScript, Collections.singleton("*"));
            }
        } catch (Throwable ignored) {
        }
    }

    WebView makeWeb(final Tab t) {
        final WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
        w.setBackgroundColor(Color.WHITE);
        t.wv = w;
        applyDesktop(t);
        noDark(w);
        applyScripts(t);

        w.setWebViewClient(new TabClient(t));
        w.setWebChromeClient(new TabChrome(t));
        w.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                download(url, ua, cd, mime);
            }
        });
        w.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                WebView.HitTestResult r = w.getHitTestResult();
                int type = r.getType();
                String extra = r.getExtra();
                if (extra == null) return false;
                if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                        || type == WebView.HitTestResult.IMAGE_TYPE) {
                    showLinkDialog(t, extra, type == WebView.HitTestResult.IMAGE_TYPE);
                    return true;
                }
                return false;
            }
        });
        return w;
    }

    void showLinkDialog(final Tab t, final String url, final boolean image) {
        String[] items = image
                ? new String[]{"Open in new tab", "Copy image URL", "Download image", "Share"}
                : new String[]{"Open in new tab", "Open in private tab", "Copy link", "Share"};
        new AlertDialog.Builder(this).setTitle(host(url)).setItems(items, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int which) {
                if (which == 0) {
                    activate(newTab(url, t.priv));
                } else if (which == 1) {
                    if (image) copy(url);
                    else activate(newTab(url, true));
                } else if (which == 2) {
                    if (image) download(url, userAgent(false), null, null);
                    else copy(url);
                } else share(url);
            }
        }).show();
    }

    static String host(String u) {
        try {
            String h = Uri.parse(u).getHost();
            return h == null ? u : h;
        } catch (Exception e) {
            return u;
        }
    }

    void copy(String s) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("url", s));
        toast("Copied");
    }

    void share(String s) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, s);
        startActivity(Intent.createChooser(i, "Share"));
    }

    // ------------------------------------------------------------------ downloads

    DownloadManager dm() {
        return (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
    }

    void download(String url, String ua, String cd, String mime) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            toast("This file can't be downloaded");
            return;
        }
        try {
            DownloadManager d = dm();
            if (d == null) {
                toast("Download failed");
                return;
            }
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            if (mime != null && !mime.isEmpty()) r.setMimeType(mime);
            String ck = CookieManager.getInstance().getCookie(url);
            if (ck != null) r.addRequestHeader("cookie", ck);
            if (ua != null) r.addRequestHeader("User-Agent", ua);
            String fn = URLUtil.guessFileName(url, cd, mime);
            r.setTitle(fn);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fn);
            long id = d.enqueue(r);
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", fn);
            o.put("url", url);
            o.put("t", System.currentTimeMillis());
            JSONArray n = new JSONArray();
            n.put(o);
            for (int i = 0; i < Math.min(dls.length(), 199); i++) n.put(dls.get(i));
            dls = n;
            saveSoon();
            toast("Download started");
            if (mode.equals("downloads")) pushDownloads();
        } catch (Exception e) {
            toast("Download failed");
        }
    }

    void pushDownloads() {
        if (!uiReady) return;
        DownloadManager d = dm();
        if (d == null) return;
        JSONArray out = new JSONArray();
        JSONArray keep = new JSONArray();
        boolean changed = false;
        for (int i = 0; i < dls.length(); i++) {
            JSONObject r = dls.optJSONObject(i);
            if (r == null) continue;
            Cursor c = null;
            try {
                c = d.query(new DownloadManager.Query().setFilterById(r.optLong("id")));
                if (c != null && c.moveToFirst()) {
                    int st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    long total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    long done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    String lu = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
                    String name = r.optString("name");
                    if (st == DownloadManager.STATUS_SUCCESSFUL && lu != null) {
                        String seg = Uri.parse(lu).getLastPathSegment();
                        if (seg != null && !seg.isEmpty()) name = seg;
                    }
                    String status;
                    if (st == DownloadManager.STATUS_SUCCESSFUL) status = "done";
                    else if (st == DownloadManager.STATUS_FAILED) status = "failed";
                    else if (st == DownloadManager.STATUS_PAUSED) status = "paused";
                    else if (st == DownloadManager.STATUS_PENDING) status = "pending";
                    else status = "running";
                    JSONObject o = new JSONObject();
                    o.put("id", String.valueOf(r.optLong("id")));
                    o.put("name", name);
                    o.put("url", r.optString("url"));
                    o.put("t", r.optLong("t"));
                    o.put("status", status);
                    o.put("total", total);
                    o.put("done", done);
                    out.put(o);
                    keep.put(r);
                } else {
                    changed = true;
                }
            } catch (Exception e) {
                keep.put(r);
            } finally {
                if (c != null) c.close();
            }
        }
        if (changed) {
            dls = keep;
            saveSoon();
        }
        ui.evaluateJavascript("App.setDownloads(" + js(out) + ")", null);
    }

    void startDlPoll() {
        stopDlPoll();
        dlPoll = new Runnable() {
            @Override
            public void run() {
                if (!mode.equals("downloads")) return;
                pushDownloads();
                main.postDelayed(this, 1000);
            }
        };
        main.post(dlPoll);
    }

    void stopDlPoll() {
        if (dlPoll != null) main.removeCallbacks(dlPoll);
        dlPoll = null;
    }

    void openDownload(long id, boolean share) {
        DownloadManager d = dm();
        Uri u = d == null ? null : d.getUriForDownloadedFile(id);
        if (u == null) {
            toast("File not found");
            return;
        }
        String mime = d.getMimeTypeForDownloadedFile(id);
        if (mime == null || mime.isEmpty()) mime = "*/*";
        try {
            if (share) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType(mime);
                i.putExtra(Intent.EXTRA_STREAM, u);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Share"));
            } else {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(u, mime);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
        } catch (Exception e) {
            toast("No app found to open this file");
        }
    }

    void removeDownload(long id, boolean deleteFile) {
        if (deleteFile) {
            DownloadManager d = dm();
            if (d != null) d.remove(id);
        }
        JSONArray n = new JSONArray();
        for (int i = 0; i < dls.length(); i++) {
            JSONObject o = dls.optJSONObject(i);
            if (o != null && o.optLong("id") != id) n.put(o);
        }
        dls = n;
        saveSoon();
        pushDownloads();
    }

    void retryDownload(long id) {
        String url = null;
        for (int i = 0; i < dls.length(); i++) {
            JSONObject o = dls.optJSONObject(i);
            if (o != null && o.optLong("id") == id) url = o.optString("url");
        }
        if (url == null) return;
        removeDownload(id, true);
        download(url, baseUa, null, null);
    }

    void clearFinishedDownloads() {
        DownloadManager d = dm();
        JSONArray n = new JSONArray();
        for (int i = 0; i < dls.length(); i++) {
            JSONObject o = dls.optJSONObject(i);
            if (o == null) continue;
            boolean active = false;
            Cursor c = null;
            try {
                c = d == null ? null : d.query(new DownloadManager.Query().setFilterById(o.optLong("id")));
                if (c != null && c.moveToFirst()) {
                    int st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    active = st == DownloadManager.STATUS_RUNNING || st == DownloadManager.STATUS_PENDING || st == DownloadManager.STATUS_PAUSED;
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.close();
            }
            if (active) n.put(o);
        }
        dls = n;
        saveSoon();
        pushDownloads();
        toast("List cleared");
    }

    boolean handleScheme(WebView view, String url) {
        try {
            if (url.startsWith("intent:")) {
                Intent i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                String fb = i.getStringExtra("browser_fallback_url");
                if (fb != null && fb.startsWith("http")) view.loadUrl(fb);
                return true; // never launch other apps from intent: links
            }
            if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:") || url.startsWith("geo:")) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    static String errorPage(String url, String desc) {
        String safe = url.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
        return "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>"
                + "body{font-family:sans-serif;background:#f6f1e9;color:#1b1a17;display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;margin:0;padding:24px;text-align:center}"
                + "h2{margin:0 0 8px}p{color:#6f6a60;margin:4px 0}a{margin-top:22px;background:linear-gradient(135deg,#6c4dff,#00b8d9);color:#fff;padding:13px 30px;border-radius:26px;text-decoration:none;font-weight:700}"
                + "</style></head><body><h2>Can't open this page</h2><p>" + desc + "</p><p style='font-size:13px;word-break:break-all'>" + safe
                + "</p><a href=\"" + safe + "\">Try again</a></body></html>";
    }

    class TabClient extends WebViewClient {
        final Tab t;

        TabClient(Tab t) {
            this.t = t;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            if (!req.isForMainFrame() && flag("adblock", true)) {
                Uri u = req.getUrl();
                if (AdBlocker.isBlocked(u.toString(), u.getHost())) {
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
            }
            return null;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            String url = req.getUrl().toString();
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:") || url.startsWith("data:")) return false;
            return handleScheme(view, url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            t.url = url;
            t.loading = true;
            t.progress = 5;
            if (!pageScript.isEmpty() && !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                view.evaluateJavascript(pageScript, null);
            }
            if (t == cur) {
                pushTop();
                pushState();
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            t.loading = false;
            t.progress = 100;
            t.url = url;
            String title = view.getTitle();
            t.title = title == null || title.startsWith("http") ? host(url) : title;
            if (!pageScript.isEmpty() && !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                view.evaluateJavascript(pageScript, null);
            }
            addHistory(t, url, t.title);
            saveSoon();
            if (t == cur) {
                pushTop();
                pushState();
            }
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            if (url != null && url.startsWith("http")) t.url = url;
            if (t == cur) {
                pushTop();
                pushState();
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
            if (!req.isForMainFrame()) return;
            String u = req.getUrl().toString();
            view.loadDataWithBaseURL(u, errorPage(u, String.valueOf(err.getDescription())), "text/html", "utf-8", u);
        }
    }

    class TabChrome extends WebChromeClient {
        final Tab t;

        TabChrome(Tab t) {
            this.t = t;
        }

        @Override
        public void onProgressChanged(WebView view, int p) {
            t.progress = p;
            t.loading = p < 100;
            if (t == cur) pushTop();
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            if (title != null && !title.isEmpty()) {
                t.title = title;
                if (t == cur) pushState();
            }
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
            if (!isUserGesture && flag("popups", true)) return false;
            Tab nt = newTab(null, t.priv);
            ensureWebView(nt);
            WebView.WebViewTransport tr = (WebView.WebViewTransport) resultMsg.obj;
            tr.setWebView(nt.wv);
            resultMsg.sendToTarget();
            activate(nt);
            return true;
        }

        @Override
        public void onCloseWindow(WebView window) {
            for (Tab x : new ArrayList<>(tabs)) if (x.wv == window) closeTab(x.id);
        }

        @Override
        public void onShowCustomView(View v, CustomViewCallback cb) {
            if (customView != null) {
                cb.onCustomViewHidden();
                return;
            }
            customView = v;
            customCb = cb;
            v.setBackgroundColor(Color.BLACK);
            root.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        }

        @Override
        public void onHideCustomView() {
            hideCustom();
        }

        @Override
        public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, FileChooserParams params) {
            if (fileCb != null) fileCb.onReceiveValue(null);
            fileCb = cb;
            try {
                startActivityForResult(params.createIntent(), FILE_REQ);
            } catch (Exception e) {
                fileCb = null;
                return false;
            }
            return true;
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, final JsResult r) {
            new AlertDialog.Builder(MainActivity.this).setTitle(host(url)).setMessage(message)
                    .setPositiveButton("OK", (d, w) -> r.confirm()).setOnCancelListener(d -> r.cancel()).show();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final JsResult r) {
            new AlertDialog.Builder(MainActivity.this).setTitle(host(url)).setMessage(message)
                    .setPositiveButton("OK", (d, w) -> r.confirm()).setNegativeButton("Cancel", (d, w) -> r.cancel())
                    .setOnCancelListener(d -> r.cancel()).show();
            return true;
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage m) {
            return true;
        }
    }

    void hideCustom() {
        if (customView == null) return;
        root.removeView(customView);
        if (customCb != null) customCb.onCustomViewHidden();
        customView = null;
        customCb = null;
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        applyTheme();
    }

    // ------------------------------------------------------------------ bridge (UI -> native)

    class Bridge {
        @JavascriptInterface
        public void nav(final String m, final String arg) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    if (m.equals("back")) {
                        activate(cur);
                    } else if (m.equals("tabs")) {
                        if (mode.equals("web") && cur != null) captureThumb(cur);
                        show("tabs", "");
                    } else if (m.equals("search")) {
                        String a = arg == null ? "" : arg;
                        if (a.isEmpty() && mode.equals("web") && cur != null) a = cur.url;
                        show("search", a);
                    } else {
                        show(m, arg);
                    }
                }
            });
        }

        @JavascriptInterface
        public void go(final String text, final String engine) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    String url = resolve(text, engine == null || engine.isEmpty() ? setting("engine", "google") : engine);
                    if (url != null) loadInCur(url);
                }
            });
        }

        @JavascriptInterface
        public void open(final String url) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    loadInCur(url);
                }
            });
        }

        @JavascriptInterface
        public void newTab(final boolean priv) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    activate(MainActivity.this.newTab(null, priv));
                }
            });
        }

        @JavascriptInterface
        public void switchTab(final int id) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    Tab t = findTab(id);
                    if (t != null) activate(t);
                }
            });
        }

        @JavascriptInterface
        public void closeTab(final int id) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.closeTab(id);
                }
            });
        }

        @JavascriptInterface
        public void back() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    if (cur != null && cur.wv != null && cur.wv.canGoBack()) cur.wv.goBack();
                }
            });
        }

        @JavascriptInterface
        public void forward() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    if (cur != null && cur.wv != null && cur.wv.canGoForward()) cur.wv.goForward();
                }
            });
        }

        @JavascriptInterface
        public void openMenu() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    menuOpen = true;
                    setExpanded(true);
                    pushState();
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            ui.evaluateJavascript("App.openMenu()", null);
                        }
                    });
                }
            });
        }

        @JavascriptInterface
        public void closeMenu() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    menuOpen = false;
                    if (mode.equals("web")) setExpanded(false);
                }
            });
        }

        @JavascriptInterface
        public void action(final String name) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    doAction(name);
                }
            });
        }

        @JavascriptInterface
        public void topAction(final String name) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    if (name.equals("bookmark")) toggleBookmark();
                    else doAction(name);
                }
            });
        }

        @JavascriptInterface
        public void setSetting(final String k, final String v) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        settings.put(k, v);
                    } catch (JSONException ignored) {
                    }
                    if (k.equals("themeMode") || k.equals("lightStyle") || k.equals("darkStyle")) {
                        applyTheme();
                    } else if (k.equals("adblock") || k.equals("darkPages")) {
                        rebuildPageScript();
                        for (Tab t : tabs) applyScripts(t);
                        if (cur != null && cur.wv != null) cur.wv.reload();
                    }
                    saveSoon();
                    pushState();
                }
            });
        }

        @JavascriptInterface
        public void delHistory(final String t, final String url) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    deleteHistory(t, url);
                }
            });
        }

        @JavascriptInterface
        public void clearHistory() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    history = new JSONArray();
                    saveSoon();
                    pushState();
                    toast("History cleared");
                }
            });
        }

        @JavascriptInterface
        public void clearData() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    for (Tab t : tabs) if (t.wv != null) t.wv.clearCache(true);
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    WebStorage.getInstance().deleteAllData();
                    toast("Cookies and cache cleared");
                }
            });
        }

        @JavascriptInterface
        public void delBookmark(final String url) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    removeBookmark(url, false);
                }
            });
        }

        @JavascriptInterface
        public void introDone() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    endIntro();
                }
            });
        }

        @JavascriptInterface
        public void dlOpen(final String id) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    openDownload(Long.parseLong(id), false);
                }
            });
        }

        @JavascriptInterface
        public void dlShare(final String id) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    openDownload(Long.parseLong(id), true);
                }
            });
        }

        @JavascriptInterface
        public void dlDelete(final String id) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    removeDownload(Long.parseLong(id), true);
                }
            });
        }

        @JavascriptInterface
        public void dlRetry(final String id) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    retryDownload(Long.parseLong(id));
                }
            });
        }

        @JavascriptInterface
        public void dlClear() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    clearFinishedDownloads();
                }
            });
        }

        @JavascriptInterface
        public void suggest(final String q) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        URL u = new URL("https://suggestqueries.google.com/complete/search?client=firefox&q=" + URLEncoder.encode(q, "UTF-8"));
                        HttpURLConnection c = (HttpURLConnection) u.openConnection();
                        c.setConnectTimeout(3000);
                        c.setReadTimeout(3000);
                        c.setRequestProperty("User-Agent", baseUa);
                        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                        r.close();
                        final JSONArray s = new JSONArray(sb.toString()).getJSONArray(1);
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                ui.evaluateJavascript("App.suggestions(" + JSONObject.quote(q) + "," + s.toString() + ")", null);
                            }
                        });
                    } catch (Exception ignored) {
                    }
                }
            }).start();
        }
    }

    void doAction(String name) {
        switch (name) {
            case "reload":
                if (cur != null && cur.wv != null) cur.wv.reload();
                break;
            case "stop":
                if (cur != null && cur.wv != null) cur.wv.stopLoading();
                break;
            case "bookmark":
                toggleBookmark();
                break;
            case "desktop":
                if (cur != null) {
                    cur.desktop = !cur.desktop;
                    applyDesktop(cur);
                    if (cur.wv != null) cur.wv.reload();
                    pushState();
                    toast(cur.desktop ? "Desktop site on" : "Desktop site off");
                }
                break;
            case "share":
                if (cur != null && !isBlank(cur)) share(cur.url);
                break;
            case "home":
                goHome();
                break;
            case "downloads":
                show("downloads", "");
                break;
            case "exit":
                saveNow();
                finishAndRemoveTask();
                break;
            default:
                break;
        }
    }
}
