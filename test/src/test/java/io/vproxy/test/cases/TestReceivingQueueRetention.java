package io.vproxy.test.cases;

import io.vproxy.base.util.ByteArray;
import io.vproxy.vfd.IPPort;
import io.vproxy.vpacket.conntrack.tcp.*;
import org.junit.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.*;

import static org.junit.Assert.*;

public class TestReceivingQueueRetention {
    private static TcpEntry entry(long origin) {
        var entry = new TcpEntry(null, new IPPort("192.0.2.1", 12345), new IPPort("192.0.2.2", 443), origin - 1);
        entry.setState(TcpState.ESTABLISHED);
        return entry;
    }

    private static byte[] payload(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; ++i) bytes[i] = (byte) (i * 31 + (i >>> 8));
        return bytes;
    }

    private static class Storage {
        final ArrayDeque<Segment> ready;
        final TreeMap<Long, Segment> outOfOrder;
        final TcpEntry.ReceivingQueue queue;

        @SuppressWarnings("unchecked")
        Storage(TcpEntry.ReceivingQueue queue) throws Exception {
            this.queue = queue;
            var readyField = queue.getClass().getDeclaredField("q");
            readyField.setAccessible(true);
            ready = (ArrayDeque<Segment>) readyField.get(queue);
            var oooField = queue.getClass().getDeclaredField("oooBuffer");
            oooField.setAccessible(true);
            outOfOrder = (TreeMap<Long, Segment>) oooField.get(queue);
        }

        void assertCopiedBackingBound() {
            long logical = 0, backing = 0;
            for (var segments : List.of(ready, outOfOrder.values())) {
                for (var segment : segments) {
                    // Derive unread length from sequence/read positions, not
                    // from the implementation's private cursor or offsets.
                    int visible = (int) (segment.seqEndExclusive - Math.max(segment.seqBeginInclusive, queue.getReadSeq()));
                    assertTrue("empty data must not occupy queue metadata", visible > 0);
                    logical += visible;
                    backing += segment.data.retainedBackingSize();
                    assertTrue("individual owned segment retains excessive backing",
                        segment.data.retainedBackingSize() <= visible * 4L);
                }
            }
            assertTrue(logical <= TcpEntry.RMEM_MAX);
            assertTrue(backing <= logical * 4);
            assertEquals(TcpEntry.RMEM_MAX - logical, queue.getWindow());
        }
    }

    @Test
    public void copiedInputCanBeReusedAndSmallTailDoesNotRetainLargeBacking() throws Exception {
        long origin = 100_000;
        var queue = entry(origin).receivingQueue;
        var storage = new Storage(queue);
        byte[] input = payload(65536), expected = input.clone();
        queue.storeCopied(origin, ByteArray.from(input).unmodifiable());
        Arrays.fill(input, (byte) 0);
        var dst = ByteBuffer.allocateDirect(expected.length + 2);
        dst.position(1).limit(expected.length);
        try {
            queue.apiRead(dst.asReadOnlyBuffer());
            fail("readonly destination must not consume queue contents");
        } catch (ReadOnlyBufferException expectedException) {
            assertEquals(expected.length, queue.getCurrentSize());
            assertEquals(origin, queue.getReadSeq());
        }
        assertEquals(expected.length - 1, queue.apiRead(dst));
        assertEquals(expected.length, dst.position());
        dst.position(1);
        byte[] read = new byte[expected.length - 1];
        dst.get(read);
        assertArrayEquals(Arrays.copyOf(expected, read.length), read);
        assertEquals(1, queue.getCurrentSize());
        assertEquals(1, storage.ready.getFirst().data.retainedBackingSize());
        assertEquals(expected[expected.length - 1], storage.ready.getFirst().data.get(0));
        storage.assertCopiedBackingBound();
    }

    @Test
    public void oneByteReadsBoundBackingAndAmortizedCompactionBytes() throws Exception {
        long origin = 100;
        var queue = entry(origin).receivingQueue;
        var storage = new Storage(queue);
        byte[] bytes = payload(65536);
        queue.storeCopied(origin, ByteArray.from(bytes));
        long backing = bytes.length, copied = 0;
        int compactions = 0;
        var one = ByteBuffer.allocate(1);
        for (int i = 0; i < bytes.length; ++i) {
            if ((i & 1) == 0) {
                one.clear();
                assertEquals(1, queue.apiRead(one));
                assertEquals(bytes[i], one.get(0));
            } else {
                var result = queue.apiRead(1);
                assertEquals(1, result.length());
                assertEquals(bytes[i], result.get(0));
            }
            assertEquals(origin + i + 1, queue.getReadSeq());
            assertEquals(origin + bytes.length, queue.getExpectingSeq());
            if (!storage.ready.isEmpty()) {
                long retained = storage.ready.getFirst().data.retainedBackingSize();
                if (retained < backing) {
                    copied += storage.ready.getFirst().data.length();
                    ++compactions;
                }
                backing = retained;
            }
            storage.assertCopiedBackingBound();
        }
        assertTrue(compactions > 0);
        assertTrue("compacting every read would violate the amortized copy budget", copied <= bytes.length / 3);
        System.out.println("receive_compaction input=" + bytes.length + " copied_tail_bytes=" + copied + " compactions=" + compactions);
        assertFalse(queue.hasMoreDataToRead());
    }

    @Test
    public void ingressPrefixAndGapRecoveryCompactOwnedSlices() throws Exception {
        long origin = 100_000;
        var queue = entry(origin).receivingQueue;
        var storage = new Storage(queue);
        byte[] large = payload(8192);
        queue.storeCopied(origin - large.length + 1, ByteArray.from(large));
        assertEquals(1, queue.getCurrentSize());
        assertEquals(1, storage.ready.getFirst().data.retainedBackingSize());
        assertEquals(large[large.length - 1], queue.apiRead(1).get(0));

        origin = queue.getExpectingSeq();
        byte[] bytes = payload(8208);
        queue.storeCopied(origin + 16, ByteArray.from(bytes).sub(16, bytes.length - 16));
        assertTrue(queue.hasOutOfOrderData());
        queue.storeCopied(origin, ByteArray.from(bytes).sub(0, bytes.length - 1));
        assertFalse(queue.hasOutOfOrderData());
        assertEquals(1, storage.ready.getLast().data.length());
        assertEquals(1, storage.ready.getLast().data.retainedBackingSize());
        storage.assertCopiedBackingBound();
        assertArrayEquals(bytes, queue.apiRead(Integer.MAX_VALUE).toJavaArray());
        assertEquals(origin + bytes.length, queue.getReadSeq());
    }

    @Test
    public void randomizedReorderingOverlapsAndPartialReadsMatchIndependentByteModel() throws Exception {
        for (long origin : new long[]{100, 0xfffffff0L}) {
            var random = new Random(0x524543564d454dL);
            var queue = entry(origin).receivingQueue;
            var storage = new Storage(queue);
            byte[] bytes = payload(4096);
            var input = ByteArray.from(bytes);
            var ranges = new ArrayList<int[]>();
            for (int offset = 0; offset < bytes.length; ) {
                int length = Math.min(bytes.length - offset, random.nextInt(80) + 1);
                ranges.add(new int[]{offset, length});
                offset += length;
            }
            for (int i = 0; i < 80; ++i) {
                int offset = random.nextInt(bytes.length);
                ranges.add(new int[]{offset, Math.min(bytes.length - offset, random.nextInt(128) + 1)});
            }
            Collections.shuffle(ranges, random);
            boolean[] received = new boolean[bytes.length];
            int expectedPrefix = 0, read = 0;
            for (var range : ranges) {
                var data = input.sub(range[0], range[1]);
                queue.storeCopied(origin + range[0], random.nextBoolean() ? data : data.unmodifiable());
                Arrays.fill(received, range[0], range[0] + range[1], true);
                while (expectedPrefix < received.length && received[expectedPrefix]) ++expectedPrefix;
                assertEquals(origin + expectedPrefix, queue.getExpectingSeq());
                int amount = Math.min(expectedPrefix - read, random.nextInt(65));
                if (amount > 0) {
                    byte[] actual;
                    if (random.nextBoolean()) {
                        actual = queue.apiRead(amount).toJavaArray();
                    } else {
                        var dst = ByteBuffer.allocateDirect(amount + 4);
                        dst.position(2).limit(2 + amount);
                        assertEquals(amount, queue.apiRead(dst));
                        assertEquals(2 + amount, dst.position());
                        actual = new byte[amount];
                        dst.position(2).get(actual);
                    }
                    assertArrayEquals(Arrays.copyOfRange(bytes, read, read + amount), actual);
                    read += amount;
                }
                assertEquals(origin + read, queue.getReadSeq());
                assertEquals(expectedPrefix - read, queue.getCurrentSize());
                storage.assertCopiedBackingBound();
            }
            assertArrayEquals(Arrays.copyOfRange(bytes, read, bytes.length), queue.apiRead(Integer.MAX_VALUE).toJavaArray());
            assertEquals(origin + bytes.length, queue.getReadSeq());
            storage.assertCopiedBackingBound();
        }
    }

    @Test
    public void legacySharedAndReadonlyPayloadsKeepTheirAliasContract() throws Exception {
        for (boolean readonly : new boolean[]{false, true}) {
            var queue = entry(100).receivingQueue;
            var storage = new Storage(queue);
            byte[] bytes = payload(8192);
            var input = ByteArray.from(bytes);
            queue.store(new Segment(100, readonly ? input.unmodifiable() : input));
            var visible = queue.apiRead(bytes.length - 1);
            bytes[0] = 77;
            assertEquals(77, visible.get(0));
            bytes[bytes.length - 1] = 88;
            assertEquals(bytes.length, storage.ready.getFirst().data.retainedBackingSize());
            var tail = queue.apiRead(1);
            assertEquals(88, tail.get(0));
            if (readonly) {
                try {
                    tail.set(0, (byte) 99);
                    fail("readonly payload must remain readonly");
                } catch (UnsupportedOperationException expected) {
                    assertEquals(88, bytes[bytes.length - 1]);
                }
            } else {
                tail.set(0, (byte) 99);
                assertEquals(99, bytes[bytes.length - 1]);
            }
        }
    }

    @Test
    public void mixedOwnedAndLegacyOverlapNeverCompactsSharedData() throws Exception {
        for (boolean legacyOnLeft : new boolean[]{true, false}) {
            var queue = entry(100).receivingQueue;
            var storage = new Storage(queue);
            byte[] shared = payload(8192);
            if (legacyOnLeft) {
                queue.store(new Segment(108, ByteArray.from(shared).unmodifiable()));
                queue.storeCopied(108 + shared.length - 1, ByteArray.from(1, 2));
            } else {
                queue.store(new Segment(108 + shared.length - 1, ByteArray.from(shared).unmodifiable()));
                queue.storeCopied(108, ByteArray.from(payload(shared.length)));
            }
            queue.storeCopied(100, ByteArray.allocate(8));
            assertFalse(queue.hasOutOfOrderData());
            assertEquals(8, queue.apiRead(ByteBuffer.allocate(8)));
            assertEquals(shared.length - 1, queue.apiRead(ByteBuffer.allocate(shared.length - 1)));
            shared[shared.length - 1] = 88;
            var tail = storage.ready.getFirst().data;
            int sharedOffset = legacyOnLeft ? 0 : tail.length() - 1;
            assertEquals(88, tail.get(sharedOffset));
            try {
                tail.set(sharedOffset, (byte) 99);
                fail("merging private input must not make a legacy suffix writable");
            } catch (UnsupportedOperationException expected) {
                assertEquals(88, shared[shared.length - 1]);
            }
        }
    }

    @Test
    public void copiedNativeInputSurvivesSourceScopeClosure() throws Exception {
        var queue = entry(100).receivingQueue;
        byte[] expected = payload(8192);
        try (var arena = Arena.ofConfined()) {
            var memory = arena.allocate(expected.length + 1, 1).asSlice(1);
            memory.copyFrom(MemorySegment.ofArray(expected));
            queue.storeCopied(100, ByteArray.from(memory.asReadOnly()));
        }
        assertArrayEquals(Arrays.copyOf(expected, expected.length - 1), queue.apiRead(expected.length - 1).toJavaArray());
        new Storage(queue).assertCopiedBackingBound();
        assertEquals(expected[expected.length - 1], queue.apiRead(1).get(0));
    }

    @Test
    public void backingCacheResetsAcrossOwnedAndLegacyHeads() throws Exception {
        var queue = entry(100).receivingQueue;
        var storage = new Storage(queue);
        byte[] large = payload(32768), shared = payload(16384), small = payload(8192);
        queue.storeCopied(100, ByteArray.from(large));
        queue.store(new Segment(100 + large.length, ByteArray.from(shared).unmodifiable()));
        queue.storeCopied(100 + large.length + shared.length, ByteArray.from(small));
        assertEquals(large.length - 1, queue.apiRead(ByteBuffer.allocate(large.length - 1)));
        assertEquals(1, storage.ready.getFirst().data.retainedBackingSize());
        assertEquals(large[large.length - 1], queue.apiRead(1).get(0));
        assertEquals(shared.length - 1, queue.apiRead(ByteBuffer.allocate(shared.length - 1)));
        shared[shared.length - 1] = 99;
        assertEquals(shared.length, storage.ready.getFirst().data.retainedBackingSize());
        assertEquals(99, queue.apiRead(1).get(0));
        assertEquals(small.length - 1, queue.apiRead(ByteBuffer.allocate(small.length - 1)));
        assertEquals(1, storage.ready.getFirst().data.retainedBackingSize());
        storage.assertCopiedBackingBound();
        assertEquals(small[small.length - 1], queue.apiRead(1).get(0));
        assertEquals(TcpEntry.RMEM_MAX, queue.getWindow());
    }

    @Test
    public void emptyFutureSegmentsCannotConsumeMetadataOrAdvertiseSack() throws Exception {
        var queue = entry(100).receivingQueue;
        var storage = new Storage(queue);
        for (int i = 0; i < 10000; ++i) {
            if ((i & 1) == 0) queue.store(new Segment(101 + i, ByteArray.allocate(0)));
            else queue.storeCopied(101 + i, ByteArray.allocate(0));
        }
        assertEquals(100, queue.getExpectingSeq());
        assertEquals(100, queue.getReadSeq());
        assertEquals(0, queue.getCurrentSize());
        assertFalse(queue.hasOutOfOrderData());
        assertTrue(queue.getSAckBlocks().isEmpty());
        assertTrue(storage.outOfOrder.isEmpty());
        storage.assertCopiedBackingBound();
    }

    @Test
    public void outOfOrderMemoryLeavesRoomForItsMissingGap() throws Exception {
        for (boolean copied : new boolean[]{true, false}) {
            var queue = entry(100).receivingQueue;
            byte[] bytes = payload(TcpEntry.RMEM_MAX + 1);
            var data = ByteArray.from(bytes);
            if (copied) queue.storeCopied(101, data.sub(1, TcpEntry.RMEM_MAX));
            else queue.store(new Segment(101, data.sub(1, TcpEntry.RMEM_MAX)));
            assertEquals(1, queue.getWindow());
            assertEquals(100, queue.getExpectingSeq());
            assertEquals(100 + TcpEntry.RMEM_MAX, queue.getSAckBlocks().getFirst().seqEndExclusive);
            if (copied) queue.storeCopied(100, data.sub(0, 1));
            else queue.store(new Segment(100, data.sub(0, 1)));
            assertFalse(queue.hasOutOfOrderData());
            assertEquals(100 + TcpEntry.RMEM_MAX, queue.getExpectingSeq());
            assertEquals(TcpEntry.RMEM_MAX, queue.getCurrentSize());
            assertEquals(0, queue.getWindow());
            assertArrayEquals(Arrays.copyOf(bytes, TcpEntry.RMEM_MAX), queue.apiRead(Integer.MAX_VALUE).toJavaArray());
            assertEquals(TcpEntry.RMEM_MAX, queue.getWindow());
        }
    }

    @Test
    public void duplicatesAreTrimmedBeforeClippingNewSuffixToAvailableMemory() throws Exception {
        for (boolean copied : new boolean[]{true, false}) {
            var queue = entry(100).receivingQueue;
            var data = ByteArray.from(payload(TcpEntry.RMEM_MAX));
            if (copied) queue.storeCopied(100, data.sub(0, data.length() - 1));
            else queue.store(new Segment(100, data.sub(0, data.length() - 1)));
            assertEquals(1, queue.getWindow());
            if (copied) queue.storeCopied(100, data);
            else queue.store(new Segment(100, data));
            assertEquals(100 + data.length(), queue.getExpectingSeq());
            assertEquals(data.length(), queue.getCurrentSize());
            assertArrayEquals(data.toJavaArray(), queue.apiRead(Integer.MAX_VALUE).toJavaArray());
            assertEquals(TcpEntry.RMEM_MAX, queue.getWindow());
        }
    }

    @Test
    public void futurePayloadBeyondReceiveSequenceCapacityCannotOccupyQueue() throws Exception {
        var queue = entry(0xfffffff0L).receivingQueue;
        var storage = new Storage(queue);
        long seq = queue.getExpectingSeq();
        queue.storeCopied(seq + TcpEntry.RMEM_MAX, ByteArray.from(1, 2, 3));
        queue.store(new Segment(seq + TcpEntry.RMEM_MAX + 1, ByteArray.from(1, 2, 3)));
        assertFalse(queue.hasOutOfOrderData());
        assertEquals(seq, queue.getExpectingSeq());
        storage.assertCopiedBackingBound();
        queue.storeCopied(seq + TcpEntry.RMEM_MAX - 1, ByteArray.from(1, 2, 3));
        assertEquals(1, storage.outOfOrder.firstEntry().getValue().data.length());
        assertEquals(seq + TcpEntry.RMEM_MAX, queue.getSAckBlocks().getFirst().seqEndExclusive);
        storage.assertCopiedBackingBound();
    }

    @Test
    public void copiedOversizeInputRespectsWindowAndReadableReentry() throws Exception {
        var entry = entry(100);
        var queue = entry.receivingQueue;
        var storage = new Storage(queue);
        byte[] bytes = payload(TcpEntry.RMEM_MAX + 16);
        var calls = new int[1];
        entry.setConnectionHandler(new ConnectionHandler() {
            public void writable(TcpEntry entry) {}
            public void destroy(TcpEntry entry) {}
            public void readable(TcpEntry entry) {
                ++calls[0];
                assertEquals(0, queue.getWindow());
                assertEquals(TcpEntry.RMEM_MAX, queue.getCurrentSize());
                var result = queue.apiRead(TcpEntry.RMEM_MAX - 1);
                assertArrayEquals(Arrays.copyOf(bytes, TcpEntry.RMEM_MAX - 1), result.toJavaArray());
                storage.assertCopiedBackingBound();
            }
        });
        queue.storeCopied(100, ByteArray.from(bytes));
        assertEquals(1, calls[0]);
        assertEquals(100 + TcpEntry.RMEM_MAX, queue.getExpectingSeq());
        assertEquals(1, storage.ready.getFirst().data.retainedBackingSize());
        assertEquals(bytes[TcpEntry.RMEM_MAX - 1], queue.apiRead(1).get(0));
        storage.assertCopiedBackingBound();
    }
}
