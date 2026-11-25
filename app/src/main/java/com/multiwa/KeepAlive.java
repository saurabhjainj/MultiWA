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
    public static class S6 extends KeepAlive {}
    public static class S7 extends KeepAlive {}
    public static class S8 extends KeepAlive {}
    public static class S9 extends KeepAlive {}
    public static class S10 extends KeepAlive {}

    static final Class<?>[] SERVICES = { S1.class, S2.class, S3.class, S4.class, S5.class, S6.class, S7.class, S8.class, S9.class, S10.class };

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
                .setLargeIcon(WaActivity.avatar(WaActivity.slotName(this, s), MainActivity.color(this, s)))
                .setColor(MainActivity.color(this, s))
                .setGroup("bg")
                .setOngoing(true)
                .build();
        startForeground(100 + s, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        // Keep Wi-Fi awake with screen off so the WhatsApp connection is not dropped.
        if (wifiLock == null) {
            android.net.wifi.WifiManager wm = getApplicationContext().getSystemService(android.net.wifi.WifiManager.class);
            wifiLock = wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "multiwa:" + s);
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }
        // Keep CPU awake with screen off so WhatsApp Web handles new messages instantly.
        if (cpuLock == null) {
            android.os.PowerManager pm = getSystemService(android.os.PowerManager.class);
            cpuLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "multiwa:cpu" + s);
            cpuLock.setReferenceCounted(false);
            cpuLock.acquire();
        }
        return START_NOT_STICKY;
    }

    private android.net.wifi.WifiManager.WifiLock wifiLock;
    private android.os.PowerManager.WakeLock cpuLock;

    @Override
    public void onDestroy() {
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        if (cpuLock != null && cpuLock.isHeld()) cpuLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
