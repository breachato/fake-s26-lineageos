package it.breachato.home;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.MediaStore;
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
import it.breachato.ui.Ios;

/**
 * Lock screen shown above the keyguard, everything centred: lock glyph, date and a large clock
 * at the top; charging pill, torch and camera buttons, swipe hint and home bar at the bottom.
 * Swiping up asks Android to dismiss the keyguard, which brings up its PIN entry; the PIN
 * itself stays Android's, as it is also the disk-encryption credential.
 */
public class LockCoverActivity extends Activity {
    private LinearLayout top, bottom;
    private TextView charge;
    private Glyph torchGlyph;
    private float downY;
    private VelocityTracker vt;
    private boolean leaving, torchOn;

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
        charge.setText((level >= 100 ? "Fully charged" : "Charging") + "  ·  " + level + "%");
        charge.setVisibility(plugged ? View.VISIBLE : View.INVISIBLE);
    }

    // --- swipe up ------------------------------------------------------------------------
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
                follow(Math.min(0, e.getRawY() - downY));
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
        top.setTranslationY(dy * 0.5f);
        bottom.setTranslationY(dy * 0.85f);
        top.setAlpha(1f - k);
        bottom.setAlpha(1f - k * 1.4f);
        float s = 1f - 0.06f * k;
        top.setScaleX(s);
        top.setScaleY(s);
    }

    private void reset(boolean animate) {
        if (!animate) {
            top.setTranslationY(0); bottom.setTranslationY(0); top.setAlpha(1); bottom.setAlpha(1);
            top.setScaleX(1); top.setScaleY(1);
            return;
        }
        top.animate().translationY(0).alpha(1).scaleX(1).scaleY(1).setDuration(450).setInterpolator(Glass.EASE).start();
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

    // --- quick actions -------------------------------------------------------------------
    private void toggleTorch() {
        try {
            CameraManager cm = getSystemService(CameraManager.class);
            for (String id : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) {
                    torchOn = !torchOn;
                    cm.setTorchMode(id, torchOn);
                    torchGlyph.setActive(torchOn);
                    return;
                }
            }
        } catch (Exception ignored) { }
    }

    private void openCamera() {
        try {
            startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }

    // --- UI ------------------------------------------------------------------------------
    private View buildUi() {
        final Context c = this;
        FrameLayout root = new FrameLayout(c);
        View scrim = new View(c);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x73000000, 0x14000000, 0x00000000, 0x14000000, 0x80000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        top = new LinearLayout(c);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        top.setPadding(0, Glass.px(c, 58), 0, 0);
        Glyph lock = new Glyph(c, Glyph.LOCK);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(Glass.px(c, 18), Glass.px(c, 24));
        ll.bottomMargin = Glass.px(c, 14);
        top.addView(lock, ll);
        TextClock date = new TextClock(c);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        date.setTextSize(19);
        date.setTypeface(Ios.medium());
        date.setTextColor(0xE6FFFFFF);
        date.setGravity(Gravity.CENTER);
        top.addView(date, new LinearLayout.LayoutParams(-1, -2));
        TextClock clock = new TextClock(c);
        clock.setFormat24Hour("H:mm");
        clock.setFormat12Hour("h:mm");
        clock.setTextSize(100);
        clock.setLetterSpacing(-0.035f);
        clock.setIncludeFontPadding(false);
        clock.setTypeface(Glass.sans(c, 600));
        clock.setTextColor(0xFFFFFFFF);
        clock.setGravity(Gravity.CENTER);
        clock.setShadowLayer(Glass.dp(c, 18), 0, 0, 0x40000000);
        top.addView(clock, new LinearLayout.LayoutParams(-1, -2));
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        bottom = new LinearLayout(c);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(Glass.px(c, 44), 0, Glass.px(c, 44), Glass.px(c, 10));

        charge = Glass.text(c, "", 14, 0xFFFFFFFF, Ios.medium());
        charge.setPadding(Glass.px(c, 16), Glass.px(c, 8), Glass.px(c, 16), Glass.px(c, 8));
        charge.setBackground(Glass.card(c, 18, 0x59000000, 0x26FFFFFF));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.bottomMargin = Glass.px(c, 22);
        bottom.addView(charge, cl);

        // torch on the left, camera on the right, centred between them nothing but space
        LinearLayout actions = new LinearLayout(c);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        torchGlyph = new Glyph(c, Glyph.TORCH);
        actions.addView(roundButton(c, torchGlyph, new View.OnClickListener() {
            @Override public void onClick(View v) { toggleTorch(); }
        }));
        actions.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        actions.addView(roundButton(c, new Glyph(c, Glyph.CAMERA), new View.OnClickListener() {
            @Override public void onClick(View v) { openCamera(); }
        }));
        bottom.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = Glass.text(c, "Swipe up to open", 14, 0xCCFFFFFF, Ios.regular());
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Glass.px(c, 20), 0, 0);
        bottom.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        View bar = new View(c);
        bar.setBackground(Glass.card(c, 3, 0xE6FFFFFF, 0x00000000));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Glass.px(c, 134), Glass.px(c, 5));
        bl.topMargin = Glass.px(c, 14);
        bottom.addView(bar, bl);
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        return root;
    }

    private View roundButton(Context c, Glyph g, View.OnClickListener l) {
        FrameLayout b = new FrameLayout(c);
        b.setBackground(Glass.circle(c, 0x59000000, 0x26FFFFFF));
        int pad = Glass.px(c, 15);
        b.setPadding(pad, pad, pad, pad);
        b.addView(g, new FrameLayout.LayoutParams(-1, -1));
        b.setLayoutParams(new LinearLayout.LayoutParams(Glass.px(c, 52), Glass.px(c, 52)));
        Glass.pressable(b);
        b.setOnClickListener(l);
        return b;
    }

    /** Small line glyphs drawn in code: padlock, torch, camera. */
    static final class Glyph extends View {
        static final int LOCK = 0, TORCH = 1, CAMERA = 2;
        private final int type;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF r = new RectF();
        private boolean active;

        Glyph(Context c, int type) {
            super(c);
            this.type = type;
            p.setColor(0xFFFFFFFF);
        }

        void setActive(boolean on) {
            active = on;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), s = Math.min(w, h);
            float cx = w / 2f, cy = h / 2f;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            if (type == LOCK) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(w * 0.16f);
                r.set(w * 0.22f, h * 0.06f, w * 0.78f, h * 0.62f);
                c.drawArc(r, 180, 180, false, p);
                c.drawLine(w * 0.22f, h * 0.34f, w * 0.22f, h * 0.46f, p);
                c.drawLine(w * 0.78f, h * 0.34f, w * 0.78f, h * 0.46f, p);
                p.setStyle(Paint.Style.FILL);
                r.set(0, h * 0.42f, w, h);
                c.drawRoundRect(r, w * 0.16f, w * 0.16f, p);
            } else if (type == TORCH) {
                p.setStyle(active ? Paint.Style.FILL : Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.09f);
                path.reset();                         // head: trapezoid, then the handle
                path.moveTo(cx - s * 0.32f, cy - s * 0.42f);
                path.lineTo(cx + s * 0.32f, cy - s * 0.42f);
                path.lineTo(cx + s * 0.18f, cy - s * 0.12f);
                path.lineTo(cx - s * 0.18f, cy - s * 0.12f);
                path.close();
                c.drawPath(path, p);
                r.set(cx - s * 0.18f, cy - s * 0.12f, cx + s * 0.18f, cy + s * 0.48f);
                c.drawRoundRect(r, s * 0.06f, s * 0.06f, p);
                if (!active) {
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx, cy + s * 0.06f, s * 0.05f, p);
                }
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.09f);
                r.set(cx - s * 0.46f, cy - s * 0.26f, cx + s * 0.46f, cy + s * 0.38f);
                c.drawRoundRect(r, s * 0.1f, s * 0.1f, p);
                path.reset();                         // viewfinder bump on top
                path.moveTo(cx - s * 0.18f, cy - s * 0.26f);
                path.lineTo(cx - s * 0.1f, cy - s * 0.4f);
                path.lineTo(cx + s * 0.1f, cy - s * 0.4f);
                path.lineTo(cx + s * 0.18f, cy - s * 0.26f);
                c.drawPath(path, p);
                c.drawCircle(cx, cy + s * 0.06f, s * 0.15f, p);
            }
        }
    }
}
