package io.github.ptrinh.tvremote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Sets the relay's address from install.sh:
 *   am broadcast -n io.github.ptrinh.tvremote/.RelayReceiver --es url https://tv-remote.<you>.workers.dev
 * The manifest guards it with the DUMP permission, which ADB's shell holds and other apps don't, so nothing else
 * on the TV can point the remote at another relay.
 */
public class RelayReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String url = Relay.clean(intent.getStringExtra("url"));
        if (url == null) {
            Log.w(RemoteRelay.TAG, "not a relay address: " + intent.getStringExtra("url"));
            setResultCode(1);
            return;
        }
        context.getSharedPreferences(Relay.PREFS, Context.MODE_PRIVATE).edit().putString(Relay.KEY, url).commit();
        RemoteRelay.setBase(url);
        setResultCode(0);
        setResultData("relay " + url);
    }
}
