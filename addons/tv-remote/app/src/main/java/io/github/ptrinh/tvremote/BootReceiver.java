package io.github.ptrinh.tvremote;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a reboot or an update: connect to the relay again, and put the lock screen back if the TV was locked. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        RemoteService.start(context);
        // The watchdog shows the lock screen (and keeps it in front) when the TV was locked.
        TvLock.start(context);
    }
}
