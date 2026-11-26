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
    static final int MAX = 10;
    static final int[] COLORS = {0xFF00A884, 0xFF7F5AF0, 0xFFFF6B5B, 0xFF3A86FF, 0xFFFFB020,
            0xFF25D366, 0xFFE84393, 0xFF00B4D8, 0xFFFF7A00, 0xFF607D8B};

    /** Colours the user can pick from for an account. */
    static final int[] PALETTE = {
            0xFF00A884, 0xFF25D366, 0xFF7F5AF0, 0xFFB15CFF,
            0xFFFF6B5B, 0xFFE84393, 0xFF3A86FF, 0xFF00B4D8,
            0xFFFFB020, 0xFFFF7A00, 0xFF8D6E63, 0xFF607D8B };

    /** Slots the user has added (first 5 are on by default). */
    static java.util.List<Integer> activeSlots(android.content.Context c) {
        SharedPreferences p = c.getSharedPreferences("names", MODE_MULTI_PROCESS);
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (int i = 0; i < MAX; i++) if (p.getBoolean("show" + i, i < 5)) out.add(i);
        return out;
    }

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

        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0B1F2A, 0xFF0E3B3A, 0xFF07141B}));
        frame.addView(glow(0x5500A884, px(340)), glowLp(px(340), Gravity.TOP | Gravity.END, -px(120), -px(110)));
        frame.addView(glow(0x447F5AF0, px(300)), glowLp(px(300), Gravity.BOTTOM | Gravity.START, -px(120), -px(90)));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        frame.addView(sv);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(16), px(44), px(16), px(28));
        sv.addView(root);

        greeting = text(greet(), 13, 0xB3FFFFFF, false);
        greeting.setPadding(px(6), 0, 0, 0);
        root.addView(greeting);
        // Title: your own name (long-press to change)
        TextView title = text(prefs.getString("title", "Inbox HQ"), 34, Color.WHITE, true);
        title.setPadding(px(6), px(2), 0, px(12));
        title.setOnLongClickListener(v -> { editTitle(title); return true; });
        root.addView(title);

        // 🌍 Three world clocks (tap one to change its city)
        clocks = new LinearLayout(this);
        clocks.setPadding(px(6), 0, px(6), px(12));
        root.addView(clocks);
        refreshClocks();

        // Stats strip: accounts · unread · scheduled
        stats = new LinearLayout(this);
        stats.setPadding(px(6), 0, px(6), 0);
        root.addView(stats);

        // Quick actions: lock + schedule
        lockBtn = pill("", 0x2200A884);
        lockBtn.setOnClickListener(v -> { Lock.setEnabled(this, !Lock.enabled(this)); updateLockLabel(); });
        TextView schedBtn = pill("⏰  Schedule", 0x22FFB020);
        schedBtn.setOnClickListener(v -> startActivity(new Intent(this, ScheduleActivity.class)));
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(px(6), px(12), px(6), px(6));
        actions.addView(lockBtn, statLp(false));
        actions.addView(schedBtn, statLp(true));
        TextView setBtn = pill("⚙️", 0x22FFFFFF);
        setBtn.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(px(52), LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = px(8);
        actions.addView(setBtn, sp);
        root.addView(actions);
        updateLockLabel();

        TextView section = text("YOUR ACCOUNTS", 11, 0x99FFFFFF, true);
        section.setLetterSpacing(0.12f);
        section.setPadding(px(6), px(18), 0, px(4));
        root.addView(section);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        TextView hint = text("Long-press a tile → rename, colour, logout or remove", 12, 0x80FFFFFF, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, px(14), 0, 0);
        root.addView(hint);

        setContentView(frame);
        build(true);
        if (getIntent().getBooleanExtra("connectAll", false)) connectAll(this);

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
            for (int i = 0; i < MAX; i++) sb.append(prefs.getInt("u" + i, 0)).append(',');
            if (!sb.toString().equals(lastCounts)) {
                boolean first = lastCounts.isEmpty();
                lastCounts = sb.toString();
                if (!first) build(false);
            }
            refreshClocks();
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

    private LinearLayout stats;
    private TextView greeting;

    private void build(boolean animate) {
        list.removeAllViews();
        java.util.List<Integer> slots = activeSlots(this);
        LinearLayout row = null;
        int n = 0;
        for (int i : slots) {
            if (n % 2 == 0) { row = gridRow(); list.addView(row); }
            row.addView(tile(i, n, animate), cell());
            n++;
        }
        if (slots.size() < MAX) {
            if (n % 2 == 0) { row = gridRow(); list.addView(row); }
            row.addView(addTile(), cell());
            n++;
        }
        if (n % 2 == 1) row.addView(new View(this), cell()); // keep last tile half width
        refreshStats();
        Extras.updateWidget(this);
    }

    private LinearLayout gridRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private LinearLayout.LayoutParams cell() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(px(6), px(6), px(6), px(6));
        return lp;
    }

    /** Colourful account tile: glowing gradient, big avatar, unread badge. */
    private View tile(int i, int pos, boolean animate) {
        String name = WaActivity.slotName(this, i);
        int color = color(this, i);
        int unread = prefs.getInt("u" + i, 0);

        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(px(14), px(14), px(14), px(14));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{(color & 0x00FFFFFF) | 0x66000000, (color & 0x00FFFFFF) | 0x14000000});
        bg.setCornerRadius(px(24));
        bg.setStroke(px(1), (color & 0x00FFFFFF) | 0x77000000);
        t.setBackground(bg);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar = text(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), 21, Color.WHITE, true);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{color, darker(color)});
        circle.setShape(GradientDrawable.OVAL);
        circle.setStroke(px(2), 0x55FFFFFF);
        avatar.setBackground(circle);
        top.addView(avatar, new LinearLayout.LayoutParams(px(46), px(46)));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        if (unread > 0) {
            TextView badge = text(unread > 99 ? "99+" : String.valueOf(unread), 12, Color.WHITE, true);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(px(7), 0, px(7), 0);
            badge.setMinWidth(px(24));
            GradientDrawable bb = new GradientDrawable();
            bb.setCornerRadius(px(12));
            bb.setColor(0xFFFF3B30);
            badge.setBackground(bb);
            top.addView(badge, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, px(24)));
        }
        t.addView(top);

        TextView nm = text(name + (Extras.muted(this, i) ? "  🔕" : ""), 16, Color.WHITE, true);
        nm.setSingleLine(true);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nm.setPadding(0, px(12), 0, px(2));
        t.addView(nm);
        t.addView(text(unread > 0 ? "● " + unread + " unread" : "✓ All read", 12,
                unread > 0 ? 0xFFFFB4AE : 0x99FFFFFF, false));
        int today = Extras.today(this, i);
        if (today > 0) t.addView(text("📊 Today " + today + " msgs", 11, 0x99FFFFFF, false));
        TextView open = text("Open  ›", 13, Color.WHITE, true);
        open.setPadding(0, px(12), 0, 0);
        open.setAlpha(0.85f);
        t.addView(open);

        t.setOnClickListener(v -> v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80)
                .withEndAction(() -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80);
                    Intent it = new Intent(this, WaActivity.SLOTS[i]);
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(it);
                }));
        t.setOnLongClickListener(v -> { editMenu(i); return true; });

        if (animate) {
            t.setAlpha(0f);
            t.setScaleX(0.85f);
            t.setScaleY(0.85f);
            t.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(pos * 60L).setDuration(380)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start();
        }
        return t;
    }

    private View addTile() {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setMinimumHeight(px(150));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(24));
        g.setColor(0x0DFFFFFF);
        g.setStroke(px(2), 0x44FFFFFF, px(8), px(6));
        t.setBackground(g);
        TextView plus = text("+", 34, Color.WHITE, false);
        plus.setGravity(Gravity.CENTER);
        t.addView(plus);
        TextView l = text("Add account", 13, 0xCCFFFFFF, true);
        l.setGravity(Gravity.CENTER);
        t.addView(l);
        t.setOnClickListener(v -> addAccount());
        return t;
    }

    /** Top strip: accounts · unread · scheduled. */
    private void refreshStats() {
        if (stats == null) return;
        stats.removeAllViews();
        java.util.List<Integer> slots = activeSlots(this);
        int unread = 0;
        for (int i : slots) unread += prefs.getInt("u" + i, 0);
        int pending = 0;
        org.json.JSONArray a = Sched.all(this);
        for (int k = 0; k < a.length(); k++)
            if ("pending".equals(a.optJSONObject(k).optString("status"))) pending++;
        stats.setOnClickListener(v -> Extras.showSummary(this));
        stats.addView(stat(String.valueOf(slots.size()), "Accounts", 0xFF00A884), statLp(false));
        stats.addView(stat(String.valueOf(unread), "Unread", 0xFFFF6B5B), statLp(true));
        stats.addView(stat(String.valueOf(pending), "Scheduled", 0xFFFFB020), statLp(true));
        if (greeting != null) greeting.setText(greet());
    }

    private View stat(String value, String label, int accent) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setPadding(px(12), px(10), px(12), px(10));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(16));
        g.setColor(0x14FFFFFF);
        s.setBackground(g);
        s.addView(text(value, 22, accent, true));
        s.addView(text(label, 11, 0x99FFFFFF, false));
        return s;
    }

    private LinearLayout.LayoutParams statLp(boolean gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        if (gap) lp.leftMargin = px(8);
        return lp;
    }

    private String greet() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String g = h < 12 ? "Good morning ☀️" : h < 17 ? "Good afternoon 🌤️" : "Good evening 🌙";
        return g + "  ·  " + new java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.ENGLISH).format(new java.util.Date());
    }

    /** Soft coloured glow for the background. */
    private View glow(int color, int size) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(size / 2f);
        g.setColors(new int[]{color, color & 0x00FFFFFF});
        v.setBackground(g);
        return v;
    }

    private android.widget.FrameLayout.LayoutParams glowLp(int size, int gravity, int mx, int my) {
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(size, size, gravity);
        lp.setMargins(mx, my, mx, my);
        return lp;
    }

    private void editMenu(int i) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(WaActivity.slotName(this, i))
                .setItems(new String[]{"✏️  Rename", "🎨  Change colour", "🚪  Logout / reset", "➖  Remove account",
                        Extras.muted(this, i) ? "🔔  Unmute" : "🔕  Mute"}, (d, w) -> {
                    if (w == 0) rename(i);
                    else if (w == 1) pickColor(i);
                    else if (w == 2) confirmReset(i);
                    else if (w == 3) confirmRemove(i);
                    else pickMute(i);
                })
                .show();
    }

    private void pickMute(int i) {
        if (Extras.muted(this, i)) { Extras.mute(this, i, 0); build(false); return; }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🔕 " + WaActivity.slotName(this, i) + " — mute for")
                .setItems(new String[]{"1 hour", "8 hours (overnight)", "Always"}, (d, w) -> {
                    Extras.mute(this, i, w == 0 ? 3600_000L : w == 1 ? 8 * 3600_000L : -1);
                    build(false);
                })
                .show();
    }

    /** Opens every account briefly so each one connects, then returns here. */
    static void connectAll(Activity a) {
        java.util.List<Integer> slots = activeSlots(a);
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        for (int k = 0; k < slots.size(); k++) {
            int s = slots.get(k);
            h.postDelayed(() -> a.startActivity(new Intent(a, WaActivity.SLOTS[s])
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("bg", true)), k * 700L);
        }
        h.postDelayed(() -> a.startActivity(new Intent(a, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)), slots.size() * 700L + 1500);
        android.widget.Toast.makeText(a, "Connecting all accounts…", android.widget.Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("connectAll", false)) connectAll(this);
    }

    private void addAccount() {
        for (int i = 0; i < MAX; i++) {
            if (!prefs.getBoolean("show" + i, i < 5)) {
                prefs.edit().putBoolean("show" + i, true)
                        .putString("n" + i, "WhatsApp " + (i + 1)).putInt("u" + i, 0).commit();
                App.refreshChannels(this);
                build(false);
                Intent it = new Intent(this, WaActivity.SLOTS[i]);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it); // shows QR code to link the new number
                return;
            }
        }
        android.widget.Toast.makeText(this, "Maximum " + MAX + " accounts allowed", android.widget.Toast.LENGTH_LONG).show();
    }

    private void confirmRemove(int i) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(WaActivity.slotName(this, i) + ": remove this account?")
                .setMessage("This account will be logged out and removed from the list. You can add it again later with \"+ Add account\".")
                .setPositiveButton("Remove", (d, w) -> {
                    prefs.edit().putBoolean("show" + i, false).putInt("u" + i, 0).remove("n" + i).remove("c" + i).commit();
                    Intent it = new Intent(this, WaActivity.SLOTS[i]);
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    it.putExtra("remove", true);
                    startActivity(it);
                    build(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmReset(int i) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(WaActivity.slotName(this, i) + ": log out?")
                .setMessage("WhatsApp will be removed from this slot. To link it again, scan a new QR code.")
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
                .setTitle(WaActivity.slotName(this, i) + " colour")
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
                .setTitle("Rename account " + (i + 1))
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

    // ---------- Title + world clocks ----------
    private LinearLayout clocks;

    private void editTitle(TextView title) {
        EditText in = new EditText(this);
        in.setText(title.getText());
        in.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Home screen title")
                .setMessage("Your business name, or anything you like")
                .setView(in)
                .setPositiveButton("Save", (d, w) -> {
                    String t = in.getText().toString().trim();
                    if (t.isEmpty()) t = "Inbox HQ";
                    prefs.edit().putString("title", t).commit();
                    title.setText(t);
                })
                .setNegativeButton("Cancel", null).show();
    }

    static final String[] ZONES = {
            "India|Asia/Kolkata", "Dubai|Asia/Dubai", "London|Europe/London", "New York|America/New_York",
            "Singapore|Asia/Singapore", "Hong Kong|Asia/Hong_Kong", "Shanghai|Asia/Shanghai", "Tokyo|Asia/Tokyo",
            "Sydney|Australia/Sydney", "Los Angeles|America/Los_Angeles", "Chicago|America/Chicago",
            "Toronto|America/Toronto", "Frankfurt|Europe/Berlin", "Paris|Europe/Paris", "Riyadh|Asia/Riyadh",
            "Doha|Asia/Qatar", "Kathmandu|Asia/Kathmandu", "Dhaka|Asia/Dhaka", "Bangkok|Asia/Bangkok",
            "Johannesburg|Africa/Johannesburg", "Nairobi|Africa/Nairobi", "São Paulo|America/Sao_Paulo" };
    private static final String[] DEFAULT_TZ = {"India|Asia/Kolkata", "Dubai|Asia/Dubai", "London|Europe/London"};

    private static final int MAX_CLOCKS = 6;

    /** Saved clocks as "City|Zone" (first run: India, Dubai, London). */
    private java.util.List<String> clockList() {
        java.util.List<String> l = new java.util.ArrayList<>();
        String saved = prefs.getString("clocks", null);
        try {
            if (saved != null) {
                org.json.JSONArray a = new org.json.JSONArray(saved);
                for (int i = 0; i < a.length(); i++) l.add(a.getString(i));
                return l;
            }
        } catch (Exception ignored) {}
        for (int k = 0; k < 3; k++) {          // carry over the earlier 3-clock setting
            String z = prefs.getString("tz" + k, DEFAULT_TZ[k]);
            if (!z.isEmpty()) l.add(z);
        }
        return l;
    }

    private void saveClocks(java.util.List<String> l) {
        prefs.edit().putString("clocks", new org.json.JSONArray(l).toString()).commit();
        refreshClocks();
    }

    private void refreshClocks() {
        if (clocks == null) return;
        clocks.setOrientation(LinearLayout.VERTICAL);
        clocks.removeAllViews();
        java.util.List<String> list = clockList();
        String today = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(new java.util.Date());
        int cells = list.size() < MAX_CLOCKS ? list.size() + 1 : list.size(); // + "Add clock" card
        LinearLayout row = null;
        for (int k = 0; k < cells; k++) {
            if (k % 3 == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                if (k > 0) rlp.topMargin = px(8);
                clocks.addView(row, rlp);
            }
            final int idx = k;
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(px(10), px(9), px(10), px(9));
            c.setMinimumHeight(px(66));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(16));
            if (k < list.size()) {
                g.setColor(0x14FFFFFF);
                g.setStroke(px(1), 0x22FFFFFF);
                String[] parts = list.get(k).split("\\|");
                java.util.TimeZone tz = java.util.TimeZone.getTimeZone(parts[1]);
                int h = java.util.Calendar.getInstance(tz).get(java.util.Calendar.HOUR_OF_DAY);
                java.text.SimpleDateFormat tf = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH);
                tf.setTimeZone(tz);
                java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US);
                df.setTimeZone(tz);
                String d = df.format(new java.util.Date());
                String day = d.equals(today) ? "Today" : d.compareTo(today) > 0 ? "Tomorrow" : "Yesterday";
                TextView city = text((h >= 6 && h < 18 ? "☀️ " : "🌙 ") + parts[0], 11, 0xB3FFFFFF, false);
                city.setSingleLine(true);
                city.setEllipsize(android.text.TextUtils.TruncateAt.END);
                c.addView(city);
                TextView t = text(tf.format(new java.util.Date()), 17, Color.WHITE, true);
                t.setPadding(0, px(2), 0, 0);
                c.addView(t);
                c.addView(text(day, 10, 0x80FFFFFF, false));
                c.setOnClickListener(v -> clockMenu(idx));
                c.setOnLongClickListener(v -> { removeClock(idx); return true; });
            } else {
                g.setColor(0x0AFFFFFF);
                g.setStroke(px(1), 0x44FFFFFF, px(6), px(4));
                c.setGravity(Gravity.CENTER);
                TextView plus = text("+", 20, Color.WHITE, false);
                plus.setGravity(Gravity.CENTER);
                c.addView(plus);
                TextView add = text("Add clock", 10, 0x99FFFFFF, true);
                add.setGravity(Gravity.CENTER);
                c.addView(add);
                c.setOnClickListener(v -> pickZone(-1));
            }
            c.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
            if (k % 3 > 0) lp.leftMargin = px(8);
            row.addView(c, lp);
        }
        // keep every row three columns wide
        int rem = cells % 3;
        if (rem != 0 && row != null) for (int k = rem; k < 3; k++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1);
            lp.leftMargin = px(8);
            row.addView(new View(this), lp);
        }
    }

    /** Tap a clock: change its city or remove it. */
    private void clockMenu(int idx) {
        String name = clockList().get(idx).split("\\|")[0];
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🕒 " + name)
                .setItems(new String[]{"🌍  Change city", "🗑  Remove this clock"}, (d, w) -> {
                    if (w == 0) pickZone(idx); else removeClock(idx);
                })
                .show();
    }

    private void removeClock(int idx) {
        java.util.List<String> l = clockList();
        if (idx < 0 || idx >= l.size()) return;
        String name = l.get(idx).split("\\|")[0];
        l.remove(idx);
        saveClocks(l);
        android.widget.Toast.makeText(this, name + " clock removed", android.widget.Toast.LENGTH_SHORT).show();
    }

    /** idx = -1 adds a new clock, otherwise changes that clock's city. */
    private void pickZone(int idx) {
        java.util.List<String> l = clockList();
        String[] names = new String[ZONES.length];
        for (int i = 0; i < ZONES.length; i++)
            names[i] = (l.contains(ZONES[i]) ? "✓  " : "🌍  ") + ZONES[i].split("\\|")[0];
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(idx < 0 ? "Add a clock" : "Change city")
                .setItems(names, (d, w) -> {
                    java.util.List<String> cur = clockList();
                    if (idx < 0) { if (cur.size() < MAX_CLOCKS) cur.add(ZONES[w]); }
                    else if (idx < cur.size()) cur.set(idx, ZONES[w]);
                    saveClocks(cur);
                })
                .show();
    }

    private TextView pill(String s, int tint) {
        TextView t = text(s, 14, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(12), px(12), px(12), px(12));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(18));
        g.setColor(tint);
        g.setStroke(px(1), 0x22FFFFFF);
        t.setBackground(g);
        return t;
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
