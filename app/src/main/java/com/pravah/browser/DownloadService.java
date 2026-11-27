package com.pravah.browser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/** Keeps Pravah running (and the CPU awake) while videos are being downloaded in the background. */
public class DownloadService extends Service {
    static final String CH = "pravah_dl";
    static final int ID = 8;
    private PowerManager.WakeLock wl;

    private static void channel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(new NotificationChannel(CH, "Downloads", NotificationManager.IMPORTANCE_LOW));
    }

    private static Notification build(Context c, String text, int pct, boolean ongoing) {
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        b.setContentTitle("Pravah downloads").setContentText(text).setContentIntent(pi).setOnlyAlertOnce(true);
        if (ongoing) {
            b.setSmallIcon(android.R.drawable.stat_sys_download).setOngoing(true);
            if (pct >= 0) b.setProgress(100, Math.min(100, pct), false);
            else b.setProgress(0, 0, true);
        } else {
            b.setSmallIcon(android.R.drawable.stat_sys_download_done).setAutoCancel(true);
        }
        return b.build();
    }

    static void post(Context c, String text, int pct) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(ID, build(c, text, pct, true));
        } catch (Exception ignored) {
        }
    }

    static void done(Context c, String text) {
        try {
            channel(c);
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(100 + (int) (System.currentTimeMillis() % 800), build(c, text, -1, false));
        } catch (Exception ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        channel(this);
        Notification n = build(this, "Downloading video...", -1, true);
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(ID, n);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            if (wl == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pravah:download");
                    wl.acquire(3L * 60 * 60 * 1000);
                }
            }
        } catch (Exception ignored) {
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            if (wl != null && wl.isHeld()) wl.release();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
