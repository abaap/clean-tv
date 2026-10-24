package io.github.ptrinh.tvremote;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * "This TV is locked" over everything, with the PIN pad. Every key stays here (Back does nothing); Home
 * leaves, and {@link TvLock}'s watchdog brings this back a moment later while the TV is still locked.
 * The right PIN unlocks, as does "unlock" from the phone.
 */
public class LockActivity extends Activity {
    private static final String TAG = "TvRemoteLock";

    /** In front right now (resumed). Main thread. */
    static volatile boolean visible;
    private static LockActivity current;

    private PinPad pad;

    /** Brings the lock screen to the front. Needs display over other apps when another app is in front. */
    static void show(Context ctx) {
        try {
            ctx.startActivity(new Intent(ctx, LockActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (Exception e) {
            Log.w(TAG, "could not show the lock: " + e);
        }
    }

    /** Unlocked: the lock screen goes. Main thread. */
    static void close() {
        LockActivity a = current;
        if (a != null) a.finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = this;
        float d = getResources().getDisplayMetrics().density;
        // Two columns: the message on the left, the PIN pad on the right. A TV screen is wide and short (540 dp),
        // so stacked one above the other the pad's last row (⌫ 0) fell below the bottom edge.
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(0xFF0B0B0F);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView icon = new TextView(this);
        icon.setText("🔒");
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 56);
        icon.setGravity(Gravity.CENTER);
        text.addView(icon, wrap());

        TextView title = new TextView(this);
        title.setText(R.string.lock_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams tp = wrap();
        tp.topMargin = (int) (12 * d);
        text.addView(title, tp);

        TextView sub = new TextView(this);
        sub.setText(R.string.lock_subtitle);
        sub.setTextColor(0xB3FFFFFF);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = wrap();
        sp.topMargin = (int) (8 * d);
        text.addView(sub, sp);
        v.addView(text, wrap());

        pad = new PinPad(this, getString(R.string.pin_unlock), (p, pin) -> {
            String why = Pin.attempt(this, pin, SystemClock.elapsedRealtime());
            if (why != null) {
                p.fail(why);
                return;
            }
            TvLock.unlock(this);
            finish();
        });
        LinearLayout.LayoutParams pp = wrap();
        pp.leftMargin = (int) (72 * d);
        pp.gravity = Gravity.CENTER_VERTICAL;
        v.addView(pad.view, pp);
        setContentView(v);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!TvLock.locked(this)) finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!TvLock.locked(this)) finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        if (!TvLock.locked(this)) {
            finish();
            return;
        }
        pad.reset();
        pad.focus();
    }

    @Override
    protected void onPause() {
        visible = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        visible = false;
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int k = event.getKeyCode();
        if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE) return true;
        if (pad.key(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        // Locked: Back stays here.
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
