package com.multiwa;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Scheduled messages: stored as JSON, fired by AlarmManager into the account's own process. */
public final class Sched {

    /** One receiver per account process, so the alarm lands where that account's WhatsApp runs. */
    public static abstract class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            long id = intent.getLongExtra("id", -1);
            JSONObject o = find(c, id);
            if (o == null || !"pending".equals(o.optString("status"))) return;
            WaActivity w = WaActivity.current;
            if (w != null) {
                w.runOnUiThread(() -> w.sendScheduled(id, o.optString("phone"), o.optString("text")));
            } else {
                // Account not open/connected right now.
                setStatus(c, id, "failed");
                notifyResult(c, o.optInt("slot"), false, o.optString("phone"));
            }
        }
    }
    public static class R1 extends Receiver {}
    public static class R2 extends Receiver {}
    public static class R3 extends Receiver {}
    public static class R4 extends Receiver {}
    public static class R5 extends Receiver {}
    public static class R6 extends Receiver {}
    public static class R7 extends Receiver {}
    public static class R8 extends Receiver {}
    public static class R9 extends Receiver {}
    public static class R10 extends Receiver {}
    static final Class<?>[] RECEIVERS = { R1.class, R2.class, R3.class, R4.class, R5.class, R6.class, R7.class, R8.class, R9.class, R10.class };

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("sched", Context.MODE_MULTI_PROCESS);
    }

    static synchronized JSONArray all(Context c) {
        try { return new JSONArray(p(c).getString("list", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    private static void saveAll(Context c, JSONArray a) {
        p(c).edit().putString("list", a.toString()).commit();
    }

    static JSONObject find(Context c, long id) {
        JSONArray a = all(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optLong("id") == id) return o;
        }
        return null;
    }

    static synchronized void setStatus(Context c, long id, String status) {
        JSONArray a = all(c);
        try {
            for (int i = 0; i < a.length(); i++)
                if (a.getJSONObject(i).optLong("id") == id) a.getJSONObject(i).put("status", status);
        } catch (Exception ignored) {}
        saveAll(c, a);
    }

    static synchronized void add(Context c, int slot, String phone, String text, long when) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", System.currentTimeMillis());
            o.put("slot", slot);
            o.put("phone", phone);
            o.put("text", text);
            o.put("when", when);
            o.put("status", "pending");
            JSONArray a = all(c);
            a.put(o);
            saveAll(c, a);
            arm(c, o);
        } catch (Exception ignored) {}
    }

    static synchronized void remove(Context c, long id) {
        JSONArray a = all(c), out = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            if (o.optLong("id") == id) c.getSystemService(AlarmManager.class).cancel(pi(c, o));
            else out.put(o);
        }
        saveAll(c, out);
    }

    private static PendingIntent pi(Context c, JSONObject o) {
        Intent i = new Intent(c, RECEIVERS[o.optInt("slot")]).putExtra("id", o.optLong("id"));
        return PendingIntent.getBroadcast(c, (int) o.optLong("id"), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void arm(Context c, JSONObject o) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        long when = o.optLong("when");
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi(c, o));
        else
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi(c, o));
    }

    static void notifyResult(Context c, int slot, boolean ok, String phone) {
        String name = WaActivity.slotName(c, slot);
        Notification n = new Notification.Builder(c, "msg_" + slot)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(MainActivity.color(c, slot))
                .setContentTitle(ok ? "⏰ Scheduled message bhej diya" : "⏰ Scheduled message nahi gaya")
                .setContentText(name + " → +" + phone + (ok ? "" : " · account khula/connected nahi tha"))
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify((int) (System.currentTimeMillis() % 100000) + 1000, n);
    }
}
