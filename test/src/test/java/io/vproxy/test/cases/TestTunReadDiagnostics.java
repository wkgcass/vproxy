package io.vproxy.test.cases;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class TestTunReadDiagnostics {
    static class Diagnostics {
        final Class<?> type = Class.forName("io.vproxy.vswitch.iface.TunIface$TunReadDiagnostics");
        final Object value;
        final Method record;

        Diagnostics() throws Exception {
            var ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            value = ctor.newInstance();
            Method method;
            try {
                method = type.getDeclaredMethod("record", int.class, long.class, long.class, long.class, boolean.class, boolean.class);
            } catch (NoSuchMethodException oldVersion) {
                method = type.getDeclaredMethod("record", int.class, long.class);
            }
            method.setAccessible(true);
            record = method;
        }

        void record(int packets, long bytes, long nanos, long now, boolean yield, boolean error) throws Exception {
            if (record.getParameterCount() == 2) record.invoke(value, packets, nanos);
            else record.invoke(value, packets, bytes, nanos, now, yield, error);
        }

        long field(String name) throws Exception {
            var f = type.getDeclaredField(name);
            f.setAccessible(true);
            return ((Number) f.get(value)).longValue();
        }

        long quantile(int percentile) throws Exception {
            var m = type.getDeclaredMethod("percentileUpperBound", int.class);
            m.setAccessible(true);
            return (Long) m.invoke(value, percentile);
        }
    }

    @Test
    public void firstPacketAndPeriodicReportsRemainCappedAcrossClockWrap() throws Exception {
        var d = new Diagnostics();
        long now = Long.MAX_VALUE - 1_000_000_000;
        d.record(4, 160, 8_000_000, now, true, false);
        assertEquals("the first useful batch must be visible", 1, d.field("reportCount"));
        d.record(1, 40, 1000, now + 1, false, false);
        assertEquals(1, d.field("reportCount"));
        for (int i = 0; i < 100; ++i) {
            now += 15_000_000_000L;
            d.record(4, 160, 8_000_000, now, true, false);
            assertEquals(Math.min(32, i + 2), d.field("reportCount"));
        }
        assertEquals(102, d.field("callbacks"));
        assertEquals(405, d.field("packets"));
        assertEquals(16_200, d.field("bytes"));
        assertEquals(101, d.field("budgetYields"));
        var histogram = d.type.getDeclaredField("callbackHistogram");
        histogram.setAccessible(true);
        long[] buckets = (long[]) histogram.get(d.value);
        assertEquals("fixed diagnostic storage", 32, buckets.length);
        long count = 0;
        for (long bucket : buckets) count += bucket;
        assertEquals(102, count);
    }

    @Test
    public void nearestRankPercentilesReportHistogramUpperBounds() throws Exception {
        var d = new Diagnostics();
        assertEquals(0, d.quantile(95));
        for (int i = 0; i < 98; ++i) d.record(1, 40, 1000, 1, false, false);
        d.record(1, 40, 2000, 1, false, false);
        d.record(1, 40, 1_000_000, 1, false, false);
        assertEquals(1000, d.quantile(95));
        assertEquals(2000, d.quantile(99));
        assertEquals(1_024_000, d.quantile(100));
        assertEquals(1_000_000, d.field("maxCallbackNanos"));
        assertEquals(1_100_000, d.field("elapsedNanos"));
        assertEquals(4000, d.field("bytes"));
    }

    @Test
    public void emptyAndErrorCallbacksCountWithoutPrematureOutput() throws Exception {
        var d = new Diagnostics();
        d.record(0, 0, 1000, 1, false, true);
        assertEquals(0, d.field("reportCount"));
        assertEquals(1, d.field("readErrors"));
        d.record(32, 1280, 1001, 2, true, false);
        assertEquals(1, d.field("reportCount"));
        assertEquals(2, d.field("callbacks"));
        assertEquals(32, d.field("maxPacketsPerCallback"));
        assertEquals(1280, d.field("maxBytesPerCallback"));
        assertEquals(1, d.field("budgetYields"));
        assertEquals(2000, d.quantile(95));
    }

    @Test
    public void actualHandlerAccountsForWireBytesYieldsAndReadErrors() throws Exception {
        var d = new Diagnostics();
        try (var f = new TestTunReadFairness.Fixture(65, 0)) {
            var diagnosticField = f.iface.getClass().getSuperclass().getDeclaredField("tunReadDiagnostics");
            diagnosticField.setAccessible(true);
            diagnosticField.set(f.iface, d.value);
            f.input.errorAt = 32;
            f.drain(65);
            assertEquals(4, d.field("callbacks"));
            assertEquals(65, d.field("packets"));
            assertEquals("IP bytes exclude the Darwin AF header", 2600, d.field("bytes"));
            assertEquals(1280, d.field("maxBytesPerCallback"));
            assertEquals(2, d.field("budgetYields"));
            assertEquals(1, d.field("readErrors"));
            assertEquals(1, d.field("reportCount"));
        }
    }
}
