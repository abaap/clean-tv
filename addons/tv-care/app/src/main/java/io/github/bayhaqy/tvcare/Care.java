package io.github.bayhaqy.tvcare;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TV Care: RAM and storage rings, system and live network readings, a Clean RAM button and the recently used apps,
 * each with Close. Everything is read only while the screen is resumed, once a second; nothing runs in the background.
 */
public class Care extends Activity {
    static final int RED = 0xFFE11D2E, WHITE = 0xFFF5F5F7, DIM = 0xFF8A8A93, CARD = 0xFF141417, LINE = 0xFF26262B;
    private static final String HOME = "com.gathin.tvhome";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Typeface face;
    private float dp;
    private Ring ram, disk;
    private TextView sys, net, cleanResult, appsHint, clean;
    private LinearLayout appsRow;
    private long[] cpuPrev;
    private long rxPrev = -1, txPrev = -1, tPrev;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        face = getResources().getFont(R.font.outfit);
        FrameLayout root = new FrameLayout(this);
        root.addView(new Backdrop(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(48), px(32), px(48), px(24));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.BOTTOM);
        head.addView(text(getString(R.string.app_name), 34, WHITE));
        TextView sub = text(getString(R.string.subtitle), 11, DIM);
        sub.setLetterSpacing(0.22f);
        sub.setPadding(px(16), 0, 0, px(8));
        head.addView(sub);
        page.addView(head);

