package com.multiwa;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Small features shared across processes: mute, daily counts, quick replies, auto-reply, widget, boot. */
public final class Extras {

    static SharedPreferences p(Context c) {
        return c.getSharedPreferences("names", Context.MODE_MULTI_PROCESS);
    }

    // ---------- Mute ----------
    static boolean muted(Context c, int s) { return System.currentTimeMillis() < p(c).getLong("mute" + s, 0); }

    /** ms > 0: mute for that long, -1: always, 0: unmute. */
    static void mute(Context c, int s, long ms) {
        long until = ms < 0 ? Long.MAX_VALUE : ms == 0 ? 0 : System.currentTimeMillis() + ms;
        p(c).edit().putLong("mute" + s, until).commit();
    }

    // ---------- Today's message count ----------
    private static String day() { return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()); }

    static void countToday(Context c, int s) {
        String k = "d" + day() + "_" + s;
        p(c).edit().putInt(k, p(c).getInt(k, 0) + 1).apply();
    }

    static int today(Context c, int s) { return p(c).getInt("d" + day() + "_" + s, 0); }

    static void showSummary(Activity a) {
        StringBuilder sb = new StringBuilder();
        int total = 0;
        for (int s : MainActivity.activeSlots(a)) {
            int n = today(a, s);
            total += n;
            sb.append("● ").append(WaActivity.slotName(a, s)).append(":  ").append(n).append(" messages\n");
        }
        sb.append("\nTotal: ").append(total).append(" messages\n\n(Only counts messages that arrived while the app was in the background.)");
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("📊 Today's summary").setMessage(sb.toString()).setPositiveButton("OK", null).show();
    }

    // ---------- Settings ----------
    static boolean mobile(Context c) { return p(c).getBoolean("mobile", true); }
    static boolean dark(Context c) { return p(c).getBoolean("dark", false); }

    // ---------- Account bar (adjustable) ----------
    static final String[] BAR_SIZES = {"Small", "Medium", "Large"};
    static int barSize(Context c) { return p(c).getInt("bar_size", 1); }
    static boolean barBottom(Context c) { return p(c).getBoolean("bar_bottom", false); }
    static boolean barShowName(Context c) { return p(c).getBoolean("bar_name", true); }

    static String barSummary(Context c) {
        return "Size: " + BAR_SIZES[barSize(c)] + " · " + (barBottom(c) ? "Bottom" : "Top")
                + " · Name " + (barShowName(c) ? "ON" : "OFF");
    }

    /** Tap a line to change it; changes apply right away. */
    static void showBarSettings(Activity a, Runnable onChange) {
        String[] items = {
                "📏  Size: " + BAR_SIZES[barSize(a)] + "  (tap to change)",
                "↕️  Position: " + (barBottom(a) ? "Bottom" : "Top") + "  (tap to change)",
                "🏷️  Account name: " + (barShowName(a) ? "Show" : "Hide") + "  (tap to change)" };
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Account bar")
                .setItems(items, (d, w) -> {
                    SharedPreferences.Editor e = p(a).edit();
                    if (w == 0) e.putInt("bar_size", (barSize(a) + 1) % BAR_SIZES.length);
                    else if (w == 1) e.putBoolean("bar_bottom", !barBottom(a));
                    else e.putBoolean("bar_name", !barShowName(a));
                    e.commit();
                    if (onChange != null) onChange.run();
                    showBarSettings(a, onChange); // reopen so you can keep adjusting
                })
                .setPositiveButton("Done", null)
                .show();
    }

    // ---------- Quick replies ----------
    static List<String> quick(Context c) {
        List<String> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p(c).getString("qr",
                    "[\"Payment received ✅\",\"Will send by tomorrow\",\"Please wait a moment 🙏\",\"Thank you!\"]"));
            for (int i = 0; i < a.length(); i++) l.add(a.getString(i));
        } catch (Exception ignored) {}
        return l;
    }

    static void setQuick(Context c, List<String> l) { p(c).edit().putString("qr", new JSONArray(l).toString()).commit(); }

    // ---------- Auto-reply ----------
    static boolean arOn(Context c, int s) { return p(c).getBoolean("ar_on" + s, false); }
    static String arMsg(Context c, int s) { return p(c).getString("ar_msg" + s, "Busy right now, I'll reply soon 🙏"); }
    static boolean arNight(Context c, int s) { return p(c).getBoolean("ar_night" + s, false); }

    static boolean arActiveNow(Context c, int s) {
        if (!arOn(c, s)) return false;
        if (!arNight(c, s)) return true;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h >= 22 || h < 8;
    }

    // ---------- JavaScript for WhatsApp Web ----------
    static String q(String s) { return JSONObject.quote(s == null ? "" : s); }

    /** Open the chat whose title is `name` (top of chat list), type `text`, press Send. */
    static String replyJs(String name, String text) {
        return "(function(n,t){function q(s){return document.querySelector(s)}"
                + "var sp=[].slice.call(document.querySelectorAll('#pane-side span[title]'));"
                + "var hit=sp.filter(function(s){return s.getAttribute('title')===n})[0];"
                + "if(!hit){MWA.replied(false);return}"
                + "var row=hit.closest('[role=\"listitem\"],[role=\"row\"]')||hit;"
                + "['mousedown','mouseup','click'].forEach(function(e){row.dispatchEvent(new MouseEvent(e,{bubbles:true}))});"
                + "var k=0;var iv=setInterval(function(){k++;var box=q('#main footer div[contenteditable=\"true\"]');"
                + "if(box){clearInterval(iv);box.focus();document.execCommand('insertText',false,t);"
                + "setTimeout(function(){var b=q('#main [data-icon=\"send\"],#main [data-icon=\"wds-ic-send-filled\"],#main button[aria-label=\"Send\"]');"
                + "if(b){(b.closest('button')||b).click();MWA.replied(true)}else MWA.replied(false)},700);return}"
                + "if(k>25){clearInterval(iv);MWA.replied(false)}},300)})(" + q(name) + "," + q(text) + ")";
    }

    /** Put text into the open chat's message box (user presses Send). */
    static String insertJs(String text) {
        return "(function(t){var b=document.querySelector('#main footer div[contenteditable=\"true\"]');"
                + "if(!b){MWA.toast('Open a chat first');return}b.focus();document.execCommand('insertText',false,t)})(" + q(text) + ")";
    }

    /** Mobile layout + dark mode, depending on settings. */
    static String settingsJs(Context c) {
        return "window.__mwaMobile=" + mobile(c) + ";window.__mwaDark=" + dark(c) + ";" + MOBILE_JS + DARK_JS + MENU_JS;
    }

    // Message options menu (Reply, Copy, Forward…): open with long-press, and keep it fully on screen.
    private static final String MENU_JS =
            "(function(){if(window.__mwaMenu)return;window.__mwaMenu=1;"
            // no text-selection popup on messages (WhatsApp's own menu has Copy)
            + "var st=document.createElement('style');st.textContent='#main [data-id]{-webkit-user-select:none;user-select:none;-webkit-touch-callout:none}';"
            + "(document.head||document.documentElement).appendChild(st);"
            // long-press a message -> open its menu at the finger (only if the browser didn't already)
            + "var tm=null,sx=0,sy=0,tg=null,nat=false;"
            + "document.addEventListener('touchstart',function(e){var m=e.target.closest&&e.target.closest('#main [data-id]');"
            + "if(!m||e.touches.length>1)return;var t=e.touches[0];sx=t.clientX;sy=t.clientY;tg=e.target;nat=false;clearTimeout(tm);"
            + "tm=setTimeout(function(){tm=null;if(nat)return;tg.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true,clientX:sx,clientY:sy,button:2}));"
            + "if(navigator.vibrate)navigator.vibrate(15)},650)},{passive:true,capture:true});"
            + "document.addEventListener('contextmenu',function(e){if(e.isTrusted)nat=true},true);"
            + "['touchmove','touchend','touchcancel'].forEach(function(n){document.addEventListener(n,function(e){"
            + "if(n==='touchmove'&&tm&&e.touches[0]){var t=e.touches[0];if(Math.abs(t.clientX-sx)<10&&Math.abs(t.clientY-sy)<10)return}"
            + "clearTimeout(tm);tm=null},{passive:true,capture:true})});"
            // keep any popup menu inside the screen
            + "var busy=false;function fix(){busy=false;var W=innerWidth,H=innerHeight;"
            + "document.querySelectorAll('[role=\"application\"],[role=\"menu\"],[role=\"listbox\"]').forEach(function(el){"
            + "var r=el.getBoundingClientRect();if(!r.width||!r.height)return;var dx=0,dy=0;"
            + "if(r.right>W-6)dx=W-6-r.right;if(r.left+dx<6)dx=6-r.left;if(r.bottom>H-6)dy=H-6-r.bottom;if(r.top+dy<6)dy=6-r.top;"
            + "if(dx||dy){var s=el.__mwaS||[0,0];s=[s[0]+dx,s[1]+dy];el.__mwaS=s;el.style.translate=s[0]+'px '+s[1]+'px'}})}"
            + "new MutationObserver(function(){if(!busy){busy=true;requestAnimationFrame(fix)}})"
            + ".observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['style']});"
            + "})();";

    // One panel at a time: chat list, or the open chat full-screen.
    // Finds the real left column (chat list + Archived/Settings drawers) and the right column (open chat)
    // ONCE, from WhatsApp's own two-column layout, then only switches which one is visible.
    private static final String MOBILE_JS =
            "(function(){if(!window.__mwaMobile||window.__mwaMob)return;window.__mwaMob=1;var L=null,C=null;"
            + "function find(){var s=document.getElementById('side');if(!s)return false;var l=s;"
            + "while(l.parentElement&&l.parentElement!==document.body){var p=l.parentElement,pr=p.getBoundingClientRect(),lr=l.getBoundingClientRect();"
            + "if(p.children.length>=2&&pr.width>=innerWidth*0.9&&lr.width>0&&lr.width<=pr.width*0.75){"
            + "var right=[].some.call(p.children,function(k){if(k===l)return false;var r=k.getBoundingClientRect();return r.width>0&&r.left>=lr.right-2});"
            + "if(right){L=l;C=p;return true}}l=p}return false}"
            + "setInterval(function(){try{"
            + "var m=document.querySelector('meta[name=viewport]');if(!m&&document.head){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m)}"
            + "if(m&&m.content!=='width=640')m.content='width=640';"
            + "if(!L||!document.contains(L)||!document.contains(C)){"
            + "if(C&&document.contains(C))[].forEach.call(C.children,function(k){k.style.display='';k.style.maxWidth='';k.style.flex='';k.style.width=''});"
            + "L=null;C=null;if(!find())return}"
            + "var o=!!document.getElementById('main');"
            + "[].forEach.call(C.children,function(k){if(k===L){k.style.display=o?'none':'';k.style.maxWidth=o?'':'100%';k.style.flex=o?'':'1 1 100%';k.style.width=o?'':'100%'}"
            + "else{k.style.display=o?'':'none';k.style.maxWidth=o?'100%':'';k.style.flex=o?'1 1 100%':'';k.style.width=o?'100%':''}})"
            + "}catch(e){}},400)})();";

    private static final String DARK_JS =
            "(function(){try{if(!window.__mwaDark){if(localStorage.getItem('__mwaDark')==='1'){localStorage.setItem('theme','\"light\"');localStorage.removeItem('__mwaDark');document.body&&document.body.classList.remove('dark')}return}"
            + "localStorage.setItem('theme','\"dark\"');localStorage.setItem('__mwaDark','1')}catch(e){}"
            + "setInterval(function(){if(document.body&&!document.body.classList.contains('dark'))document.body.classList.add('dark')},1000)})();";

    // ---------- Reply from notification ----------
    public static abstract class ReplyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            Bundle r = RemoteInput.getResultsFromIntent(intent);
            CharSequence t = r == null ? null : r.getCharSequence("reply");
            String who = intent.getStringExtra("who");
            WaActivity w = WaActivity.current;
            if (t != null && w != null) w.runOnUiThread(() -> w.replyTo(who, t.toString(), false));
            else simple(c, intent.getIntExtra("slot", 0), "Reply not sent", "Open the account to reply");
        }
    }
    public static class Q1 extends ReplyReceiver {}
    public static class Q2 extends ReplyReceiver {}
    public static class Q3 extends ReplyReceiver {}
    public static class Q4 extends ReplyReceiver {}
    public static class Q5 extends ReplyReceiver {}
    public static class Q6 extends ReplyReceiver {}
    public static class Q7 extends ReplyReceiver {}
    public static class Q8 extends ReplyReceiver {}
    public static class Q9 extends ReplyReceiver {}
    public static class Q10 extends ReplyReceiver {}
    static final Class<?>[] REPLY = { Q1.class, Q2.class, Q3.class, Q4.class, Q5.class,
            Q6.class, Q7.class, Q8.class, Q9.class, Q10.class };

    static Notification.Action replyAction(Context c, int slot, String who) {
        Intent i = new Intent(c, REPLY[slot]).putExtra("who", who).putExtra("slot", slot);
        PendingIntent pi = PendingIntent.getBroadcast(c, 300 + slot, i,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        RemoteInput ri = new RemoteInput.Builder("reply").setLabel("Reply to " + who).build();
        return new Notification.Action.Builder(null, "↩ Reply", pi).addRemoteInput(ri).build();
    }

    static void simple(Context c, int slot, String title, String text) {
        Notification n = new Notification.Builder(c, "general")
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(MainActivity.color(c, slot))
                .setContentTitle(title).setContentText(text).setAutoCancel(true).build();
        c.getSystemService(NotificationManager.class).notify(2000 + slot, n);
    }

    // ---------- Phone restart ----------
    public static class Boot extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            Sched.rearmAll(c); // alarms are cleared on restart
            Intent open = new Intent(c, MainActivity.class).putExtra("connectAll", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(c, 900, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(c, "general")
                    .setSmallIcon(R.drawable.ic_stat)
                    .setColor(0xFF00A884)
                    .setContentTitle("📱 Phone restarted")
                    .setContentText("Tap to reconnect all your WhatsApp accounts")
                    .setContentIntent(pi).setAutoCancel(true).build();
            c.getSystemService(NotificationManager.class).notify(901, n);
        }
    }

    // ---------- Home screen widget ----------
    public static class Widget extends AppWidgetProvider {
        @Override
        public void onUpdate(Context c, AppWidgetManager m, int[] ids) { updateWidget(c); }
    }

    private static final int[] W_IDS = { R.id.w0, R.id.w1, R.id.w2, R.id.w3, R.id.w4,
            R.id.w5, R.id.w6, R.id.w7, R.id.w8, R.id.w9 };

    static void updateWidget(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, Widget.class));
            if (ids == null || ids.length == 0) return;
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_multi);
            List<Integer> slots = MainActivity.activeSlots(c);
            for (int k = 0; k < W_IDS.length; k++) {
                if (k < slots.size()) {
                    int s = slots.get(k);
                    Bitmap av = badged(WaActivity.avatar(WaActivity.slotName(c, s), MainActivity.color(c, s)),
                            p(c).getInt("u" + s, 0));
                    rv.setImageViewBitmap(W_IDS[k], av);
                    rv.setViewVisibility(W_IDS[k], View.VISIBLE);
                    Intent open = new Intent(c, WaActivity.SLOTS[s]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    rv.setOnClickPendingIntent(W_IDS[k], PendingIntent.getActivity(c, 200 + s, open,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
                } else {
                    rv.setViewVisibility(W_IDS[k], k < 5 ? View.INVISIBLE : View.GONE);
                }
            }
            rv.setViewVisibility(R.id.row2, slots.size() > 5 ? View.VISIBLE : View.GONE);
            m.updateAppWidget(ids, rv);
        } catch (Exception ignored) {}
    }

    /** Avatar with a red unread count in the corner. */
    static Bitmap badged(Bitmap src, int n) {
        Bitmap b = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.drawBitmap(src, null, new android.graphics.RectF(16, 16, 144, 144), null);
        if (n > 0) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFFF3B30);
            cv.drawCircle(128, 32, 30, p);
            p.setColor(Color.WHITE);
            p.setTextSize(n > 9 ? 28 : 34);
            p.setFakeBoldText(true);
            p.setTextAlign(Paint.Align.CENTER);
            cv.drawText(n > 99 ? "99+" : String.valueOf(n), 128, 32 - (p.descent() + p.ascent()) / 2, p);
        }
        return b;
    }
}
