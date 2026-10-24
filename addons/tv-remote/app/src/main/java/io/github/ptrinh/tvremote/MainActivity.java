package io.github.ptrinh.tvremote;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The card's screen: the pairing QR code (the relay's phone page with #<TV id>.<phone key>), whether the
 * relay is online, New code (a new phone key: phones paired before stop working) and Hide code for when
 * guests are watching; showing it again asks for the PIN, if one is set. Below, what install.sh granted.
 * Opening it also starts {@link RemoteService}. In the tvOS style: black, a warm glow, glass buttons that
 * turn white when focused.
 */
public class MainActivity extends Activity {
    private static final String PREF_HIDDEN = "qr_hidden";
    private static final int ACCENT = 0xFFFF8A00;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private float d;
    private TextView status, hint, hideButton;
    private View statusDot;
    private ImageView qr;
    private TextView qrHidden;
    private FrameLayout qrCard;
    private LinearLayout checks;
    private String shownUrl;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            drawStatus();
            drawChecks();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        d = getResources().getDisplayMetrics().density;
        RemoteService.start(this);
        TvLock.start(this);
        RemoteRelay.start(this, Relay.url(this));

        FrameLayout root = new FrameLayout(this);
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setColors(new int[]{0x55FF8A00, 0x00000000});
        glow.setGradientRadius(700 * d);
        glow.setGradientCenter(0.15f, 0f);
        root.setBackground(glow);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (80 * d), (int) (48 * d), (int) (80 * d), (int) (48 * d));
        root.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = text(getString(R.string.app_name), 48, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        left.addView(title);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        statusRow.addView(statusDot, new LinearLayout.LayoutParams((int) (12 * d), (int) (12 * d)));
        status = text("", 20, 0xCCFFFFFF);
        LinearLayout.LayoutParams sp = wrap();
        sp.leftMargin = (int) (10 * d);
        statusRow.addView(status, sp);
        left.addView(statusRow, top(wrap(), 10));

        hint = text("", 20, 0x99FFFFFF);
        hint.setMaxWidth((int) (560 * d));
        hint.setLineSpacing(0, 1.15f);
        left.addView(hint, top(wrap(), 24));

        LinearLayout buttons = new LinearLayout(this);
        TextView newCode = button(getString(R.string.new_code));
        newCode.setOnClickListener(v -> {
            RemoteRelay.newPhoneKey(this);
            shownUrl = null;
            drawQr();
            Toast.makeText(this, R.string.new_code_done, Toast.LENGTH_LONG).show();
        });
        buttons.addView(newCode);
        hideButton = button("");
        hideButton.setOnClickListener(v -> toggleHidden());
        LinearLayout.LayoutParams hp = wrap();
        hp.leftMargin = (int) (14 * d);
        buttons.addView(hideButton, hp);
        left.addView(buttons, top(wrap(), 30));

        checks = new LinearLayout(this);
        checks.setOrientation(LinearLayout.VERTICAL);
        left.addView(checks, top(wrap(), 34));

        qrCard = new FrameLayout(this);
        GradientDrawable white = new GradientDrawable();
        white.setColor(Color.WHITE);
        white.setCornerRadius(28 * d);
        qrCard.setBackground(white);
        qrCard.setPadding((int) (14 * d), (int) (14 * d), (int) (14 * d), (int) (14 * d));
        qr = new ImageView(this);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrCard.addView(qr, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        qrHidden = text("🔒", 64, Color.BLACK);
        qrHidden.setGravity(Gravity.CENTER);
        qrCard.addView(qrHidden, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int side = (int) (340 * d);
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(side, side);
        qp.leftMargin = (int) (48 * d);
        row.addView(qrCard, qp);

        setContentView(root);
        if (Relay.url(this).isEmpty()) {
            hint.setText(R.string.card_no_relay);
            qrCard.setVisibility(View.GONE);
            buttons.setVisibility(View.GONE);
            statusRow.setVisibility(View.GONE);
        } else {
            drawQr();
            newCode.requestFocus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    // ---- the code -----------------------------------------------------------------------------------

    private boolean hidden() {
        return Pin.prefs(this).getBoolean(PREF_HIDDEN, false);
    }

    private void toggleHidden() {
        if (!hidden()) {
            Pin.prefs(this).edit().putBoolean(PREF_HIDDEN, true).apply();
            drawQr();
            return;
        }
        Runnable show = () -> {
            Pin.prefs(this).edit().putBoolean(PREF_HIDDEN, false).apply();
            drawQr();
        };
        if (!Pin.hasPin(Pin.prefs(this))) {
            show.run();
            return;
        }
        askPin(getString(R.string.pin_show_code), show);
    }

    private void drawQr() {
        if (Relay.url(this).isEmpty()) return;
        boolean h = hidden();
        hideButton.setText(h ? R.string.show_code : R.string.hide_code);
        hint.setText(h ? R.string.card_hidden_hint : R.string.card_hint);
        qr.setVisibility(h ? View.INVISIBLE : View.VISIBLE);
        qrHidden.setVisibility(h ? View.VISIBLE : View.GONE);
        if (h) {
            qr.setImageBitmap(null);
            shownUrl = null;
            return;
        }
        String url = RemoteRelay.phoneUrl(this);
        if (url != null && !url.equals(shownUrl)) {
            qr.setImageBitmap(qrBitmap(url));
            shownUrl = url;
        }
    }

    /** A QR code of {@code text}, black on white with the four-module quiet zone, sharp when scaled. */
    static Bitmap qrBitmap(String text) {
        boolean[][] m = Qr.encode(text.getBytes(StandardCharsets.UTF_8));
        if (m == null) return null;
        int n = m.length + 8;
        int[] px = new int[n * n];
        Arrays.fill(px, Color.WHITE);
        for (int y = 0; y < m.length; y++) {
            for (int x = 0; x < m.length; x++) if (m[y][x]) px[(y + 4) * n + x + 4] = Color.BLACK;
        }
        Bitmap small = Bitmap.createBitmap(px, n, n, Bitmap.Config.RGB_565);
        return Bitmap.createScaledBitmap(small, n * 8, n * 8, false);
    }

    private void askPin(String title, Runnable then) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        PinPad pad = new PinPad(this, title, (p, pin) -> {
            String why = Pin.attempt(this, pin, SystemClock.elapsedRealtime());
            if (why != null) {
                p.fail(why);
                return;
            }
            dialog.dismiss();
            then.run();
        });
        dialog.setContentView(pad.view);
        Window w = dialog.getWindow();
        if (w != null) w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.setOnKeyListener((di, keyCode, event) -> pad.key(event));
        dialog.show();
        pad.focus();
    }

    // ---- status ------------------------------------------------------------------------------------

    private void drawStatus() {
        if (Relay.url(this).isEmpty()) return;
        boolean on = RemoteRelay.online();
        status.setText(on ? R.string.status_online : R.string.status_connecting);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(on ? 0xFF30D158 : 0xFFFF9F0A);
        statusDot.setBackground(dot);
        drawQr();
    }

    /** What install.sh grants, one line each: green On, or how to fix it. */
    private void drawChecks() {
        checks.removeAllViews();
        check(R.string.check_overlay, Settings.canDrawOverlays(this));
        check(R.string.check_accessibility, KeyService.running != null);
        check(R.string.check_usage, ScreenTime.allowed(this));
        check(R.string.check_capture, opAllowed(this, "android:project_media"));
        check(R.string.check_secure, checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED);
    }

    private void check(int label, boolean on) {
        LinearLayout r = new LinearLayout(this);
        TextView name = text(getString(label), 17, 0x99FFFFFF);
        r.addView(name, new LinearLayout.LayoutParams((int) (300 * d), ViewGroup.LayoutParams.WRAP_CONTENT));
        r.addView(text(getString(on ? R.string.check_on : R.string.check_off), 17, on ? 0xFF30D158 : 0xFFFF9F0A));
        checks.addView(r, top(wrap(), 4));
    }

    /** An app-op is allowed for this app; PROJECT_MEDIA has no public constant, hence the string. */
    @SuppressWarnings("deprecation")   // unsafeCheckOpNoThrow is Android 10+; this one works on 8 too
    static boolean opAllowed(android.content.Context ctx, String op) {
        try {
            AppOpsManager ops = ctx.getSystemService(AppOpsManager.class);
            return ops != null && ops.checkOpNoThrow(op, Process.myUid(), ctx.getPackageName()) == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;   // an op this Android doesn't know
        }
    }

    // ---- views -------------------------------------------------------------------------------------

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private TextView button(String label) {
        TextView b = text(label, 20, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setPadding((int) (28 * d), 0, (int) (28 * d), 0);
        b.setMinHeight((int) (52 * d));
        b.setMinimumWidth((int) (150 * d));
        b.setBackground(PinPad.keyBackground(d));
        b.setOnFocusChangeListener((v, has) -> ((TextView) v).setTextColor(has ? 0xFF0B0B0D : Color.WHITE));
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return b;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams top(LinearLayout.LayoutParams lp, int dp) {
        lp.topMargin = (int) (dp * d);
        return lp;
    }
}
