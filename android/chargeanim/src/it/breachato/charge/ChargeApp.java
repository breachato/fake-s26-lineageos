package it.breachato.charge;

import android.app.Application;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Battery in the status bar and charging feedback:
 * - a horizontal battery with the percentage inside (slot "breachato_battery") and a bolt
 *   next to it while plugged in (slot "breachato_charging"), replacing SystemUI's battery,
 *   which is hidden through icon_blacklist;
 * - the full-screen animation when a charger is connected while the phone is locked.
 * Setting status-bar icons needs STATUS_BAR, so this app is signed with the platform key.
 */
public class ChargeApp extends Application {
    private static final String TAG = "ChargeApp";
    private static final String SLOT_BATTERY = "breachato_battery";
    private static final String SLOT_BOLT = "breachato_charging";

    private Object statusBar;
    private Method setIcon, setIconVisibility;
    private int batRes, boltRes;
    private int shownLevel = -1;
    private Boolean shownPlugged;

    @Override public void onCreate() {
        super.onCreate();
        batRes = getResources().getIdentifier("bat", "drawable", getPackageName());
        boltRes = getResources().getIdentifier("sb_bolt", "drawable", getPackageName());
        try {
            IBinder b = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "statusbar");
            statusBar = Class.forName("com.android.internal.statusbar.IStatusBarService$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, b);
            setIcon = statusBar.getClass().getMethod("setIcon",
                    String.class, String.class, int.class, int.class, String.class);
            setIconVisibility = statusBar.getClass().getMethod("setIconVisibility",
                    String.class, boolean.class);
        } catch (Exception e) {
            Log.e(TAG, "status bar service unavailable", e);
        }

        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                KeyguardManager km = getSystemService(KeyguardManager.class);
                PowerManager pm = getSystemService(PowerManager.class);
                // only over the lock screen or a dark screen, never on top of an app in use
                if (pm.isInteractive() && !km.isKeyguardLocked()) return;
                startActivity(new Intent(c, ChargeActivity.class).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            }
        }, new IntentFilter(Intent.ACTION_POWER_CONNECTED));

        // sticky: the current state is delivered right away, then on every change
        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { update(i); }
        }, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void update(Intent i) {
        if (statusBar == null) return;
        int scale = Math.max(1, i.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        int level = Math.round(100f * i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) / scale);
        level = Math.max(0, Math.min(100, level));
        boolean plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        try {
            if (level != shownLevel) {   // BATTERY_CHANGED fires often
                setIcon.invoke(statusBar, SLOT_BATTERY, getPackageName(), batRes, level,
                        "Battery " + level + "%");
                setIconVisibility.invoke(statusBar, SLOT_BATTERY, true);
                shownLevel = level;
            }
            if (shownPlugged == null || plugged != shownPlugged) {
                if (plugged) {
                    setIcon.invoke(statusBar, SLOT_BOLT, getPackageName(), boltRes, 0, "Charging");
                }
                setIconVisibility.invoke(statusBar, SLOT_BOLT, plugged);
                shownPlugged = plugged;
            }
        } catch (Exception e) {
            Log.e(TAG, "setIcon failed", e);
        }
    }
}
