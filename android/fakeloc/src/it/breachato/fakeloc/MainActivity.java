package it.breachato.fakeloc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import it.breachato.ui.Glass;
import it.breachato.ui.Ios;

/** Settings-style page to pick where the phone reports itself: a city or custom coordinates. */
public class MainActivity extends Activity {
    private LinearLayout placeRow, coordsRow;
    private EditText lat, lon;
    private final List<TextView> checks = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ios.BG);
        getWindow().setNavigationBarColor(Ios.BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        startForegroundService(new Intent(this, FakeLocService.class));
        setContentView(buildUi());
        show();
    }

    private void choose(Places.Place p) {
        Places.set(this, p);
        startForegroundService(new Intent(this, FakeLocService.class).setAction(FakeLocService.ACTION_RELOAD));
        show();
    }

    private void show() {
        Places.Place p = Places.current(this);
        Ios.value(placeRow).setText(p.name);
        Ios.value(coordsRow).setText(String.format(Locale.US, "%.4f, %.4f", p.lat, p.lon));
        for (int i = 0; i < checks.size(); i++) {
            Places.Place c = Places.PRESETS[i];
            checks.get(i).setVisibility(c.name.equals(p.name) && c.lat == p.lat ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private View buildUi() {
        final Context c = this;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ios.BG);
        col.setPadding(0, Glass.px(c, 56), 0, Glass.px(c, 40));
        col.addView(Ios.largeTitle(c, "Location"));

        col.addView(Ios.header(c, "Current location"));
        LinearLayout now = Ios.group(c);
        placeRow = Ios.row(c, "Apps see you in", "");
        coordsRow = Ios.row(c, "Coordinates", "");
        Ios.value(coordsRow).setTextSize(15);
        LinearLayout gps = Ios.row(c, "GPS hardware", "Off");
        Ios.value(gps).setTextColor(Ios.GREEN);
        Ios.add(now, placeRow);
        Ios.add(now, coordsRow);
        Ios.add(now, gps);
        col.addView(now);
        col.addView(Ios.footer(c, "The GPS chip stays off: apps only get the location chosen here."));

        col.addView(Ios.header(c, "Custom coordinates"));
        LinearLayout custom = Ios.group(c);
        lat = field(c);
        lon = field(c);
        Ios.add(custom, fieldRow(c, "Latitude", lat, "e.g. 51.5074"));
        Ios.add(custom, fieldRow(c, "Longitude", lon, "e.g. -0.1278"));
        TextView apply = Glass.text(c, "Use these coordinates", 17, Ios.BLUE, Ios.regular());
        apply.setMinHeight(Glass.px(c, 46));
        apply.setGravity(Gravity.CENTER_VERTICAL);
        apply.setPadding(Glass.px(c, 16), 0, Glass.px(c, 16), 0);
        Ios.tappable(apply);
        apply.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    double la = Double.parseDouble(lat.getText().toString().trim().replace(',', '.'));
                    double lo = Double.parseDouble(lon.getText().toString().trim().replace(',', '.'));
                    if (Math.abs(la) > 90 || Math.abs(lo) > 180) throw new NumberFormatException();
                    choose(new Places.Place("Custom", "", la, lo));
                } catch (NumberFormatException e) {
                    lat.setError("Invalid coordinates");
                }
            }
        });
        Ios.add(custom, apply);
        col.addView(custom);

        col.addView(Ios.header(c, "Cities"));
        LinearLayout cities = Ios.group(c);
        for (final Places.Place p : Places.PRESETS) {
            LinearLayout r = Ios.row(c, p.name, null);
            TextView country = Glass.text(c, p.country, 15, Ios.SECONDARY, Ios.regular());
            country.setPadding(0, 0, Glass.px(c, 12), 0);
            r.addView(country);
            TextView check = Glass.text(c, "✓", 18, Ios.BLUE, Ios.medium());
            check.setVisibility(View.INVISIBLE);
            r.addView(check);
            checks.add(check);
            Ios.tappable(r);
            r.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { choose(p); }
            });
            Ios.add(cities, r);
        }
        col.addView(cities);

        ScrollView sv = new ScrollView(c);
        sv.setBackgroundColor(Ios.BG);
        sv.addView(col);
        return sv;
    }

    private static LinearLayout fieldRow(Context c, String label, EditText e, String hint) {
        LinearLayout r = Ios.row(c, label, null);
        e.setHint(hint);
        r.addView(e, new LinearLayout.LayoutParams(Glass.px(c, 150), -2));
        return r;
    }

    private static EditText field(Context c) {
        EditText e = new EditText(c);
        e.setHintTextColor(0xFF5A5A5E);
        e.setTextColor(Ios.LABEL);
        e.setTextSize(17);
        e.setTypeface(Ios.regular());
        e.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        e.setSingleLine(true);
        e.setBackground(null);
        e.setPadding(0, 0, 0, 0);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }
}
