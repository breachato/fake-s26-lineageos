package it.breachato.lock;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.security.SecureRandom;

/** PIN kept only as a salted SHA-256 hash, plus the wrong-attempt lockout. */
final class PinStore {
    private static final int MAX_TRIES = 5;
    private static final long LOCKOUT_MS = 30_000;

    private final SharedPreferences p;

    PinStore(Context c) {
        p = c.createDeviceProtectedStorageContext().getSharedPreferences("pin", Context.MODE_PRIVATE);
    }

    boolean hasPin() { return p.contains("hash"); }

    /** Digits in the PIN, so entry can complete on the last one; 0 when unknown. */
    int length() { return p.getInt("len", 0); }

    void setPin(String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String s = hex(salt);
        p.edit().putString("salt", s).putString("hash", hash(s, pin)).putInt("len", pin.length())
                .putInt("fails", 0).apply();
    }

    /** True if correct; counts failures and starts the lockout after MAX_TRIES. */
    boolean check(String pin) {
        boolean ok = MessageDigest.isEqual(hash(p.getString("salt", ""), pin).getBytes(),
                p.getString("hash", "").getBytes());
        if (ok) {
            p.edit().putInt("fails", 0).apply();
        } else {
            int f = p.getInt("fails", 0) + 1;
            SharedPreferences.Editor e = p.edit().putInt("fails", f);
            if (f >= MAX_TRIES) e.putLong("until", System.currentTimeMillis() + LOCKOUT_MS).putInt("fails", 0);
            e.apply();
        }
        return ok;
    }

    /** Milliseconds left in the lockout, 0 when PIN entry is allowed. */
    long lockedFor() {
        return Math.max(0, p.getLong("until", 0) - System.currentTimeMillis());
    }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes("UTF-8"));
            return hex(md.digest(pin.getBytes("UTF-8")));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
