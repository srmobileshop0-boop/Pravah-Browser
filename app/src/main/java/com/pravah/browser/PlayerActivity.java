package com.pravah.browser;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/** Pravah's own video player: 5 / 10 second jumps, pause, speed, swipe for volume / brightness / seeking. */
public class PlayerActivity extends Activity {

    private final Handler h = new Handler(Looper.getMainLooper());
    private VideoView vv;
    private MediaPlayer prepared;
    private FrameLayout root;
    private LinearLayout topBar, centerRow, bottomBar;
    private TextView titleTv, curTv, durTv, playBtn, speedBtn, hud, prevBtn, nextBtn;
    private SeekBar seek;
    private boolean dragging, controlsOn = true, landscape = true;
    private int dur = 0;
    private float speed = 1f;
    private final float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
    private ArrayList<String> listUris = new ArrayList<>(), listTitles = new ArrayList<>();
    private int index = 0;
    private String ua, referer;
    private SharedPreferences sp;
    private AudioManager am;

    // gesture state
    private int gMode = 0; // 0 none, 1 seek, 2 brightness, 3 volume
    private float gTotal = 0, gStartVol = 0, gStartBright = 0;
    private int gStartPos = 0, gTarget = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (vv != null && !dragging) {
                int p = vv.getCurrentPosition();
                seek.setProgress(p);
                curTv.setText(fmt(p));
            }
            updatePlayBtn();
            h.postDelayed(this, 400);
        }
    };
    private final Runnable hideControls = new Runnable() {
        @Override
        public void run() {
            if (vv != null && vv.isPlaying() && !dragging) setControls(false);
        }
    };
    private final Runnable hideHud = new Runnable() {
        @Override
        public void run() {
            hud.setVisibility(View.GONE);
        }
    };

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String fmt(int ms) {
        int s = Math.max(0, ms / 1000);
        int hh = s / 3600, mm = (s % 3600) / 60, ss = s % 60;
        return hh > 0 ? String.format(Locale.US, "%d:%02d:%02d", hh, mm, ss) : String.format(Locale.US, "%02d:%02d", mm, ss);
    }

    private TextView pill(String text, int sizeSp, int w, int hgt, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sizeSp);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(40));
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, hgt);
        lp.setMargins(dp(5), 0, dp(5), 0);
        t.setLayoutParams(lp);
        return t;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        sp = getSharedPreferences("player", 0);
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        String first = getIntent().getStringExtra("uri");
        ua = getIntent().getStringExtra("ua");
        referer = getIntent().getStringExtra("referer");
        ArrayList<String> lu = getIntent().getStringArrayListExtra("list_uris");
        ArrayList<String> lt = getIntent().getStringArrayListExtra("list_titles");
        if (lu != null && lt != null && lu.size() == lt.size()) {
            listUris = lu;
            listTitles = lt;
        }
        index = listUris.indexOf(first);
        if (first == null || first.isEmpty()) {
            finish();
            return;
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        vv = new VideoView(this);
        root.addView(vv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        View touch = new View(this);
        root.addView(touch, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- top bar
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(24), dp(14), dp(14));
        topBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xCC000000, 0x00000000}));
        TextView back = pill("\u2190", 20, dp(44), dp(44), 0x33FFFFFF);
        back.setOnClickListener(v -> finish());
        topBar.addView(back);
        titleTv = new TextView(this);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(16);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.setMargins(dp(8), 0, 0, 0);
        topBar.addView(titleTv, tl);
        TextView rot = pill("Rotate", 13, dp(74), dp(38), 0x33FFFFFF);
        rot.setOnClickListener(v -> {
            landscape = !landscape;
            setRequestedOrientation(landscape ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        });
        topBar.addView(rot);
        root.addView(topBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        // ---- centre buttons: -10  -5  play  +5  +10
        centerRow = new LinearLayout(this);
        centerRow.setOrientation(LinearLayout.HORIZONTAL);
        centerRow.setGravity(Gravity.CENTER);
        TextView m10 = pill("-10", 15, dp(58), dp(58), 0x66000000);
        TextView m5 = pill("-5", 14, dp(50), dp(50), 0x66000000);
        playBtn = pill("\u275A\u275A", 22, dp(76), dp(76), 0xCC2F6BFF);
        TextView p5 = pill("+5", 14, dp(50), dp(50), 0x66000000);
        TextView p10 = pill("+10", 15, dp(58), dp(58), 0x66000000);
        m10.setOnClickListener(v -> seekBy(-10000));
        m5.setOnClickListener(v -> seekBy(-5000));
        p5.setOnClickListener(v -> seekBy(5000));
        p10.setOnClickListener(v -> seekBy(10000));
        playBtn.setOnClickListener(v -> togglePlay());
        centerRow.addView(m10);
        centerRow.addView(m5);
        centerRow.addView(playBtn);
        centerRow.addView(p5);
        centerRow.addView(p10);
        root.addView(centerRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // ---- bottom bar
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(dp(14), dp(14), dp(14), dp(18));
        bottomBar.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xCC000000, 0x00000000}));
        LinearLayout seekRow = new LinearLayout(this);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        curTv = new TextView(this);
        curTv.setTextColor(Color.WHITE);
        curTv.setTextSize(13);
        curTv.setText("00:00");
        durTv = new TextView(this);
        durTv.setTextColor(Color.WHITE);
        durTv.setTextSize(13);
        durTv.setText("00:00");
        seek = new SeekBar(this);
        seekRow.addView(curTv);
        seekRow.addView(seek, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        seekRow.addView(durTv);
        bottomBar.addView(seekRow);
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, dp(8), 0, 0);
        prevBtn = pill("\u2039 Prev", 13, dp(80), dp(38), 0x33FFFFFF);
        speedBtn = pill("1x", 14, dp(70), dp(38), 0x33FFFFFF);
        nextBtn = pill("Next \u203A", 13, dp(80), dp(38), 0x33FFFFFF);
        prevBtn.setOnClickListener(v -> playIndex(index - 1));
        nextBtn.setOnClickListener(v -> playIndex(index + 1));
        speedBtn.setOnClickListener(v -> cycleSpeed());
        btnRow.addView(prevBtn);
        btnRow.addView(speedBtn);
        btnRow.addView(nextBtn);
        bottomBar.addView(btnRow);
        root.addView(bottomBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        // ---- centre hint text (seek / volume / brightness)
        hud = new TextView(this);
        hud.setTextColor(Color.WHITE);
        hud.setTextSize(20);
        hud.setTypeface(Typeface.DEFAULT_BOLD);
        hud.setGravity(Gravity.CENTER);
        hud.setPadding(dp(22), dp(12), dp(22), dp(12));
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(0xAA000000);
        hg.setCornerRadius(dp(18));
        hud.setBackground(hg);
        hud.setVisibility(View.GONE);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        hl.topMargin = dp(90);
        root.addView(hud, hl);

        setContentView(root);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser) curTv.setText(fmt(p));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                dragging = true;
                h.removeCallbacks(hideControls);
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
                vv.seekTo(s.getProgress());
                scheduleHide();
            }
        });

        vv.setOnPreparedListener(mp -> {
            prepared = mp;
            dur = vv.getDuration();
            seek.setMax(Math.max(dur, 1));
            durTv.setText(fmt(dur));
            int saved = sp.getInt(key(), 0);
            if (saved > 5000 && dur > 0 && saved < dur - 8000) vv.seekTo(saved);
            vv.start();
            applySpeed();
            updatePlayBtn();
            scheduleHide();
        });
        vv.setOnCompletionListener(mp -> {
            sp.edit().remove(key()).apply();
            if (index >= 0 && index + 1 < listUris.size()) playIndex(index + 1);
            else {
                setControls(true);
                updatePlayBtn();
            }
        });
        vv.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(PlayerActivity.this, "This video can't be played in Pravah Player", Toast.LENGTH_LONG).show();
            return true;
        });

        final GestureDetector gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                gMode = 0;
                gTotal = 0;
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                setControls(!controlsOn);
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                float w = root.getWidth();
                if (e.getX() < w / 3f) seekBy(-10000);
                else if (e.getX() > w * 2f / 3f) seekBy(10000);
                else togglePlay();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (e1 == null) return false;
                if (gMode == 0) {
                    float adx = Math.abs(e2.getX() - e1.getX()), ady = Math.abs(e2.getY() - e1.getY());
                    if (adx < dp(14) && ady < dp(14)) return true;
                    if (adx > ady) {
                        gMode = 1;
                        gStartPos = vv.getCurrentPosition();
                    } else if (e1.getX() < root.getWidth() / 2f) {
                        gMode = 2;
                        float cur = getWindow().getAttributes().screenBrightness;
                        gStartBright = cur < 0 ? 0.5f : cur;
                    } else {
                        gMode = 3;
                        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        gStartVol = max == 0 ? 0 : am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max;
                    }
                }
                if (gMode == 1) {
                    gTarget = Math.max(0, Math.min(dur, gStartPos + (int) ((e2.getX() - e1.getX()) * 90f)));
                    hud(fmt(gTarget) + "  (" + (gTarget >= gStartPos ? "+" : "-") + Math.abs(gTarget - gStartPos) / 1000 + "s)");
                } else if (gMode == 2) {
                    float v = Math.max(0.02f, Math.min(1f, gStartBright + (e1.getY() - e2.getY()) / root.getHeight() * 1.3f));
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = v;
                    getWindow().setAttributes(lp);
                    hud("Brightness " + (int) (v * 100) + "%");
                } else if (gMode == 3) {
                    float v = Math.max(0f, Math.min(1f, gStartVol + (e1.getY() - e2.getY()) / root.getHeight() * 1.3f));
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v * max), 0);
                    hud("Volume " + (int) (v * 100) + "%");
                }
                return true;
            }
        });
        touch.setOnTouchListener((v, e) -> {
            boolean r = gd.onTouchEvent(e);
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                if (gMode == 1) vv.seekTo(gTarget);
                gMode = 0;
            }
            return r || true;
        });

        loadCurrent(first, index >= 0 && index < listTitles.size() ? listTitles.get(index) : getIntent().getStringExtra("title"));
        h.post(tick);
    }

    private String curUri = "";

    private String key() {
        return "p" + curUri.hashCode();
    }

    private void loadCurrent(String uriStr, String title) {
        curUri = uriStr;
        titleTv.setText(title == null || title.isEmpty() ? "Video" : title);
        boolean multi = listUris.size() > 1;
        prevBtn.setVisibility(multi ? View.VISIBLE : View.GONE);
        nextBtn.setVisibility(multi ? View.VISIBLE : View.GONE);
        prepared = null;
        Uri u = Uri.parse(uriStr);
        String sc = u.getScheme();
        if (sc != null && sc.startsWith("http")) {
            HashMap<String, String> hd = new HashMap<>();
            if (referer != null && referer.startsWith("http")) hd.put("Referer", referer);
            if (ua != null) hd.put("User-Agent", ua);
            try {
                String ck = android.webkit.CookieManager.getInstance().getCookie(uriStr);
                if (ck != null) hd.put("Cookie", ck);
            } catch (Exception ignored) {
            }
            vv.setVideoURI(u, hd);
        } else {
            vv.setVideoURI(u);
        }
    }

    private void playIndex(int i) {
        if (i < 0 || i >= listUris.size()) return;
        saveProgress();
        index = i;
        loadCurrent(listUris.get(i), listTitles.get(i));
        seek.setProgress(0);
        curTv.setText("00:00");
    }

    private void saveProgress() {
        try {
            if (vv != null && dur > 0) {
                int p = vv.getCurrentPosition();
                if (p > 5000 && p < dur - 8000) sp.edit().putInt(key(), p).apply();
            }
        } catch (Exception ignored) {
        }
    }

    private void seekBy(int ms) {
        if (vv == null) return;
        int p = Math.max(0, Math.min(Math.max(dur, 1), vv.getCurrentPosition() + ms));
        vv.seekTo(p);
        seek.setProgress(p);
        curTv.setText(fmt(p));
        hud((ms > 0 ? "+" : "-") + Math.abs(ms) / 1000 + "s");
        scheduleHide();
    }

    private void togglePlay() {
        if (vv.isPlaying()) {
            vv.pause();
            setControls(true);
            h.removeCallbacks(hideControls);
        } else {
            vv.start();
            applySpeed();
            scheduleHide();
        }
        updatePlayBtn();
    }

    private void updatePlayBtn() {
        if (vv != null && playBtn != null) playBtn.setText(vv.isPlaying() ? "\u275A\u275A" : "\u25B6");
    }

    private void cycleSpeed() {
        int i = 0;
        for (int k = 0; k < speeds.length; k++) if (Math.abs(speeds[k] - speed) < 0.01f) i = k;
        speed = speeds[(i + 1) % speeds.length];
        speedBtn.setText((speed == (int) speed ? String.valueOf((int) speed) : String.valueOf(speed)) + "x");
        if (vv.isPlaying()) applySpeed();
        hud("Speed " + speedBtn.getText());
    }

    private void applySpeed() {
        if (Build.VERSION.SDK_INT >= 23 && prepared != null && vv.isPlaying()) {
            try {
                PlaybackParams pp = prepared.getPlaybackParams();
                pp.setSpeed(speed);
                prepared.setPlaybackParams(pp);
            } catch (Exception ignored) {
            }
        }
    }

    private void hud(String t) {
        hud.setText(t);
        hud.setVisibility(View.VISIBLE);
        h.removeCallbacks(hideHud);
        h.postDelayed(hideHud, 800);
    }

    private void setControls(boolean on) {
        controlsOn = on;
        int vis = on ? View.VISIBLE : View.GONE;
        topBar.setVisibility(vis);
        centerRow.setVisibility(vis);
        bottomBar.setVisibility(vis);
        if (on) scheduleHide();
    }

    private void scheduleHide() {
        h.removeCallbacks(hideControls);
        if (vv != null && vv.isPlaying()) h.postDelayed(hideControls, 3500);
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        immersive();
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) immersive();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
        try {
            if (vv != null && vv.isPlaying()) vv.pause();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        try {
            if (vv != null) vv.stopPlayback();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
