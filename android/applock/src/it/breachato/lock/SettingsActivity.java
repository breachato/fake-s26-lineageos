package it.breachato.lock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.text.Collator;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import it.breachato.ui.Glass;
import it.breachato.ui.Ios;

/** Settings-style page: lock on/off, trusted-session state, protected apps, change code. */
public class SettingsActivity extends Activity {
    private LockPrefs prefs;
    private TextView sessionState;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new LockPrefs(this);
        getWindow().setStatusBarColor(Ios.BG);
        getWindow().setNavigationBarColor(Ios.BG);
        setContentView(buildUi());
    }

    @Override protected void onResume() {
        super.onResume();
        showSession();
    }

    private void showSession() {
        if (LockService.trusted) {
            sessionState.setText("Trusted until the screen turns off");
            sessionState.setTextColor(Ios.GREEN);
        } else {
            sessionState.setText(Math.min(LockService.goodCodes, LockService.TRUST_AFTER) + " of " + LockService.TRUST_AFTER);
            sessionState.setTextColor(Ios.SECONDARY);
        }
    }

    private View buildUi() {
        final Context c = this;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ios.BG);
        col.setPadding(0, Glass.px(c, 56), 0, Glass.px(c, 40));
        col.addView(Ios.largeTitle(c, "App lock"));

        LinearLayout main = Ios.group(c);
        LinearLayout onRow = Ios.row(c, "Ask for the code", null);
        onRow.addView(toggle(c, prefs.enabled(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) { prefs.setEnabled(on); }
        }));
        Ios.add(main, onRow);
        col.addView(Ios.header(c, ""));
        col.addView(main);
        col.addView(Ios.footer(c, "The apps chosen below ask for the code every time you open them. "
                + "After " + LockService.TRUST_AFTER + " correct codes, at least "
                + (LockService.TRUST_SPACING_MS / 1000) + " seconds apart, the phone trusts "
                + "the session and stops asking until the screen turns off."));

        col.addView(Ios.header(c, "Session"));
        LinearLayout session = Ios.group(c);
        LinearLayout state = Ios.row(c, "Correct codes", "");
        sessionState = Ios.value(state);
        Ios.add(session, state);
        TextView end = Glass.text(c, "End session", 17, Ios.RED, Ios.regular());
        end.setMinHeight(Glass.px(c, 46));
        end.setGravity(android.view.Gravity.CENTER_VERTICAL);
        end.setPadding(Glass.px(c, 16), 0, Glass.px(c, 16), 0);
        Ios.tappable(end);
        end.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { LockService.endSession(); showSession(); }
        });
        Ios.add(session, end);
        col.addView(session);

        col.addView(Ios.header(c, "Protected apps"));
        LinearLayout apps = Ios.group(c);
        PackageManager pm = getPackageManager();
        List<ResolveInfo> list = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        final Collator col8 = Collator.getInstance(Locale.ITALIAN);
        final PackageManager fpm = pm;
        Collections.sort(list, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return col8.compare(a.loadLabel(fpm).toString(), b.loadLabel(fpm).toString());
            }
        });
        Set<String> locked = prefs.apps();
        String last = null;
        for (ResolveInfo ri : list) {
            final String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || pkg.equals(last)) continue;
            last = pkg;
            LinearLayout r = Ios.row(c, ri.loadLabel(pm).toString(), null);
            ImageView icon = new ImageView(c);
            icon.setImageDrawable(ri.loadIcon(pm));
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Glass.px(c, 30), Glass.px(c, 30));
            il.rightMargin = Glass.px(c, 14);
            r.addView(icon, 0, il);
            r.addView(toggle(c, locked.contains(pkg), new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton v, boolean on) { prefs.setLocked(pkg, on); }
            }));
            Ios.add(apps, r);
        }
        col.addView(apps);

        col.addView(Ios.header(c, ""));
        LinearLayout code = Ios.group(c);
        TextView change = Glass.text(c, "Change code", 17, Ios.BLUE, Ios.regular());
        change.setMinHeight(Glass.px(c, 46));
        change.setGravity(android.view.Gravity.CENTER_VERTICAL);
        change.setPadding(Glass.px(c, 16), 0, Glass.px(c, 16), 0);
        Ios.tappable(change);
        change.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(c, PinActivity.class).putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_CHANGE));
            }
        });
        Ios.add(code, change);
        col.addView(code);

        ScrollView sv = new ScrollView(c);
        sv.setBackgroundColor(Ios.BG);
        sv.addView(col);
        return sv;
    }

    /** Green switch like a settings toggle. */
    private static Switch toggle(Context c, boolean on, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(c);
        s.setChecked(on);
        int[][] states = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(states, new int[] {0xFFFFFFFF, 0xFFFFFFFF}));
        s.setTrackTintList(new ColorStateList(states, new int[] {Ios.GREEN, 0xFF39393D}));
        s.setOnCheckedChangeListener(l);
        return s;
    }
}
