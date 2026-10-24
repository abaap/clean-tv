package io.github.ptrinh.tvremote;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;

/**
 * A message from the phone, large in the middle of the screen over whatever is playing (dimmed behind),
 * until someone presses OK or Back, or the phone clears it. Started by the relay connection from the
 * background, which Android allows because the add-on may display over other apps.
 */
public class BannerActivity extends Activity {
    private static final String TAG = "TvRemote";
    static final String EXTRA_TEXT = "text";

    /** In front right now; the lock watchdog lets a message sit over the lock screen. */
    static volatile boolean visible;
    private static BannerActivity current;

    private TextView body, hint;
    private LinearLayout card;

    static void show(Context ctx, String text) {
        if (text == null || text.trim().isEmpty()) return;
        try {
            ctx.startActivity(new Intent(ctx, BannerActivity.class)
                    .putExtra(EXTRA_TEXT, text.trim())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (Exception e) {
            Log.w(TAG, "could not show the message: " + e);
        }
    }

    /** The phone cleared it. Main thread. */
    static void clear() {
        BannerActivity a = current;
        if (a != null) a.finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = this;
        float d = getResources().getDisplayMetrics().density;
        FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(0xB3000000);

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (48 * d);
        card.setPadding(pad, (int) (36 * d), pad, (int) (32 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF01C1C22);
        bg.setCornerRadius(32 * d);
        bg.setStroke((int) Math.max(1, d), 0x33FFFFFF);
        card.setBackground(bg);
        card.setElevation(24 * d);
        card.setMinimumWidth((int) (420 * d));

        TextView label = new TextView(this);
        label.setText(R.string.banner_title);
        label.setTextColor(0xFFFF9F0A);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        label.setLetterSpacing(0.12f);
        label.setAllCaps(true);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(label);

        body = new TextView(this);
        body.setTextColor(Color.WHITE);
        body.setLineSpacing(0, 1.12f);
        body.setGravity(Gravity.CENTER);
        body.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        body.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.78f) - 2 * pad);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = (int) (18 * d);
        bp.bottomMargin = (int) (26 * d);
        card.addView(body, bp);

        hint = new TextView(this);
        hint.setTextColor(0x99FFFFFF);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        card.addView(hint);

        scrim.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setContentView(scrim);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        bind(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        bind(intent);
    }

    private void bind(Intent intent) {
        String text = intent != null ? intent.getStringExtra(EXTRA_TEXT) : null;
        if (text == null || text.isEmpty()) {
            finish();
            return;
        }
        body.setText(text);
        // Large enough to read across the room; long messages step down so they still fit.
        int len = text.length();
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, len <= 40 ? 56 : len <= 120 ? 42 : len <= 250 ? 32 : 26);
        hint.setText(getString(R.string.banner_hint, DateFormat.getTimeFormat(this).format(new Date())));
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        card.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new OvershootInterpolator(1.1f)).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
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
        // The message takes every key; OK or Back closes it.
        int k = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_UP && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER || k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE)) {
            finish();
        }
        return true;
    }
}
