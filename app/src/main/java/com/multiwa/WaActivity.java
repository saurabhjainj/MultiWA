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
import android.widget.LinearLayout;
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
    public static class A6 extends WaActivity {}
    public static class A7 extends WaActivity {}
    public static class A8 extends WaActivity {}
    public static class A9 extends WaActivity {}
    public static class A10 extends WaActivity {}

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
        current = this;
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
        // Top account bar (all accounts, one tap to switch) + WhatsApp below it.
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(makeTopBar(), new LinearLayout.LayoutParams(-1, -2));
        page.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(page);
        placeBar();

        // Keyboard: always let the page take focus when the message box is tapped.
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && !v.hasFocus()) v.requestFocus();
            return false; // let WebView handle the tap normally (emoji, buttons, typing)
        });

        // Keep this page's engine alive when the screen is off / app in background,
        // so WhatsApp doesn't have to load again when you come back.
        web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);

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
            public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail d) {
                // The system stopped the page engine (low memory). Don't crash: rebuild quietly.
                ((android.view.ViewGroup) v.getParent()).removeView(v);
                v.destroy();
                web = null;
                recreate();
                return true;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                pageLoadedAt = System.currentTimeMillis();
                if (pendingSendId != -1) {
                    v.evaluateJavascript(SEND_JS.replace("__ID__", String.valueOf(pendingSendId)), null);
                    pendingSendId = -1;
                }
                v.evaluateJavascript(NOTIFY_JS, null); // fallback if document-start injection unsupported
                v.evaluateJavascript(Extras.settingsJs(WaActivity.this), null);
                if (!visible) v.evaluateJavascript("window.__mwaSetBg&&__mwaSetBg(true)", null);
            }
        });

        web.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void show(String title, String body) {
                lastPreview = System.currentTimeMillis();
                String all = (title + " " + body).toLowerCase();
                if (all.contains("call") || all.contains("कॉल")) { postCall(title, body); return; }
                Extras.countToday(WaActivity.this, mySlot());
                postMessage(title, body);
                runOnUiThread(() -> maybeAutoReply(title, body));
            }

            @android.webkit.JavascriptInterface
            public void replied(boolean ok) {
                runOnUiThread(() -> {
                    if (ok) { if (!replyAuto) cancelMessage(); }
                    else if (!replyAuto) Extras.simple(WaActivity.this, mySlot(),
                            "Reply not sent", "Open the account to reply");
                });
            }

            @android.webkit.JavascriptInterface
            public void toast(String s) { WaActivity.this.toast(s); }

            @android.webkit.JavascriptInterface
            public void sent(String id, boolean ok) {
                long sid = Long.parseLong(id);
                org.json.JSONObject o = Sched.find(WaActivity.this, sid);
                Sched.done(WaActivity.this, sid, ok);
                Sched.notifyResult(WaActivity.this, mySlot(), ok, o == null ? "" : o.optString("phone"));
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

        // Opened by "Connect all": connect, then step aside.
        if (getIntent().getBooleanExtra("bg", false)) web.postDelayed(() -> moveTaskToBack(true), 5000);

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
                toast("Download started…");
            }
        });

        if (getIntent().getBooleanExtra("remove", false)) { removeAccount(); return; }
        if (getIntent().getBooleanExtra("reset", false)) { resetAccount(); return; }
        if (b != null) web.restoreState(b);
        else web.loadUrl("https://web.whatsapp.com/");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("reset", false)) resetAccount();
        if (intent.getBooleanExtra("remove", false)) removeAccount();
        if (intent.getBooleanExtra("bg", false)) web.postDelayed(() -> moveTaskToBack(true), 5000);
    }

    /** "Remove account": logout, stop background connection, close this slot. */
    private void removeAccount() {
        resetAccount();
        stopService(new Intent(this, KeepAlive.SERVICES[mySlot()]));
        getSystemService(android.app.NotificationManager.class).cancel(100 + mySlot());
        finishAndRemoveTask();
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
        toast(slotName(this, mySlot()) + " was reset. Scan a new QR code.");
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
            toast("Couldn't save the file");
        }
    }

    private void toast(String s) {
        runOnUiThread(() -> android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show());
    }

    static final Class<?>[] SLOTS = { A1.class, A2.class, A3.class, A4.class, A5.class, A6.class, A7.class, A8.class, A9.class, A10.class };

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

    /** The open account screen in this process (used by scheduled-message alarms). */
    static volatile WaActivity current;
    private long pendingSendId = -1;

    /** Opens the chat with the message pre-filled, then presses Send. */
    void sendScheduled(long id, String phone, String text) {
        pendingSendId = id;
        web.loadUrl("https://web.whatsapp.com/send?phone=" + phone + "&text=" + Uri.encode(text));
    }

    // Waits for WhatsApp to open the chat, clicks Send, reports back. Gives up after ~90 s.
    private static final String SEND_JS =
            "(function(){var id='__ID__',t=0;var iv=setInterval(function(){t++;"
            + "var b=document.querySelector('[data-icon=\"send\"],[data-icon=\"wds-ic-send-filled\"],button[aria-label=\"Send\"],[data-testid=\"send\"]');"
            + "if(b){(b.closest('button')||b).click();clearInterval(iv);setTimeout(function(){MWA.sent(id,true)},1500);return;}"
            + "if(t>10&&t%5==0&&/invalid|not on WhatsApp/i.test((document.body&&document.body.innerText)||'')){clearInterval(iv);MWA.sent(id,false);return;}"
            + "if(t>90){clearInterval(iv);MWA.sent(id,false);}},1000);})();";

    /** Unread count changed (from title). Shows a simple alert if WhatsApp's own one didn't come. */
    private void onUnread(int n) {
        if (n == lastUnread) return;
        int prev = lastUnread;
        lastUnread = n;
        getSharedPreferences("names", MODE_MULTI_PROCESS).edit().putInt("u" + mySlot(), n).commit();
        Extras.updateWidget(this);
        if (n == 0) { cancelMessage(); return; }
        // Ignore old unread chats counted while the page is first loading.
        if (n > prev && System.currentTimeMillis() - pageLoadedAt > 25000) {
            long t = System.currentTimeMillis();
            web.postDelayed(() -> {
                // Skip if WhatsApp's own notification (with sender + text) already came.
                if (lastPreview < t - 4000 && lastUnread > 0) {
                    Extras.countToday(this, mySlot());
                    postMessage("New message", lastUnread + (lastUnread == 1 ? " unread chat" : " unread chats"));
                }
            }, 2500);
        }
    }

    private volatile boolean visible;
    private GradientDrawable switchBg;
    private volatile long lastPreview;
    private int lastUnread;

    /** Recent messages for this account, shown stacked like a chat in one notification. */
    private final java.util.ArrayList<String[]> history = new java.util.ArrayList<>();

    private volatile boolean replyAuto;
    private final java.util.Map<String, Long> autoSent = new java.util.HashMap<>();

    /** Reply to a chat by its name (used by notification reply and auto-reply). */
    void replyTo(String who, String text, boolean auto) {
        replyAuto = auto;
        web.evaluateJavascript(Extras.replyJs(who, text), null);
    }

    /** Auto-reply once per person every 6 hours, never in groups, never while you're looking. */
    private void maybeAutoReply(String sender, String body) {
        int s = mySlot();
        if (visible || !Extras.arActiveNow(this, s) || sender == null || sender.isEmpty()) return;
        if (body != null && body.matches("^[^:]{1,40}: [\\s\\S]*")) return; // looks like a group message
        Long last = autoSent.get(sender);
        if (last != null && System.currentTimeMillis() - last < 6 * 3600_000L) return;
        autoSent.put(sender, System.currentTimeMillis());
        web.postDelayed(() -> replyTo(sender, Extras.arMsg(this, s), true), 3000);
    }

    // ---------- Top account bar ----------
    private LinearLayout page, topBar, barSlots;
    private boolean barAtBottom;

    /** Put the bar at the top or bottom, as chosen in settings. */
    private void placeBar() {
        boolean bottom = Extras.barBottom(this);
        if (page == null || (bottom == barAtBottom && page.indexOfChild(topBar) == (bottom ? 1 : 0))) return;
        barAtBottom = bottom;
        page.removeView(topBar);
        page.addView(topBar, bottom ? 1 : 0, new LinearLayout.LayoutParams(-1, -2));
    }
    private TextView barName;
    private final android.os.Handler barHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable barTick = new Runnable() {
        @Override public void run() { fillBar(); barHandler.postDelayed(this, 3000); }
    };

    private int dpx(float v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private View makeTopBar() {
        topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dpx(6), dpx(4), dpx(4), dpx(4));

        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        barSlots = new LinearLayout(this);
        barSlots.setGravity(Gravity.CENTER_VERTICAL);
        hs.addView(barSlots);
        topBar.addView(hs, new LinearLayout.LayoutParams(0, -2, 1));

        barName = new TextView(this);
        barName.setTextColor(Color.WHITE);
        barName.setTextSize(13);
        barName.setSingleLine(true);
        barName.setMaxWidth(dpx(110));
        barName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        barName.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        barName.setPadding(dpx(6), 0, dpx(4), 0);
        topBar.addView(barName);

        topBar.addView(barIcon("⌂", v -> {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            overridePendingTransition(0, 0);
        }));
        TextView more = barIcon("⋮", v -> showSwitcher()); // quick reply, reload, home
        more.setOnLongClickListener(v -> { Extras.showBarSettings(this, () -> { placeBar(); fillBar(); }); return true; });
        topBar.addView(more);
        fillBar();
        return topBar;
    }

    private TextView barIcon(String s, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(21);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dpx(38));
        t.setPadding(0, dpx(4), 0, dpx(4));
        t.setOnClickListener(l);
        return t;
    }

    /** (Re)draw the account circles with unread badges. */
    private void fillBar() {
        if (barSlots == null) return;
        int me = mySlot();
        int myColor = MainActivity.color(this, me);
        int sz = Extras.barSize(this);
        int avDp = new int[]{28, 36, 44}[sz], cellDp = new int[]{36, 46, 56}[sz], txt = new int[]{12, 15, 18}[sz];
        topBar.setBackgroundColor(MainActivity.darker(myColor));
        barName.setText(slotName(this, me));
        barName.setTextSize(new int[]{11, 13, 15}[sz]);
        barName.setVisibility(Extras.barShowName(this) ? View.VISIBLE : View.GONE);
        barSlots.removeAllViews();
        android.content.SharedPreferences p = getSharedPreferences("names", MODE_MULTI_PROCESS);
        for (int i : MainActivity.activeSlots(this)) {
            String name = slotName(this, i);
            int c = MainActivity.color(this, i);
            FrameLayout cell = new FrameLayout(this);

            TextView av = new TextView(this);
            av.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
            av.setTextColor(Color.WHITE);
            av.setTextSize(txt);
            av.setGravity(Gravity.CENTER);
            av.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c, MainActivity.darker(c)});
            g.setShape(GradientDrawable.OVAL);
            if (i == me) g.setStroke(dpx(3), Color.WHITE); else av.setAlpha(0.75f);
            av.setBackground(g);
            cell.addView(av, new FrameLayout.LayoutParams(dpx(avDp), dpx(avDp), Gravity.CENTER));

            int u = p.getInt("u" + i, 0);
            if (u > 0 && i != me) {
                TextView b = new TextView(this);
                b.setText(u > 99 ? "99+" : String.valueOf(u));
                b.setTextColor(Color.WHITE);
                b.setTextSize(9);
                b.setGravity(Gravity.CENTER);
                b.setPadding(dpx(4), 0, dpx(4), 0);
                b.setMinWidth(dpx(16));
                GradientDrawable bb = new GradientDrawable();
                bb.setCornerRadius(dpx(8));
                bb.setColor(0xFFFF3B30);
                bb.setStroke(dpx(1), MainActivity.darker(myColor));
                b.setBackground(bb);
                cell.addView(b, new FrameLayout.LayoutParams(-2, dpx(16), Gravity.TOP | Gravity.END));
            }

            cell.setOnClickListener(v -> {
                if (i == me) return;
                startActivity(new Intent(this, SLOTS[i]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                overridePendingTransition(0, 0);
            });
            cell.setOnLongClickListener(v -> { toast(name); return true; });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpx(cellDp), dpx(cellDp));
            lp.rightMargin = dpx(2);
            barSlots.addView(cell, lp);
        }
    }

    /** Swipe the ⇄ button left/right: next/previous account. */
    private void switchBy(int d) {
        java.util.List<Integer> shown = MainActivity.activeSlots(this);
        int idx = shown.indexOf(mySlot());
        if (shown.size() < 2 || idx < 0) return;
        int next = shown.get((idx + d + shown.size()) % shown.size());
        startActivity(new Intent(this, SLOTS[next]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        overridePendingTransition(d > 0 ? android.R.anim.slide_in_left : android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void showQuickReplies() {
        java.util.List<String> l = Extras.quick(this);
        if (l.isEmpty()) { toast("Add quick replies in Settings"); return; }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("⚡ Quick reply")
                .setItems(l.toArray(new String[0]), (d, w) -> web.evaluateJavascript(Extras.insertJs(l.get(w)), null))
                .show();
    }

    private void postMessage(String sender, String body) {
        if (visible || Extras.muted(this, mySlot())) return; // looking at it, or muted
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
        String label = name + (msgs.size() > 1 ? "  ·  " + msgs.size() + " new messages" : "");

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
                        (android.graphics.drawable.Icon) null, "➜ Open " + name, pi).build())
                .build();
        if (!"New message".equals(last[0])) {
            // Rebuild with an inline "↩ Reply" box for the last sender.
            n = android.app.Notification.Builder.recoverBuilder(this, n)
                    .addAction(Extras.replyAction(this, s, last[0])).build();
        }
        getSystemService(android.app.NotificationManager.class).notify(10 + s, n);
    }

    /** Incoming call: rings with ringtone and shows which account is being called. */
    private void postCall(String caller, String body) {
        if (visible || Extras.muted(this, mySlot())) return;
        int s = mySlot();
        int color = MainActivity.color(this, s);
        String name = slotName(this, s);
        Intent open = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, 50 + s, open,
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        android.text.SpannableStringBuilder callText = new android.text.SpannableStringBuilder();
        appendLine(callText, caller, "is calling");
        android.app.Notification n = new android.app.Notification.Builder(this, "call_" + s)
                .setSmallIcon(R.drawable.ic_stat)
                .setStyle(new android.app.Notification.DecoratedCustomViewStyle())
                .setCustomContentView(card("📞 " + name + "  ·  call", color, avatar(name, color), callText, 2))
                .setContentTitle("📞 " + name + ": incoming call")
                .setContentText(caller + (body.isEmpty() ? "" : " · " + body))
                .setSubText(name)
                .setColor(color)
                .setCategory(android.app.Notification.CATEGORY_CALL)
                .setAutoCancel(true)
                .setTimeoutAfter(45000)
                .setContentIntent(pi)
                .addAction(new android.app.Notification.Action.Builder(
                        (android.graphics.drawable.Icon) null, "📞 Open " + name, pi).build())
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
        placeBar();
        barHandler.post(barTick); // refresh account bar (names, colours, unread, size)
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        barHandler.removeCallbacks(barTick);
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

        final float[] start = new float[3];
        final boolean[] moved = {false};
        final float dens = getResources().getDisplayMetrics().density;
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = e.getRawY(); start[1] = v.getTranslationY(); start[2] = e.getRawX(); moved[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dy = e.getRawY() - start[0];
                    float dxm = e.getRawX() - start[2];
                    if (Math.abs(dy) > 15 && Math.abs(dy) > Math.abs(dxm)) { moved[0] = true; v.setTranslationY(start[1] + dy); }
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = e.getRawX() - start[2];
                    if (!moved[0] && Math.abs(dx) > 50 * dens) { switchBy(dx < 0 ? 1 : -1); return true; }
                    if (!moved[0]) showSwitcher();
                    return true;
            }
            return false;
        });
        return b;
    }

    private void showSwitcher() {
        int me = mySlot();
        java.util.List<Integer> shown = MainActivity.activeSlots(this);
        int k = shown.size();
        CharSequence[] items = new CharSequence[k + 3];
        for (int j = 0; j < k; j++) {
            int i = shown.get(j);
            String label = "●  " + slotName(this, i) + (i == me ? "   ✓" : "");
            android.text.SpannableString s = new android.text.SpannableString(label);
            s.setSpan(new android.text.style.ForegroundColorSpan(MainActivity.color(this, i)), 0, 1, 0);
            items[j] = s;
        }
        items[k] = "⚡  Quick reply";
        items[k + 1] = "🔄  Reload";
        items[k + 2] = "⌂  Home screen";
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Switch WhatsApp")
                .setItems(items, (dlg, which) -> {
                    if (which < k && shown.get(which) == me) return;
                    if (which == k) { showQuickReplies(); return; }
                    if (which == k + 1) { web.reload(); toast("Reloading…"); return; }
                    Class<?> target = which < k ? SLOTS[shown.get(which)] : MainActivity.class;
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
        if (web != null) web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (Extras.mobile(this)) {
            // Mobile layout: Back closes the open chat first (like the real app).
            web.evaluateJavascript("(function(){if(document.getElementById('main')){"
                    + "document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',keyCode:27,which:27,bubbles:true}));return 1}return 0})()",
                    r -> { if (!"1".equals(r)) moveTaskToBack(true); });
            return;
        }
        if (web.canGoBack()) web.goBack();
        else moveTaskToBack(true); // keep session alive instead of closing
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        CookieManager.getInstance().flush();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
