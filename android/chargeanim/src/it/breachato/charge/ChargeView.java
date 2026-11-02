package it.breachato.charge;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Big horizontal battery that fades in, fills green up to the real level with the
 * percentage counting along, holds, then fades out (about 2.8 s in total).
 */
public class ChargeView extends View {
    private static final long TOTAL = 2800;
    private static final int GREEN = Color.rgb(52, 199, 89);

    private final int level;
    private final Runnable onDone;
    private final float dp;
    private final Paint dim = new Paint();
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bolt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boltEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path boltPath = new Path();
    private float t;   // ms since start

    public ChargeView(Context c, int level, Runnable onDone) {
        super(c);
        this.level = Math.max(1, Math.min(100, level));
        this.onDone = onDone;
        dp = getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null);   // BlurMaskFilter needs a software layer

        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(3.5f * dp);
        fill.setColor(GREEN);
        glow.setColor(GREEN);
        glow.setMaskFilter(new BlurMaskFilter(18 * dp, BlurMaskFilter.Blur.NORMAL));
        bolt.setColor(Color.WHITE);
        boltEdge.setColor(Color.BLACK);
        boltEdge.setStyle(Paint.Style.STROKE);
        boltEdge.setStrokeJoin(Paint.Join.ROUND);
        boltEdge.setStrokeWidth(2.5f * dp);
        big.setColor(Color.WHITE);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        small.setColor(Color.WHITE);
        small.setTextAlign(Paint.Align.CENTER);
        small.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));

        ValueAnimator a = ValueAnimator.ofFloat(0, TOTAL).setDuration(TOTAL);
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator v) {
                t = (float) v.getAnimatedValue();
                invalidate();
            }
        });
        a.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator x) { onDone.run(); }
        });
        a.start();
    }

    /** Lightning bolt filling the box (x, y, w, h); shared with the status-bar icon. */
    static void bolt(Path p, float x, float y, float w, float h) {
        p.reset();
        p.moveTo(x + 0.62f * w, y);
        p.lineTo(x + 0.04f * w, y + 0.58f * h);
        p.lineTo(x + 0.46f * w, y + 0.58f * h);
        p.lineTo(x + 0.34f * w, y + h);
        p.lineTo(x + 0.96f * w, y + 0.40f * h);
        p.lineTo(x + 0.54f * w, y + 0.40f * h);
        p.close();
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float easeOut(float u) { u = clamp01(u); return 1 - (1 - u) * (1 - u) * (1 - u); }
    private static float smooth(float u) { u = clamp01(u); return u * u * (3 - 2 * u); }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        // timeline: fade in 0-300, fill 200-1300, hold, fade out 2300-2800
        float in = easeOut(t / 300f);
        float out = smooth((t - 2300f) / 500f);
        float alpha = in * (1 - out);
        float scale = 0.92f + 0.08f * in + 0.04f * out;
        float frac = level / 100f * easeOut((t - 200f) / 1100f);

        dim.setColor(Color.argb(Math.round(170 * alpha), 0, 0, 0));
        c.drawRect(0, 0, w, h, dim);

        float bw = Math.min(w * 0.46f, 360 * dp), bh = bw * 0.46f;
        float cx = w / 2f, cy = h * 0.43f;
        c.save();
        c.scale(scale, scale, cx, cy);

        // body outline and terminal cap
        float rad = bh * 0.26f;
        r.set(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2);
        outline.setColor(Color.argb(Math.round(230 * alpha), 255, 255, 255));
        c.drawRoundRect(r, rad, rad, outline);
        float capW = bw * 0.045f, capH = bh * 0.34f, gap = 3.5f * dp;
        RectF cap = new RectF(r.right + gap, cy - capH / 2, r.right + gap + capW, cy + capH / 2);
        fill.setColor(Color.argb(Math.round(230 * alpha), 255, 255, 255));
        c.drawRoundRect(cap, capW, capW, fill);

        // green level, with a soft glow behind it
        float inset = outline.getStrokeWidth() + 4 * dp;
        RectF inner = new RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset);
        if (frac > 0) {
            RectF lv = new RectF(inner.left, inner.top, inner.left + inner.width() * frac, inner.bottom);
            float ir = Math.min(rad - inset / 2, lv.width() / 2);
            glow.setAlpha(Math.round(120 * alpha));
            c.drawRoundRect(lv, ir, ir, glow);
            fill.setColor(GREEN);
            fill.setAlpha(Math.round(255 * alpha));
            c.drawRoundRect(lv, ir, ir, fill);
        }

        // lightning bolt centered in the body
        float bhgt = bh * 0.62f, bwid = bhgt * 0.56f;
        bolt(boltPath, cx - bwid / 2, cy - bhgt / 2, bwid, bhgt);
        boltEdge.setAlpha(Math.round(90 * alpha));
        c.drawPath(boltPath, boltEdge);
        bolt.setAlpha(Math.round(255 * alpha));
        c.drawPath(boltPath, bolt);

        // percentage counting up with the fill, and a small caption
        big.setTextSize(bh * 0.52f);
        big.setAlpha(Math.round(255 * alpha));
        c.drawText(Math.round(frac * 100) + "%", cx, r.bottom + bh * 0.95f, big);
        small.setTextSize(bh * 0.20f);
        small.setAlpha(Math.round(180 * alpha));
        c.drawText("Charging", cx, r.bottom + bh * 1.35f, small);
        c.restore();
    }
}
