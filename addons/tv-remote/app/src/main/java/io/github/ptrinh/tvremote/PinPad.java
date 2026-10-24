package io.github.ptrinh.tvremote;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A PIN pad for the remote: a title, four dots, a status line and a 3 by 4 grid (1 to 9, delete, 0) for
 * the D-pad; the remote's number keys work too ({@link #key}). Once four digits are in, the listener
 * decides: {@link #fail} or done. Built in code, in the tvOS style of the rest of the add-on.
 */
final class PinPad {
    interface Listener {
        void entered(PinPad pad, String pin);
    }

    private static final String KEYS = "123456789<0";

    final LinearLayout view;
    private final TextView title, dots, status;
    private final GridLayout grid;
    private final Listener listener;
    private final StringBuilder digits = new StringBuilder();

    PinPad(Context ctx, String titleText, Listener listener) {
        this.listener = listener;
        float d = ctx.getResources().getDisplayMetrics().density;
        view = new LinearLayout(ctx);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        view.setPadding((int) (28 * d), (int) (22 * d), (int) (28 * d), (int) (20 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF01C1C22);
        bg.setCornerRadius(28 * d);
        bg.setStroke((int) Math.max(1, d), 0x26FFFFFF);
        view.setBackground(bg);

        title = text(ctx, titleText, 22, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.addView(title);
        dots = text(ctx, "", 26, Color.WHITE);
        view.addView(dots, margins(14 * d));
        status = text(ctx, "", 15, 0xFFFF8A80);
        status.setVisibility(View.GONE);
        view.addView(status, margins(6 * d));

        grid = new GridLayout(ctx);
        grid.setColumnCount(3);
        for (int i = 0; i < KEYS.length(); i++) {
            final char k = KEYS.charAt(i);
            TextView key = text(ctx, k == '<' ? "⌫" : String.valueOf(k), 22, Color.WHITE);
            key.setFocusable(true);
            key.setClickable(true);
            key.setBackground(keyBackground(d));
            key.setOnFocusChangeListener((v, has) -> ((TextView) v).setTextColor(has ? 0xFF0B0B0D : Color.WHITE));
            key.setOnClickListener(v -> press(k));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = (int) (76 * d);
            lp.height = (int) (52 * d);
            lp.setMargins((int) (5 * d), (int) (5 * d), (int) (5 * d), (int) (5 * d));
            grid.addView(key, lp);
        }
        view.addView(grid, margins(14 * d));
        drawDots();
    }

    /** Focus on 5, the middle key. */
    void focus() {
        grid.getChildAt(4).requestFocus();
    }

    /** The remote's number keys and delete; true when used. */
    boolean key(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int code = event.getKeyCode();
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            press((char) ('0' + code - KeyEvent.KEYCODE_0));
            return true;
        }
        if (code == KeyEvent.KEYCODE_DEL) {
            press('<');
            return true;
        }
        return false;
    }

    /** Clears the digits and says what went wrong. */
    void fail(String message) {
        digits.setLength(0);
        drawDots();
        status.setText(message);
        status.setVisibility(View.VISIBLE);
    }

    void reset() {
        digits.setLength(0);
        drawDots();
        status.setVisibility(View.GONE);
    }

    private void press(char k) {
        if (k == '<') {
            if (digits.length() > 0) digits.setLength(digits.length() - 1);
        } else if (digits.length() < Pin.LENGTH) {
            digits.append(k);
        }
        drawDots();
        if (digits.length() == Pin.LENGTH) listener.entered(this, digits.toString());
    }

    private void drawDots() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Pin.LENGTH; i++) {
            if (i > 0) sb.append("  ");
            sb.append(i < digits.length() ? '●' : '○');
        }
        dots.setText(sb);
    }

    private static TextView text(Context ctx, String s, int sp, int color) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private static LinearLayout.LayoutParams margins(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) top;
        return lp;
    }

    /** Glass key; white when focused, like a focused button on Apple TV. */
    static StateListDrawable keyBackground(float d) {
        GradientDrawable idle = new GradientDrawable();
        idle.setColor(0x1FFFFFFF);
        idle.setCornerRadius(16 * d);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0xFFF5F5F7);
        focused.setCornerRadius(16 * d);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{}, idle);
        return s;
    }
}
