package io.github.bayhaqy.tvcare;

import java.util.Locale;

/** Parsing and formatting behind the TV Care screen. No Android types, so it is unit tested on the JVM. */
final class Readings {
    private Readings() {}

    /** {available, total} in bytes from /proc/meminfo; free + buffers + cached on kernels without MemAvailable. */
    static long[] memInfo(String text) {
        if (text == null) return null;
        long total = -1, avail = -1, free = 0, buffers = 0, cached = 0;
        for (String line : text.split("\n")) {
            String[] t = line.trim().split("\\s+");
            if (t.length < 2) continue;
            long kb;
            try {
                kb = Long.parseLong(t[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            switch (t[0]) {
                case "MemTotal:": total = kb; break;
                case "MemAvailable:": avail = kb; break;
                case "MemFree:": free = kb; break;
                case "Buffers:": buffers = kb; break;
                case "Cached:": cached = kb; break;
                default: break;
            }
        }
        if (total <= 0) return null;
        if (avail < 0) avail = Math.min(total, free + buffers + cached);
        return new long[] {avail * 1024, total * 1024};
    }

    static int percent(long part, long whole) {
        return whole <= 0 ? 0 : (int) Math.round(part * 100.0 / whole);
    }

    static String bytes(long b) {
        double gb = b / (1024.0 * 1024 * 1024);
        if (gb >= 1) return String.format(Locale.US, "%.1f GB", gb);
        return Math.round(b / (1024.0 * 1024)) + " MB";
    }

    /** Bytes per second as a network speed in bits: "800 kb/s", "12.4 Mb/s", "120 Mb/s". */
    static String rate(long bytesPerSecond) {
        double bits = bytesPerSecond * 8.0;
        if (bits < 1_000_000) return Math.round(bits / 1000) + " kb/s";
        double mb = bits / 1_000_000;
        return mb >= 100 ? Math.round(mb) + " Mb/s" : String.format(Locale.US, "%.1f Mb/s", mb);
    }

    /** Uptime with the caller's formats: "%d hari %d jam", "%d jam %d mnt", "%d mnt" (or their English twins). */
    static String uptime(long ms, String daysHours, String hoursMinutes, String minutes) {
        long min = ms / 60_000, h = min / 60, d = h / 24;
        if (d > 0) return String.format(Locale.ROOT, daysHours, d, h % 24);
        if (h > 0) return String.format(Locale.ROOT, hoursMinutes, h, min % 60);
        return String.format(Locale.ROOT, minutes, min);
    }

    /** Wi-Fi signal as 0 to 4 bars. */
    static int bars(int rssi) {
        if (rssi >= -55) return 4;
        if (rssi >= -66) return 3;
        if (rssi >= -77) return 2;
        if (rssi >= -88) return 1;
        return 0;
    }

    /** {total, idle} jiffies from the first line of /proc/stat, or null. */
    static long[] procStat(String line) {
        if (line == null || !line.startsWith("cpu ")) return null;
        String[] t = line.trim().split("\\s+");
        if (t.length < 5) return null;
        long total = 0, idle = 0;
        try {
            for (int i = 1; i < t.length && i <= 8; i++) {
                long v = Long.parseLong(t[i]);
                total += v;
                if (i == 4 || i == 5) idle += v;   // idle and iowait
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return new long[] {total, idle};
    }

    /** Busy percent between two procStat samples, or -1 when no time passed. */
    static int cpuPercent(long[] before, long[] after) {
        long dt = after[0] - before[0], di = after[1] - before[1];
        if (dt <= 0) return -1;
        return (int) Math.round((dt - di) * 100.0 / dt);
    }

    /** A thermal zone's reading in degrees C (millidegrees or degrees), NaN when absent or implausible. */
    static float temperature(String raw) {
        if (raw == null) return Float.NaN;
        try {
            float v = Float.parseFloat(raw.trim());
            if (v > 1000) v /= 1000f;
            return v > 0 && v < 150 ? v : Float.NaN;
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }
}
