package it.breachato.lock;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Covers the protected apps with the passcode screen whenever they come to the foreground.
 * An app stays unlocked only while it is in use: leaving it or turning the screen off locks it.
 * Trusted session: after TRUST_AFTER correct codes in one screen-on session, each at least
 * TRUST_SPACING_MS after the previous one, no code is asked until the screen turns off.
 */
public class LockService extends AccessibilityService {
    static final int TRUST_AFTER = 3;
    static final long TRUST_SPACING_MS = 20_000;

    /** Package unlocked by the passcode screen, cleared when the user leaves it. */
    static volatile String unlocked;
    static volatile boolean trusted;
    static volatile int goodCodes;
    private static long lastGood;

    // windows that appear on top of an app without meaning the user left it
    private static final Set<String> TRANSIENT = new HashSet<>(Arrays.asList(
            "com.android.systemui", "com.google.android.inputmethod.latin", "android"));

    private LockPrefs prefs;
    private volatile boolean enabled;
    private volatile Set<String> locked;

    private final SharedPreferences.OnSharedPreferenceChangeListener reload =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) { load(); }
            };

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { endSession(); }
    };

    /** A correct code was entered to open an app; counts towards the trusted session. */
    static synchronized void goodCode() {
        long now = System.currentTimeMillis();
        if (lastGood == 0 || now - lastGood >= TRUST_SPACING_MS) {
            goodCodes++;
            lastGood = now;
        }
        if (goodCodes >= TRUST_AFTER) trusted = true;
    }

    static synchronized void endSession() {
        unlocked = null;
        trusted = false;
        goodCodes = 0;
        lastGood = 0;
    }

    private void load() {
        enabled = prefs.enabled();
        locked = prefs.apps();
    }

    @Override protected void onServiceConnected() {
        prefs = new LockPrefs(this);
        prefs.listen(reload);
        load();
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (pkg.equals(getPackageName()) || TRANSIENT.contains(pkg)) return;
        if (!pkg.equals(unlocked)) unlocked = null;   // moved to another app: relock
        if (!enabled || trusted || locked == null || !locked.contains(pkg) || unlocked != null) return;
        startActivity(new Intent(this, PinActivity.class)
                .putExtra(PinActivity.EXTRA_TARGET, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        unregisterReceiver(screenOff);
        super.onDestroy();
    }
}
