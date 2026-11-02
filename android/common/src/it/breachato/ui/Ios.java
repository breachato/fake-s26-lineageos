package it.breachato.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Settings-style building blocks in a dark phone theme: large title, grouped inset cards,
 * rows with value / checkmark and hairline separators, small grey section headers and footers.
 */
public final class Ios {
    public static final int BG = 0xFF000000;
    public static final int GROUP = 0xFF1C1C1E;
    public static final int SEPARATOR = 0xFF38383A;
    public static final int LABEL = 0xFFFFFFFF;
    public static final int SECONDARY = 0xFF8E8E93;
    public static final int BLUE = 0xFF0A84FF;
    public static final int GREEN = 0xFF30D158;
    public static final int RED = 0xFFFF453A;

    private Ios() { }

    public static Typeface regular() { return Typeface.create("sans-serif", Typeface.NORMAL); }
    public static Typeface medium() { return Typeface.create("sans-serif-medium", Typeface.NORMAL); }
    public static Typeface light() { return Typeface.create("sans-serif-light", Typeface.NORMAL); }
    public static Typeface bold() { return Typeface.create("sans-serif", Typeface.BOLD); }

    public static TextView largeTitle(Context c, String s) {
        TextView t = Glass.text(c, s, 34, LABEL, bold());
        t.setPadding(Glass.px(c, 20), Glass.px(c, 4), Glass.px(c, 20), Glass.px(c, 10));
        return t;
    }

    public static TextView header(Context c, String s) {
        TextView t = Glass.text(c, s.toUpperCase(Locale.ITALIAN), 12.5f, SECONDARY, regular());
        t.setLetterSpacing(0.03f);
        t.setPadding(Glass.px(c, 36), Glass.px(c, 26), Glass.px(c, 20), Glass.px(c, 7));
        return t;
    }

    public static TextView footer(Context c, String s) {
        TextView t = Glass.text(c, s, 12.5f, SECONDARY, regular());
        t.setLineSpacing(0, 1.15f);
        t.setPadding(Glass.px(c, 36), Glass.px(c, 7), Glass.px(c, 28), 0);
        return t;
    }

    /** Inset rounded group; add rows with {@link #add}. */
    public static LinearLayout group(Context c) {
        LinearLayout g = new LinearLayout(c);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(Glass.card(c, 12, GROUP, GROUP));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(Glass.px(c, 16), 0, Glass.px(c, 16), 0);
        g.setLayoutParams(lp);
        g.setClipToOutline(true);
        return g;
    }

    /** Adds a row to a group, with a separator inset from the left above every row but the first. */
    public static void add(LinearLayout group, View row) {
        Context c = group.getContext();
        if (group.getChildCount() > 0) {
            View sep = new View(c);
            sep.setBackgroundColor(SEPARATOR);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, Glass.px(c, 0.5f)));
            lp.leftMargin = Glass.px(c, 16);
            group.addView(sep, lp);
        }
        group.addView(row);
    }

    /** Title on the left, optional value (grey) on the right. */
    public static LinearLayout row(Context c, String title, String value) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Glass.px(c, 46));
        r.setPadding(Glass.px(c, 16), Glass.px(c, 11), Glass.px(c, 16), Glass.px(c, 11));
        TextView t = Glass.text(c, title, 17, LABEL, regular());
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        if (value != null) {
            TextView v = Glass.text(c, value, 17, SECONDARY, regular());
            v.setTag("value");
            r.addView(v);
        }
        return r;
    }

    public static TextView value(LinearLayout row) { return (TextView) row.findViewWithTag("value"); }

    /** Pressed highlight like a table cell. */
    public static void tappable(View v) {
        v.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF), null,
                new android.graphics.drawable.ColorDrawable(0xFFFFFFFF)));
    }
}
