package com.multiwa;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;
import java.util.List;

/** One WhatsApp Web session. Each subclass runs in its own process (see manifest). */
public abstract class WaActivity extends Activity {

    public static class A1 extends WaActivity {}
    public static class A2 extends WaActivity {}
    public static class A3 extends WaActivity {}
    public static class A4 extends WaActivity {}
    public static class A5 extends WaActivity {}

    // WhatsApp Web refuses mobile browsers, so present as desktop Chrome.
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final int REQ_FILE = 1, REQ_PERM = 2;

    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private PermissionRequest pendingPerm;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        // Status bar in the account's colour, so you always know which number is open.
        getWindow().setStatusBarColor(MainActivity.darker(MainActivity.COLORS[mySlot()]));
        FrameLayout frame = new FrameLayout(this);
        frame.addView(web);
        frame.addView(makeSwitchButton());
        setContentView(frame);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUserAgentString(UA);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String host = u.getHost() == null ? "" : u.getHost();
                if (host.endsWith("whatsapp.com") || host.endsWith("whatsapp.net")) return false;
                // Other links (shared in chats) open in the normal browser.
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(p.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest req) {
                runOnUiThread(() -> handlePermission(req));
            }
        });

        if (b != null) web.restoreState(b);
        else web.loadUrl("https://web.whatsapp.com/");
    }

    static final Class<?>[] SLOTS = { A1.class, A2.class, A3.class, A4.class, A5.class };

    private int mySlot() {
        for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i] == getClass()) return i;
        return 0;
    }

    static String slotName(android.content.Context c, int i) {
        // MODE_MULTI_PROCESS so names renamed on the home screen show up here too.
        return c.getSharedPreferences("names", MODE_MULTI_PROCESS)
                .getString("n" + i, "WhatsApp " + (i + 1));
    }

    /** Small floating round button: shows current number, tap to switch. Drag to move it. */
    private View makeSwitchButton() {
        int d = (int) (52 * getResources().getDisplayMetrics().density);
        TextView b = new TextView(this);
        b.setText("⇄ " + (mySlot() + 1));
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        int c = MainActivity.COLORS[mySlot()];
        bg.setColor((c & 0x00FFFFFF) | 0xE6000000);
        bg.setStroke((int) (2 * getResources().getDisplayMetrics().density), Color.WHITE);
        b.setBackground(bg);
        b.setElevation(8);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(d, d, Gravity.END | Gravity.TOP);
        lp.topMargin = (int) (getResources().getDisplayMetrics().heightPixels * 0.35f);
        lp.rightMargin = d / 5;
        b.setLayoutParams(lp);

        final float[] start = new float[2];
        final boolean[] moved = {false};
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = e.getRawY(); start[1] = v.getTranslationY(); moved[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dy = e.getRawY() - start[0];
                    if (Math.abs(dy) > 15) moved[0] = true;
                    v.setTranslationY(start[1] + dy);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved[0]) showSwitcher();
                    return true;
            }
            return false;
        });
        return b;
    }

    private void showSwitcher() {
        int me = mySlot();
        CharSequence[] items = new CharSequence[SLOTS.length + 1];
        for (int i = 0; i < SLOTS.length; i++) {
            String label = "●  " + slotName(this, i) + (i == me ? "   ✓" : "");
            android.text.SpannableString s = new android.text.SpannableString(label);
            s.setSpan(new android.text.style.ForegroundColorSpan(MainActivity.COLORS[i]), 0, 1, 0);
            items[i] = s;
        }
        items[SLOTS.length] = "⌂  Home screen";
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Switch WhatsApp")
                .setItems(items, (dlg, which) -> {
                    if (which == me) return;
                    Class<?> target = which < SLOTS.length ? SLOTS[which] : MainActivity.class;
                    Intent it = new Intent(this, target);
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(it);
                    overridePendingTransition(0, 0);
                })
                .show();
    }

    private void handlePermission(PermissionRequest req) {
        List<String> need = new ArrayList<>();
        for (String r : req.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && !has(Manifest.permission.RECORD_AUDIO))
                need.add(Manifest.permission.RECORD_AUDIO);
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && !has(Manifest.permission.CAMERA))
                need.add(Manifest.permission.CAMERA);
        }
        if (need.isEmpty()) {
            req.grant(req.getResources());
        } else {
            pendingPerm = req;
            requestPermissions(need.toArray(new String[0]), REQ_PERM);
        }
    }

    private boolean has(String perm) {
        return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == REQ_PERM && pendingPerm != null) {
            boolean ok = true;
            for (int r : res) ok &= r == PackageManager.PERMISSION_GRANTED;
            if (ok) pendingPerm.grant(pendingPerm.getResources()); else pendingPerm.deny();
            pendingPerm = null;
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_FILE && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCb = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else moveTaskToBack(true); // keep session alive instead of closing
    }

    @Override
    protected void onDestroy() {
        CookieManager.getInstance().flush();
        web.destroy();
        super.onDestroy();
    }
}
