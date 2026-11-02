package it.breachato.ui;

import android.animation.TimeInterpolator;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared look for the custom apps: near-black base, frosted dark glass cards with hairline
 * borders, lavender accent, Geist / Geist Mono, dot grid and the site's ease curve.
 * Android 11 has no live backdrop blur, so "glass" sits on a pre-blurred copy of the wallpaper.
 */
public final class Glass {
    public static final int BG = 0xFF070707;
    public static final int CARD = 0xC70E0E0F;        // rgba(14,14,15,.78)
    public static final int FILL = 0x0FFFFFFF;        // glass key / chip fill
    public static final int FILL_PRESSED = 0x29FFFFFF;
    public static final int LINE = 0x14FFFFFF;        // rgba(255,255,255,.08)
    public static final int LINE2 = 0x29FFFFFF;       // rgba(255,255,255,.16)
    public static final int FG = 0xFFEDEDED;
    public static final int MUT = 0xFF8D8D8D;
    public static final int DIM = 0xFF555555;
    public static final int ACC = 0xFFA594FF;
    public static final int OK = 0xFF6EE7B7;
    public static final int ERR = 0xFFFF8A8A;
    /** cubic-bezier(.16,1,.3,1) */
    public static final TimeInterpolator EASE = new PathInterpolator(0.16f, 1f, 0.3f, 1f);

    private static final Map<String, Typeface> FONTS = new HashMap<>();
    private static Bitmap blurred;

    private Glass() { }

    public static float dp(Context c, float v) { return v * c.getResources().getDisplayMetrics().density; }

    public static int px(Context c, float v) { return Math.round(dp(c, v)); }

    public static Typeface sans(Context c, int weight) { return font(c, "fonts/geist.ttf", weight); }

    public static Typeface mono(Context c, int weight) { return font(c, "fonts/geistmono.ttf", weight); }

    private static Typeface font(Context c, String path, int weight) {
        String key = path + weight;
        Typeface t = FONTS.get(key);
        if (t == null) {
            try {
                t = new Typeface.Builder(c.getAssets(), path)
                        .setFontVariationSettings("'wght' " + weight).build();
            } catch (Exception e) {
                t = Typeface.create("sans-serif", Typeface.NORMAL);
            }
            FONTS.put(key, t);
        }
        return t;
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Small uppercase monospace label, letter-spaced, like the site's captions. */
    public static TextView label(Context c, String s) {
        TextView t = text(c, s.toUpperCase(Locale.ITALIAN), 11, MUT, mono(c, 450));
        t.setLetterSpacing(0.14f);
        return t;
    }

    public static GradientDrawable card(Context c, float radiusDp, int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(Math.max(1, px(c, 1)), stroke);
        return g;
    }

    public static GradientDrawable circle(Context c, int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        g.setStroke(Math.max(1, px(c, 1)), stroke);
        return g;
    }

    /** Frosted, darkened wallpaper with the dot grid on top; plain base color if unavailable. */
    public static Drawable backdrop(Context c, int scrim) {
        Bitmap b = blurredWallpaper(c);
        Drawable base = b != null ? new BitmapDrawable(c.getResources(), b) : new ColorDrawable(BG);
        if (base instanceof BitmapDrawable) ((BitmapDrawable) base).setFilterBitmap(true);
        return new LayerDrawable(new Drawable[] {base, new ColorDrawable(scrim), new Dots(c)});
    }

    public static synchronized Bitmap blurredWallpaper(Context c) {
        if (blurred != null) return blurred;
        try {
            Drawable d = WallpaperManager.getInstance(c).getDrawable();
            if (d == null) return null;
            int w = 96, h = Math.max(1, Math.round(w * (float) d.getIntrinsicHeight() / Math.max(1, d.getIntrinsicWidth())));
            Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, w, h);
            d.draw(new Canvas(small));
            for (int i = 0; i < 3; i++) boxBlur(small, 4);
            blurred = small;
        } catch (Exception e) {
            blurred = null;
        }
        return blurred;
    }

    /** Separable box blur in place; three passes approximate a gaussian. */
    private static void boxBlur(Bitmap b, int r) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h], tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        pass(px, tmp, w, h, r, true);
        pass(tmp, px, w, h, r, false);
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static void pass(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int len = horizontal ? w : h, lines = horizontal ? h : w;
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < len; i++) {
                int a = 0, rr = 0, g = 0, bb = 0, n = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.min(len - 1, Math.max(0, i + k));
                    int p = horizontal ? in[l * w + j] : in[j * w + l];
                    a += p >>> 24; rr += (p >> 16) & 255; g += (p >> 8) & 255; bb += p & 255; n++;
                }
                int v = ((a / n) << 24) | ((rr / n) << 16) | ((g / n) << 8) | (bb / n);
                if (horizontal) out[l * w + i] = v; else out[i * w + l] = v;
            }
        }
    }

    /** Press feedback: quick scale down, eased spring back. */
    public static void pressable(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(90).setInterpolator(EASE).start();
                } else if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(EASE).start();
                }
                return false;
            }
        });
    }

    /** Faint dot grid, as on the site's background. */
    public static final class Dots extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float step, r;

        public Dots(Context c) {
            p.setColor(Color.WHITE);
            p.setAlpha(16);
            step = dp(c, 22);
            r = dp(c, 0.9f);
        }

        @Override public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height();
            for (float y = step / 2; y < h; y += step) {
                for (float x = step / 2; x < w; x += step) c.drawCircle(x, y, r, p);
            }
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
