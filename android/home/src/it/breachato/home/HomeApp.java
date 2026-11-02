package it.breachato.home;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * Raises the custom lock screen as the display turns off, so it is already in place (above
 * the keyguard) when the phone wakes up.
 */
public class HomeApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                startActivity(new Intent(c, LockCoverActivity.class).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            }
        }, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }
}
