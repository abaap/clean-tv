package io.github.bayhaqy.tvcare;

import org.junit.Test;

import static org.junit.Assert.*;

public class ReadingsTest {
    private static final String[] ID = {"%d hari %d jam", "%d jam %d mnt", "%d mnt"};

    @Test public void memInfoReadsAvailableAndTotal() {
        long[] m = Readings.memInfo("MemTotal:         924856 kB\nMemFree:           73296 kB\nMemAvailable:     286880 kB\n");
        assertArrayEquals(new long[] {286880L * 1024, 924856L * 1024}, m);
    }

    @Test public void memInfoFallsBackToFreePlusCached() {
        long[] m = Readings.memInfo("MemTotal: 1000 kB\nMemFree: 100 kB\nBuffers: 50 kB\nCached: 200 kB\n");
        assertArrayEquals(new long[] {350L * 1024, 1000L * 1024}, m);
        assertNull(Readings.memInfo("garbage"));
        assertNull(Readings.memInfo(null));
    }

    @Test public void percentRounds() {
        assertEquals(69, Readings.percent(638, 924));
        assertEquals(0, Readings.percent(5, 0));
        assertEquals(100, Readings.percent(10, 10));
    }

    @Test public void bytesInGbOrMb() {
        assertEquals("2.2 GB", Readings.bytes(2_362_232_012L));
        assertEquals("512 MB", Readings.bytes(512L * 1024 * 1024));
        assertEquals("0 MB", Readings.bytes(0));
    }

    @Test public void rateInBits() {
        assertEquals("0 kb/s", Readings.rate(0));
        assertEquals("800 kb/s", Readings.rate(100_000));
        assertEquals("12.4 Mb/s", Readings.rate(1_550_000));
        assertEquals("120 Mb/s", Readings.rate(15_000_000));
    }

    @Test public void uptimeInIndonesian() {
        assertEquals("12 mnt", Readings.uptime(12 * 60_000L, ID[0], ID[1], ID[2]));
        assertEquals("4 jam 12 mnt", Readings.uptime((4 * 60 + 12) * 60_000L, ID[0], ID[1], ID[2]));
        assertEquals("3 hari 4 jam", Readings.uptime(((3 * 24 + 4) * 60 + 30) * 60_000L, ID[0], ID[1], ID[2]));
    }

    @Test public void signalBarsFromRssi() {
        assertEquals(4, Readings.bars(-50));
        assertEquals(3, Readings.bars(-60));
        assertEquals(2, Readings.bars(-70));
        assertEquals(1, Readings.bars(-85));
        assertEquals(0, Readings.bars(-95));
        assertEquals(0, Readings.bars(-127));
    }

    @Test public void cpuPercentFromProcStatDeltas() {
        long[] a = Readings.procStat("cpu  100 0 100 800 0 0 0 0 0 0");
        long[] b = Readings.procStat("cpu  150 0 150 900 0 0 0 0 0 0");
        assertEquals(50, Readings.cpuPercent(a, b));
        assertEquals(-1, Readings.cpuPercent(b, b));
        assertNull(Readings.procStat("intr 1 2 3"));
    }

    @Test public void temperatureInMillidegreesOrDegrees() {
        assertEquals(45.5f, Readings.temperature("45500"), 0.01f);
        assertEquals(52f, Readings.temperature("52\n"), 0.01f);
        assertTrue(Float.isNaN(Readings.temperature("-1")));
        assertTrue(Float.isNaN(Readings.temperature("abc")));
        assertTrue(Float.isNaN(Readings.temperature(null)));
    }
}
