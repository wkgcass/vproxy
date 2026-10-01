package io.vproxy.test.cases;

import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.conntrack.tcp.*;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class TestSendingQueueCursor {
    private static TcpEntry entry(int mss, int capacity, int sequence) {
        var entry = new TcpEntry(null, new IPPort("192.0.2.1", 12345), new IPPort("192.0.2.2", 443), sequence);
        entry.setState(TcpState.ESTABLISHED);
        // TcpEntry's argument initializes the peer's receive sequence; its
        // send sequence is randomized. Pin the three initial send cursors so
        // the second randomized case actually crosses the unsigned 32-bit edge.
        try {
            for (String name : new String[]{"latestSeq", "ackSeq", "fetchSeq"}) {
                var field = entry.sendingQueue.getClass().getDeclaredField(name);
                field.setAccessible(true);
                field.setLong(entry.sendingQueue, Integer.toUnsignedLong(sequence));
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        entry.sendingQueue.init(0, mss, 1, capacity);
        return entry;
    }

    private static byte[] payload(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; ++i) bytes[i] = (byte) (i * 31 + (i >>> 8));
        return bytes;
    }

    private static int assertRange(List<Segment> segments, byte[] bytes, long origin, int from, int end) {
        int position = from;
        for (var segment : segments) {
            assertEquals(origin + position, segment.seqBeginInclusive);
            int length = segment.data.length();
            assertEquals(origin + position + length, segment.seqEndExclusive);
            assertTrue(position + length <= end);
            assertArrayEquals(Arrays.copyOfRange(bytes, position, position + length), segment.data.toJavaArray());
            position += length;
        }
        assertEquals(end, position);
        return position;
    }

    private static class CountingDeque extends ArrayDeque<Segment> {
        long visits;

        @Override
        public Iterator<Segment> iterator() {
            var delegate = super.iterator();
            return new Iterator<>() {
                public boolean hasNext() { return delegate.hasNext(); }
                public Segment next() { ++visits; return delegate.next(); }
                public void remove() { delegate.remove(); }
            };
        }
    }

    @Test
    public void incrementalSendsHaveLinearQueueTraversalWork() throws Exception {
        int size = 256;
        var tcp = entry(1, size, 100);
        var queue = tcp.sendingQueue;
        var probes = new ArrayList<CountingDeque>();
        // Count all deque traversal work, independent of the cursor layout.
        for (var field : queue.getClass().getDeclaredFields()) {
            if (field.getType() != ArrayDeque.class) continue;
            field.setAccessible(true);
            var probe = new CountingDeque();
            probe.addAll((ArrayDeque<Segment>) field.get(queue));
            field.set(queue, probe);
            probes.add(probe);
        }
        byte[] bytes = payload(size);
        assertEquals(size, queue.apiWrite(ByteBuffer.wrap(bytes)));
        long origin = queue.getAckSeq();
        for (int window = 1; window <= size; ++window) {
            queue.ack(origin, window);
            assertRange(queue.fetch(false), bytes, origin, window - 1, window);
        }
        long visits = probes.stream().mapToLong(p -> p.visits).sum();
        System.out.println("send_queue_walk segments=" + size + " visits=" + visits);
        assertTrue("incremental sends must not repeatedly walk the sent prefix: " + visits,
            visits <= size * 3L);
    }

    @Test
    public void randomPartialAcksAndWindowChangesPreserveNewAndRetransmittedRanges() {
        for (int sequence : new int[]{100, -16}) {
            var random = new Random(0x53454e44514cL);
            byte[] bytes = payload(4096);
            var queue = entry(64, bytes.length, sequence).sendingQueue;
            assertEquals(bytes.length, queue.apiWrite(ByteBuffer.wrap(bytes)));
            long origin = queue.getAckSeq();
            int acknowledged = 0, sent = 0;
            for (int step = 0; step < 1000; ++step) {
                acknowledged += random.nextInt(sent - acknowledged + 1);
                int window = random.nextInt(129);
                queue.ack(origin + acknowledged, window);
                int end = Math.max(sent, Math.min(bytes.length, acknowledged + window));
                sent = assertRange(queue.fetch(false), bytes, origin, sent, end);
                assertEquals(sent - acknowledged, queue.getBytesInFlight());
                if (step % 7 == 0) {
                    assertRange(queue.fetch(true), bytes, origin, acknowledged, sent);
                    assertEquals(sent - acknowledged, queue.getBytesInFlight());
                }
            }
            queue.ack(origin + sent, 65535);
            assertRange(queue.fetch(false), bytes, origin, sent, bytes.length);
            queue.ack(origin + bytes.length, 65535);
            assertEquals(0, queue.getCurrentSize());
            assertFalse(queue.hasMoreData());
            assertTrue(queue.fetch(true).isEmpty());
        }
    }

    @Test
    public void probeAcksMovePartiallySentHeadWithoutLosingItsUnsentTail() {
        byte[] bytes = payload(16);
        var queue = entry(4, bytes.length, 100).sendingQueue;
        assertEquals(bytes.length, queue.apiWrite(ByteBuffer.wrap(bytes)));
        long origin = queue.getAckSeq();
        for (int i = 0; i < 3; ++i) {
            assertTrue(queue.fetch(false).isEmpty());
            assertRange(List.of(queue.fetchZeroWindowProbe()), bytes, origin, i, i + 1);
            queue.ack(origin + i + 1, 0);
        }
        queue.ack(origin + 3, 16);
        assertRange(queue.fetch(false), bytes, origin, 3, bytes.length);
        assertRange(queue.fetch(true), bytes, origin, 3, bytes.length);
    }

    @Test
    public void sackAndPartialAckDoNotHideNewSuffixAndFinAckReleasesQueues() throws Exception {
        byte[] bytes = payload(16);
        var tcp = entry(4, bytes.length, 100);
        var queue = tcp.sendingQueue;
        assertEquals(bytes.length, queue.apiWrite(ByteBuffer.wrap(bytes)));
        long origin = queue.getAckSeq();
        queue.ack(origin, 8);
        assertRange(queue.fetch(false), bytes, origin, 0, 8);
        queue.markSackRetransmitSegments(List.of(new SAckTuple(origin + 4, origin + 8)));
        assertRange(queue.fetch(true), bytes, origin, 0, 4);
        queue.ack(origin + 2, 16);
        assertRange(queue.fetch(false), bytes, origin, 8, bytes.length);
        var retransmit = queue.fetch(true);
        assertRange(retransmit.subList(0, 1), bytes, origin, 2, 4);
        assertRange(retransmit.subList(1, retransmit.size()), bytes, origin, 8, bytes.length);
        tcp.setState(TcpState.FIN_WAIT_1);
        queue.markFinSent();
        queue.ack(origin + bytes.length + 1, 16);
        assertTrue(queue.ackOfFinReceived());
        assertFalse(queue.hasMoreData());
        for (var field : queue.getClass().getDeclaredFields()) {
            if (field.getType() != ArrayDeque.class) continue;
            field.setAccessible(true);
            assertTrue(((ArrayDeque<?>) field.get(queue)).isEmpty());
        }
    }

    @Test
    public void writableReentryCanAppendAndFetchAfterPartialAck() {
        byte[] bytes = payload(6);
        var tcp = entry(2, 16, 100);
        var queue = tcp.sendingQueue;
        long origin = queue.getAckSeq();
        assertEquals(4, queue.apiWrite(ByteBuffer.wrap(bytes, 0, 4)));
        queue.ack(origin, 2);
        assertRange(queue.fetch(false), bytes, origin, 0, 2);
        var called = new AtomicBoolean();
        tcp.setConnectionHandler(new ConnectionHandler() {
            public void readable(TcpEntry entry) {}
            public void destroy(TcpEntry entry) {}
            public void writable(TcpEntry entry) {
                assertFalse(called.getAndSet(true));
                assertEquals(2, queue.apiWrite(ByteBuffer.wrap(bytes, 4, 2)));
                assertRange(queue.fetch(false), bytes, origin, 2, 6);
            }
        });
        queue.ack(origin + 1, 6);
        assertTrue(called.get());
        assertRange(queue.fetch(true), bytes, origin, 1, 6);
    }
}
