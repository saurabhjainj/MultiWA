package com.multiwa;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.webkit.WebView;

public class App extends Application {
    static final String CH_MSG = "messages", CH_BG = "background";

    @Override
    public void onCreate() {
        super.onCreate();
        // Give every account process (":wa1".. ":wa5") its own WebView storage,
        // so cookies/logins never mix between accounts.
        String p = Application.getProcessName();
        int i = p.indexOf(":wa");
        if (i >= 0) WebView.setDataDirectorySuffix(p.substring(i + 1));

        NotificationManager nm = getSystemService(NotificationManager.class);
        refreshChannels(this);
        NotificationChannel bg = new NotificationChannel(CH_BG, "Background connection",
                NotificationManager.IMPORTANCE_MIN);
        bg.setShowBadge(false);
        nm.createNotificationChannel(bg);
    }

    /**
     * One channel per account: own light colour + own vibration
     * (account 1 = 1 buzz, account 2 = 2 buzzes ...). The user can also pick a
     * different ringtone per account in Android's notification settings.
     */
    static void refreshChannels(android.content.Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        for (int s = 0; s < 5; s++) {
            NotificationChannel ch = new NotificationChannel("msg_" + s,
                    WaActivity.slotName(c, s) + " messages", NotificationManager.IMPORTANCE_HIGH);
            ch.enableLights(true);
            ch.setLightColor(MainActivity.COLORS[s]);
            ch.enableVibration(true);
            long[] v = new long[(s + 1) * 2];
            for (int k = 0; k < v.length; k += 2) { v[k] = k == 0 ? 0 : 110; v[k + 1] = 140; }
            ch.setVibrationPattern(v);
            nm.createNotificationChannel(ch);

            // Calls: ringtone sound + long vibration, per account.
            NotificationChannel call = new NotificationChannel("call_" + s,
                    WaActivity.slotName(c, s) + " calls", NotificationManager.IMPORTANCE_HIGH);
            call.setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE),
                    new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
            call.enableVibration(true);
            call.setVibrationPattern(new long[]{0, 800, 500, 800, 500, 800});
            call.enableLights(true);
            call.setLightColor(MainActivity.COLORS[s]);
            nm.createNotificationChannel(call);
        }
    }
}
