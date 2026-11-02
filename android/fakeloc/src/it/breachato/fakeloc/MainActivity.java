package it.breachato.fakeloc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        startForegroundService(new Intent(this, FakeLocService.class));
        TextView t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(18);
        t.setPadding(48, 48, 48, 48);
        t.setText("Fake location active\n\nGPS hardware off.\nApps see:\nReykjavík, Iceland\n"
                + FakeLocService.LAT + ", " + FakeLocService.LON);
        setContentView(t);
    }
}
