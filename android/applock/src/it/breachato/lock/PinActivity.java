package it.breachato.lock;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Dark keypad. Opened by LockService on top of a protected app (EXTRA_TARGET set), or from
 * the launcher to set / change the PIN. Back from a protected app goes to the home screen.
 */
public class PinActivity extends Activity {
    static final String EXTRA_TARGET = "target";
    private static final int MIN_LEN = 4, MAX_LEN = 8;

    private PinStore store;
    private String target;          // package to unlock, null when managing the PIN
    private String firstEntry;      // while setting a new PIN: the first of the two entries
    private boolean verifiedOld;    // while changing the PIN: old one already checked
    private final StringBuilder entry = new StringBuilder();
    private TextView title, dots;
    private final Handler h = new Handler(Looper.getMainLooper());
    private float dp;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        store = new PinStore(this);
        setContentView(buildUi());
        onNewIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        target = i.getStringExtra(EXTRA_TARGET);
        firstEntry = null;
        verifiedOld = false;
        entry.setLength(0);
        refresh(null);
    }

    private String prompt() {
        if (target != null) return "Enter PIN";
        if (!store.hasPin()) return firstEntry == null ? "Choose a PIN (4-8 digits)" : "Confirm the PIN";
        if (!verifiedOld) return "Current PIN";
        return firstEntry == null ? "New PIN" : "Confirm the new PIN";
    }

    private void refresh(String message) {
        long wait = store.lockedFor();
        boolean checking = target != null || (store.hasPin() && !verifiedOld);
        if (checking && wait > 0) {
            title.setText("Too many attempts, try again in " + (wait / 1000 + 1) + " s");
            h.postDelayed(new Runnable() { @Override public void run() { refresh(null); } }, 1000);
        } else {
            title.setText(message != null ? message : prompt());
        }
        StringBuilder d = new StringBuilder();
        for (int k = 0; k < Math.max(MIN_LEN, entry.length()); k++) d.append(k < entry.length() ? "● " : "○ ");
        dots.setText(d.toString().trim());
    }

    private void submit() {
        String pin = entry.toString();
        entry.setLength(0);
        if (pin.length() < MIN_LEN) { refresh("At least " + MIN_LEN + " digits"); return; }
        boolean checking = target != null || (store.hasPin() && !verifiedOld);
        if (checking) {
            if (store.lockedFor() > 0) { refresh(null); return; }
            if (!store.check(pin)) { refresh("Wrong PIN"); return; }
            if (target != null) {
                LockService.unlocked = target;
                finish();
                overridePendingTransition(0, 0);
                return;
            }
            verifiedOld = true;
            refresh(null);
            return;
        }
        if (firstEntry == null) { firstEntry = pin; refresh(null); return; }
        if (!firstEntry.equals(pin)) { firstEntry = null; refresh("PINs do not match, start over"); return; }
        store.setPin(pin);
        title.setText("PIN saved");
        h.postDelayed(new Runnable() { @Override public void run() { finish(); } }, 700);
    }

    @Override public void onBackPressed() {
        if (target != null) {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
    }

    // --- UI -------------------------------------------------------------------
    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);

        TextView lock = text("🔒", 34);
        title = text("", 18);
        title.setPadding(0, px(12), 0, px(18));
        dots = text("", 22);
        dots.setLetterSpacing(0.15f);
        dots.setPadding(0, 0, 0, px(36));
        root.addView(lock);
        root.addView(title);
        root.addView(dots);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK"};
        for (final String k : keys) {
            TextView key = text(k, k.length() == 1 && Character.isDigit(k.charAt(0)) ? 28 : 20);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.rgb(28, 28, 30));
            key.setBackground(bg);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = px(76);
            lp.height = px(76);
            lp.setMargins(px(12), px(10), px(12), px(10));
            key.setLayoutParams(lp);
            key.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { press(k); }
            });
            grid.addView(key);
        }
        root.addView(grid);
        return root;
    }

    private void press(String k) {
        if (k.equals("⌫")) {
            if (entry.length() > 0) entry.setLength(entry.length() - 1);
        } else if (k.equals("OK")) {
            submit();
            return;
        } else if (entry.length() < MAX_LEN) {
            entry.append(k);
        }
        refresh(null);
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        return t;
    }

    private int px(int v) { return Math.round(v * dp); }
}
