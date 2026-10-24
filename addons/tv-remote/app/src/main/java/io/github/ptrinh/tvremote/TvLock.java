package io.github.ptrinh.tvremote;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

/**
 * Lock TV from the phone remote: {@link LockActivity} ("This TV is locked") comes to the front and stays
 * there until someone types the PIN ({@link Pin}). Left for another app or Home while locked, it comes
 * back after a moment (a watchdog checks every 1.5 s). The lock timer locks the TV when its time is up and
 * counts down in a small pill over whatever is on screen (TYPE_APPLICATION_OVERLAY, with the permission
 * install.sh grants; without it, a toast at the start and in the last minutes). Both survive a restart:
 * the state is in the add-on's preferences, the timer as a wall-clock time. All on the main thread.
 */
final class TvLock {
    private static final String TAG = "TvRemoteLock";
    static final String PREF_LOCKED = "tv_locked";
    static final String PREF_LOCK_AT = "tv_lock_at";
    static final int MAX_TIMER_MIN = 12 * 60;
    /** How soon the lock screen comes back after something else took the front. */
    private static final long WATCH_MS = 1500;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static Context app;
    private static TextView pill;
    private static long lastToastMin = -1;
    private static final Runnable TICK = TvLock::tick;
    private static final Runnable WATCH = TvLock::watch;

    private TvLock() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return Pin.prefs(ctx);
    }

    static boolean locked(Context ctx) {
        return prefs(ctx).getBoolean(PREF_LOCKED, false) && Pin.hasPin(prefs(ctx));
    }

    /** Wall-clock time the timer locks the TV, 0 for none. */
    static long lockAt(Context ctx) {
        return prefs(ctx).getLong(PREF_LOCK_AT, 0);
    }

    /** Once per process: picks up a lock or a timer from before a restart. */
    static void start(Context ctx) {
        if (app != null) return;
        app = ctx.getApplicationContext();
        if (lockAt(app) > 0) main.post(TICK);
        if (locked(app)) main.post(WATCH);
    }

    /** Locks now. False without a PIN (there would be no way back). */
    static boolean lock(Context ctx) {
        start(ctx);
        SharedPreferences p = prefs(ctx);
        if (!Pin.hasPin(p)) return false;
        p.edit().putBoolean(PREF_LOCKED, true).putLong(PREF_LOCK_AT, 0).commit();
        RemoteRelay.event(null);
        main.post(() -> {
            removePill();
            main.removeCallbacks(TICK);
            main.removeCallbacks(WATCH);
            watch();
        });
        return true;
    }

    static void unlock(Context ctx) {
        start(ctx);
        prefs(ctx).edit().putBoolean(PREF_LOCKED, false).commit();
        RemoteRelay.event(null);
        main.post(() -> {
            main.removeCallbacks(WATCH);
            LockActivity.close();
        });
    }

    /** Starts (or restarts) the timer; minutes 1 to MAX_TIMER_MIN. False without a PIN. */
    static boolean startTimer(Context ctx, int minutes) {
        start(ctx);
        SharedPreferences p = prefs(ctx);
        if (!Pin.hasPin(p)) return false;
        p.edit().putLong(PREF_LOCK_AT, System.currentTimeMillis() + minutes * 60_000L).apply();
        RemoteRelay.event(null);
        lastToastMin = -1;
        main.post(() -> {
            main.removeCallbacks(TICK);
            tick();
        });
        return true;
    }

    static void cancelTimer(Context ctx) {
        start(ctx);
        prefs(ctx).edit().putLong(PREF_LOCK_AT, 0).apply();
        RemoteRelay.event(null);
        main.post(() -> {
            main.removeCallbacks(TICK);
            removePill();
        });
    }

    /** For the phone page: locked, PIN set, and the timer's end. */
    static JSONObject state(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            o.put("locked", locked(ctx)).put("hasPin", Pin.hasPin(prefs(ctx))).put("lockAt", lockAt(ctx));
        } catch (Exception ignored) {
            // Never fails for these types.
        }
        return o;
    }

    // ---- keeping the lock screen in front -----------------------------------------------------------

    /**
     * While locked: the lock screen is in front, or a message from the phone is (it closes back onto the
     * lock), or the screen is off; otherwise bring the lock screen back.
     */
    private static void watch() {
        if (app == null || !locked(app)) return;
        PowerManager pm = app.getSystemService(PowerManager.class);
        boolean screenOn = pm == null || pm.isInteractive();
        if (screenOn && !LockActivity.visible && !BannerActivity.visible) LockActivity.show(app);
        main.postDelayed(WATCH, WATCH_MS);
    }

    // ---- the countdown ------------------------------------------------------------------------------

    private static void tick() {
        if (app == null) return;
        long at = lockAt(app);
        if (at <= 0) {
            removePill();
            return;
        }
        long left = at - System.currentTimeMillis();
        if (left <= 0) {
            removePill();
            if (!lock(app)) cancelTimer(app);   // the PIN was removed meanwhile
            return;
        }
        long secs = (left + 999) / 1000;
        String text = "🔒 " + (secs >= 3600
                ? String.format("%d:%02d:%02d", secs / 3600, secs / 60 % 60, secs % 60)
                : String.format("%d:%02d", secs / 60, secs % 60));
        if (!showPill(text, secs <= 60)) {
            // No overlay permission: say so at the start and in the last minutes.
            long min = (secs + 59) / 60;
            if (lastToastMin < 0 || (min != lastToastMin && (min == 5 || min == 1))) {
                Toast.makeText(app, app.getString(R.string.lock_timer_toast, (int) min), Toast.LENGTH_LONG).show();
            }
            lastToastMin = min;
        }
        main.postDelayed(TICK, 1000 - System.currentTimeMillis() % 1000 + 5);
    }

    private static boolean showPill(String text, boolean soon) {
        if (!Settings.canDrawOverlays(app)) return false;
        try {
            if (pill == null) {
                float d = app.getResources().getDisplayMetrics().density;
                TextView v = new TextView(app);
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                v.setTextColor(Color.WHITE);
                v.setPadding((int) (18 * d), (int) (8 * d), (int) (18 * d), (int) (8 * d));
                v.setFontFeatureSettings("tnum");
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.END;
                lp.x = (int) (32 * d);
                lp.y = (int) (28 * d);
                app.getSystemService(WindowManager.class).addView(v, lp);
                pill = v;
            }
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(100);
            bg.setColor(soon ? 0xE6FF9F0A : 0xCC1C1C22);
            pill.setBackground(bg);
            pill.setTextColor(soon ? 0xFF1A1000 : Color.WHITE);
            pill.setText(text);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "no countdown overlay: " + e);
            pill = null;
            return false;
        }
    }

    private static void removePill() {
        if (pill == null) return;
        try {
            app.getSystemService(WindowManager.class).removeView(pill);
        } catch (Exception ignored) {
            // Already gone.
        }
        pill = null;
    }
}
