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
        // Pretend the page is always on screen, so Android/Chrome does not slow down
        // WhatsApp Web's timers in background (that slowdown delays notifications).
        web = new WebView(this) {
            @Override
            protected void onWindowVisibilityChanged(int visibility) {
                super.onWindowVisibilityChanged(View.VISIBLE);
            }
        };
        // Status bar in the account's colour, so you always know which number is open.
        getWindow().setStatusBarColor(MainActivity.darker(MainActivity.color(this, mySlot())));
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

            @Override
            public void onPageFinished(WebView v, String url) {
                pageLoadedAt = System.currentTimeMillis();
                v.evaluateJavascript(NOTIFY_JS, null); // fallback if document-start injection unsupported
                if (!visible) v.evaluateJavascript("window.__mwaSetBg&&__mwaSetBg(true)", null);
            }
        });

        web.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void show(String title, String body) {
                lastPreview = System.currentTimeMillis();
                String all = (title + " " + body).toLowerCase();
                if (all.contains("call") || all.contains("कॉल")) postCall(title, body);
                else postMessage(title, body);
            }

            @android.webkit.JavascriptInterface
            public void count(int n) {
                runOnUiThread(() -> onUnread(n));
            }

            @android.webkit.JavascriptInterface
            public void save(String dataUrl, String mime, String name) {
                saveFile(dataUrl, mime, name);
            }
        }, "MWA");

        // Inject the notification hook BEFORE WhatsApp Web's own scripts run,
        // so WhatsApp sees notifications as allowed and every alert reaches Android.
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
            androidx.webkit.WebViewCompat.addDocumentStartJavaScript(web, NOTIFY_JS,
                    java.util.Collections.singleton("https://web.whatsapp.com"));
        }

        // Keep this account's process alive in background so messages keep arriving.
        startForegroundService(new Intent(this, KeepAlive.SERVICES[mySlot()]));

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView v, String title) {
                // WhatsApp Web shows unread count in the title, e.g. "(3) WhatsApp".
                int n = 0;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\((\\d+)\\)").matcher(title == null ? "" : title);
                if (m.find()) n = Integer.parseInt(m.group(1));
                onUnread(n);
            }

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

        // Photos, videos, PDFs etc. → saved to Downloads/MultiWA
        web.setDownloadListener((url, ua, cd, mime, len) -> {
            String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            String name = slotName(this, mySlot()).replaceAll("[^A-Za-z0-9]", "") + "_"
                    + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date())
                    + (ext != null ? "." + ext : "");
            if (url.startsWith("blob:")) {
                web.evaluateJavascript("(async()=>{try{const r=await fetch('" + url + "');const b=await r.blob();"
                        + "const f=new FileReader();f.onloadend=()=>MWA.save(f.result,b.type||'" + mime + "','" + name + "');"
                        + "f.readAsDataURL(b);}catch(e){}})()", null);
            } else if (url.startsWith("data:")) {
                saveFile(url, mime, name);
            } else {
                android.app.DownloadManager.Request r = new android.app.DownloadManager.Request(Uri.parse(url));
                r.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
                r.addRequestHeader("User-Agent", UA);
                r.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "MultiWA/" + name);
                r.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                getSystemService(android.app.DownloadManager.class).enqueue(r);
                toast("Download shuru…");
            }
        });

        if (getIntent().getBooleanExtra("reset", false)) { resetAccount(); return; }
        if (b != null) web.restoreState(b);
        else web.loadUrl("https://web.whatsapp.com/");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("reset", false)) resetAccount();
    }

    /** Logout this account: wipe its cookies + storage, show a fresh QR code. */
    private void resetAccount() {
        android.webkit.WebStorage.getInstance().deleteAllData();
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        web.clearCache(true);
        web.clearHistory();
        getSharedPreferences("names", MODE_MULTI_PROCESS).edit().putInt("u" + mySlot(), 0).apply();
        cancelMessage();
        web.loadUrl("https://web.whatsapp.com/");
        toast(slotName(this, mySlot()) + " reset ho gaya. Naya QR scan karo.");
    }

    /** Save a base64 "data:" URL into Downloads/MultiWA. */
    private void saveFile(String dataUrl, String mime, String name) {
        try {
            int comma = dataUrl.indexOf(',');
            byte[] bytes = android.util.Base64.decode(dataUrl.substring(comma + 1), android.util.Base64.DEFAULT);
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime);
            java.io.OutputStream out;
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/MultiWA");
                Uri u = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                out = getContentResolver().openOutputStream(u);
            } else {
                java.io.File dir = new java.io.File(getExternalFilesDir(null), "MultiWA");
                dir.mkdirs();
                out = new java.io.FileOutputStream(new java.io.File(dir, name));
            }
            out.write(bytes);
            out.close();
            toast("Saved: Downloads/MultiWA/" + name);
        } catch (Exception e) {
            toast("Save nahi ho paaya");
        }
    }

    private void toast(String s) {
        runOnUiThread(() -> android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show());
    }

    static final Class<?>[] SLOTS = { A1.class, A2.class, A3.class, A4.class, A5.class };

    // Replaces the browser Notification API so WhatsApp Web's own message
    // notifications (sender + text) are passed to Android.
    private static final String NOTIFY_JS =
            "(function(){if(window.__mwa)return;window.__mwa=1;"
            // 1. Browser notifications -> Android
            + "function send(t,o){o=o||{};try{MWA.show(String(t||''),String(o.body||''))}catch(e){}}"
            + "function N(t,o){send(t,o);this.close=function(){};this.addEventListener=function(){};}"
            + "N.permission='granted';"
            + "N.requestPermission=function(cb){if(cb)cb('granted');return Promise.resolve('granted')};"
            + "try{Object.defineProperty(window,'Notification',{value:N,writable:true,configurable:true})}catch(e){window.Notification=N}"
            + "try{if(window.ServiceWorkerRegistration)ServiceWorkerRegistration.prototype.showNotification=function(t,o){send(t,o);return Promise.resolve()}}catch(e){}"
            + "try{var q=navigator.permissions.query.bind(navigator.permissions);"
            + "navigator.permissions.query=function(p){if(p&&p.name==='notifications')return Promise.resolve({state:'granted',onchange:null});return q(p)}}catch(e){}"
            // 2. Tell WhatsApp when the app is in background (so it sends alerts)
            + "var bg=false;window.__mwaSetBg=function(b){bg=b;try{window.dispatchEvent(new Event(b?'blur':'focus'))}catch(e){}};"
            + "try{document.hasFocus=function(){return !bg}}catch(e){}"
            // 3. Backup: watch unread count in the title every 1.5 s
            + "var last=-1;setInterval(function(){var m=/^\\((\\d+)\\)/.exec(document.title||'');"
            + "var n=m?parseInt(m[1]):0;if(n!==last){last=n;try{MWA.count(n)}catch(e){}}},1500);"
            + "})();";

    private long pageLoadedAt;

    /** Unread count changed (from title). Shows a simple alert if WhatsApp's own one didn't come. */
    private void onUnread(int n) {
        if (n == lastUnread) return;
        int prev = lastUnread;
        lastUnread = n;
        getSharedPreferences("names", MODE_MULTI_PROCESS).edit().putInt("u" + mySlot(), n).apply();
        if (n == 0) { cancelMessage(); return; }
        // Ignore old unread chats counted while the page is first loading.
        if (n > prev && System.currentTimeMillis() - pageLoadedAt > 25000) {
            long t = System.currentTimeMillis();
            web.postDelayed(() -> {
                // Skip if WhatsApp's own notification (with sender + text) already came.
                if (lastPreview < t - 4000 && lastUnread > 0)
                    postMessage("New message", lastUnread + (lastUnread == 1 ? " unread chat" : " unread chats"));
            }, 2500);
        }
    }

    private volatile boolean visible;
    private GradientDrawable switchBg;
    private volatile long lastPreview;
    private int lastUnread;

    /** Recent messages for this account, shown stacked like a chat in one notification. */
    private final java.util.ArrayList<String[]> history = new java.util.ArrayList<>();

    private void postMessage(String sender, String body) {
        if (visible) return; // already looking at this account
        int s = mySlot();
        int color = MainActivity.color(this, s);
        String name = slotName(this, s);
        long now = System.currentTimeMillis();

        java.util.List<String[]> msgs;
        synchronized (history) {
            history.add(new String[]{sender, body, String.valueOf(now)});
            while (history.size() > 6) history.remove(0);
            msgs = new java.util.ArrayList<>(history);
        }

        Intent open = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, s, open,
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);

        String[] last = msgs.get(msgs.size() - 1);
        android.text.SpannableStringBuilder all = new android.text.SpannableStringBuilder();
        for (String[] m : msgs) {
            if (all.length() > 0) all.append('\n');
            appendLine(all, m[0], m[1]);
        }
        android.text.SpannableStringBuilder one = new android.text.SpannableStringBuilder();
        appendLine(one, last[0], last[1]);
        android.graphics.Bitmap av = avatar(name, color);
        String label = name + (msgs.size() > 1 ? "  ·  " + msgs.size() + " naye messages" : "");

        android.app.Notification n = new android.app.Notification.Builder(this, "msg_" + s)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(name + " · " + last[0])
                .setContentText(last[1])
                .setStyle(new android.app.Notification.DecoratedCustomViewStyle())
                .setCustomContentView(card(label, color, av, one, 1))
                .setCustomBigContentView(card(label, color, av, all, 8))
                .setColor(color)
                .setCategory(android.app.Notification.CATEGORY_MESSAGE)
                .setWhen(now)
                .setShowWhen(true)
                .setNumber(Math.max(lastUnread, msgs.size())) // count on the app icon badge
                .setBadgeIconType(android.app.Notification.BADGE_ICON_SMALL)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .addAction(new android.app.Notification.Action.Builder(
                        (android.graphics.drawable.Icon) null, "➜ " + name + " kholo", pi).build())
                .build();
        getSystemService(android.app.NotificationManager.class).notify(10 + s, n);
    }

    /** Incoming call: rings with ringtone and shows which account is being called. */
    private void postCall(String caller, String body) {
        if (visible) return;
        int s = mySlot();
        int color = MainActivity.color(this, s);
        String name = slotName(this, s);
        Intent open = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, 50 + s, open,
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        android.text.SpannableStringBuilder callText = new android.text.SpannableStringBuilder();
        appendLine(callText, caller, "call kar rahe hain");
        android.app.Notification n = new android.app.Notification.Builder(this, "call_" + s)
                .setSmallIcon(R.drawable.ic_stat)
                .setStyle(new android.app.Notification.DecoratedCustomViewStyle())
                .setCustomContentView(card("📞 " + name + "  ·  call", color, avatar(name, color), callText, 2))
                .setContentTitle("📞 " + name + " pe call aa rahi hai")
                .setContentText(caller + (body.isEmpty() ? "" : " · " + body))
                .setSubText(name)
                .setColor(color)
                .setCategory(android.app.Notification.CATEGORY_CALL)
                .setAutoCancel(true)
                .setTimeoutAfter(45000)
                .setContentIntent(pi)
                .addAction(new android.app.Notification.Action.Builder(
                        (android.graphics.drawable.Icon) null, "📞 " + name + " kholo", pi).build())
                .build();
        n.flags |= android.app.Notification.FLAG_INSISTENT; // keep ringing until seen
        getSystemService(android.app.NotificationManager.class).notify(60 + s, n);
    }

    /** Style A "Rangeen card" notification view, tinted in the account's colour. */
    private android.widget.RemoteViews card(String label, int color, android.graphics.Bitmap av,
                                            CharSequence body, int maxLines) {
        android.widget.RemoteViews rv = new android.widget.RemoteViews(getPackageName(), R.layout.notif_card);
        rv.setInt(R.id.bg, "setColorFilter", color);
        rv.setInt(R.id.bg, "setImageAlpha", 46);      // light tint of account colour
        rv.setInt(R.id.strip, "setColorFilter", color);
        rv.setImageViewBitmap(R.id.avatar, av);
        rv.setTextViewText(R.id.account, label.toUpperCase());
        rv.setTextColor(R.id.account, color);
        rv.setTextViewText(R.id.body, body);
        rv.setInt(R.id.body, "setMaxLines", maxLines);
        return rv;
    }

    /** "Sender: message" with the sender in bold. */
    private static void appendLine(android.text.SpannableStringBuilder sb, String sender, String msg) {
        int st = sb.length();
        sb.append(sender);
        sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), st, sb.length(), 0);
        sb.append("  ").append(msg);
    }

    /** Round coloured badge with the first letter, e.g. green "O" for Office. */
    static android.graphics.Bitmap avatar(String text, int color) {
        int size = 128;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.LinearGradient(0, 0, size, size, color,
                MainActivity.darker(color), android.graphics.Shader.TileMode.CLAMP));
        cv.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setShader(null);
        p.setColor(Color.WHITE);
        p.setTextSize(58);
        p.setFakeBoldText(true);
        p.setTextAlign(android.graphics.Paint.Align.CENTER);
        String letter = (text == null || text.trim().isEmpty()) ? "?" : text.trim().substring(0, 1).toUpperCase();
        cv.drawText(letter, size / 2f, size / 2f - (p.descent() + p.ascent()) / 2, p);
        return bmp;
    }

    private void cancelMessage() {
        synchronized (history) { history.clear(); }
        getSystemService(android.app.NotificationManager.class).cancel(60 + mySlot());
        getSystemService(android.app.NotificationManager.class).cancel(10 + mySlot());
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        Lock.check(this);
        if (web != null) web.evaluateJavascript("window.__mwaSetBg&&__mwaSetBg(false)", null);
        cancelMessage();
        // Pick up a colour changed on the home screen.
        int c = MainActivity.color(this, mySlot());
        getWindow().setStatusBarColor(MainActivity.darker(c));
        if (switchBg != null) switchBg.setColor((c & 0x00FFFFFF) | 0xE6000000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        Lock.touch(this);
        if (web != null) web.evaluateJavascript("window.__mwaSetBg&&__mwaSetBg(true)", null);
    }

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
        int c = MainActivity.color(this, mySlot());
        bg.setColor((c & 0x00FFFFFF) | 0xE6000000);
        bg.setStroke((int) (2 * getResources().getDisplayMetrics().density), Color.WHITE);
        b.setBackground(bg);
        switchBg = bg;
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
            s.setSpan(new android.text.style.ForegroundColorSpan(MainActivity.color(this, i)), 0, 1, 0);
            items[i] = s;
        }
        items = java.util.Arrays.copyOf(items, SLOTS.length + 2);
        items[SLOTS.length] = "🔄  Reload";
        items[SLOTS.length + 1] = "⌂  Home screen";
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Switch WhatsApp")
                .setItems(items, (dlg, which) -> {
                    if (which == me) return;
                    if (which == SLOTS.length) { web.reload(); toast("Reload ho raha hai…"); return; }
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
