package it.breachato.fakeloc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.Random;

/**
 * Replaces the gps, network and fused providers with test providers that report the place
 * chosen in the app (with a few metres of drift so it looks like a real fix).
 * The GPS chip stays off; apps that need a location still get one.
 */
public class FakeLocService extends Service {
    static final String TAG = "FakeLoc";
    static final String ACTION_RELOAD = "it.breachato.fakeloc.RELOAD";
    static final String[] PROVIDERS = {
        LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused"
    };
    static volatile boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private LocationManager lm;
    private double lat, lon, dLat, dLon;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            push();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                "fakeloc", "Fake location", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "fakeloc")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Fake location active")
                .setContentText("GPS off")
                .setOngoing(true)
                .build();
        startForeground(1, n);
        reload();

        lm = getSystemService(LocationManager.class);
        for (String p : PROVIDERS) {
            try { lm.removeTestProvider(p); } catch (Exception ignored) { }
            try {
                lm.addTestProvider(p, p.equals("network"), p.equals("gps"), false, false,
                        true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
                lm.setTestProviderEnabled(p, true);
            } catch (Exception e) {
                Log.w(TAG, "provider " + p + ": " + e);
            }
        }
        running = true;
        handler.post(tick);
    }

    private void push() {
        // bounded random walk, ~20 m max from the anchor
        dLat = clamp(dLat + (rnd.nextDouble() - 0.5) * 0.00002, 0.00018);
        dLon = clamp(dLon + (rnd.nextDouble() - 0.5) * 0.00004, 0.00036);
        long now = System.currentTimeMillis();
        long nanos = SystemClock.elapsedRealtimeNanos();
        for (String p : PROVIDERS) {
            Location l = new Location(p);
            l.setLatitude(lat + dLat);
            l.setLongitude(lon + dLon);
            l.setAltitude(74 + rnd.nextDouble() * 2);
            l.setAccuracy(p.equals("network") ? 30f : 6f + rnd.nextFloat() * 6f);
            l.setSpeed(0f);
            l.setBearing(0f);
            l.setTime(now);
            l.setElapsedRealtimeNanos(nanos);
            try {
                lm.setTestProviderLocation(p, l);
            } catch (Exception e) {
                Log.w(TAG, "push " + p + ": " + e);
            }
        }
    }

    private static double clamp(double v, double max) {
        return Math.max(-max, Math.min(max, v));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_RELOAD.equals(intent.getAction())) reload();
        return START_STICKY;
    }

    /** Takes the place chosen in the app; the drift restarts around it. */
    private void reload() {
        Places.Place p = Places.current(this);
        lat = p.lat;
        lon = p.lon;
        dLat = 0;
        dLon = 0;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(tick);
        running = false;
        for (String p : PROVIDERS) {
            try { lm.removeTestProvider(p); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
