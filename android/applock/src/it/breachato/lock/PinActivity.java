package it.breachato.lock;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import it.breachato.ui.Glass;
import it.breachato.ui.Ios;

/**
 * Passcode screen on a frosted wallpaper: round translucent keys with letters, outline dots
 * that fill, "Cancel" / "Delete" beside the zero. Opened by LockService on top of a
 * protected app (EXTRA_TARGET set), from the launcher to reach the settings (first use sets
 * the code), or from the settings to change the code (EXTRA_MODE = MODE_CHANGE).
 * Entry completes on the last digit; back from a protected app goes to the home screen.
 */
public class PinActivity extends Activity {
    static final String EXTRA_TARGET = "target";
    static final String EXTRA_MODE = "mode";
    static final String MODE_CHANGE = "change";
    private static final int MIN_LEN = 4, MAX_LEN = 8;
    private static final String[] LETTERS = {"", "", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ"};

    private PinStore store;
    private String target;          // package to unlock, null when managing the code
    private boolean changeMode;     // changing the code (from the settings) instead of opening them
    private String firstEntry;      // while setting a new code: the first of the two entries
    private boolean verifiedOld;    // while changing the code: old one already checked
    private final StringBuilder entry = new StringBuilder();
    private final Handler h = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private ImageView icon;
    private TextView title, subtitle, left, right;
    private Dots dots;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        store = new PinStore(this);
        setContentView(buildUi());
        onNewIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        target = i.getStringExtra(EXTRA_TARGET);
        changeMode = MODE_CHANGE.equals(i.getStringExtra(EXTRA_MODE));
        firstEntry = null;
        verifiedOld = false;
        entry.setLength(0);
        showTarget();
        refresh(null);
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(220).setInterpolator(Glass.EASE).start();
    }

    /** Verifying an existing code; with no code yet every flow (even unlocking an app) sets one first. */
    private boolean checking() { return store.hasPin() && (target != null || !verifiedOld); }

    /** Digits that complete an entry automatically; 0 = needs the OK key. */
    private int autoLength() { return checking() ? store.length() : 0; }

    private String prompt() {
        if (!store.hasPin()) return firstEntry == null ? "Choose a code" : "Repeat the code";
        if (target != null || !changeMode) return "Enter code";
        if (!verifiedOld) return "Current code";
        return firstEntry == null ? "New code" : "Repeat the new code";
    }

    private void showTarget() {
        PackageManager pm = getPackageManager();
        Drawable d = null;
        String name = null;
        if (target != null) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(target, 0);
                d = pm.getApplicationIcon(ai);
                name = pm.getApplicationLabel(ai).toString();
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        icon.setImageDrawable(d);
        icon.setVisibility(d == null ? View.GONE : View.VISIBLE);
        subtitle.setTag(name);
    }

    private void refresh(String error) {
        long wait = store.lockedFor();
        title.setText(prompt());
        String name = (String) subtitle.getTag();
        if (checking() && wait > 0) {
            subtitle.setText("Try again in " + (wait / 1000 + 1) + " seconds");
            subtitle.setTextColor(Ios.RED);
            h.removeCallbacksAndMessages(null);
            h.postDelayed(new Runnable() { @Override public void run() { refresh(null); } }, 1000);
        } else if (error != null) {
            subtitle.setText(error);
            subtitle.setTextColor(Ios.RED);
        } else if (!store.hasPin()) {
            subtitle.setText(MIN_LEN + " to " + MAX_LEN + " digits");
            subtitle.setTextColor(0xB3FFFFFF);
        } else {
            subtitle.setText(name != null ? "to open " + name : "");
            subtitle.setTextColor(0xB3FFFFFF);
        }
        int auto = autoLength();
        dots.set(entry.length(), auto > 0 ? auto : Math.max(MIN_LEN, entry.length()));
        // bottom row: OK appears when the length is free (choosing a code), the delete key once typing
        left.setText(auto > 0 ? (target != null ? "Cancel" : "") : (entry.length() >= MIN_LEN ? "OK" : (target != null ? "Cancel" : "")));
        right.setText(entry.length() > 0 ? "Delete" : (target != null ? "" : "Close"));
    }

    private void submit() {
        String pin = entry.toString();
        if (pin.length() < MIN_LEN) { refresh("At least " + MIN_LEN + " digits"); return; }
        entry.setLength(0);
        if (checking()) {
            if (store.lockedFor() > 0) { refresh(null); return; }
            if (!store.check(pin)) { wrong("Wrong code"); return; }
            if (target != null || !changeMode) { success(); return; }
            verifiedOld = true;
            refresh(null);
            return;
        }
        if (firstEntry == null) { firstEntry = pin; refresh(null); return; }
        if (!firstEntry.equals(pin)) { firstEntry = null; wrong("The codes do not match"); return; }
        store.setPin(pin);
        success();
    }