        LinearLayout cards = new LinearLayout(this);
        cards.setPadding(0, px(18), 0, 0);
        ram = new Ring(this, face);
        disk = new Ring(this, face);
        cards.addView(card(getString(R.string.memory), ram), weighted(1f));
        cards.addView(card(getString(R.string.storage), disk), weighted(1f));
        sys = text("", 15, WHITE);
        sys.setLineSpacing(px(6), 1f);
        cards.addView(card(getString(R.string.system), sys), weighted(1.15f));
        net = text("", 15, WHITE);
        net.setLineSpacing(px(6), 1f);
        cards.addView(card(getString(R.string.network), net), weighted(1.35f));
        page.addView(cards, new LinearLayout.LayoutParams(-1, px(196)));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setPadding(0, px(20), 0, 0);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        clean = button(getString(R.string.clean), 18);
        clean.setOnClickListener(v -> cleanAll());
        left.addView(clean, new LinearLayout.LayoutParams(px(220), px(56)));
        cleanResult = text(getString(R.string.clean_hint), 13, DIM);
        cleanResult.setPadding(px(4), px(10), 0, 0);
        left.addView(cleanResult, new LinearLayout.LayoutParams(px(220), -2));
        bottom.addView(left);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(px(28), 0, 0, 0);
        TextView appsLabel = text(getString(R.string.recent_apps), 11, DIM);
        appsLabel.setLetterSpacing(0.18f);
        right.addView(appsLabel);
        appsHint = text("", 13, DIM);
        right.addView(appsHint);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, px(10), px(24), px(10));
        appsRow = new LinearLayout(this);
        scroll.addView(appsRow);
        right.addView(scroll);
        bottom.addView(right, new LinearLayout.LayoutParams(0, -2, 1f));
        page.addView(bottom);

        setContentView(root);
        clean.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cpuPrev = null;
        rxPrev = -1;
        loadApps();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    // ---- readings ------------------------------------------------------------------------------------

    private long[] mem() {
        StringBuilder s = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            for (String line; (line = r.readLine()) != null; ) s.append(line).append('\n');
        } catch (Exception e) {
            return null;
        }
        return Readings.memInfo(s.toString());
    }

    private void refresh() {
        long[] m = mem();
        if (m != null) {
            long used = m[1] - m[0];
            ram.set(Readings.percent(used, m[1]), Readings.bytes(used) + " / " + Readings.bytes(m[1]));
        }
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        long total = fs.getTotalBytes(), free = fs.getAvailableBytes();
        disk.set(Readings.percent(total - free, total), getString(R.string.disk_free, Readings.bytes(free), Readings.bytes(total)));

        StringBuilder s = new StringBuilder();
        int cpu = cpu();
        s.append(cpu >= 0 ? getString(R.string.cpu_percent, cpu)
                : getString(R.string.cpu_cores, Runtime.getRuntime().availableProcessors()));
        s.append('\n').append(getString(R.string.thermal, thermal()));
        s.append('\n').append(getString(R.string.uptime, Readings.uptime(SystemClock.elapsedRealtime(),
                getString(R.string.days_hours), getString(R.string.hours_minutes), getString(R.string.minutes))));
        sys.setText(s);
        net.setText(network());
    }

    private int cpu() {
        String line = null;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/stat"))) {
            line = r.readLine();
        } catch (Exception ignored) {
            // Most TVs keep /proc/stat from apps: then CPU shows the core count instead.
        }
        long[] now = Readings.procStat(line);
        if (now == null) return -1;
        int p = cpuPrev == null ? -1 : Readings.cpuPercent(cpuPrev, now);
        cpuPrev = now;
        return p;
    }

    private String thermal() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm == null || android.os.Build.VERSION.SDK_INT < 29) return "–";
        switch (pm.getCurrentThermalStatus()) {
            case PowerManager.THERMAL_STATUS_NONE: return getString(R.string.thermal_normal);
            case PowerManager.THERMAL_STATUS_LIGHT: return getString(R.string.thermal_warm);
            case PowerManager.THERMAL_STATUS_MODERATE: return getString(R.string.thermal_hot);
            case PowerManager.THERMAL_STATUS_SEVERE:
            case PowerManager.THERMAL_STATUS_CRITICAL:
            case PowerManager.THERMAL_STATUS_EMERGENCY:
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return getString(R.string.thermal_very_hot);
            default: return "–";
        }
    }

    private String network() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network n = cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        if (caps == null) return getString(R.string.not_connected);
        StringBuilder s = new StringBuilder();
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            WifiInfo w = getSystemService(WifiManager.class).getConnectionInfo();
            String ssid = w == null ? "" : w.getSSID().replace("\"", "");
            if (ssid.isEmpty() || ssid.contains("unknown")) ssid = "Wi-Fi";
            int bars = w == null ? 0 : Readings.bars(w.getRssi());
            s.append(ssid).append("  ").append("▮▮▮▮".substring(0, bars)).append("▯▯▯▯".substring(bars));
            if (w != null && w.getLinkSpeed() > 0) s.append("  ").append(w.getLinkSpeed()).append(" Mbps");
        } else {
            s.append("Ethernet");
        }
        LinkProperties lp = cm.getLinkProperties(n);
        if (lp != null) {
            for (LinkAddress a : lp.getLinkAddresses()) {
                if (a.getAddress() instanceof Inet4Address) {
                    s.append("\nIP  ").append(a.getAddress().getHostAddress());
                    break;
                }
            }
        }
        long rx = TrafficStats.getTotalRxBytes(), tx = TrafficStats.getTotalTxBytes(), t = SystemClock.elapsedRealtime();
        if (rxPrev >= 0 && t > tPrev) {
            long sec = Math.max(1, (t - tPrev) / 1000);
            s.append("\n↓ ").append(Readings.rate((rx - rxPrev) / sec)).append("   ↑ ").append(Readings.rate((tx - txPrev) / sec));
        } else {
            s.append("\n↓ …   ↑ …");
        }
        rxPrev = rx;
        txPrev = tx;
        tPrev = t;
        return s.toString();
    }

    // ---- actions -------------------------------------------------------------------------------------

    private void cleanAll() {
        long[] before = mem();
        ActivityManager am = getSystemService(ActivityManager.class);
        int n = 0;
        for (ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
            if (ai.packageName.equals(getPackageName()) || ai.packageName.equals(HOME)) continue;
            am.killBackgroundProcesses(ai.packageName);
            n++;
        }
        cleanResult.setText(getString(R.string.cleaning, n));
        handler.postDelayed(() -> {
            long[] after = mem();
            long freed = before == null || after == null ? 0 : Math.max(0, after[0] - before[0]);
            cleanResult.setText(getString(R.string.cleaned, Readings.bytes(freed)));
            loadApps();
            refresh();
            clean.requestFocus();   // the app row was rebuilt under the focus
        }, 1500);
    }

    private void loadApps() {
        appsRow.removeAllViews();
        UsageStatsManager usm = getSystemService(UsageStatsManager.class);
        long now = System.currentTimeMillis();
        List<UsageStats> list = usm == null ? null
                : usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 3 * 86_400_000L, now);
        if (list == null || list.isEmpty()) {
            appsHint.setText(getString(R.string.no_usage_access));
            return;
        }
        appsHint.setText("");
        List<UsageStats> sorted = new ArrayList<>(list);
        Collections.sort(sorted, (a, b) -> Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed()));
        PackageManager pm = getPackageManager();
        Set<String> seen = new HashSet<>();
        for (UsageStats u : sorted) {
            String pkg = u.getPackageName();
            if (u.getLastTimeUsed() <= 0 || pkg.equals(getPackageName()) || pkg.equals(HOME) || !seen.add(pkg)) continue;
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) continue;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                appsRow.addView(appChip(pkg, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai)));
            } catch (PackageManager.NameNotFoundException ignored) {
                // Uninstalled since it was used.
            }
            if (appsRow.getChildCount() == 8) break;
        }
    }

    private View appChip(String pkg, String label, Drawable icon) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(px(10), px(12), px(10), px(10));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        chip.addView(iv, new LinearLayout.LayoutParams(px(40), px(40)));
        TextView name = text(label, 13, WHITE);
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, px(8), 0, 0);
        chip.addView(name, new LinearLayout.LayoutParams(px(104), -2));
        TextView state = text(getString(R.string.close), 11, DIM);
        state.setGravity(Gravity.CENTER);
        chip.addView(state, new LinearLayout.LayoutParams(px(104), -2));
        focusable(chip, name, state);
        chip.setOnClickListener(v -> {
            long[] before = mem();
            getSystemService(ActivityManager.class).killBackgroundProcesses(pkg);
            handler.postDelayed(() -> {
                long[] after = mem();
                long freed = before == null || after == null ? 0 : Math.max(0, after[0] - before[0]);
                state.setText(freed > 0 ? getString(R.string.closed_freed, Readings.bytes(freed)) : getString(R.string.closed));
            }, 800);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(px(12));
        chip.setLayoutParams(lp);
        return chip;
    }

    // ---- look ----------------------------------------------------------------------------------------

    private int px(float v) { return Math.round(v * dp); }

    private LinearLayout.LayoutParams weighted(float w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, w);
        lp.setMarginEnd(px(14));
        return lp;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(face);
        return t;
    }

    private GradientDrawable panel(int fill, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(px(radius));
        if (stroke != 0) g.setStroke(Math.max(1, px(1)), stroke);
        return g;
    }

    private View card(String title, View body) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(px(18), px(16), px(18), px(16));
        c.setBackground(panel(0xCC141417, LINE, 18));
        TextView t = text(title, 11, DIM);
        t.setLetterSpacing(0.22f);
        c.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = px(10);
        c.addView(body, lp);
        return c;
    }

    private TextView button(String label, float sp) {
        TextView b = text(label, sp, WHITE);
        b.setGravity(Gravity.CENTER);
        focusable(b, b, null);
        return b;
    }

    /** Red-edged glass at rest; white with dark text when focused, slightly lifted. */
    private void focusable(View v, TextView main, TextView second) {
        v.setFocusable(true);
        v.setClickable(true);
        v.setBackground(panel(0xCC141417, LINE, 16));
        v.setOnFocusChangeListener((view, has) -> {
            view.setBackground(has ? panel(WHITE, 0, 16) : panel(0xCC141417, LINE, 16));
            main.setTextColor(has ? 0xFF0A0A0C : WHITE);
            if (second != null) second.setTextColor(has ? RED : DIM);
            view.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(120).start();
        });
    }

    /** Near-black with one red glow at the top right and a faint one at the bottom left. */
    private static final class Backdrop extends View {
        private final Paint p = new Paint();

        Backdrop(Context c) { super(c); }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.drawColor(0xFF0A0A0C);
            p.setShader(new RadialGradient(w * 0.85f, h * 0.05f, w * 0.55f, 0x66B3001B, 0x00000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(new RadialGradient(w * 0.05f, h * 1.0f, w * 0.4f, 0x2EFFFFFF, 0x00000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
        }
    }
}
