package com.multiwa;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/** Settings: connect all, quick replies, auto-reply, layout, dark mode, backup/restore, summary. */
public class SettingsActivity extends Activity {
    private static final int REQ_BACKUP = 51, REQ_RESTORE = 52;
    private LinearLayout list;
    private float dp;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        prefs = Extras.p(this);
        getWindow().setStatusBarColor(0xFF0B1F2A);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0B1F2A, 0xFF0E3B3A, 0xFF07141B}));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(18), px(44), px(18), px(28));
        sv.addView(root);
        root.addView(text("⚙️ Settings", 26, Color.WHITE, true));
        TextView sub = text("After changing layout or dark mode, open an account and tap ⋮ → Reload", 12, 0x99FFFFFF, false);
        sub.setPadding(0, px(4), 0, px(16));
        root.addView(sub);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        setContentView(sv);
        build();
    }

    private void build() {
        list.removeAllViews();
        row("🔌", "Connect all accounts", "One tap after a phone restart", () -> MainActivity.connectAll(this));
        row("⚡", "Quick replies", Extras.quick(this).size() + " saved replies", this::editQuick);
        row("🤖", "Auto-reply", "Automatic answer when busy or at night", this::pickAutoReply);
        int sp = Extras.speed(this);
        String[] spIcon = {"🔋", "⏱️", "⚡"};
        String[] spText = {
                "Most battery saving; alerts can be late after long sleep",
                "Wakes every 5 min: good alerts, moderate battery",
                "Instant alerts, high battery use" };
        row(spIcon[sp], "Notification speed: " + Extras.SPEEDS[sp], spText[sp] + " (tap to change)", () -> {
            prefs.edit().putInt("speed", (Extras.speed(this) + 1) % 3).commit();
            toast("Applies to each account the next time you open it");
            build();
        });
        row("📱", "Mobile layout: " + (Extras.mobile(this) ? "ON" : "OFF"), "One chat per screen, bigger text", () -> {
            prefs.edit().putBoolean("mobile", !Extras.mobile(this)).commit(); build();
        });
        row("📐", "Page size: " + Extras.PAGE_SIZES[Extras.pageSize(this)], "Mobile layout text size (tap to change)", () -> {
            prefs.edit().putInt("page_size", (Extras.pageSize(this) + 1) % Extras.PAGE_SIZES.length).commit(); build();
        });
        row("🌙", "Dark mode: " + (Extras.dark(this) ? "ON" : "OFF"), "All accounts dark", () -> {
            prefs.edit().putBoolean("dark", !Extras.dark(this)).commit(); build();
        });
        row("📏", "Account bar", Extras.barSummary(this), () -> Extras.showBarSettings(this, this::build));
        row("📊", "Today's summary", "Messages per account today", () -> Extras.showSummary(this));
        row("💾", "Create backup", "Names, colours, schedules, quick replies", () -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json").putExtra(Intent.EXTRA_TITLE, "MultiWA-backup.json");
            startActivityForResult(i, REQ_BACKUP);
        });
        row("📂", "Restore from backup", "Bring settings to a new phone", () -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            startActivityForResult(i, REQ_RESTORE);
        });
    }

    private void row(String icon, String title, String subtitle, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(px(14), px(14), px(14), px(14));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(18));
        g.setColor(0x14FFFFFF);
        g.setStroke(px(1), 0x22FFFFFF);
        r.setBackground(g);
        TextView ic = text(icon, 22, Color.WHITE, false);
        r.addView(ic);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(px(14), 0, 0, 0);
        t.addView(text(title, 15, Color.WHITE, true));
        t.addView(text(subtitle, 12, 0x99FFFFFF, false));
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(text("›", 24, 0xFF00A884, true));
        r.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = px(10);
        list.addView(r, lp);
    }

    // ---- Quick replies ----
    private void editQuick() {
        List<String> l = Extras.quick(this);
        String[] items = new String[l.size() + 1];
        for (int i = 0; i < l.size(); i++) items[i] = "🗑  " + l.get(i);
        items[l.size()] = "➕  Add a quick reply";
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("⚡ Quick replies (tap to delete)")
                .setItems(items, (d, w) -> {
                    if (w < l.size()) { l.remove(w); Extras.setQuick(this, l); build(); editQuick(); return; }
                    EditText in = new EditText(this);
                    in.setHint("e.g. Payment received ✅");
                    new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                            .setTitle("New quick reply").setView(in)
                            .setPositiveButton("Save", (d2, w2) -> {
                                String s = in.getText().toString().trim();
                                if (!s.isEmpty()) { l.add(s); Extras.setQuick(this, l); build(); }
                            })
                            .setNegativeButton("Cancel", null).show();
                })
                .show();
    }

    // ---- Auto-reply ----
    private void pickAutoReply() {
        List<Integer> slots = MainActivity.activeSlots(this);
        String[] names = new String[slots.size()];
        for (int j = 0; j < names.length; j++) {
            int s = slots.get(j);
            names[j] = (Extras.arOn(this, s) ? "🟢  " : "⚪  ") + WaActivity.slotName(this, s);
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🤖 Auto-reply for which account?")
                .setItems(names, (d, w) -> editAutoReply(slots.get(w)))
                .show();
    }

    private void editAutoReply(int s) {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(px(22), px(8), px(22), 0);
        CheckBox on = new CheckBox(this);
        on.setText("Auto-reply ON");
        on.setChecked(Extras.arOn(this, s));
        f.addView(on);
        EditText msg = new EditText(this);
        msg.setText(Extras.arMsg(this, s));
        msg.setMinLines(2);
        f.addView(msg);
        CheckBox night = new CheckBox(this);
        night.setText("Only at night (10 PM – 8 AM)");
        night.setChecked(Extras.arNight(this, s));
        f.addView(night);
        TextView note = text("Sent at most once per person every 6 hours. Never sent in groups.", 12, 0x99FFFFFF, false);
        note.setPadding(0, px(8), 0, 0);
        f.addView(note);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🤖 " + WaActivity.slotName(this, s))
                .setView(f)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.edit().putBoolean("ar_on" + s, on.isChecked())
                            .putString("ar_msg" + s, msg.getText().toString().trim())
                            .putBoolean("ar_night" + s, night.isChecked()).commit();
                    toast("Auto-reply saved");
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ---- Backup / restore ----
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (req == REQ_BACKUP) {
                JSONObject out = new JSONObject();
                JSONObject p = new JSONObject();
                for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
                    String k = e.getKey();
                    if (k.startsWith("u") && k.length() <= 3) continue;      // unread counts
                    if (k.startsWith("d2") || k.equals("unlockedAt")) continue; // daily counts, lock time
                    Object v = e.getValue();
                    String t = v instanceof Boolean ? "b" : v instanceof Integer ? "i" : v instanceof Long ? "l" : "s";
                    p.put(k, new JSONObject().put("t", t).put("v", v));
                }
                out.put("prefs", p);
                out.put("sched", Sched.all(this));
                try (java.io.OutputStream os = getContentResolver().openOutputStream(data.getData())) {
                    os.write(out.toString(2).getBytes("UTF-8"));
                }
                toast("Backup saved ✅");
            } else if (req == REQ_RESTORE) {
                StringBuilder sb = new StringBuilder();
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                        getContentResolver().openInputStream(data.getData()), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                JSONObject in = new JSONObject(sb.toString());
                JSONObject p = in.getJSONObject("prefs");
                SharedPreferences.Editor ed = prefs.edit();
                for (java.util.Iterator<String> it = p.keys(); it.hasNext(); ) {
                    String k = it.next();
                    JSONObject e = p.getJSONObject(k);
                    switch (e.getString("t")) {
                        case "b": ed.putBoolean(k, e.getBoolean("v")); break;
                        case "i": ed.putInt(k, e.getInt("v")); break;
                        case "l": ed.putLong(k, e.getLong("v")); break;
                        default: ed.putString(k, e.getString("v"));
                    }
                }
                ed.commit();
                JSONArray sched = in.optJSONArray("sched");
                if (sched != null) {
                    getSharedPreferences("sched", MODE_MULTI_PROCESS).edit().putString("list", sched.toString()).commit();
                    Sched.rearmAll(this);
                }
                App.refreshChannels(this);
                toast("Settings restored ✅ Scan each account's QR code again.");
                build();
            }
        } catch (Exception e) {
            toast("This isn't a valid backup file");
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        return t;
    }

    private int px(float v) { return (int) (v * dp); }
}
