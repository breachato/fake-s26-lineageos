package it.breachato.home;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import it.breachato.ui.Glass;

/**
 * Lock screen shown above the keyguard: date, large clock, charging pill and a swipe-up
 * gesture. Swiping up asks Android to dismiss the keyguard, which brings up its PIN entry;
 * the PIN itself stays Android's, as it is also the disk-encryption credential.
 */
public class LockCoverActivity extends Activity {
    private LinearLayout top, bottom;
    private TextView charge;
    private float downY;
    private VelocityTracker vt;
    private boolean leaving;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        setContentView(buildUi());
    }

    @Override protected void onResume() {
        super.onResume();
        leaving = false;
        reset(false);
        showCharge();
    }

    @Override public void onBackPressed() { /* stays until swiped away */ }

    private void showCharge() {
        Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bat == null) return;
        int scale = Math.max(1, bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        int level = Math.round(100f * bat.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) / scale);
        boolean plugged = bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        charge.setText((level >= 100 ? "FULLY CHARGED" : "CHARGING") + "  ·  " + level + "%");
        charge.setVisibility(plugged ? View.VISIBLE : View.GONE);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (leaving) return true;
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
        float dy;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = e.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                dy = Math.min(0, e.getRawY() - downY);
                follow(dy);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dy = Math.min(0, e.getRawY() - downY);
                vt.computeCurrentVelocity(1000);
                float vy = vt.getYVelocity();
                vt.recycle();
                vt = null;
                if (-dy > Glass.dp(this, 110) || vy < -1200) unlock(); else reset(true);
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void follow(float dy) {
        float k = Math.min(1f, -dy / Glass.dp(this, 320));
        top.setTranslationY(dy * 0.55f);
        bottom.setTranslationY(dy * 0.85f);
        top.setAlpha(1f - k);
        bottom.setAlpha(1f - k * 1.4f);
    }

    private void reset(boolean animate) {
        if (!animate) {
            top.setTranslationY(0); bottom.setTranslationY(0); top.setAlpha(1); bottom.setAlpha(1);
            return;
        }
        top.animate().translationY(0).alpha(1).setDuration(450).setInterpolator(Glass.EASE).start();
        bottom.animate().translationY(0).alpha(1).setDuration(450).setInterpolator(Glass.EASE).start();
    }

    private void unlock() {
        leaving = true;
        float h = -getWindow().getDecorView().getHeight() * 0.35f;
        top.animate().translationY(h).alpha(0).setDuration(260).setInterpolator(Glass.EASE).start();
        bottom.animate().translationY(h).alpha(0).setDuration(260).setInterpolator(Glass.EASE)
                .withEndAction(new Runnable() {
                    @Override public void run() { dismiss(); }
                }).start();
    }

    private void dismiss() {
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km == null || !km.isKeyguardLocked()) { finishQuietly(); return; }
        km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
            @Override public void onDismissSucceeded() { finishQuietly(); }
            @Override public void onDismissCancelled() { leaving = false; reset(true); }
            @Override public void onDismissError() { leaving = false; reset(true); }
        });
    }

    private void finishQuietly() {
        finish();
        overridePendingTransition(0, 0);
    }

    // --- UI -----------------------------------------------------------------------------
    private View buildUi() {
        Context c = this;
        FrameLayout root = new FrameLayout(c);
        View scrim = new View(c);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x66070707, 0x00070707, 0x00070707, 0x99070707}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        top = new LinearLayout(c);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        top.setPadding(0, Glass.px(c, 92), 0, 0);
        TextClock date = new TextClock(c);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        date.setAllCaps(true);
        date.setTextSize(13);
        date.setLetterSpacing(0.16f);
        date.setTypeface(Glass.mono(c, 500));
        date.setTextColor(0xD9EDEDED);
        top.addView(date);
        TextClock clock = new TextClock(c);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextSize(96);
        clock.setLetterSpacing(-0.04f);
        clock.setIncludeFontPadding(false);
        clock.setTypeface(Glass.sans(c, 600));
        clock.setTextColor(Glass.FG);
        clock.setShadowLayer(Glass.dp(c, 24), 0, 0, 0x55000000);
        clock.setPadding(0, Glass.px(c, 4), 0, 0);
        top.addView(clock);
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        bottom = new LinearLayout(c);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(0, 0, 0, Glass.px(c, 14));
        charge = Glass.label(c, "");
        charge.setTextColor(Glass.OK);
        charge.setPadding(Glass.px(c, 16), Glass.px(c, 9), Glass.px(c, 16), Glass.px(c, 9));
        charge.setBackground(Glass.card(c, 20, Glass.CARD, Glass.LINE2));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.bottomMargin = Glass.px(c, 26);
        bottom.addView(charge, cl);
        TextView hint = Glass.label(c, "Swipe up to unlock");
        hint.setTextColor(0xB3EDEDED);
        bottom.addView(hint);
        View bar = new View(c);
        bar.setBackground(Glass.card(c, 3, 0xD9EDEDED, 0x00000000));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Glass.px(c, 134), Glass.px(c, 5));
        bl.topMargin = Glass.px(c, 18);
        bottom.addView(bar, bl);
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        return root;
    }
}
