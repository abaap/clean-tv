package io.github.ptrinh.tvremote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RelayTest {
    @Test
    public void takesAnOriginAndDropsATrailingSlash() {
        assertEquals("https://tv-remote.me.workers.dev", Relay.clean("https://tv-remote.me.workers.dev/"));
        assertEquals("https://relay.example.com:8443", Relay.clean(" https://relay.example.com:8443 "));
    }

    @Test
    public void refusesAnythingElse() {
        assertNull(Relay.clean(null));
        assertNull(Relay.clean("tv-remote.me.workers.dev"));
        assertNull(Relay.clean("https://tv-remote.me.workers.dev/path"));
        assertNull(Relay.clean("https://evil.example.com@x"));
        assertNull(Relay.clean("javascript:alert(1)"));
    }
}
