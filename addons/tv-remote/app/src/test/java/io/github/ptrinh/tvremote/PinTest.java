package io.github.ptrinh.tvremote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.security.SecureRandom;

public class PinTest {
    @Test
    public void storedPinMatchesOnlyItself() {
        String stored = Pin.store("1234", new SecureRandom());
        assertTrue(Pin.matches(stored, "1234"));
        assertFalse(Pin.matches(stored, "1235"));
        assertFalse(Pin.matches(stored, "12345"));
        assertFalse(Pin.matches(stored, ""));
        assertFalse(Pin.matches(stored, null));
    }

    @Test
    public void storedFormIsSaltedAndHasNoPin() {
        String a = Pin.store("1234", new SecureRandom());
        String b = Pin.store("1234", new SecureRandom());
        assertNotEquals(a, b);
        assertFalse(a.contains("1234"));
        assertEquals(32 + 1 + 64, a.length());
    }

    @Test
    public void brokenStoreMatchesNothing() {
        assertFalse(Pin.matches("", "1234"));
        assertFalse(Pin.matches(null, "1234"));
        assertFalse(Pin.matches("abc:def", "1234"));
    }

    @Test
    public void onlyFourDigitsAreValid() {
        assertTrue(Pin.valid("0000"));
        assertFalse(Pin.valid("123"));
        assertFalse(Pin.valid("12a4"));
        assertFalse(Pin.valid("١٢٣٤"));
    }
}
