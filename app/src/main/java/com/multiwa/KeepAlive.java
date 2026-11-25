package com.multiwa;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

/**
 * Runs inside each account's process (":wa1".. ":wa5") so Android does not freeze
 * that process in the background. That keeps WhatsApp Web connected and able to notify.
 */
public abstract class KeepAlive extends Service {
    public static class S1 extends KeepAlive {}
    public static class S2 extends KeepAlive {}
    public static class S3 extends KeepAlive {}
    public static class S4 extends KeepAlive {}
    public static class S5 extends KeepAlive {}

    static final Class<?>[] SERVICES = { S1.class, S2.class, S3.class, S4.class, S5.class };

    private int slot() {
        for (int i = 0; i < SERVICES.length; i++) if (SERVICES[i] == getClass()) return i;
        return 0;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int s = slot();
        Notification n = new Notification.Builder(this, App.CH_BG)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("🟢 " + WaActivity.slotName(this, s) + " · online")
                .setContentText("Naye messages aate hi bata dunga")
                .setLargeIcon(WaActivity.avatar(WaActivity.slotName(this, s), MainActivity.COLORS[s]))
                .setColor(MainActivity.COLORS[s])
                .setGroup("bg")
                .setOngoing(true)
                .build();
        startForeground(100 + s, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
