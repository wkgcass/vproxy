package io.vproxy.test.cases;

import io.vproxy.base.Config;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.conntrack.tcp.TcpEntry;
import io.vproxy.vpacket.conntrack.tcp.TcpState;
import io.vproxy.vpacket.conntrack.tcp.ConnectionHandler;
import org.junit.After;
import org.junit.Test;
import java.nio.ByteBuffer;
import java.util.Map;
import static org.junit.Assert.*;

public class TestSendingQueueAge {
    private final long saved = Config.currentTimestamp;
    @After public void restoreClock() { Config.currentTimestamp = saved; }
    static TcpEntry.SendingQueue queue() { return queue(-1); }
    static TcpEntry.SendingQueue queue(long origin) {
        Config.currentTimestamp = 1000;
        var tcp = new TcpEntry(null, new IPPort("192.0.2.1", 12345), new IPPort("192.0.2.2", 443), 100);
        tcp.setState(TcpState.ESTABLISHED);
        if (origin >= 0) {
            try {
                for (String name : new String[]{"latestSeq", "ackSeq", "fetchSeq"}) {
                    var f = tcp.sendingQueue.getClass().getDeclaredField(name);
                    f.setAccessible(true); f.setLong(tcp.sendingQueue, origin);
                }
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        tcp.sendingQueue.init(0, 1000, 1, 10000);
        assertEquals(6000, tcp.sendingQueue.apiWrite(ByteBuffer.wrap(new byte[6000])));
        return tcp.sendingQueue;
    }
    static void fetch(TcpEntry.SendingQueue q, int bytes) {
        assertEquals(bytes, q.fetch().stream().mapToInt(s -> s.data.length()).sum());
    }
    static void laterSuffix(TcpEntry.SendingQueue q, long origin) {
        q.ack(origin, 500); fetch(q, 500);
        Config.currentTimestamp = 11000;
        q.ack(origin, 1000); fetch(q, 500);
    }
    @Test public void recentSuffixKeepsItsOwnFirstSendAge() {
        var q = queue(); long origin = q.getAckSeq(); laterSuffix(q, origin);
        Config.currentTimestamp = 11100;
        q.ack(origin + 500, 500);
        Config.currentTimestamp = 16000;
        assertNull("remaining bytes first sent at 11000, only 5000ms ago", q.checkRetransmissionTimeout());
        Config.currentTimestamp = 26000;
        assertNotNull("expire at the suffix's own 15000ms boundary", q.checkRetransmissionTimeout());
    }
    @Test public void unackedOldPrefixDoesNotHaveItsAgeRenewed() {
        var q = queue(); long origin = q.getAckSeq(); laterSuffix(q, origin);
        Config.currentTimestamp = 11100;
        q.ack(origin + 250, 750);
        Config.currentTimestamp = 16000;
        assertNotNull(q.checkRetransmissionTimeout());
    }
    @Test public void unsentSuffixHasNoRetransmissionAge() {
        var q = queue(); long origin = q.getAckSeq(); q.ack(origin, 500); fetch(q, 500);
        Config.currentTimestamp = 11100;
        q.ack(origin + 500, 0);
        Config.currentTimestamp = 16000;
        assertNull(q.checkRetransmissionTimeout());
    }
    @Test public void retransmitDoesNotRenewAbsoluteDeadline() {
        var q = queue(); long origin = q.getAckSeq(); q.ack(origin, 500); fetch(q, 500);
        Config.currentTimestamp = 14999;
        q.fetch(true);
        Config.currentTimestamp = 16000;
        assertNotNull(q.checkRetransmissionTimeout());
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static Map<?, ?> histories(TcpEntry.SendingQueue q) throws ReflectiveOperationException {
        Object tail = field(q, "partialFirstSentRanges");
        if (tail == null) return null;
        var snapshot = new java.util.IdentityHashMap<Object, Object>();
        var visited = new java.util.IdentityHashMap<Object, Boolean>();
        Object first = field(tail, "next");
        Object entry = first;
        do {
            assertNull("metadata entries must not repeat before the head", visited.put(entry, true));
            assertNull("one entry per segment", snapshot.put(field(entry, "segment"), entry));
            entry = field(entry, "next");
        } while (entry != first);
        assertTrue(visited.containsKey(tail));
        return snapshot;
    }

    private static int ranges(Object history) throws ReflectiveOperationException {
        int count = 0;
        for (Object range = field(history, "first"); range != null; range = field(range, "next")) ++count;
        return count;
    }

    @Test public void wholeMssAndSameTickPartialSendsNeedNoHistory() throws Exception {
        var q = queue(); long origin = q.getAckSeq();
        q.ack(origin, 6000); fetch(q, 6000);
        assertNull(histories(q));
        q = queue(); origin = q.getAckSeq();
        q.ack(origin, 500); fetch(q, 500);
        assertNull(histories(q));
        q.ack(origin, 1000); fetch(q, 500);
        assertNull(histories(q));
        q.ack(origin + 500, 500);
        Config.currentTimestamp = 16000;
        assertNotNull(q.checkRetransmissionTimeout());
    }

    @Test public void partialAckPreservesEachOfFourSendTimes() {
        var q = queue(); long origin = q.getAckSeq();
        for (int i = 1; i <= 4; ++i) {
            Config.currentTimestamp = i * 1000;
            q.ack(origin, i * 250); fetch(q, 250);
        }
        q.ack(origin + 275, 725);
        Config.currentTimestamp = 16999;
        assertNull(q.checkRetransmissionTimeout());
        Config.currentTimestamp = 17000;
        assertNotNull(q.checkRetransmissionTimeout());
        q.ack(origin + 500, 500);
        Config.currentTimestamp = 17999;
        assertNull(q.checkRetransmissionTimeout());
        Config.currentTimestamp = 18000;
        assertNotNull(q.checkRetransmissionTimeout());
        q.ack(origin + 750, 250);
        Config.currentTimestamp = 18999;
        assertNull(q.checkRetransmissionTimeout());
        Config.currentTimestamp = 19000;
        assertNotNull(q.checkRetransmissionTimeout());
    }

    @Test public void retiredSegmentsAndAcknowledgedRangesAreReleased() throws Exception {
        var q = queue(); long origin = q.getAckSeq();
        for (int i = 1; i <= 3; ++i) {
            Config.currentTimestamp = i * 1000;
            q.ack(origin, i * 250); fetch(q, 250);
        }
        Object retired = histories(q).keySet().iterator().next();
        q.ack(origin + 250, 500);
        assertFalse(histories(q).containsKey(retired));
        assertEquals(1, histories(q).size());
        assertEquals(2, ranges(histories(q).values().iterator().next()));
        q.ack(origin + 500, 250);
        assertNull(histories(q));
        // A further send after history became unnecessary must create the
        // correct new boundary, rather than forget the outstanding suffix.
        Config.currentTimestamp = 9000;
        q.ack(origin + 500, 500); fetch(q, 250);
        q.ack(origin + 750, 250);
        assertNull(histories(q));
        Config.currentTimestamp = 23999;
        assertNull(q.checkRetransmissionTimeout());
        Config.currentTimestamp = 24000;
        assertNotNull(q.checkRetransmissionTimeout());
        q.ack(origin + 1000, 0);
        assertNull(histories(q));
        assertNull(q.checkRetransmissionTimeout());
    }

    @Test public void bytewiseWindowsHaveBoundedHistoryAndRetransmitAddsNone() throws Exception {
        var q = queue(); long origin = q.getAckSeq();
        for (int i = 1; i <= 1000; ++i) {
            Config.currentTimestamp = 1000 + i;
            q.ack(origin, i); fetch(q, 1);
        }
        assertEquals(1, histories(q).size());
        assertEquals(1000, ranges(histories(q).values().iterator().next()));
        Config.currentTimestamp = 10000;
        assertEquals(1000, q.fetch(true).getFirst().data.length());
        assertEquals(1000, ranges(histories(q).values().iterator().next()));
        for (int i = 1; i < 999; ++i) {
            q.ack(origin + i, 1000 - i);
            assertEquals(1000 - i, ranges(histories(q).values().iterator().next()));
        }
        q.ack(origin + 999, 1);
        assertNull(histories(q));
        Config.currentTimestamp = 17000;
        assertNotNull(q.checkRetransmissionTimeout());
        q.ack(origin + 1000, 0);
        assertNull(histories(q));
    }

    @Test public void suffixRetransmissionKeepsItsOriginalDeadline() {
        var q = queue(); long origin = q.getAckSeq(); laterSuffix(q, origin);
        Config.currentTimestamp = 11100;
        q.ack(origin + 500, 500);
        Config.currentTimestamp = 24999;
        assertEquals(500, q.fetch(true).getFirst().data.length());
        assertNull(q.checkRetransmissionTimeout());
        Config.currentTimestamp = 26000;
        assertNotNull(q.checkRetransmissionTimeout());
    }

    @Test public void fullAckRemovesOnlyItsOwnHistory() throws Exception {
        var q = queue(); long origin = q.getAckSeq();
        for (int i = 1; i <= 4; ++i) {
            Config.currentTimestamp = i * 1000;
            q.ack(origin, i * 500); fetch(q, 500);
        }
        assertEquals(2, histories(q).size());
        q.ack(origin + 1000, 1000);
        assertEquals(1, histories(q).size());
        q.ack(origin + 1500, 500);
        assertNull(histories(q));
        Config.currentTimestamp = 18999;
        assertNull(q.checkRetransmissionTimeout());
        Config.currentTimestamp = 19000;
        assertNotNull(q.checkRetransmissionTimeout());
    }

    @Test public void firstSendAgeAcrossUnsignedSequenceBoundary() {
        long origin = 0xffff_ff00L;
        var q = queue(origin);
        laterSuffix(q, origin);
        q.ack(origin + 500, 500);
        Config.currentTimestamp = 25999;
        assertNull(q.checkRetransmissionTimeout());
        Config.currentTimestamp = 26000;
        assertNotNull(q.checkRetransmissionTimeout());
    }

    @Test public void historyFifoSkipsWholeSentSegmentsBetweenEntries() throws Exception {
        var q = queue(); long origin = q.getAckSeq();
        q.ack(origin, 1000); fetch(q, 1000);
        for (int i = 1; i <= 2; ++i) {
            Config.currentTimestamp = 1000 + i * 100;
            q.ack(origin, 1000 + i * 500); fetch(q, 500);
        }
        Config.currentTimestamp = 1300;
        q.ack(origin, 3000); fetch(q, 1000);
        for (int i = 1; i <= 2; ++i) {
            Config.currentTimestamp = 1300 + i * 100;
            q.ack(origin, 3000 + i * 500); fetch(q, 500);
        }
        assertEquals(2, histories(q).size());
        q.ack(origin + 1000, 3000);
        assertEquals(2, histories(q).size());
        q.ack(origin + 2000, 2000);
        assertEquals(1, histories(q).size());
        q.ack(origin + 3000, 1000);
        assertEquals(1, histories(q).size());
        q.ack(origin + 3500, 500);
        assertNull(histories(q));
        Config.currentTimestamp = 16499;
        assertNull(q.checkRetransmissionTimeout());
        Config.currentTimestamp = 16500;
        assertNotNull(q.checkRetransmissionTimeout());
    }

    @Test public void partialAckReentryCanAppendNewHistoryAfterReplacingHead() throws Exception {
        var q = queue(); long origin = q.getAckSeq(); laterSuffix(q, origin);
        var tcp = (TcpEntry) field(q, "this$0");
        tcp.setConnectionHandler(new ConnectionHandler() {
            boolean called;
            public void readable(TcpEntry entry) {}
            public void destroy(TcpEntry entry) {}
            public void writable(TcpEntry entry) {
                if (called) return;
                called = true;
                Config.currentTimestamp = 12000;
                q.ack(origin + 250, 1500); fetch(q, 750);
                Config.currentTimestamp = 12500;
                q.ack(origin + 250, 1750); fetch(q, 250);
            }
        });
        q.ack(origin + 250, 750);
        assertEquals(2, histories(q).size());
        Config.currentTimestamp = 16000;
        assertNotNull(q.checkRetransmissionTimeout());
        q.ack(origin + 1000, 1000);
        Config.currentTimestamp = 26999;
        assertNull(q.checkRetransmissionTimeout());
        Config.currentTimestamp = 27000;
        assertNotNull(q.checkRetransmissionTimeout());
        q.ack(origin + 1750, 250);
        assertNull(histories(q));
        Config.currentTimestamp = 27499;
        assertNull(q.checkRetransmissionTimeout());
        Config.currentTimestamp = 27500;
        assertNotNull(q.checkRetransmissionTimeout());
    }
}
