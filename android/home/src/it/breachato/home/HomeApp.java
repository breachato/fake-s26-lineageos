package it.breachato.home;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.os.SystemClock;

/**
 * Screen-off duties, kept alive by the persistent process:
 * - raises the custom lock screen as the display turns off, so it is already in place (above
 *   the keyguard) when the phone wakes up;
 * - wave to wake: two quick passes of a hand over the proximity sensor turn the screen on.
 *   The touch controller here is powered down with the display, so double tap cannot work;
 *   the proximity sensor is a wake-up sensor and is only listened to while the screen is off.
 */
public class HomeApp extends Application implements SensorEventListener {
    private static final long WAVE_MAX_COVER_MS = 900;    // longer = pocket / face down, not a wave
    private static final long WAVES_WINDOW_MS = 1500;    // both waves within this time

    private SensorManager sm;
    private Sensor prox;
    private long nearSince = -1, lastWave;
    private int waves;

    @Override public void onCreate() {
        super.onCreate();
        sm = getSystemService(SensorManager.class);
        prox = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY, true);
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                    startActivity(new Intent(c, LockCoverActivity.class).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
                    listen(true);
                } else {
                    listen(false);
                }
            }
        }, f);
    }

    private void listen(boolean on) {
        if (prox == null) return;
        if (on) {
            nearSince = -1;
            waves = 0;
            sm.registerListener(this, prox, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            sm.unregisterListener(this);
        }
    }

    @Override public void onSensorChanged(SensorEvent e) {
        boolean near = e.values[0] < Math.min(e.sensor.getMaximumRange(), 3f);
        long now = SystemClock.elapsedRealtime();
        if (near) {
            nearSince = now;
            return;
        }
        if (nearSince < 0) return;
        long covered = now - nearSince;
        nearSince = -1;
        if (covered > WAVE_MAX_COVER_MS) { waves = 0; return; }
        if (waves > 0 && now - lastWave > WAVES_WINDOW_MS) waves = 0;
        waves++;
        lastWave = now;
        if (waves >= 2) {
            waves = 0;
            wake();
        }
    }

    @SuppressWarnings("deprecation")
    private void wake() {
        PowerManager pm = getSystemService(PowerManager.class);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, "breachato:wave");
        wl.acquire(1500);
    }

    @Override public void onAccuracyChanged(Sensor s, int a) { }
}
