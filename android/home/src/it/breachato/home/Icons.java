package it.breachato.home;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/** App icons cut to one rounded-square shape, so the grid looks uniform; cached per app. */
final class Icons {
    private static final Map<String, Drawable> CACHE = new HashMap<>();

    private Icons() { }

    static void clear() { CACHE.clear(); }

    static Drawable get(Context c, ComponentName cn, Drawable icon, int sizePx) {
        String key = cn.flattenToString() + "@" + sizePx;
        Drawable d = CACHE.get(key);
        if (d == null) {
            d = new BitmapDrawable(c.getResources(), shape(icon, sizePx));
            CACHE.put(key, d);
        }
        return d;
    }

    static Bitmap shape(Drawable icon, int s) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Path mask = new Path();
        float r = s * 0.225f;
        mask.addRoundRect(new RectF(0, 0, s, s), r, r, Path.Direction.CW);
        cv.clipPath(mask);
        if (icon instanceof AdaptiveIconDrawable) {
            // adaptive layers are 108dp with a 72dp safe zone: draw them 1.5x and centred
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
            int o = s / 4;
            for (Drawable layer : new Drawable[] {a.getBackground(), a.getForeground()}) {
                if (layer == null) continue;
                layer.setBounds(-o, -o, s + o, s + o);
                layer.draw(cv);
            }
        } else {
            // legacy square icon: light tile behind it, icon slightly inset
            cv.drawColor(0xFFF2F2F4);
            int i = Math.round(s * 0.12f);
            icon.setBounds(i, i, s - i, s - i);
            icon.draw(cv);
        }
        return b;
    }
}
