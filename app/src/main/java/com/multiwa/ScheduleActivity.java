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
        TextView sub = text("The app sends the message automatically at the set time", 13, 0xB3FFFFFF, false);
        sub.setPadding(0, px(4), 0, px(18));
        root.addView(sub);

        TextView add = text("+  Schedule a new message", 15, Color.WHITE, true);
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

        TextView hint = text("Long-press → delete", 12, 0x80FFFFFF, false);
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
            TextView e = text("No messages scheduled yet", 14, 0x99FFFFFF, false);
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
        String badge = "sent".equals(st) ? "✅ Sent" : "failed".equals(st) ? "❌ Not sent" : "⏳ Pending";

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

        String who = o.optString("name").isEmpty() ? "+" + o.optString("phone")
                : o.optString("name") + " (+" + o.optString("phone") + ")";
        TextView top = text(WaActivity.slotName(this, slot).toUpperCase() + "  →  " + who, 11, color, true);
        c.addView(top);
        TextView msg = text(o.optString("text"), 15, Color.WHITE, false);
        msg.setPadding(0, px(4), 0, px(6));
        c.addView(msg);
        String rp = o.optString("repeat", "none");
        int ri = java.util.Arrays.asList(REPEAT_KEYS).indexOf(rp);
        String repTxt = ri > 0 ? "   ·   🔁 " + REPEAT_LABELS[ri] : "";
        c.addView(text("🕒 " + fmt.format(new Date(o.optLong("when"))) + "   ·   " + badge + repTxt, 12, 0xB3FFFFFF, false));

        long id = o.optLong("id");
        c.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Delete this?")
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
                .setTitle("Send from which account?")
                .setItems(names, (d, w) -> form(slots.get(w)))
                .show();
    }

    private void form(int slot) {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(px(22), px(8), px(22), 0);

        EditText phone = new EditText(this);
        phone.setHint("Number(s), separated by commas (e.g. 9876543210)");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        f.addView(phone);

        // 📒 Pick from phonebook (no contacts permission needed: user picks one contact)
        TextView pick = text("📒  Pick from contacts", 14, 0xFF00A884, true);
        pick.setPadding(0, px(8), 0, px(6));
        pick.setOnClickListener(v -> {
            pickPhone = phone;
            pickName = new String[]{""};
            try {
                startActivityForResult(new android.content.Intent(android.content.Intent.ACTION_PICK,
                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI), 41);
            } catch (Exception e) { toast("Couldn't open contacts"); }
        });
        f.addView(pick);
        contactLabel = text("", 13, 0xB3FFFFFF, false);
        f.addView(contactLabel);
        final String[] nameRef = new String[]{""};
        pickName = nameRef;

        EditText msg = new EditText(this);
        msg.setHint("Type your message");
        msg.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        msg.setMinLines(2);
        f.addView(msg);

        Calendar when = Calendar.getInstance();
        when.add(Calendar.MINUTE, 10);
        when.set(Calendar.SECOND, 0);
        TextView time = text("📅  " + fmt.format(when.getTime()) + "  (change)", 15, 0xFF00A884, true);
        time.setPadding(0, px(14), 0, px(10));
        time.setOnClickListener(v -> new DatePickerDialog(this, (dp1, y, m, d) -> {
            when.set(y, m, d);
            new TimePickerDialog(this, (tp, h, min) -> {
                when.set(Calendar.HOUR_OF_DAY, h);
                when.set(Calendar.MINUTE, min);
                time.setText("📅  " + fmt.format(when.getTime()) + "  (change)");
            }, when.get(Calendar.HOUR_OF_DAY), when.get(Calendar.MINUTE), false).show();
        }, when.get(Calendar.YEAR), when.get(Calendar.MONTH), when.get(Calendar.DAY_OF_MONTH)).show());
        f.addView(time);

        // 🔁 Repeat: tap to cycle
        final int[] repeat = {0};
        TextView rep = text("🔁  Repeat: " + REPEAT_LABELS[0] + "  (change)", 15, 0xFF00A884, true);
        rep.setPadding(0, px(4), 0, px(10));
        rep.setOnClickListener(v -> {
            repeat[0] = (repeat[0] + 1) % REPEAT_KEYS.length;
            rep.setText("🔁  Repeat: " + REPEAT_LABELS[repeat[0]] + "  (change)");
        });
        f.addView(rep);
        picked.clear();

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("⏰ " + WaActivity.slotName(this, slot) + ": schedule")
                .setView(f)
                .setPositiveButton("Schedule", (d, w) -> {
                    String text = msg.getText().toString().trim();
                    if (text.isEmpty()) { toast("Type your message"); return; }
                    if (when.getTimeInMillis() < System.currentTimeMillis()) { toast("Pick a future time"); return; }
                    // One or more numbers, separated by commas.
                    java.util.List<String> nums = new java.util.ArrayList<>();
                    for (String part : phone.getText().toString().split("[,;\\n]")) {
                        String num = part.replaceAll("[^0-9]", "").replaceFirst("^0+", "");
                        if (num.length() == 10) num = "91" + num; // Indian number without code
                        if (num.length() >= 11 && !nums.contains(num)) nums.add(num);
                    }
                    if (nums.isEmpty()) { toast("Invalid number"); return; }
                    if (nums.size() > 20) { toast("Max 20 numbers at once (to avoid a WhatsApp ban)"); return; }
                    for (int k = 0; k < nums.size(); k++) {
                        String nm = picked.containsKey(nums.get(k)) ? picked.get(nums.get(k)) : "";
                        // 2 minutes gap between people, to look natural to WhatsApp
                        Sched.add(this, slot, nums.get(k), text, when.getTimeInMillis() + k * 120_000L, nm, REPEAT_KEYS[repeat[0]]);
                    }
                    toast(nums.size() + " message(s) scheduled: " + fmt.format(when.getTime()));
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    static final String[] REPEAT_KEYS = {"none", "daily", "weekly", "monthly"};
    static final String[] REPEAT_LABELS = {"Never", "Daily", "Weekly", "Monthly"};
    private final java.util.LinkedHashMap<String, String> picked = new java.util.LinkedHashMap<>();
    private EditText pickPhone;
    private String[] pickName = {""};
    private TextView contactLabel;

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 41 || res != RESULT_OK || data == null || data.getData() == null || pickPhone == null) return;
        try (android.database.Cursor c = getContentResolver().query(data.getData(), new String[]{
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String num = c.getString(0);
                String nm = c.getString(1) == null ? "" : c.getString(1);
                String cur = pickPhone.getText().toString().trim();
                pickPhone.setText(cur.isEmpty() ? num : cur + ", " + num);
                String key = num.replaceAll("[^0-9]", "").replaceFirst("^0+", "");
                if (key.length() == 10) key = "91" + key;
                picked.put(key, nm);
                pickName[0] = nm;
                if (contactLabel != null) contactLabel.setText("👤 " + android.text.TextUtils.join(", ", picked.values()));
            }
        } catch (Exception e) { toast("Contact not found"); }
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
