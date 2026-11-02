package it.breachato.lock;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Lock on/off and the set of protected apps (device-protected storage). */
final class LockPrefs {
    static final Set<String> DEFAULT_APPS = new HashSet<>(Arrays.asList(
            "org.fdroid.fdroid", "com.android.chrome", "com.android.documentsui"));

    private final SharedPreferences p;

    LockPrefs(Context c) {
        p = c.createDeviceProtectedStorageContext().getSharedPreferences("lock", Context.MODE_PRIVATE);
    }

    boolean enabled() { return p.getBoolean("enabled", true); }

    void setEnabled(boolean on) { p.edit().putBoolean("enabled", on).apply(); }

    Set<String> apps() { return new HashSet<>(p.getStringSet("apps", DEFAULT_APPS)); }

    void setLocked(String pkg, boolean locked) {
        Set<String> s = apps();
        if (locked) s.add(pkg); else s.remove(pkg);
        p.edit().putStringSet("apps", s).apply();
    }

    void listen(SharedPreferences.OnSharedPreferenceChangeListener l) { p.registerOnSharedPreferenceChangeListener(l); }
}
