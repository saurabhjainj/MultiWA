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

    private SharedPreferences prefs;
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
        sub.setPadding(0, px(4), 0, px(28));
        root.addView(sub);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        TextView hint = text("Lamba dabao → naam badlo", 12, 0x80FFFFFF, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, px(16), 0, 0);
        root.addView(hint);

        setContentView(sv);
        build(true);
    }

    private void build(boolean animate) {
        list.removeAllViews();
        for (int i = 0; i < 5; i++) list.addView(card(i, animate));
    }

    private View card(int i, boolean animate) {
        String name = WaActivity.slotName(this, i);
        int color = COLORS[i];

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

        TextView arrow = text("›", 30, color, true);
        row.addView(arrow);

        row.setOnClickListener(v -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                .withEndAction(() -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80);
                    Intent it = new Intent(this, WaActivity.SLOTS[i]);
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(it);
                }));
        row.setOnLongClickListener(v -> { rename(i); return true; });

        if (animate) {
            row.setAlpha(0f);
            row.setTranslationY(px(40));
            row.animate().alpha(1f).translationY(0).setStartDelay(i * 70L).setDuration(380)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        return row;
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
