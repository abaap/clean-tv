package io.github.ptrinh.tvremote;

import android.content.Context;

/**
 * The relay's address: the one install.sh set on this TV (RelayReceiver), else the one built in with
 * -PrelayUrl, else none. Set at install time, one APK works with anyone's relay and a new relay needs no rebuild.
 */
final class Relay {
    static final String PREFS = "relay";
    static final String KEY = "url";

    private Relay() {
    }

    static String url(Context ctx) {
        String set = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        return !set.isEmpty() ? set : BuildConfig.RELAY_URL;
    }

    /** https://host[:port] with nothing after it (a trailing slash is dropped); null when it is not that. */
    static String clean(String s) {
        if (s == null) return null;
        s = s.trim().replaceAll("/+$", "");
        return s.matches("https?://[A-Za-z0-9.-]+(:\\d+)?") ? s : null;
    }
}
