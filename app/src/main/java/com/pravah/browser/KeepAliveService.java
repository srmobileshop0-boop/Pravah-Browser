package com.pravah.browser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** Keeps Pravah alive while a video or music keeps playing after you leave the app ("Listen in background"). */
public class KeepAliveService extends Service {
    static final String CH = "pravah_bg";
    final Handler h = new Handler(Looper.getMainLooper());
    final Runnable check = new Runnable() {
        @Override
        public void run() {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am == null || !am.isMusicActive()) {
                stopSelf();
            } else {
                h.postDelayed(this, 6000);
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CH, "Background playback", NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        Notification n = b.setContentTitle("Pravah Browser").setContentText("Playing in background")
                .setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(pi).setOngoing(true).build();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(7, n);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        h.removeCallbacks(check);
        h.postDelayed(check, 8000);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        h.removeCallbacks(check);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
