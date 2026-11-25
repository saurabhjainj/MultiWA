package com.multiwa;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** Optional app lock: fingerprint / face / phone screen-lock before showing chats. */
final class Lock {
    private static final long GRACE_MS = 60_000; // no re-prompt within 1 minute
    private static boolean showing;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("names", Context.MODE_MULTI_PROCESS);
    }

    static boolean enabled(Context c) { return prefs(c).getBoolean("lock", false); }

    static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean("lock", on).putLong("unlockedAt", System.currentTimeMillis()).commit();
    }

    /** Call from onPause: remember we were unlocked, so switching accounts doesn't re-ask. */
    static void touch(Context c) {
        if (!showing && enabled(c)) prefs(c).edit().putLong("unlockedAt", System.currentTimeMillis()).apply();
    }

    /** Call from onResume. */
    static void check(Activity a) {
        if (showing || !enabled(a)) return;
        if (System.currentTimeMillis() - prefs(a).getLong("unlockedAt", 0) < GRACE_MS) return;
        showing = true;

        // Hide the chats behind a dark cover until unlocked.
        ViewGroup content = a.findViewById(android.R.id.content);
        FrameLayout cover = new FrameLayout(a);
        cover.setBackgroundColor(0xFF0B1F2A);
        cover.setClickable(true);
        content.addView(cover, new ViewGroup.LayoutParams(-1, -1));

        BiometricPrompt.Builder b = new BiometricPrompt.Builder(a)
                .setTitle("Multi WA unlock")
                .setSubtitle("Fingerprint ya screen lock lagao");
        if (Build.VERSION.SDK_INT >= 30) {
            b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else if (Build.VERSION.SDK_INT == 29) {
            b.setDeviceCredentialAllowed(true);
        } else {
            b.setNegativeButton("Cancel", a.getMainExecutor(), (d, w) -> fail(a, cover));
        }
        b.build().authenticate(new CancellationSignal(), a.getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                        prefs(a).edit().putLong("unlockedAt", System.currentTimeMillis()).apply();
                        content.removeView(cover);
                        showing = false;
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence msg) {
                        // Phone has no fingerprint/screen lock at all: don't lock the user out.
                        if (code == 1 || code == 11 || code == 12 || code == 14) {
                            content.removeView(cover);
                            showing = false;
                        } else {
                            fail(a, cover);
                        }
                    }
                });
    }

    private static void fail(Activity a, View cover) {
        showing = false;
        ((ViewGroup) cover.getParent()).removeView(cover);
        a.moveTaskToBack(true); // leave chats hidden
    }
}
