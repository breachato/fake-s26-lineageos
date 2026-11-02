package it.breachato.home;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import it.breachato.ui.Glass;

/**
 * Icon-grid home: pages of rounded icons with labels, page dots, a search pill and a frosted
 * dock. Pull down (or tap the pill) to search; long press an icon for dock / info / uninstall.
 */
public class HomeActivity extends Activity {
    private static final int COLS = 4, ROWS = 6, DOCK = 4;
    private static final String[] DEFAULT_DOCK = {
            "com.google.android.dialer", "com.android.chrome", "com.google.android.apps.messaging",
            "org.lineageos.snap", "com.android.camera2"};

    static final class App {
        final ComponentName cn;
        final String label, key;
        final Drawable icon;
        final boolean system;

        App(ComponentName cn, String label, Drawable icon, boolean system) {
            this.cn = cn;
            this.label = label;
            this.icon = icon;
            this.system = system;
            key = Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ITALIAN);
        }
    }

    private SharedPreferences prefs;
    private final List<App> apps = new ArrayList<>();
    private Pager pager;
    private LinearLayout dots, dock;
    private FrameLayout search;
    private EditText query;
    private GridLayout results;
    private int iconPx;

    private final BroadcastReceiver pkgChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { Icons.clear(); loadApps(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        iconPx = Glass.px(this, 60);
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        setContentView(buildUi());
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgChanged, f);
        loadApps();
    }

    @Override protected void onDestroy() {
        unregisterReceiver(pkgChanged);
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (search.getVisibility() == View.VISIBLE) closeSearch();
        else pager.snapTo(0, true);          // Home pressed: back to the first page
    }

    @Override public void onBackPressed() { if (search.getVisibility() == View.VISIBLE) closeSearch(); }

    // --- data ---------------------------------------------------------------------------
    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        apps.clear();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            apps.add(new App(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                    ri.loadLabel(pm).toString(), ri.loadIcon(pm), (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0));
        }
        final Collator col = Collator.getInstance(Locale.ITALIAN);
        Collections.sort(apps, new Comparator<App>() {
            @Override public int compare(App a, App b) { return col.compare(a.key, b.key); }
        });
        if (!prefs.contains("dock")) {
            List<String> d = new ArrayList<>();
            for (String pkg : DEFAULT_DOCK) {
                if (d.size() == DOCK) break;
                for (App a : apps) if (a.cn.getPackageName().equals(pkg)) { d.add(a.cn.flattenToString()); break; }
            }
            prefs.edit().putString("dock", TextUtils.join("\n", d)).apply();
        }
        render();
    }

    private List<String> dockList() {
        String s = prefs.getString("dock", "");
        return s.isEmpty() ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(s.split("\n")));
    }

    private void render() {
        List<String> inDock = dockList();
        List<App> grid = new ArrayList<>();
        dock.removeAllViews();
        for (String flat : inDock) {
            for (App a : apps) if (a.cn.flattenToString().equals(flat)) { dock.addView(icon(a, false)); break; }
        }
        for (App a : apps) if (!inDock.contains(a.cn.flattenToString())) grid.add(a);

        final int keep = pager.page();       // stay on the same page after a re-render
        pager.removeAllViews();
        int perPage = COLS * ROWS;
        int pages = Math.max(1, (grid.size() + perPage - 1) / perPage);
        for (int p = 0; p < pages; p++) {
            GridLayout g = new GridLayout(this);
            g.setColumnCount(COLS);
            g.setPadding(Glass.px(this, 14), Glass.px(this, 56), Glass.px(this, 14), 0);
            for (int i = p * perPage; i < Math.min(grid.size(), (p + 1) * perPage); i++) {
                View cell = icon(grid.get(i), true);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                        GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
                lp.width = 0;
                lp.setMargins(0, 0, 0, Glass.px(this, 18));
                g.addView(cell, lp);
            }
            pager.addView(g, new FrameLayout.LayoutParams(-1, -1));
        }
        dots.removeAllViews();
        for (int p = 0; p < pages; p++) {
            View d = new View(this);
            d.setBackground(Glass.circle(this, Color.WHITE, 0x00000000));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Glass.px(this, 7), Glass.px(this, 7));
            lp.setMargins(Glass.px(this, 4), 0, Glass.px(this, 4), 0);
            dots.addView(d, lp);
        }
        dots.setVisibility(pages > 1 ? View.VISIBLE : View.INVISIBLE);
        pager.post(new Runnable() {
            @Override public void run() {
                pager.snapTo(Math.min(keep, pager.getChildCount() - 1), false);
                markDots(pager.page());
            }
        });
    }

    private void markDots(int page) {
        for (int i = 0; i < dots.getChildCount(); i++) dots.getChildAt(i).setAlpha(i == page ? 1f : 0.35f);
    }

    // --- actions ------------------------------------------------------------------------
    private void launch(App a, View from) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        Bundle opts = from == null ? null : ActivityOptions.makeScaleUpAnimation(
                from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
        try {
            startActivity(i, opts);
        } catch (Exception ignored) { }
    }

    private void menu(final App a, View anchor) {
        final Context c = this;
        final List<String> d = dockList();
        final String flat = a.cn.flattenToString();
        final boolean docked = d.contains(flat);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Glass.card(c, 16, 0xF21C1C1E, Glass.LINE2));
        box.setPadding(0, Glass.px(c, 4), 0, Glass.px(c, 4));
        final PopupWindow pw = new PopupWindow(box, Glass.px(c, 240), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setElevation(Glass.dp(c, 24));
        if (docked || d.size() < DOCK) {
            box.addView(menuItem(c, docked ? "Remove from Dock" : "Add to Dock", new Runnable() {
                @Override public void run() {
                    if (docked) d.remove(flat); else d.add(flat);
                    prefs.edit().putString("dock", TextUtils.join("\n", d)).apply();
                    render();
                }
            }, pw));
        }
        box.addView(menuItem(c, "App info", new Runnable() {
            @Override public void run() {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", a.cn.getPackageName(), null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }, pw));
        if (!a.system) {
            TextView del = (TextView) menuItem(c, "Uninstall", new Runnable() {
                @Override public void run() {
                    startActivity(new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", a.cn.getPackageName(), null)));
                }
            }, pw);
            del.setTextColor(Glass.ERR);
            box.addView(del);
        }
        box.setAlpha(0f);
        box.setScaleX(0.9f);
        box.setScaleY(0.9f);
        pw.showAsDropDown(anchor, 0, Glass.px(c, 6));
        box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(Glass.EASE).start();
    }

    private View menuItem(Context c, String s, final Runnable r, final PopupWindow pw) {
        TextView t = Glass.text(c, s, 16, Glass.FG, Glass.sans(c, 450));
        t.setPadding(Glass.px(c, 18), Glass.px(c, 13), Glass.px(c, 18), Glass.px(c, 13));
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pw.dismiss(); r.run(); }
        });
        return t;
    }

    private void openSearch() {
        if (search.getVisibility() == View.VISIBLE) return;
        search.setVisibility(View.VISIBLE);
        search.setAlpha(0f);
        search.setTranslationY(-Glass.dp(this, 40));
        search.animate().alpha(1f).translationY(0).setDuration(380).setInterpolator(Glass.EASE).start();
        query.setText("");
        fillResults("");
        query.requestFocus();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.showSoftInput(query, InputMethodManager.SHOW_IMPLICIT);
    }

    private void closeSearch() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(query.getWindowToken(), 0);
        search.animate().alpha(0f).translationY(-Glass.dp(this, 30)).setDuration(260).setInterpolator(Glass.EASE)
                .withEndAction(new Runnable() { @Override public void run() { search.setVisibility(View.GONE); } }).start();
    }

    private void fillResults(String q) {
        String key = Normalizer.normalize(q, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ITALIAN).trim();
        results.removeAllViews();
        int n = 0;
        for (App a : apps) {
            if (!key.isEmpty() && !a.key.contains(key)) continue;
            View cell = icon(a, true);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.setMargins(0, 0, 0, Glass.px(this, 18));
            results.addView(cell, lp);
            if (++n >= (key.isEmpty() ? 8 : 40)) break;
        }
    }

    // --- UI -----------------------------------------------------------------------------
    private View icon(final App a, boolean label) {
        Context c = this;
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(Icons.get(c, a.cn, a.icon, iconPx));
        iv.setElevation(Glass.dp(c, 3));
        cell.addView(iv, new LinearLayout.LayoutParams(iconPx, iconPx));
        if (label) {
            TextView t = Glass.text(c, a.label, 11.5f, Color.WHITE, Glass.sans(c, 450));
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setShadowLayer(Glass.dp(c, 3), 0, Glass.dp(c, 0.5f), 0x99000000);
            t.setPadding(Glass.px(c, 4), Glass.px(c, 6), Glass.px(c, 4), 0);
            cell.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        Glass.pressable(cell);
        cell.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { launch(a, v); }
        });
        cell.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                menu(a, v);
                return true;
            }
        });
        return cell;
    }

    private View buildUi() {
        final Context c = this;
        FrameLayout root = new FrameLayout(c);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        pager = new Pager(c);
        pager.setListener(new Pager.Listener() {
            @Override public void onPage(int p) { markDots(p); }
            @Override public void onPullDown() { openSearch(); }
        });
        col.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1f));

        dots = new LinearLayout(c);
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
        dl.bottomMargin = Glass.px(c, 10);
        col.addView(dots, dl);

        TextView pill = Glass.text(c, "⌕  Search", 13, 0xE6FFFFFF, Glass.sans(c, 500));
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(Glass.px(c, 16), Glass.px(c, 7), Glass.px(c, 16), Glass.px(c, 7));
        pill.setBackground(Glass.card(c, 16, 0x40FFFFFF, 0x1FFFFFFF));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
        pl.gravity = Gravity.CENTER_HORIZONTAL;
        pl.bottomMargin = Glass.px(c, 14);
        Glass.pressable(pill);
        pill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openSearch(); }
        });
        col.addView(pill, pl);

        dock = new LinearLayout(c);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setBackground(Glass.card(c, 32, 0x38FFFFFF, 0x24FFFFFF));
        dock.setPadding(Glass.px(c, 10), Glass.px(c, 14), Glass.px(c, 10), Glass.px(c, 14));
        LinearLayout.LayoutParams dk = new LinearLayout.LayoutParams(-1, -2);
        dk.setMargins(Glass.px(c, 12), 0, Glass.px(c, 12), Glass.px(c, 30));
        col.addView(dock, dk);
        dock.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override public void onChildViewAdded(View parent, View child) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
                child.setLayoutParams(lp);
            }
            @Override public void onChildViewRemoved(View parent, View child) { }
        });
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        root.addView(buildSearch(c), new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    private View buildSearch(final Context c) {
        search = new FrameLayout(c);
        search.setBackground(Glass.backdrop(c, 0xB3070707));
        search.setVisibility(View.GONE);
        search.setClickable(true);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Glass.px(c, 16), Glass.px(c, 52), Glass.px(c, 16), 0);

        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        query = new EditText(c);
        query.setHint("Search");
        query.setHintTextColor(0x99FFFFFF);
        query.setTextColor(Color.WHITE);
        query.setTextSize(17);
        query.setTypeface(Glass.sans(c, 450));
        query.setSingleLine(true);
        query.setImeOptions(EditorInfo.IME_ACTION_GO);
        query.setBackground(Glass.card(c, 14, 0x33FFFFFF, 0x1FFFFFFF));
        query.setPadding(Glass.px(c, 16), Glass.px(c, 11), Glass.px(c, 16), Glass.px(c, 11));
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { fillResults(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        query.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                if (results.getChildCount() > 0) results.getChildAt(0).performClick();
                return true;
            }
        });
        bar.addView(query, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView cancel = Glass.text(c, "Cancel", 16, Glass.ACC, Glass.sans(c, 450));
        cancel.setPadding(Glass.px(c, 14), Glass.px(c, 8), Glass.px(c, 4), Glass.px(c, 8));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeSearch(); }
        });
        bar.addView(cancel);
        col.addView(bar);

        TextView sub = Glass.label(c, "Apps");
        sub.setPadding(Glass.px(c, 4), Glass.px(c, 22), 0, Glass.px(c, 12));
        col.addView(sub);
        results = new GridLayout(c);
        results.setColumnCount(COLS);
        ScrollView sv = new ScrollView(c);
        sv.addView(results);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        search.addView(col, new FrameLayout.LayoutParams(-1, -1));
        return search;
    }
}
