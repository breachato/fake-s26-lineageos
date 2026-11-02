package it.breachato.lock;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Covers the protected apps with the PIN screen whenever they come to the foreground.
 * An app stays unlocked only while it is in use: leaving it or turning the screen off locks it.
 */
public class LockService extends AccessibilityService {
    static final Set<String> LOCKED = new HashSet<>(Arrays.asList(
            "org.fdroid.fdroid", "com.android.chrome", "com.android.documentsui"));

    /** Package unlocked by the PIN screen, cleared when the user leaves it. */
    static volatile String unlocked;

    // windows that appear on top of an app without meaning the user left it
    private static final Set<String> TRANSIENT = new HashSet<>(Arrays.asList(
            "com.android.systemui", "com.google.android.inputmethod.latin", "android"));

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { unlocked = null; }
    };

    @Override protected void onServiceConnected() {
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (pkg.equals(getPackageName()) || TRANSIENT.contains(pkg)) return;
        if (!pkg.equals(unlocked)) unlocked = null;   // moved to another app: relock
        if (LOCKED.contains(pkg) && unlocked == null) {
            startActivity(new Intent(this, PinActivity.class)
                    .putExtra(PinActivity.EXTRA_TARGET, pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        }
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        unregisterReceiver(screenOff);
        super.onDestroy();
    }
}
