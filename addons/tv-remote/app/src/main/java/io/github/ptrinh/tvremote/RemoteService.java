package io.github.ptrinh.tvremote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * Keeps the process, and with it the relay connection ({@link RemoteRelay}), the lock and the lock timer
 * ({@link TvLock}), alive while other apps are in front. A foreground service with a minimal notification
 * (importance min: no sound, no icon in the status area). Started by the card and at boot; with no relay
 * relay address (Relay.url) there is nothing to hold, and it stops.
 */
public class RemoteService extends Service {
    private static final String CHANNEL = "remote";
    private static final int ID = 1;

    static void start(Context ctx) {
        if (Relay.url(ctx).isEmpty()) return;
        try {
            ctx.startForegroundService(new Intent(ctx, RemoteService.class));
        } catch (Exception e) {
            // Android 12+ refuses some background starts; the card or the accessibility service starts it later.
            Log.w(RemoteRelay.TAG, "remote service not started: " + e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = notification();
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(ID, n);
        if (Relay.url(this).isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        RemoteRelay.start(this, Relay.url(this));
        TvLock.start(this);
        return START_STICKY;
    }

    private Notification notification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel c = new NotificationChannel(CHANNEL, getString(R.string.remote_channel), NotificationManager.IMPORTANCE_MIN);
            c.setShowBadge(false);
            nm.createNotificationChannel(c);
        }
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_foreground)
                .setContentTitle(getString(R.string.remote_notification))
                .setOngoing(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
