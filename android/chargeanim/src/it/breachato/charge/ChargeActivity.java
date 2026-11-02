package it.breachato.charge;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.WindowManager;

public class ChargeActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        // draw under the status bar; no immersive flags, they pop the "full screen" hint
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = 0;
        if (bat != null) {
            int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            level = Math.round(100f * bat.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) / Math.max(1, scale));
        }
        // no lambdas: dx without desugaring can't handle invokedynamic
        setContentView(new ChargeView(this, level, new Runnable() {
            @Override public void run() { close(); }
        }));
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        close();
        return true;
    }

    // onStop, not onPause: keyguard and system dialogs pause us briefly without hiding us
    @Override protected void onStop() {
        super.onStop();
        close();
    }

    private void close() {
        if (isFinishing()) return;
        finish();
        overridePendingTransition(0, 0);
    }
}
