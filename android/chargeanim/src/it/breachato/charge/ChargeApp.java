package it.breachato.charge;

import android.app.Application;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Icon;
import android.os.BatteryManager;
import android.os.PowerManager;

/**
 * Charging feedback for a status bar that only shows the battery percentage as text:
 * a bolt icon in the status bar while plugged in, and the full-screen animation when a
 * charger is connected while the phone is locked.
 */
public class ChargeApp extends Application {
    private static final String CHANNEL = "charging";
    private static final int NOTIF_ID = 1;

    private NotificationManager nm;
    private Icon boltIcon;
    private int shownLevel = -1;
    private boolean shownFull;

    @Override public void onCreate() {
        super.onCreate();
        nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Charging", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
        boltIcon = Icon.createWithBitmap(boltBitmap());

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
        boolean plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        if (!plugged) {
            if (shownLevel >= 0) nm.cancel(NOTIF_ID);
            shownLevel = -1;
            return;
        }
        int scale = Math.max(1, i.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        int level = Math.round(100f * i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) / scale);
        boolean full = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0) == BatteryManager.BATTERY_STATUS_FULL
                || level >= 100;
        if (level == shownLevel && full == shownFull) return;   // BATTERY_CHANGED fires often
        shownLevel = level;
        shownFull = full;
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(boltIcon)
                .setContentTitle(full ? "Fully charged" : "Charging")
                .setContentText(level + "%")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
        nm.notify(NOTIF_ID, n);
    }

    /** Status-bar icon: only the alpha channel counts, the system tints it. */
    private Bitmap boltBitmap() {
        int s = Math.round(24 * getResources().getDisplayMetrics().density);
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        Path path = new Path();
        float h = s * 0.84f, w = h * 0.56f;
        ChargeView.bolt(path, (s - w) / 2f, (s - h) / 2f, w, h);
        new Canvas(b).drawPath(path, p);
        return b;
    }
}