    private void wrong(String msg) {
        refresh(msg);
        ObjectAnimator.ofFloat(dots, "translationX", 0, -22, 20, -15, 11, -6, 0).setDuration(450).start();
        Vibrator v = getSystemService(Vibrator.class);
        if (v != null) v.vibrate(VibrationEffect.createWaveform(new long[] {0, 35, 70, 35}, -1));
    }

    private void success() {
        if (target != null) {
            LockService.unlocked = target;
            LockService.goodCode();
        } else if (!changeMode) {
            // opened from the launcher: code verified (or just chosen), go to the settings
            startActivity(new Intent(this, SettingsActivity.class));
        }
        root.animate().alpha(0f).scaleX(1.06f).scaleY(1.06f).setDuration(240).setInterpolator(Glass.EASE)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        finish();
                        overridePendingTransition(0, 0);
                    }
                }).start();
    }

    @Override public void onBackPressed() { cancel(); }

    private void cancel() {
        if (target != null) {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
        overridePendingTransition(0, 0);
    }

    private void digit(int d) {
        if (entry.length() >= MAX_LEN) return;
        entry.append(d);
        int auto = autoLength();
        refresh(null);
        if (auto > 0 && entry.length() == auto) {
            h.postDelayed(new Runnable() { @Override public void run() { submit(); } }, 110);
        }
    }

    // --- UI -----------------------------------------------------------------------------
    private View buildUi() {
        Context c = this;
        root = new FrameLayout(c);
        root.setBackground(Glass.backdrop(c, 0x99000000));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, Glass.px(c, 84), 0, Glass.px(c, 30));

        icon = new ImageView(c);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Glass.px(c, 44), Glass.px(c, 44));
        il.bottomMargin = Glass.px(c, 14);
        col.addView(icon, il);

        title = Glass.text(c, "", 20, 0xFFFFFFFF, Ios.regular());
        title.setGravity(Gravity.CENTER);
        col.addView(title);
        subtitle = Glass.text(c, "", 14, 0xB3FFFFFF, Ios.regular());
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, Glass.px(c, 6), 0, 0);
        col.addView(subtitle);

        dots = new Dots(c);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, Glass.px(c, 20));
        dl.topMargin = Glass.px(c, 22);
        col.addView(dots, dl);

        col.addView(new View(c), new LinearLayout.LayoutParams(1, 0, 1f));

        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(3);
        for (int k = 1; k <= 12; k++) {
            View key;
            if (k <= 9) key = key(c, k);
            else if (k == 10) key = left = textKey(c, true);
            else if (k == 11) key = key(c, 0);
            else key = right = textKey(c, false);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = Glass.px(c, 78);
            lp.height = Glass.px(c, 78);
            lp.setMargins(Glass.px(c, 14), Glass.px(c, 8), Glass.px(c, 14), Glass.px(c, 8));
            grid.addView(key, lp);
        }
        col.addView(grid, new LinearLayout.LayoutParams(-2, -2));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    private View key(Context c, final int d) {
        LinearLayout k = new LinearLayout(c);
        k.setOrientation(LinearLayout.VERTICAL);
        k.setGravity(Gravity.CENTER);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_pressed}, oval(0x99FFFFFF));
        bg.addState(new int[] {}, oval(0x33FFFFFF));
        k.setBackground(bg);
        TextView n = Glass.text(c, String.valueOf(d), 34, 0xFFFFFFFF, Ios.light());
        n.setGravity(Gravity.CENTER);
        k.addView(n);
        if (!LETTERS[d].isEmpty()) {
            TextView l = Glass.text(c, LETTERS[d], 9.5f, 0xFFFFFFFF, Ios.medium());
            l.setLetterSpacing(0.22f);
            l.setGravity(Gravity.CENTER);
            k.addView(l);
        }
        k.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                digit(d);
            }
        });
        return k;
    }

    private TextView textKey(Context c, final boolean isLeft) {
        TextView t = Glass.text(c, "", 16, 0xFFFFFFFF, Ios.regular());
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String s = ((TextView) v).getText().toString();
                if (s.equals("OK")) submit();
                else if (s.equals("Cancel") || s.equals("Close")) cancel();
                else if (s.equals("Delete") && entry.length() > 0) { entry.setLength(entry.length() - 1); refresh(null); }
            }
        });
        return t;
    }

    private static GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /** Outline dots that fill white as digits are typed. */
    private static final class Dots extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int filled, total = 4;

        Dots(Context c) {
            super(c);
            fill.setColor(0xFFFFFFFF);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(Glass.dp(c, 1.2f));
            ring.setColor(0xFFFFFFFF);
        }

        void set(int filled, int total) {
            this.filled = filled;
            this.total = total;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float r = Glass.dp(getContext(), 6.5f), gap = Glass.dp(getContext(), 26);
            float w = (total - 1) * gap, x0 = getWidth() / 2f - w / 2f, y = getHeight() / 2f;
            for (int i = 0; i < total; i++) {
                float x = x0 + i * gap;
                if (i < filled) c.drawCircle(x, y, r, fill);
                else c.drawCircle(x, y, r - ring.getStrokeWidth() / 2, ring);
            }
        }
    }
}
