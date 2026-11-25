package com.multiwa;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** List + create scheduled messages. */
public class ScheduleActivity extends Activity {
    private LinearLayout list;
    private float dp;
    private final SimpleDateFormat fmt = new SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(0xFF0B1F2A);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0B1F2A, 0xFF0E3B3A, 0xFF07141B}));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(22), px(44), px(22), px(28));
        sv.addView(root);

        root.addView(text("⏰ Scheduled messages", 26, Color.WHITE, true));
        TextView sub = text("Time aane pe app khud message bhej dega", 13, 0xB3FFFFFF, false);
        sub.setPadding(0, px(4), 0, px(18));
        root.addView(sub);

        TextView add = text("+  Naya message schedule karo", 15, Color.WHITE, true);
        add.setGravity(Gravity.CENTER);
        add.setPadding(0, px(14), 0, px(14));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(16));
        g.setColor(0xFF00A884);
        add.setBackground(g);
        add.setOnClickListener(v -> pickAccount());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.bottomMargin = px(18);
        root.addView(add, alp);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        TextView hint = text("Lamba dabao → delete", 12, 0x80FFFFFF, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, px(12), 0, 0);
        root.addView(hint);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        list.removeAllViews();
        JSONArray a = Sched.all(this);
        if (a.length() == 0) {
            TextView e = text("Abhi koi message schedule nahi hai", 14, 0x99FFFFFF, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, px(30), 0, px(30));
            list.addView(e);
        }
        for (int i = a.length() - 1; i >= 0; i--) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) list.addView(card(o));
        }
    }

    private LinearLayout card(JSONObject o) {
        int slot = o.optInt("slot");
        int color = MainActivity.color(this, slot);
        String st = o.optString("status");
        String badge = "sent".equals(st) ? "✅ Bhej diya" : "failed".equals(st) ? "❌ Nahi gaya" : "⏳ Pending";

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(px(16), px(12), px(16), px(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(16));
        bg.setColor(0x14FFFFFF);
        bg.setStroke(px(1), (color & 0x00FFFFFF) | 0x88000000);
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = px(10);
        c.setLayoutParams(lp);

        TextView top = text(WaActivity.slotName(this, slot).toUpperCase() + "  →  +" + o.optString("phone"), 11, color, true);
        c.addView(top);
        TextView msg = text(o.optString("text"), 15, Color.WHITE, false);
        msg.setPadding(0, px(4), 0, px(6));
        c.addView(msg);
        c.addView(text("🕒 " + fmt.format(new Date(o.optLong("when"))) + "   ·   " + badge, 12, 0xB3FFFFFF, false));

        long id = o.optLong("id");
        c.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Delete karein?")
                    .setPositiveButton("Delete", (d, w) -> { Sched.remove(this, id); refresh(); })
                    .setNegativeButton("Cancel", null).show();
            return true;
        });
        return c;
    }

    private void pickAccount() {
        java.util.List<Integer> slots = MainActivity.activeSlots(this);
        String[] names = new String[slots.size()];
        for (int j = 0; j < names.length; j++) names[j] = "●  " + WaActivity.slotName(this, slots.get(j));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Kis account se bhejna hai?")
                .setItems(names, (d, w) -> form(slots.get(w)))
                .show();
    }

    private void form(int slot) {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(px(22), px(8), px(22), 0);

        EditText phone = new EditText(this);
        phone.setHint("Number (jaise 9876543210)");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        f.addView(phone);

        EditText msg = new EditText(this);
        msg.setHint("Message likho");
        msg.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        msg.setMinLines(2);
        f.addView(msg);

        Calendar when = Calendar.getInstance();
        when.add(Calendar.MINUTE, 10);
        when.set(Calendar.SECOND, 0);
        TextView time = text("📅  " + fmt.format(when.getTime()) + "  (badlo)", 15, 0xFF00A884, true);
        time.setPadding(0, px(14), 0, px(10));
        time.setOnClickListener(v -> new DatePickerDialog(this, (dp1, y, m, d) -> {
            when.set(y, m, d);
            new TimePickerDialog(this, (tp, h, min) -> {
                when.set(Calendar.HOUR_OF_DAY, h);
                when.set(Calendar.MINUTE, min);
                time.setText("📅  " + fmt.format(when.getTime()) + "  (badlo)");
            }, when.get(Calendar.HOUR_OF_DAY), when.get(Calendar.MINUTE), false).show();
        }, when.get(Calendar.YEAR), when.get(Calendar.MONTH), when.get(Calendar.DAY_OF_MONTH)).show());
        f.addView(time);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("⏰ " + WaActivity.slotName(this, slot) + " se schedule")
                .setView(f)
                .setPositiveButton("Schedule", (d, w) -> {
                    String num = phone.getText().toString().replaceAll("[^0-9]", "");
                    if (num.length() == 10) num = "91" + num; // Indian number without code
                    String text = msg.getText().toString().trim();
                    if (num.length() < 11 || text.isEmpty()) { toast("Number ya message galat hai"); return; }
                    if (when.getTimeInMillis() < System.currentTimeMillis()) { toast("Aage ka time chuno"); return; }
                    Sched.add(this, slot, num, text, when.getTimeInMillis());
                    toast("Schedule ho gaya: " + fmt.format(when.getTime()));
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
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
