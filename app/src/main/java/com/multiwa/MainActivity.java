package com.multiwa;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    /** One colour per account: teal, violet, coral, blue, amber. */
    static final int[] COLORS = {0xFF00A884, 0xFF7F5AF0, 0xFFFF6B5B, 0xFF3A86FF, 0xFFFFB020};

    /** Colours the user can pick from for an account. */
    static final int[] PALETTE = {
            0xFF00A884, 0xFF25D366, 0xFF7F5AF0, 0xFFB15CFF,
            0xFFFF6B5B, 0xFFE84393, 0xFF3A86FF, 0xFF00B4D8,
            0xFFFFB020, 0xFFFF7A00, 0xFF8D6E63, 0xFF607D8B };

    /** Account colour: the one the user picked, else the default. */
    static int color(android.content.Context c, int slot) {
        return c.getSharedPreferences("names", MODE_MULTI_PROCESS).getInt("c" + slot, COLORS[slot]);
    }

    private SharedPreferences prefs;
    private TextView lockBtn;
    private LinearLayout list;
    private float dp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("names", MODE_MULTI_PROCESS);
        getWindow().setStatusBarColor(0xFF0B1F2A);
        getWindow().setNavigationBarColor(0xFF07141B);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0B1F2A, 0xFF0E3B3A, 0xFF07141B}));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(22), px(48), px(22), px(28));
        sv.addView(root);

        TextView title = text("Multi WA", 34, Color.WHITE, true);
        root.addView(title);
        TextView sub = text("5 WhatsApp · ek hi app", 15, 0xB3FFFFFF, false);
        sub.setPadding(0, px(4), 0, px(12));
        root.addView(sub);

        // App lock on/off chip
        lockBtn = text("", 13, Color.WHITE, true);
        lockBtn.setPadding(px(14), px(7), px(14), px(7));
        GradientDrawable chip = new GradientDrawable();
        chip.setCornerRadius(px(16));
        chip.setColor(0x22FFFFFF);
        lockBtn.setBackground(chip);
        lockBtn.setOnClickListener(v -> { Lock.setEnabled(this, !Lock.enabled(this)); updateLockLabel(); });
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.bottomMargin = px(22);
        root.addView(lockBtn, llp);
        updateLockLabel();

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        TextView hint = text("Lamba dabao → naam ya rang badlo", 12, 0x80FFFFFF, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, px(16), 0, 0);
        root.addView(hint);

        setContentView(sv);
        build(true);

        // Android 13+: ask once for permission to show message notifications.
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                   != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
        else
            askBatteryExemption();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == 7) askBatteryExemption();
    }

    /** Ask Android not to put the app to sleep, so messages arrive without delay. */
    private void askBatteryExemption() {
        android.os.PowerManager pm = getSystemService(android.os.PowerManager.class);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {}
    }

    // Refresh unread badges every 3 seconds while the home screen is open.
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private String lastCounts = "";
    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            prefs = getSharedPreferences("names", MODE_MULTI_PROCESS); // reload from other processes
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) sb.append(prefs.getInt("u" + i, 0)).append(',');
            if (!sb.toString().equals(lastCounts)) {
                boolean first = lastCounts.isEmpty();
                lastCounts = sb.toString();
                if (!first) build(false);
            }
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        lastCounts = "x";          // force one refresh when coming back
        handler.post(refresher);
        Lock.check(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Lock.touch(this);
        handler.removeCallbacks(refresher);
    }

    private void build(boolean animate) {
        list.removeAllViews();
        for (int i = 0; i < 5; i++) list.addView(card(i, animate));
    }

    private View card(int i, boolean animate) {
        String name = WaActivity.slotName(this, i);
        int color = color(this, i);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(16), px(14), px(16), px(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(20));
        bg.setColor(0x14FFFFFF);
        bg.setStroke(px(1), 0x22FFFFFF);
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(12);
        row.setLayoutParams(lp);

        // Coloured circle with first letter of the name
        TextView avatar = text(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), 20, Color.WHITE, true);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{color, darker(color)});
        circle.setShape(GradientDrawable.OVAL);
        avatar.setBackground(circle);
        row.addView(avatar, new LinearLayout.LayoutParams(px(50), px(50)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(px(14), 0, 0, 0);
        texts.addView(text(name, 17, Color.WHITE, true));
        texts.addView(text("Account " + (i + 1), 12, 0x99FFFFFF, false));
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        int unread = prefs.getInt("u" + i, 0);
        if (unread > 0) {
            TextView badge = text(unread > 99 ? "99+" : String.valueOf(unread), 12, Color.WHITE, true);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(px(7), 0, px(7), 0);
            badge.setMinWidth(px(24));
            GradientDrawable bb = new GradientDrawable();
            bb.setCornerRadius(px(12));
            bb.setColor(0xFFFF3B30);
            badge.setBackground(bb);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, px(24));
            blp.rightMargin = px(8);
            row.addView(badge, blp);
        }

        TextView arrow = text("›", 30, color, true);
        row.addView(arrow);

        row.setOnClickListener(v -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                .withEndAction(() -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80);
                    Intent it = new Intent(this, WaActivity.SLOTS[i]);
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(it);
                }));
        row.setOnLongClickListener(v -> { editMenu(i); return true; });

        if (animate) {
            row.setAlpha(0f);
            row.setTranslationY(px(40));
            row.animate().alpha(1f).translationY(0).setStartDelay(i * 70L).setDuration(380)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        return row;
    }

    private void editMenu(int i) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(WaActivity.slotName(this, i))
                .setItems(new String[]{"✏️  Naam badlo", "🎨  Rang badlo", "🚪  Logout / reset"}, (d, w) -> {
                    if (w == 0) rename(i);
                    else if (w == 1) pickColor(i);
                    else confirmReset(i);
                })
                .show();
    }

    private void confirmReset(int i) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(WaActivity.slotName(this, i) + " logout karein?")
                .setMessage("Is slot se WhatsApp hat jaayega. Dobara jodne ke liye naya QR scan karna hoga.")
                .setPositiveButton("Logout", (d, w) -> {
                    Intent it = new Intent(this, WaActivity.SLOTS[i]);
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    it.putExtra("reset", true);
                    startActivity(it);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateLockLabel() {
        lockBtn.setText(Lock.enabled(this) ? "🔒 Lock ON" : "🔓 Lock OFF");
    }

    private void pickColor(int i) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(px(20), px(12), px(20), px(4));
        AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(WaActivity.slotName(this, i) + " ka rang")
                .setView(grid)
                .setNegativeButton("Cancel", null)
                .create();
        int current = color(this, i);
        LinearLayout row = null;
        for (int k = 0; k < PALETTE.length; k++) {
            if (k % 4 == 0) {
                row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER);
                grid.addView(row);
            }
            int c = PALETTE[k];
            TextView dot = text(c == current ? "✓" : "", 20, Color.WHITE, true);
            dot.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c, darker(c)});
            g.setShape(GradientDrawable.OVAL);
            if (c == current) g.setStroke(px(3), Color.WHITE);
            dot.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(48), px(48));
            lp.setMargins(px(8), px(8), px(8), px(8));
            row.addView(dot, lp);
            dot.setOnClickListener(v -> {
                prefs.edit().putInt("c" + i, c).commit();
                dlg.dismiss();
                build(false);
            });
        }
        dlg.show();
    }

    private void rename(int i) {
        EditText in = new EditText(this);
        in.setText(WaActivity.slotName(this, i));
        in.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Account " + (i + 1) + " ka naam")
                .setView(in)
                .setPositiveButton("Save", (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (!n.isEmpty()) prefs.edit().putString("n" + i, n).commit();
                    App.refreshChannels(this);
                    build(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    static int darker(int c) {
        return Color.rgb((int) (Color.red(c) * 0.7), (int) (Color.green(c) * 0.7), (int) (Color.blue(c) * 0.7));
    }

    private int px(float v) { return (int) (v * dp); }
}
