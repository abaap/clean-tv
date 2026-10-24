package io.github.ptrinh.tvremote;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * The phone's keyboard ("type": ACTION_SET_TEXT on the focused text field in any window), the global
 * actions (Back, Home, Recents, notifications, quick settings) and standby without IP control (lock
 * screen, Android 9+). No event types and no key filtering: the system never calls in here on its own,
 * and the windows are only read when the phone types. Enabled by install.sh.
 */
public class KeyService extends AccessibilityService {
    /** The connected service; null when it is off. */
    static volatile KeyService running;

    @Override
    protected void onServiceConnected() {
        running = this;
        // The process can start for this service alone (after an update or a reboot): connect now.
        RemoteService.start(this);
        TvLock.start(this);
    }

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
