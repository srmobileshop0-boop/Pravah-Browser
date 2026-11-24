package com.pravah.browser;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
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
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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

    static final int FILE_REQ = 77, PERM_REQ = 78, LOCK_REQ = 79, WALL_REQ = 80, ROLE_REQ = 81, AUTH_REQ = 82, EXP_REQ = 83, IMP_REQ = 84, PERM_WEB = 85, PERM_GEO = 86;
    static final String[][] THEMES = {
            {"daylight", "#FFFFFF", "0"}, {"sky", "#EAF4FF", "0"},
            {"midnight", "#0F1226", "1"}, {"amoled", "#000000", "1"}};
    static final String[] ENGINE_URLS = {
            "google", "https://www.google.com/search?q=",
            "bing", "https://www.bing.com/search?q=",
            "yahoo", "https://search.yahoo.com/search?p=",
            "duckduckgo", "https://duckduckgo.com/?q=",
            "brave", "https://search.brave.com/search?q=",
            "ecosia", "https://www.ecosia.org/search?q="};

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
    View spacerView;
    int settingsDepth = 0;
    volatile boolean uiOverlay = false;
    boolean manualFs = false;
    String lastLoadUrl = "";
    long lastLoadAt = 0;
    boolean toolbarsHidden = false, unlocked = false, authBusy = false;
    long toolbarToggleAt = 0, bgAt = 0;
    static final String[][] SETTING_DEFAULTS = {
            {"addrBar", "top"}, {"scrollMode", "always"}, {"barBtn", "home"}, {"swipeFs", "true"}, {"swipeTabs", "true"}, {"fullUrl", "false"},
            {"homeUrl", ""}, {"mReader", "true"}, {"mZoom", "true"}, {"mFind", "true"}, {"mDesktop", "true"}, {"mA2hs", "true"}, {"mDev", "false"}, {"uiFont", "default"}, {"wall", "none"},
            {"textZoom", "100"}, {"autoplay", "true"}, {"block3p", "false"}, {"askDl", "false"},
            {"lock", "false"}, {"forceZoom", "false"}, {"fsRotate", "auto"},
            {"bgPlay", "true"}, {"vidToolbar", "true"}, {"vidMenu", "true"}, {"ytRecs", "false"}, {"ytShorts", "false"},
            {"httpsOnly", "false"}, {"fpProtect", "false"}, {"jsOn", "true"}, {"cookiesOn", "true"}, {"stopRedirect", "true"},
            {"permLoc", "ask"}, {"permCam", "ask"}, {"permMic", "ask"}, {"safeBrowse", "true"}, {"dnt", "true"},
            {"deamp", "true"}, {"gpc", "true"}, {"webrtc", "default"}, {"lockPriv", "false"}, {"lockHist", "false"},
            {"suggest", "true"}, {"touchSearch", "false"}, {"customEngines", "[]"}, {"delExitHist", "false"},
            {"delExitCookies", "false"}, {"delExitCache", "false"}, {"dlDest", "public"}, {"profName", ""}, {"profEmail", ""}};
    String privJs = "";
    JSONArray playlist = new JSONArray();
    Runnable pendingAuth;
    long lastAuthAt = 0;
    PermissionRequest pendingPerm;
    GeolocationPermissions.Callback pendingGeoCb;
    String pendingGeoOrigin;

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        sp = getSharedPreferences("pravah", MODE_PRIVATE);
        // The start-up intro plays only the first time the app is ever opened.
        introActive = !sp.getBoolean("introSeen14", false);
        if (introActive) sp.edit().putBoolean("introSeen14", true).apply();
        loadData();
        adJs = readAsset("adblock.js");
        darkJs = readAsset("darkpage.js");
        privJs = readAsset("privacy.js");
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

        if (Build.VERSION.SDK_INT >= 33 && !sp.getBoolean("notifAsked", false)) {
            sp.edit().putBoolean("notifAsked", true).apply();
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERM_REQ);
            }
        }
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
        if (flag("bgPlay", true)) {
            if (!isFinishing()) startKeepAlive();
        } else if (cur != null && cur.wv != null) {
            cur.wv.onPause();
        }
    }

    void startKeepAlive() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am == null || !am.isMusicActive()) return;
            Intent i = new Intent(this, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Throwable ignored) {
        }
    }

    void stopKeepAlive() {
        try {
            stopService(new Intent(this, KeepAliveService.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        bgAt = SystemClock.elapsedRealtime();
    }

    @Override
    protected void onResume() {
        super.onResume();
        stopKeepAlive();
        if (cur != null && cur.wv != null) cur.wv.onResume();
        pushState();
        if (!flag("lock", false)) {
            unlocked = true;
            return;
        }
        if (!unlocked || (SystemClock.elapsedRealtime() - bgAt > 30000 && SystemClock.elapsedRealtime() - lastAuthAt > 30000)) {
            unlocked = false;
            requireUnlock();
        }
    }

    void requireUnlock() {
        if (authBusy || root == null) return;
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) {
            unlocked = true;
            return;
        }
        Intent i = km.createConfirmDeviceCredentialIntent("Pravah Browser", "Unlock to continue");
        if (i == null) {
            unlocked = true;
            return;
        }
        authBusy = true;
        root.setVisibility(View.INVISIBLE);
        try {
            startActivityForResult(i, LOCK_REQ);
        } catch (Exception e) {
            authBusy = false;
            unlocked = true;
            root.setVisibility(View.VISIBLE);
        }
    }

    boolean isDefaultBrowser() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"));
            android.content.pm.ResolveInfo ri = getPackageManager().resolveActivity(i, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) wipeOnExit();
        stopKeepAlive();
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
        if (uiOverlay) {
            ui.evaluateJavascript("App.closeOverlay()", null);
            return;
        }
        if (manualFs && toolbarsHidden) {
            setToolbars(false);
            return;
        }
        if (mode.equals("settings") && settingsDepth > 0) {
            ui.evaluateJavascript("App.settingsBack()", null);
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
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        boolean ok = res.length > 0;
        for (int r : res) if (r != android.content.pm.PackageManager.PERMISSION_GRANTED) ok = false;
        if (req == PERM_WEB && pendingPerm != null) {
            try {
                if (ok) pendingPerm.grant(pendingPerm.getResources());
                else pendingPerm.deny();
            } catch (Exception ignored) {
            }
            pendingPerm = null;
        } else if (req == PERM_GEO && pendingGeoCb != null) {
            pendingGeoCb.invoke(pendingGeoOrigin, ok, false);
            pendingGeoCb = null;
        } else {
            super.onRequestPermissionsResult(req, perms, res);
        }
    }

    void authThen(Runnable r) {
        if (SystemClock.elapsedRealtime() - lastAuthAt < 60000) {
            r.run();
            return;
        }
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) {
            r.run();
            return;
        }
        Intent i = km.createConfirmDeviceCredentialIntent("Pravah Browser", "Confirm to continue");
        if (i == null) {
            r.run();
            return;
        }
        pendingAuth = r;
        try {
            startActivityForResult(i, AUTH_REQ);
        } catch (Exception e) {
            pendingAuth = null;
            r.run();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == FILE_REQ && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
        } else if (req == LOCK_REQ) {
            authBusy = false;
            if (res == RESULT_OK) {
                unlocked = true;
                lastAuthAt = SystemClock.elapsedRealtime();
                root.setVisibility(View.VISIBLE);
            } else {
                finish();
            }
        } else if (req == WALL_REQ) {
            if (res == RESULT_OK && data != null && data.getData() != null) saveWallpaper(data.getData());
        } else if (req == ROLE_REQ) {
            pushState();
        } else if (req == AUTH_REQ) {
            Runnable r = pendingAuth;
            pendingAuth = null;
            if (res == RESULT_OK) {
                lastAuthAt = SystemClock.elapsedRealtime();
                if (r != null) r.run();
            }
        } else if (req == EXP_REQ) {
            if (res == RESULT_OK && data != null && data.getData() != null) writeBookmarks(data.getData());
        } else if (req == IMP_REQ) {
            if (res == RESULT_OK && data != null && data.getData() != null) readBookmarks(data.getData());
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
        content = new FrameLayout(this);
        column.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        spacerView = new View(this);
        column.addView(spacerView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));
        applyBarPos();

        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ui = makeUiWebView();
        ui.loadUrl("file:///android_asset/ui.html");
        root.addView(ui, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM));
        setContentView(root);
    }

    /** Address bar on top (default) or just above the bottom bar. */
    void applyBarPos() {
        if (top == null || column == null) return;
        boolean bottom = setting("addrBar", "top").equals("bottom");
        column.removeView(top);
        column.addView(top, bottom ? 1 : 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));
    }

    void setToolbars(boolean hidden) {
        if (hidden == toolbarsHidden) return;
        toolbarsHidden = hidden;
        if (!hidden) manualFs = false;
        toolbarToggleAt = SystemClock.elapsedRealtime();
        top.setVisibility(hidden || !mode.equals("web") ? View.GONE : View.VISIBLE);
        spacerView.setVisibility(hidden ? View.GONE : View.VISIBLE);
        ui.setTranslationY(hidden ? dp(76) : 0);
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
                    pushWallpaper();
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

    static final String ZOOM_JS = "(function(){function f(){try{var m=document.querySelector('meta[name=viewport]');if(!m)return;"
            + "var c=(m.getAttribute('content')||'').replace(/(user-scalable|maximum-scale|minimum-scale)\\s*=\\s*[^,;]*[,;]?\\s*/gi,'');"
            + "m.setAttribute('content',c+(c?', ':'')+'user-scalable=yes, maximum-scale=10');}catch(e){}}"
            + "document.addEventListener('DOMContentLoaded',f);window.addEventListener('load',f);})();";

    void rebuildPageScript() {
        StringBuilder sb = new StringBuilder();
        if (flag("adblock", true) && !adJs.isEmpty()) sb.append(adJs).append("\n;\n");
        if (flag("darkPages", false) && !darkJs.isEmpty()) sb.append(darkJs).append("\n;\n");
        if (flag("forceZoom", false)) sb.append(ZOOM_JS).append("\n;\n");
        if (!privJs.isEmpty()) {
            sb.append("window.__pvCfg={fp:").append(flag("fpProtect", false))
                    .append(",dnt:").append(flag("dnt", true))
                    .append(",gpc:").append(flag("gpc", true))
                    .append(",deamp:").append(flag("deamp", true))
                    .append(",rtc:").append(setting("webrtc", "default").equals("off"))
                    .append(",bg:").append(flag("bgPlay", true))
                    .append(",vtool:").append(flag("vidToolbar", true))
                    .append(",vmenu:").append(flag("vidMenu", true))
                    .append(",ytr:").append(flag("ytRecs", false))
                    .append(",yts:").append(flag("ytShorts", false))
                    .append(",touch:").append(flag("touchSearch", false))
                    .append("};\n").append(privJs).append("\n;\n");
        }
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
            playlist = new JSONArray(sp.getString("playlist", "[]"));
        } catch (JSONException e) {
            playlist = new JSONArray();
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
                    .putString("playlist", playlist.toString())
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
        toolbarsHidden = false;
        spacerView.setVisibility(View.VISIBLE);
        ui.setTranslationY(0);
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
            for (String[] d : SETTING_DEFAULTS) st.put(d[0], setting(d[0], d[1]));
            o.put("settings", st);
            JSONObject ab = new JSONObject();
            String ver = "1.3";
            try {
                ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception ignored) {
            }
            ab.put("version", ver);
            ab.put("engine", chromeVer);
            ab.put("android", Build.VERSION.RELEASE);
            ab.put("isDefault", isDefaultBrowser());
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
            o.put("playlist", playlist);
            if (mode.equals("history") || mode.equals("search") || mode.equals("settings")) {
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
            o.put("barBtn", setting("barBtn", "home"));
            o.put("fullUrl", flag("fullUrl", false));
            o.put("swipeFs", flag("swipeFs", true));
            o.put("swipeTabs", flag("swipeTabs", true));
            o.put("pos", setting("addrBar", "top"));
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
        if (engine != null && engine.matches("^c\\d+$")) {
            try {
                JSONArray ce = new JSONArray(setting("customEngines", "[]"));
                base = ce.getJSONObject(Integer.parseInt(engine.substring(1))).getString("u");
            } catch (Exception ignored) {
            }
        }
        String enc = in;
        try {
            enc = URLEncoder.encode(in, "UTF-8");
        } catch (Exception ignored) {
        }
        return base.contains("%s") ? base.replace("%s", enc) : base + enc;
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
        url = upgrade(url);
        t.url = url;
        if (dupLoad(url)) {
            show("web", "");
            return;
        }
        t.wv.loadUrl(url);
        show("web", "");
    }

    /** true when this exact URL was just loaded (stops a page opening twice). */
    boolean dupLoad(String url) {
        long now = SystemClock.elapsedRealtime();
        if (url != null && url.equals(lastLoadUrl) && now - lastLoadAt < 1500) return true;
        lastLoadUrl = url;
        lastLoadAt = now;
        return false;
    }

    /** A link that wanted a new window: open it once, in the same tab; ads are dropped. */
    void openLinkHere(Tab t, String url) {
        if (url == null || t == null || url.isEmpty()) return;
        String h = host(url);
        if (flag("adblock", true) && (AdBlocker.isAdHost(h) || AdBlocker.isAdClick(url, h))) return;
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            if (t.wv != null && !url.startsWith("about:") && !url.startsWith("javascript:")) handleScheme(t.wv, url);
            return;
        }
        url = upgrade(url);
        if (dupLoad(url)) return;
        ensureWebView(t);
        t.url = url;
        t.wv.loadUrl(url);
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

    void applyWebPrefs(Tab t) {
        if (t.wv == null) return;
        WebSettings s = t.wv.getSettings();
        int z = 100;
        try {
            z = Integer.parseInt(setting("textZoom", "100"));
        } catch (Exception ignored) {
        }
        s.setTextZoom(Math.max(50, Math.min(250, z)));
        s.setMediaPlaybackRequiresUserGesture(!flag("autoplay", true));
        s.setJavaScriptEnabled(flag("jsOn", true));
        CookieManager.getInstance().setAcceptCookie(flag("cookiesOn", true));
        CookieManager.getInstance().setAcceptThirdPartyCookies(t.wv, !flag("block3p", false));
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                WebSettingsCompat.setSafeBrowsingEnabled(s, flag("safeBrowse", true));
            }
        } catch (Throwable ignored) {
        }
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
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        CookieManager.getInstance().setAcceptCookie(true);
        w.setBackgroundColor(Color.WHITE);
        t.wv = w;
        applyWebPrefs(t);
        w.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int x, int y, int ox, int oy) {
                if (!mode.equals("web") || cur == null || cur.wv != v || menuOpen) return;
                boolean autoHide = setting("scrollMode", "always").equals("hide");
                if (!autoHide && !toolbarsHidden) return;
                if (SystemClock.elapsedRealtime() - toolbarToggleAt < 500) return;
                int dy = y - oy;
                if (y <= dp(4) || dy < -dp(10)) setToolbars(false);
                else if (autoHide && dy > dp(12)) setToolbars(true);
            }
        });
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

    void download(final String url, final String ua, final String cd, final String mime) {
        if (flag("askDl", false) && url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
            String fn = URLUtil.guessFileName(url, cd, mime);
            new AlertDialog.Builder(this).setTitle("Download file?").setMessage(fn)
                    .setPositiveButton("Download", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            startDownload(url, ua, cd, mime);
                        }
                    }).setNegativeButton("Cancel", null).show();
            return;
        }
        startDownload(url, ua, cd, mime);
    }

    void startDownload(String url, String ua, String cd, String mime) {
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
            if (setting("dlDest", "public").equals("external")) {
                try {
                    startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Download with"));
                } catch (Exception e) {
                    toast("No downloader app found");
                }
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
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, setting("dlDest", "public").equals("pravah") ? "Pravah/" + fn : fn);
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
            if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:") || url.startsWith("geo:")) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }
            boolean allow = !flag("stopRedirect", true);
            if (url.startsWith("intent:")) {
                Intent i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                String fb = i.getStringExtra("browser_fallback_url");
                if (allow) {
                    i.setComponent(null);
                    i.setSelector(null);
                    i.addCategory(Intent.CATEGORY_BROWSABLE);
                    try {
                        startActivity(i);
                        return true;
                    } catch (Exception ignored) {
                    }
                }
                if (fb != null && fb.startsWith("http")) view.loadUrl(fb);
                else if (!allow) toast("Blocked an app redirect");
                return true;
            }
            if (allow) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } else {
                toast("Blocked an app redirect");
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    String upgrade(String url) {
        if (url == null || !url.startsWith("http://") || !flag("httpsOnly", false)) return url;
        String h = host(url);
        if (h.equals("localhost") || h.endsWith(".local") || h.matches("^[\\d.:]+$")) return url;
        return "https://" + url.substring(7);
    }

    void handleAct(Uri u) {
        String a = u.getHost() == null ? "" : u.getHost();
        if (a.equals("download")) {
            String v = u.getQueryParameter("u");
            if (v != null) download(v, userAgent(false), null, null);
        } else if (a.equals("playlist")) {
            String v = u.getQueryParameter("v");
            addPlaylist(u.getQueryParameter("u"), u.getQueryParameter("t"));
            if ("1".equals(u.getQueryParameter("dl")) && v != null && !v.isEmpty()) download(v, userAgent(false), null, null);
        } else if (a.equals("search")) {
            String q = u.getQueryParameter("q");
            if (q != null && !q.isEmpty()) {
                String url = resolve(q, setting("engine", "google"));
                if (url != null) activate(newTab(url, cur != null && cur.priv));
            }
        }
    }

    void addPlaylist(String url, String title) {
        if (url == null || !url.startsWith("http")) return;
        try {
            JSONArray n = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("url", url);
            o.put("title", title == null || title.isEmpty() ? host(url) : title);
            o.put("t", System.currentTimeMillis());
            n.put(o);
            for (int i = 0; i < playlist.length(); i++) {
                JSONObject x = playlist.getJSONObject(i);
                if (!url.equals(x.optString("url"))) n.put(x);
            }
            playlist = n;
            toast("Added to Playlist");
            saveSoon();
            pushState();
        } catch (JSONException ignored) {
        }
    }

    void wipe(boolean h, boolean ck, boolean cache, boolean bm, boolean dl, long since) {
        try {
            if (h) {
                if (since <= 0) {
                    history = new JSONArray();
                } else {
                    JSONArray n = new JSONArray();
                    for (int i = 0; i < history.length(); i++) {
                        JSONObject o = history.optJSONObject(i);
                        if (o != null && o.optLong("t") < since) n.put(o);
                    }
                    history = n;
                }
            }
            if (ck) {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
                WebStorage.getInstance().deleteAllData();
            }
            if (cache) for (Tab t : tabs) if (t.wv != null) t.wv.clearCache(true);
            if (bm) bookmarks = new JSONArray();
            if (dl) dls = new JSONArray();
        } catch (Throwable ignored) {
        }
        saveSoon();
        pushState();
    }

    void wipeOnExit() {
        boolean h = flag("delExitHist", false), c = flag("delExitCookies", false), ca = flag("delExitCache", false);
        if (h || c || ca) {
            wipe(h, c, ca, false, false, 0);
            saveNow();
        }
    }

    void clearSite(String h) {
        try {
            JSONArray n = new JSONArray();
            for (int i = 0; i < history.length(); i++) {
                JSONObject o = history.getJSONObject(i);
                if (!host(o.optString("url")).replaceFirst("^www\\.", "").equals(h)) n.put(o);
            }
            history = n;
            CookieManager cm = CookieManager.getInstance();
            String[] bases = {"https://" + h, "https://www." + h, "http://" + h};
            for (String b : bases) {
                String ck = cm.getCookie(b);
                if (ck == null) continue;
                for (String part : ck.split(";")) {
                    int eq = part.indexOf('=');
                    String name = (eq > 0 ? part.substring(0, eq) : part).trim();
                    if (name.isEmpty()) continue;
                    cm.setCookie(b, name + "=; Max-Age=0; Path=/");
                    cm.setCookie(b, name + "=; Max-Age=0; Path=/; Domain=" + h);
                }
            }
            cm.flush();
        } catch (Throwable ignored) {
        }
        saveSoon();
        pushState();
    }

    void writeBookmarks(Uri uri) {
        try {
            StringBuilder sb = new StringBuilder("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n");
            for (int i = 0; i < bookmarks.length(); i++) {
                JSONObject o = bookmarks.optJSONObject(i);
                if (o == null) continue;
                sb.append("<DT><A HREF=\"").append(esc(o.optString("url"))).append("\" ADD_DATE=\"").append(o.optLong("t") / 1000).append("\">")
                        .append(esc(o.optString("title"))).append("</A>\n");
            }
            sb.append("</DL><p>\n");
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) throw new Exception("open");
            os.write(sb.toString().getBytes("UTF-8"));
            os.close();
            toast("Bookmarks exported");
        } catch (Throwable e) {
            toast("Couldn't export bookmarks");
        }
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String unesc(String s) {
        return s.replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'").replace("&amp;", "&");
    }

    void readBookmarks(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new Exception("open");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            String txt = bo.toString("UTF-8");
            Matcher m = Pattern.compile("<A[^>]*?HREF=\"([^\"]+)\"[^>]*>(.*?)</A>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(txt);
            int added = 0;
            while (m.find()) {
                String u = unesc(m.group(1)).trim();
                if (!u.startsWith("http") || isBookmarked(u)) continue;
                JSONObject o = new JSONObject();
                o.put("url", u);
                String t = unesc(m.group(2).replaceAll("<[^>]*>", "")).trim();
                o.put("title", t.isEmpty() ? u : t);
                o.put("t", System.currentTimeMillis());
                JSONArray w = new JSONArray();
                w.put(o);
                for (int i = 0; i < bookmarks.length(); i++) w.put(bookmarks.get(i));
                bookmarks = w;
                added++;
            }
            saveSoon();
            pushState();
            toast(added + " bookmarks imported");
        } catch (Throwable e) {
            toast("Couldn't import bookmarks");
        }
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
            if (req.isForMainFrame() && flag("adblock", true)) {
                Uri mu = req.getUrl();
                if (AdBlocker.isAdClick(mu.toString(), mu.getHost()) || AdBlocker.isAdHost(mu.getHost())) {
                    return new WebResourceResponse("text/html", "utf-8", new ByteArrayInputStream("<html><body></body></html>".getBytes()));
                }
            }
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
            if (url.startsWith("pravah-act:")) {
                handleAct(req.getUrl());
                return true;
            }
            if (flag("adblock", true)) {
                String uh = req.getUrl().getHost();
                if (AdBlocker.isAdClick(url, uh) || AdBlocker.isAdHost(uh)) return true; // ad click: do nothing
                // a page that bounces you to an ad all by itself (no tap from you) is stopped
                if (!req.hasGesture() && !req.isRedirect() && AdBlocker.looksLikeAdRedirect(url)) return true;
            }
            if (url.startsWith("http://") && flag("httpsOnly", false)) {
                String up = upgrade(url);
                if (!up.equals(url)) {
                    view.loadUrl(up);
                    return true;
                }
            }
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
            if (isDialog) {
                Tab nt = newTab(null, t.priv);
                ensureWebView(nt);
                WebView.WebViewTransport tr = (WebView.WebViewTransport) resultMsg.obj;
                tr.setWebView(nt.wv);
                resultMsg.sendToTarget();
                activate(nt);
                return true;
            }
            // Normal link / target=_blank: catch the address and open it ONCE in this same tab.
            final Tab src = t;
            final boolean[] fin = new boolean[1];
            final WebView tmp = new WebView(MainActivity.this);
            tmp.setWebViewClient(new WebViewClient() {
                void take(final String url) {
                    if (fin[0] || url == null || url.equals("about:blank")) return;
                    fin[0] = true;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                tmp.stopLoading();
                                tmp.destroy();
                            } catch (Throwable ignored) {
                            }
                            openLinkHere(src, url);
                        }
                    });
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                    take(r.getUrl().toString());
                    return true;
                }

                @Override
                public void onPageStarted(WebView v, String url, Bitmap favicon) {
                    take(url);
                }
            });
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (fin[0]) return;
                    fin[0] = true;
                    try {
                        tmp.stopLoading();
                        tmp.destroy();
                    } catch (Throwable ignored) {
                    }
                }
            }, 8000);
            WebView.WebViewTransport tr = (WebView.WebViewTransport) resultMsg.obj;
            tr.setWebView(tmp);
            resultMsg.sendToTarget();
            return true;
        }

        @Override
        public void onPermissionRequest(final PermissionRequest r) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    boolean cam = false, mic = false;
                    for (String x : r.getResources()) {
                        if (x.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) cam = true;
                        else if (x.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) mic = true;
                    }
                    if (!cam && !mic) {
                        r.deny();
                        return;
                    }
                    if ((cam && setting("permCam", "ask").equals("block")) || (mic && setting("permMic", "ask").equals("block"))) {
                        r.deny();
                        return;
                    }
                    final boolean fc = cam, fm = mic;
                    String what = fc && fm ? "camera and microphone" : fc ? "camera" : "microphone";
                    new AlertDialog.Builder(MainActivity.this).setTitle(host(r.getOrigin().toString()))
                            .setMessage("Allow this site to use your " + what + "?")
                            .setPositiveButton("Allow", new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int w) {
                                    ArrayList<String> need = new ArrayList<>();
                                    if (fc && checkSelfPermission(Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                                        need.add(Manifest.permission.CAMERA);
                                    if (fm && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                                        need.add(Manifest.permission.RECORD_AUDIO);
                                    if (need.isEmpty()) {
                                        r.grant(r.getResources());
                                    } else {
                                        pendingPerm = r;
                                        requestPermissions(need.toArray(new String[0]), PERM_WEB);
                                    }
                                }
                            })
                            .setNegativeButton("Block", new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int w) {
                                    r.deny();
                                }
                            })
                            .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(android.content.DialogInterface d) {
                                    r.deny();
                                }
                            }).show();
                }
            });
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback cb) {
            if (setting("permLoc", "ask").equals("block")) {
                cb.invoke(origin, false, false);
                return;
            }
            new AlertDialog.Builder(MainActivity.this).setTitle(host(origin)).setMessage("Allow this site to use your location?")
                    .setPositiveButton("Allow", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                cb.invoke(origin, true, false);
                            } else {
                                pendingGeoCb = cb;
                                pendingGeoOrigin = origin;
                                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERM_GEO);
                            }
                        }
                    })
                    .setNegativeButton("Block", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            cb.invoke(origin, false, false);
                        }
                    })
                    .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(android.content.DialogInterface d) {
                            cb.invoke(origin, false, false);
                        }
                    }).show();
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
            setRequestedOrientation(setting("fsRotate", "auto").equals("landscape")
                    ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR);
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
                    if ((m.equals("history") || m.equals("downloads")) && flag("lockHist", false) && !mode.equals("settings")) {
                        final String mm = m, aa = arg;
                        authThen(new Runnable() {
                            @Override
                            public void run() {
                                show(mm, aa);
                            }
                        });
                    } else if (m.equals("back")) {
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
                    if (priv && flag("lockPriv", false)) {
                        authThen(new Runnable() {
                            @Override
                            public void run() {
                                activate(MainActivity.this.newTab(null, true));
                            }
                        });
                    } else {
                        activate(MainActivity.this.newTab(null, priv));
                    }
                }
            });
        }

        @JavascriptInterface
        public void switchTab(final int id) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    final Tab t = findTab(id);
                    if (t == null) return;
                    if (t.priv && flag("lockPriv", false) && t != cur) {
                        authThen(new Runnable() {
                            @Override
                            public void run() {
                                activate(t);
                            }
                        });
                    } else {
                        activate(t);
                    }
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
                    setToolbars(false);
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
                    } else if (k.equals("addrBar")) {
                        applyBarPos();
                        pushTop();
                    } else if (k.equals("barBtn") || k.equals("fullUrl") || k.equals("swipeFs") || k.equals("swipeTabs")) {
                        pushTop();
                    } else if (k.equals("scrollMode")) {
                        if (!v.equals("hide")) setToolbars(false);
                    } else if (k.equals("textZoom") || k.equals("autoplay") || k.equals("block3p") || k.equals("jsOn")
                            || k.equals("cookiesOn") || k.equals("safeBrowse")) {
                        for (Tab t : tabs) applyWebPrefs(t);
                    } else if (k.equals("lock")) {
                        if (v.equals("true")) {
                            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                            if (km == null || !km.isDeviceSecure()) {
                                try {
                                    settings.put("lock", "false");
                                } catch (JSONException ignored) {
                                }
                                toast("Set a screen lock (PIN, pattern or password) on your phone first");
                            } else {
                                unlocked = true;
                            }
                        }
                    } else if (k.equals("wall")) {
                        if (v.equals("custom")) pushWallpaper();
                    } else if (k.equals("adblock") || k.equals("darkPages") || k.equals("forceZoom") || k.equals("fpProtect")
                            || k.equals("dnt") || k.equals("gpc") || k.equals("deamp") || k.equals("webrtc") || k.equals("bgPlay")
                            || k.equals("vidToolbar") || k.equals("vidMenu") || k.equals("ytRecs") || k.equals("ytShorts")
                            || k.equals("touchSearch")) {
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
        public void overlay(boolean on) {
            uiOverlay = on;
        }

        @JavascriptInterface
        public void settingsDepth(final int d) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    settingsDepth = d;
                }
            });
        }

        @JavascriptInterface
        public void pickWallpaper() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                        i.setType("image/*");
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        startActivityForResult(Intent.createChooser(i, "Choose wallpaper"), WALL_REQ);
                    } catch (Exception e) {
                        toast("No gallery app found");
                    }
                }
            });
        }

        @JavascriptInterface
        public void delPlaylist(final String url) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        JSONArray n = new JSONArray();
                        for (int i = 0; i < playlist.length(); i++) {
                            JSONObject o = playlist.getJSONObject(i);
                            if (!url.equals(o.optString("url"))) n.put(o);
                        }
                        playlist = n;
                    } catch (JSONException ignored) {
                    }
                    saveSoon();
                    pushState();
                }
            });
        }

        @JavascriptInterface
        public void deleteData(final String json) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        JSONObject o = new JSONObject(json);
                        wipe(o.optBoolean("hist"), o.optBoolean("cookies"), o.optBoolean("cache"), o.optBoolean("bms"), o.optBoolean("dls"), o.optLong("since", 0));
                        toast("Selected data deleted");
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        @JavascriptInterface
        public void clearSite(final String h) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.clearSite(h);
                    toast("Data for " + h + " deleted");
                }
            });
        }

        @JavascriptInterface
        public void exportBookmarks() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("text/html");
                        i.putExtra(Intent.EXTRA_TITLE, "pravah-bookmarks.html");
                        startActivityForResult(i, EXP_REQ);
                    } catch (Exception e) {
                        toast("No file manager found");
                    }
                }
            });
        }

        @JavascriptInterface
        public void importBookmarks() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        startActivityForResult(i, IMP_REQ);
                    } catch (Exception e) {
                        toast("No file manager found");
                    }
                }
            });
        }

        @JavascriptInterface
        public void clearBookmarks() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    bookmarks = new JSONArray();
                    saveSoon();
                    pushState();
                    toast("Bookmarks cleared");
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
        public boolean skipIntro() {
            return !introActive;
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

    File wallFile() {
        return new File(getFilesDir(), "wallpaper.jpg");
    }

    void saveWallpaper(final Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    InputStream in = getContentResolver().openInputStream(uri);
                    BitmapFactory.decodeStream(in, null, o);
                    if (in != null) in.close();
                    int sample = 1;
                    while (o.outHeight / (sample * 2) >= 1280 && o.outWidth / (sample * 2) >= 720) sample *= 2;
                    BitmapFactory.Options o2 = new BitmapFactory.Options();
                    o2.inSampleSize = sample;
                    in = getContentResolver().openInputStream(uri);
                    Bitmap b = BitmapFactory.decodeStream(in, null, o2);
                    if (in != null) in.close();
                    if (b == null) throw new Exception("decode");
                    FileOutputStream fo = new FileOutputStream(wallFile());
                    b.compress(Bitmap.CompressFormat.JPEG, 82, fo);
                    fo.close();
                    b.recycle();
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                settings.put("wall", "custom");
                            } catch (JSONException ignored) {
                            }
                            saveSoon();
                            pushWallpaper();
                            pushState();
                            toast("Wallpaper set");
                        }
                    });
                } catch (Throwable e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            toast("Couldn't use that picture");
                        }
                    });
                }
            }
        }).start();
    }

    void pushWallpaper() {
        if (!uiReady) return;
        File f = wallFile();
        if (!f.exists()) return;
        try {
            FileInputStream fi = new FileInputStream(f);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = fi.read(buf)) > 0) bo.write(buf, 0, n);
            fi.close();
            String d = "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
            ui.evaluateJavascript("App.setWallpaper(" + JSONObject.quote(d) + ")", null);
        } catch (Throwable ignored) {
        }
    }

    void runAsset(String name) {
        if (cur == null || cur.wv == null || isBlank(cur)) return;
        String code = readAsset(name);
        if (code != null && !code.isEmpty()) cur.wv.evaluateJavascript(code, null);
    }

    void zoomDialog() {
        final String[] labels = {"50%", "75%", "90%", "100%", "110%", "125%", "150%", "175%", "200%", "250%"};
        final String[] vals = {"50", "75", "90", "100", "110", "125", "150", "175", "200", "250"};
        int sel = 3;
        String curv = setting("textZoom", "100");
        for (int i = 0; i < vals.length; i++) if (vals[i].equals(curv)) sel = i;
        new AlertDialog.Builder(this)
                .setTitle("Page zoom")
                .setSingleChoiceItems(labels, sel, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        new Bridge().setSetting("textZoom", vals[which]);
                        d.dismiss();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void addToHome() {
        if (cur == null || isBlank(cur) || cur.url == null || cur.url.isEmpty()) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                android.content.pm.ShortcutManager sm = (android.content.pm.ShortcutManager) getSystemService(Context.SHORTCUT_SERVICE);
                if (sm != null && sm.isRequestPinShortcutSupported()) {
                    Intent vi = new Intent(Intent.ACTION_VIEW, Uri.parse(cur.url));
                    vi.setClass(this, MainActivity.class);
                    String label = (cur.title == null || cur.title.isEmpty()) ? host(cur.url) : cur.title;
                    if (label.length() > 24) label = label.substring(0, 24);
                    android.content.pm.ShortcutInfo info = new android.content.pm.ShortcutInfo.Builder(this, "pv" + Math.abs(cur.url.hashCode()))
                            .setShortLabel(label)
                            .setLongLabel(label)
                            .setIcon(android.graphics.drawable.Icon.createWithResource(this, getApplicationInfo().icon))
                            .setIntent(vi)
                            .build();
                    sm.requestPinShortcut(info, null);
                    return;
                }
            }
            toast("Your launcher does not support adding shortcuts");
        } catch (Exception e) {
            toast("Could not add to home screen");
        }
    }

    void doAction(String name) {
        switch (name) {
            case "reload":
                if (cur != null && cur.wv != null) cur.wv.reload();
                break;
            case "homeBtn": {
                String hu = setting("homeUrl", "");
                if (!hu.isEmpty()) loadInCur(hu);
                else goHome();
                break;
            }
            case "back":
                if (mode.equals("web") && cur != null && cur.wv != null && cur.wv.canGoBack()) cur.wv.goBack();
                else goHome();
                break;
            case "forward":
                if (cur != null && cur.wv != null && cur.wv.canGoForward()) cur.wv.goForward();
                break;
            case "newtab":
                activate(newTab(null, false));
                break;
            case "favorites":
                show("bookmarks", "");
                break;
            case "extension":
                show("settings", "");
                main.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ui.evaluateJavascript("App.gotoSettings('ext')", null);
                    }
                }, 200);
                break;
            case "fullscreen":
                if (mode.equals("web")) {
                    manualFs = true;
                    setToolbars(true);
                }
                break;
            case "tabs":
                if (mode.equals("web") && cur != null) captureThumb(cur);
                show("tabs", "");
                break;
            case "reader":
                runAsset("reader.js");
                break;
            case "find":
                runAsset("findbar.js");
                break;
            case "devtools":
                runAsset("devtools.js");
                break;
            case "zoom":
                zoomDialog();
                break;
            case "a2hs":
                addToHome();
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
            case "default":
                if (isDefaultBrowser()) {
                    toast("Pravah is already your default browser");
                    break;
                }
                try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        android.app.role.RoleManager rm = (android.app.role.RoleManager) getSystemService(Context.ROLE_SERVICE);
                        if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)
                                && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
                            startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER), ROLE_REQ);
                            break;
                        }
                    }
                    startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    } catch (Exception e2) {
                        toast("Open Android Settings > Apps > Default apps");
                    }
                }
                break;
            case "autofill":
                try {
                    Intent ai = new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE);
                    ai.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(ai);
                } catch (Exception e) {
                    toast("Autofill settings are not available on this phone");
                }
                break;
            case "passwords":
                activate(newTab("https://passwords.google.com", false));
                break;
            case "privatedns":
                toast("Open Network & internet > Private DNS");
                try {
                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
                break;
            case "notifications":
                try {
                    Intent ni = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    ni.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    startActivity(ni);
                } catch (Exception e) {
                    toast("Can't open notification settings");
                }
                break;
            case "sharepravah":
                share("Try Pravah Browser - a fast, private browser with built-in ad blocking.");
                break;
            case "feedback":
                try {
                    Intent fi = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
                    fi.putExtra(Intent.EXTRA_SUBJECT, "Pravah Browser feedback");
                    fi.putExtra(Intent.EXTRA_TEXT, "\n\n---\nPravah " + Build.VERSION.RELEASE + " / " + Build.MODEL);
                    startActivity(Intent.createChooser(fi, "Send feedback"));
                } catch (Exception e) {
                    toast("No email app found");
                }
                break;
            case "exit":
                wipeOnExit();
                saveNow();
                finishAndRemoveTask();
                break;
            default:
                break;
        }
    }
}
