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
                done(c, id, false);
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

    static synchronized void add(Context c, int slot, String phone, String text, long when, String name) {
        add(c, slot, phone, text, when, name, "none");
    }

    /** repeat: none | daily | weekly | monthly */
    static synchronized void add(Context c, int slot, String phone, String text, long when, String name, String repeat) {
        try {
            JSONObject o = new JSONObject();
            o.put("repeat", repeat);
            o.put("name", name == null ? "" : name);
            o.put("slot", slot);
            o.put("phone", phone);
            o.put("text", text);
            o.put("when", when);
            o.put("status", "pending");
            o.put("id", System.currentTimeMillis() + (long) (Math.random() * 1000));
            JSONArray a = all(c);
            a.put(o);
            saveAll(c, a);
            arm(c, o);
        } catch (Exception ignored) {}
    }

    /** After sending: repeating ones move to their next time, others are marked sent/failed. */
    static synchronized void done(Context c, long id, boolean ok) {
        JSONArray a = all(c);
        try {
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (o.optLong("id") != id) continue;
                String rep = o.optString("repeat", "none");
                o.put("last", ok ? "sent" : "failed");
                if ("none".equals(rep)) { o.put("status", ok ? "sent" : "failed"); }
                else { o.put("when", next(o.optLong("when"), rep)); o.put("status", "pending"); arm(c, o); }
            }
        } catch (Exception ignored) {}
        saveAll(c, a);
    }

    private static long next(long when, String rep) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(when);
        do {
            if ("daily".equals(rep)) cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
            else if ("weekly".equals(rep)) cal.add(java.util.Calendar.WEEK_OF_YEAR, 1);
            else cal.add(java.util.Calendar.MONTH, 1);
        } while (cal.getTimeInMillis() < System.currentTimeMillis());
        return cal.getTimeInMillis();
    }

    /** After phone restart: alarms are lost, set them again. Missed one-time ones are marked failed. */
    static synchronized void rearmAll(Context c) {
        JSONArray a = all(c);
        try {
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (!"pending".equals(o.optString("status"))) continue;
                if (o.optLong("when") < System.currentTimeMillis()) {
                    String rep = o.optString("repeat", "none");
                    if ("none".equals(rep)) { o.put("status", "failed"); continue; }
                    o.put("when", next(o.optLong("when"), rep));
                }
                arm(c, o);
            }
        } catch (Exception ignored) {}
        saveAll(c, a);
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
                .setContentTitle(ok ? "⏰ Scheduled message sent" : "⏰ Scheduled message not sent")
                .setContentText(name + " → +" + phone + (ok ? "" : " · account wasn't open/connected"))
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify((int) (System.currentTimeMillis() % 100000) + 1000, n);
    }
}
