package io.github.ptrinh.tvremote;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * The 4-digit PIN that unlocks the TV (and shows the pairing code again), set from the phone (set_pin).
 * Kept as a salted SHA-256 ("salt:hash", hex) in the add-on's private preferences; nothing leaves the TV.
 * Five wrong PINs in a row make the pad wait 30 seconds. Ported from the launcher fork's AppLock.
 */
final class Pin {
    static final String PREFS = "tvremote";
    static final String PREF_PIN = "pin";
    static final int LENGTH = 4;
    /** Wrong PINs in a row before the pad makes you wait. */
    static final int MAX_TRIES = 5;
    static final long WAIT_MS = 30_000;

    private static int fails;
    private static long waitUntil;

    private Pin() {
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean hasPin(SharedPreferences p) {
        return parse(p.getString(PREF_PIN, "")) != null;
    }

    static void setPin(SharedPreferences p, String pin) {
        p.edit().putString(PREF_PIN, store(pin, new SecureRandom())).apply();
    }

    static boolean checkPin(SharedPreferences p, String pin) {
        return matches(p.getString(PREF_PIN, ""), pin);
    }

    /**
     * One try on a pad: null when the PIN is right, else the message to show (wrong, or wait). Counts the
     * wrong ones across every pad, so closing and opening a pad doesn't reset the wait. Main thread.
     */
    static String attempt(Context ctx, String pin, long now) {
        if (now < waitUntil) return ctx.getString(R.string.pin_wait, (int) ((waitUntil - now + 999) / 1000));
        if (checkPin(prefs(ctx), pin)) {
            fails = 0;
            return null;
        }
        if (++fails >= MAX_TRIES) {
            fails = 0;
            waitUntil = now + WAIT_MS;
            return ctx.getString(R.string.pin_wait, (int) (WAIT_MS / 1000));
        }
        return ctx.getString(R.string.pin_wrong);
    }

    /** "salt:hash", both hex. */
    static String store(String pin, SecureRandom random) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        String s = hex(salt);
        return s + ":" + hash(s, pin);
    }

    static boolean matches(String stored, String pin) {
        String[] parts = parse(stored);
        if (parts == null || !valid(pin)) return false;
        return MessageDigest.isEqual(parts[1].getBytes(StandardCharsets.US_ASCII),
                hash(parts[0], pin).getBytes(StandardCharsets.US_ASCII));
    }

    static boolean valid(String pin) {
        if (pin == null || pin.length() != LENGTH) return false;
        for (int i = 0; i < pin.length(); i++) if (pin.charAt(i) < '0' || pin.charAt(i) > '9') return false;
        return true;
    }

    private static String[] parse(String stored) {
        if (stored == null) return null;
        String[] parts = stored.split(":");
        return parts.length == 2 && parts[0].length() == 32 && parts[1].length() == 64 ? parts : null;
    }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest((salt + pin).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // every Android has SHA-256
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }
}
