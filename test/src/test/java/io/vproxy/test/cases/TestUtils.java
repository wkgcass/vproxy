package io.vproxy.test.cases;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.bytearray.CompositeByteArray;
import io.vproxy.base.util.bytearray.ByteArrayBuilder;
import io.vproxy.base.util.bytearray.RandomAccessFileByteArray;
import io.vproxy.base.util.bytearray.SimpleByteArray;
import io.vproxy.base.util.file.MappedByteBufferLogger;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.base.util.objectpool.ConcurrentObjectPool;
import io.vproxy.base.util.ringbuffer.SimpleRingBuffer;
import io.vproxy.base.util.ringbuffer.SimpleRingBufferReaderCommitter;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.IPv6;
import io.vproxy.vfd.MacAddress;
import io.vproxy.vpacket.tuples.PacketFullTuple;
import io.vproxy.commons.util.IOUtils;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class TestUtils {
    private int referenceChecksumIntermediate(int seed, byte[] bytes, int len) {
        int sum = seed;
        for (int i = 0; i < len; i += 2) {
            int word = (bytes[i] & 0xff) << 8;
            if (i + 1 < len) word |= bytes[i + 1] & 0xff;
            sum += word;
            while (sum > 0xffff) sum = (sum & 0xffff) + 1;
        }
        return sum;
    }

    private ByteArray randomComposite(byte[] bytes, Random random) {
        ByteArray result = ByteArray.allocate(0);
        int off = 0;
        while (off < bytes.length) {
            int n = Math.min(bytes.length - off, 1 + random.nextInt(23));
            result = result.concat(ByteArray.from(Arrays.copyOfRange(bytes, off, off + n)));
            off += n;
        }
        return result;
    }

    @Test
    public void optimizedByteArrayChecksumMatchesReferenceAcrossSegments() {
        Random random = new Random(0x5eed);
        int[] lengths = {0, 1, 2, 3, 20, 40, 64, 1500, 65535};
        int[] seeds = {0, 1, 0x7fff, 0xffff};
        for (int len : lengths) {
            byte[] bytes = new byte[len];
            random.nextBytes(bytes);
            ByteArray flat = ByteArray.from(bytes);
            ByteArray composite = randomComposite(bytes, random);
            ByteArray[] variants = {flat, composite, composite.unmodifiable()};
            for (ByteArray array : variants) {
                for (int limit : new int[]{0, len / 2, len}) {
                    for (int seed : seeds) {
                        assertEquals("type=" + array.getClass().getSimpleName() + ", len=" + len + ", limit=" + limit + ", seed=" + seed,
                            referenceChecksumIntermediate(seed, bytes, limit),
                            Utils.calculateChecksumIntermediate(seed, array, limit));
                    }
                }
            }
            if (len > 0) {
                ByteBuffer direct = ByteBuffer.allocateDirect(len);
                direct.put(bytes).flip();
                ByteArray memorySegment = ByteArray.from(direct);
                for (int limit : new int[]{len / 2, len}) {
                    assertEquals(referenceChecksumIntermediate(0, bytes, limit),
                        Utils.calculateChecksumIntermediate(0, memorySegment, limit));
                }
            }
        }

        byte[] joined = {1, 2, 3, 4, 5};
        for (int split = 0; split <= joined.length; ++split) {
            ByteArray first = ByteArray.from(Arrays.copyOfRange(joined, 0, split));
            ByteArray second = ByteArray.from(Arrays.copyOfRange(joined, split, joined.length));
            assertEquals(referenceChecksumIntermediate(0, joined, joined.length),
                0xffff - Utils.calculateChecksum(first, second));
        }
    }

    @Test
    public void simpleByteArrayMultiByteReadsAndWritesMatchNetworkOrder() {
        ByteArray array = ByteArray.allocate(24);
        array.int16(0, 0x89ab).int16ReverseNetworkByteOrder(2, 0x89ab)
            .int32(4, 0x89abcdef).int32ReverseNetworkByteOrder(8, 0x89abcdef)
            .int64(12, 0x0123456789abcdefL);
        assertEquals(0x89ab, array.uint16(0));
        assertEquals(0x89ab, array.uint16ReverseNetworkByteOrder(2));
        assertEquals(0x89abcdef, array.int32(4));
        assertEquals(0x89abcdef, array.int32ReverseNetworkByteOrder(8));
        assertEquals(0x0123456789abcdefL, array.int64(12));
        array.int64ReverseNetworkByteOrder(16, 0x0123456789abcdefL);
        assertEquals(0x0123456789abcdefL, array.int64ReverseNetworkByteOrder(16));
        assertEquals(0xefcdab8967452301L, array.int64(16));
        assertEquals(array.uint16(0), array.sub(0, array.length()).uint16(0));
        assertEquals(array.int32(5), array.unmodifiable().int32(5));
    }

    @Test
    public void segmentedScalarReadsMatchFlatReadsAcrossEveryBoundary() {
        ByteArray flat = ByteArray.from(0x80, 0xff, 0x01, 0x23, 0x45, 0x67, 0x89, 0xab,
            0xcd, 0xef, 0x10, 0x20, 0x30, 0x40, 0x50, 0x60);
        ByteArray segmented = ByteArray.from(0x80, 0xff, 0x01)
            .concat(ByteArray.from(0x23, 0x45, 0x67, 0x89, 0xab))
            .concat(ByteArray.from(0xcd, 0xef, 0x10, 0x20, 0x30, 0x40, 0x50, 0x60));
        ByteArray[] variants = {segmented, segmented.sub(1, segmented.length() - 1), segmented.unmodifiable()};
        for (ByteArray variant : variants) {
            int offsetBase = variant == variants[1] ? 1 : 0;
            for (int i = 0; i + 8 <= variant.length(); ++i) {
                int off = i + offsetBase;
                assertEquals(flat.uint24(i + offsetBase), variant.uint24(i));
                assertEquals(flat.uint24ReverseNetworkByteOrder(i + offsetBase), variant.uint24ReverseNetworkByteOrder(i));
                assertEquals(flat.uint32(i + offsetBase), variant.uint32(i));
                assertEquals(flat.uint32ReverseNetworkByteOrder(i + offsetBase), variant.uint32ReverseNetworkByteOrder(i));
                assertEquals(flat.int64(off), variant.int64(i));
                assertEquals(flat.int64ReverseNetworkByteOrder(off), variant.int64ReverseNetworkByteOrder(i));
            }
        }
    }

    @Test
    public void byteArrayRangeChecksAvoidIntegerOverflowAndPreserveOverlappingCopyOrder() {
        ByteArray array = ByteArray.from(1, 2, 3, 4);
        try {
            array.uint16(Integer.MAX_VALUE);
            fail("overflowing scalar range must fail");
        } catch (ArrayIndexOutOfBoundsException expected) {
            // expected
        }
        try {
            array.sub(Integer.MAX_VALUE, 1);
            fail("overflowing slice range must fail");
        } catch (ArrayIndexOutOfBoundsException expected) {
            // expected
        }
        try {
            array.sub(-1, 0);
            fail("negative slice offset must fail");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        ByteArray partialWrite = ByteArray.from(1, 2);
        try {
            partialWrite.int16(1, 0xabcd);
            fail("out-of-range scalar writes must fail");
        } catch (ArrayIndexOutOfBoundsException expected) {
            assertArrayEquals(new byte[]{1, (byte) 0xab}, partialWrite.toJavaArray());
        }
        array.copyInto(array, 1, 0, 3);
        assertArrayEquals(new byte[]{1, 1, 1, 1}, array.toJavaArray());

        ByteArray backing = ByteArray.from(1, 2, 3, 4);
        ByteArray composite = backing.sub(0, 2).concat(backing.sub(2, 2));
        composite.copyInto(backing, 1, 0, 3);
        assertArrayEquals(new byte[]{1, 1, 1, 1}, backing.toJavaArray());

        ByteBuffer readOnly = ByteBuffer.allocate(4).asReadOnlyBuffer();
        try {
            array.byteBufferPut(readOnly, 0, 2);
            fail("read-only destination must reject writes");
        } catch (java.nio.ReadOnlyBufferException expected) {
            // expected
        }
    }

    @Test
    public void segmentedByteArrayCopyUsesSameVisibleBytes() {
        byte[] bytes = new byte[100];
        new Random(123).nextBytes(bytes);
        ByteArray source = randomComposite(bytes, new Random(456)).sub(7, 81);
        ByteArray destination = ByteArray.allocate(90);
        source.copyInto(destination, 4, 3, 70);
        assertArrayEquals(Arrays.copyOfRange(bytes, 10, 80), destination.sub(4, 70).toJavaArray());

        ByteArray wrapped = source.unmodifiable();
        ByteArray target = ByteArray.allocate(80);
        wrapped.copyInto(target, 2, 1, 60);
        assertArrayEquals(Arrays.copyOfRange(bytes, 8, 68), target.sub(2, 60).toJavaArray());
    }

    @Test
    public void compositeCapturesMutableChildLengthAndKeepsSharedBytesVisible() {
        ByteArrayBuilder builder = new ByteArrayBuilder().append(1);
        ByteArray composite = builder.concat(ByteArray.from(2));
        builder.append(3);
        assertEquals(2, composite.length());
        assertArrayEquals(new byte[]{1, 2}, composite.toJavaArray());
        builder.set(0, (byte) 9);
        assertEquals(9, composite.get(0));
    }

    @Test
    public void repeatedByteArrayConcatKeepsTreeBalancedAndBytesInOrder() {
        int count = 20_000;
        ByteArray result = ByteArray.allocate(0);
        for (int i = 0; i < count; ++i) {
            result = result.concat(ByteArray.from((byte) i));
        }
        assertEquals(count, result.length());
        assertTrue("depth=" + result.treeDepth(), result.treeDepth() <= 20);
        for (int i = 0; i < count; ++i) {
            assertEquals((byte) i, result.get(i));
        }

        ByteArray prepended = ByteArray.allocate(0);
        for (int i = 0; i < count; ++i) {
            prepended = ByteArray.from((byte) i).concat(prepended);
        }
        assertEquals(count, prepended.length());
        assertTrue("depth=" + prepended.treeDepth(), prepended.treeDepth() <= 20);
        for (int i = 0; i < count; ++i) {
            assertEquals((byte) (count - i - 1), prepended.get(i));
        }
    }

    @Test
    public void addressAndTupleHashesKeepTheirOldValueWithoutBoxingAndByteArrayHashDistributesPrefixes() {
        IP ip = IP.from("2001:db8::1");
        IPPort ipPort = new IPPort(ip, 443);
        assertEquals(Objects.hash(ip, 443), ipPort.hashCode());

        IPv6 ipv6 = (IPv6) ip;
        assertEquals(Objects.hash(ipv6.getIPv6Value0(), ipv6.getIPv6Value1(), ipv6.getIPv6Value2(), ipv6.getIPv6Value3()), ipv6.hashCode());
        MacAddress mac = MacAddress.BROADCAST;
        assertEquals(Objects.hash(-1, 0xffff), mac.hashCode());

        MacAddress srcMac = new MacAddress("00:11:22:33:44:55");
        MacAddress dstMac = new MacAddress("66:77:88:99:aa:bb");
        IP src = IP.from("192.0.2.1");
        IP dst = IP.from("198.51.100.2");
        PacketFullTuple tuple = new PacketFullTuple(3, srcMac, dstMac, src, dst, 6, 123, 443);
        PacketFullTuple same = new PacketFullTuple(3, srcMac, dstMac, src, dst, 6, 123, 443);
        assertEquals(tuple, same);
        assertEquals(tuple.hashCode(), same.hashCode());
        assertEquals(Objects.hash(3, srcMac, dstMac, src, dst, 6, 123, 443), tuple.hashCode());

        ByteArray content = ByteArray.from(1, 0, 255, 42);
        ByteArray segmented = ByteArray.from(1, 0).concat(ByteArray.from(255, 42));
        int contentHash = content.hashCode();
        assertEquals(contentHash, segmented.hashCode());
        assertEquals(contentHash, segmented.unmodifiable().hashCode());
        content.set(0, (byte) 2);
        assertNotEquals(contentHash, content.hashCode());

        byte[] value = new byte[4];
        ByteArray mutable = ByteArray.from(value);
        Set<Integer> hashes = new HashSet<>();
        for (int high = 0; high < 256; ++high) {
            for (int low = 0; low < 256; ++low) {
                value[0] = (byte) high;
                value[1] = (byte) low;
                value[2] = 42;
                value[3] = 7;
                hashes.add(mutable.hashCode());
            }
        }
        assertTrue("fixed suffix prefixes should no longer collapse to one hash", hashes.size() > 60_000);
    }

    @Test
    public void compositeToNewJavaArrayHolderOffsets() {
        ByteArray array = ByteArray.from(1, 2).concat(ByteArray.from(3, 4)).concat(ByteArray.from(5, 6));
        for (int from = 0; from <= array.length(); ++from) {
            for (int len = 0; len <= array.length() - from; ++len) {
                byte[] holder = new byte[len + 4];
                Arrays.fill(holder, (byte) -1);
                array.sub(from, len).toNewJavaArray(holder, 2);
                byte[] expected = new byte[holder.length];
                Arrays.fill(expected, (byte) -1);
                for (int i = 0; i < len; ++i) {
                    expected[i + 2] = (byte) (from + i + 1);
                }
                assertArrayEquals(expected, holder);
            }
        }
    }

    @Test
    public void concatOrCopy() {
        var left = ByteArray.from(1, 2);
        var right = ByteArray.from(3, 4);
        var shared = left.concatOrCopy(0, right);
        assertTrue(shared instanceof CompositeByteArray);
        left.set(0, (byte) 5);
        assertArrayEquals(new byte[]{5, 2, 3, 4}, shared.toJavaArray());

        var copied = left.sub(0, 1).concatOrCopy(0, right);
        assertTrue(copied instanceof SimpleByteArray);
        left.set(0, (byte) 6);
        right.set(0, (byte) 7);
        assertArrayEquals(new byte[]{5, 3, 4}, copied.toJavaArray());

        var empty = ByteArray.allocate(0);
        assertSame(left, left.concatOrCopy(0, empty));
        assertSame(right, empty.concatOrCopy(0, right));
        assertSame(empty, empty.concatOrCopy(0, empty));
        var one = ByteArray.from((byte) 42);
        var zeroLimit = one.concatOrCopy(0, empty);
        assertSame(one, zeroLimit);
        one.set(0, (byte) 0);
        assertEquals(0, zeroLimit.uint8(0));
        try {
            left.concatOrCopy(-1, right);
            fail("negative limit should be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void concatOrCopySlices() {
        var backing = ByteArray.from(0, 1, 2, 3).concat(ByteArray.from(4, 5, 6, 7));
        var slice = backing.sub(3, 2); // crosses both composite children
        var tail = ByteArray.from((byte) 8);
        // Nine backing bytes minus three visible bytes wastes six bytes.
        assertTrue(slice.concatOrCopy(6, tail) instanceof CompositeByteArray);
        var copied = slice.concatOrCopy(5, tail);
        assertTrue(copied instanceof SimpleByteArray);
        assertArrayEquals(new byte[]{3, 4, 8}, copied.toJavaArray());
        assertArrayEquals(new byte[]{8, 3, 4}, tail.concatOrCopy(5, slice).toJavaArray());
        assertArrayEquals(new byte[]{5, 6, 8}, backing.sub(5, 2).concatOrCopy(5, tail).toJavaArray());
        assertArrayEquals(new byte[]{1, 2, 8}, backing.sub(1, 2).concatOrCopy(5, tail).toJavaArray());
        backing.set(3, (byte) 99);
        assertArrayEquals(new byte[]{3, 4, 8}, copied.toJavaArray());

        // Opaque wrappers are measured only by their visible length.
        assertTrue(slice.unmodifiable().concatOrCopy(0, tail) instanceof CompositeByteArray);
        assertTrue(slice.concatOrCopy(2, ByteArray.allocate(0)) instanceof SimpleByteArray);
        assertTrue(ByteArray.allocate(0).concatOrCopy(2, slice) instanceof SimpleByteArray);
    }

    @Test
    public void concatOrCopyNestedWithoutWaste() {
        var empty = ByteArray.allocate(0);
        ByteArray nested = new CompositeByteArray(ByteArray.from((byte) 1), empty);
        var tail = ByteArray.from((byte) 2);
        assertTrue(nested.concatOrCopy(0, tail) instanceof CompositeByteArray);
        nested = new CompositeByteArray(nested, empty);
        assertTrue(nested.concatOrCopy(0, tail) instanceof CompositeByteArray);

        assertArrayEquals(new byte[]{1, 2}, nested.concatOrCopy(0, tail).toJavaArray());
        assertArrayEquals(new byte[]{2, 1}, tail.concatOrCopy(0, nested).toJavaArray());
    }

    @Test
    public void concatOrCopyNestedSliceAndLargeBacking() {
        var empty = ByteArray.allocate(0);
        var nested = new CompositeByteArray(new CompositeByteArray(ByteArray.from(1, 2), empty), empty);
        var slice = nested.sub(1, 1);
        var tail = ByteArray.from((byte) 3);
        // Three backing bytes minus two visible bytes wastes one byte.
        assertTrue(slice.concatOrCopy(1, tail) instanceof CompositeByteArray);
        var copied = slice.concatOrCopy(0, tail);
        assertTrue(copied instanceof SimpleByteArray);
        assertArrayEquals(new byte[]{2, 3}, copied.toJavaArray());

        // Model a large opaque backing without allocating gigabytes.
        var huge = new SimpleByteArray(new byte[]{9}) {
            @Override
            public int length() {
                return Integer.MAX_VALUE;
            }
        };
        var tiny = huge.sub(0, 1);
        assertArrayEquals(new byte[]{9, 3}, tiny.concatOrCopy(Integer.MAX_VALUE, tail).toJavaArray());
        assertTrue(tiny.concatOrCopy(Integer.MAX_VALUE - 1, tail) instanceof CompositeByteArray);
        assertTrue(tiny.concatOrCopy(Integer.MAX_VALUE - 2, tail) instanceof SimpleByteArray);
    }

    @After
    public void tearDown() throws Exception {
        if (tempPath != null) {
            IOUtils.deleteDirectory(tempPath.toFile());
        }
    }

    private void committerIdleCheck(SimpleRingBufferReaderCommitter committer) {
        assertTrue(committer.isIdle());
        assertTrue(committer.check());
        assertEquals("SimpleRingBufferReaderCommitter[]", committer.toString());
    }

    @Test
    public void ringBufferReaderCommitter() {
        var rb = SimpleRingBuffer.allocateDirect(10);
        try {
            var committer = new SimpleRingBufferReaderCommitter(rb);

            assertEquals(0, committer.read().length);
            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("abc")));
            var segs = committer.read();
            assertEquals(1, segs.length);
            assertEquals(3, segs[0].byteSize());
            assertArrayEquals("abc".getBytes(), segs[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=0, ePos=3, len=3}]", committer.toString());

            committer.commit(segs[0]);
            committerIdleCheck(committer);
        } finally {
            rb.clean();
        }
    }

    @Test
    public void ringBufferReaderCommitterEBeforeS() {
        var rb = SimpleRingBuffer.allocateDirect(10);
        try {
            var committer = new SimpleRingBufferReaderCommitter(rb);

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("abcd")));
            var segs1 = committer.read();
            assertEquals(1, segs1.length);
            assertEquals(4, segs1[0].byteSize());
            assertArrayEquals("abcd".getBytes(), segs1[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=0, ePos=4, len=4}]", committer.toString());

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("efg")));
            var segs2 = committer.read();
            assertEquals(1, segs2.length);
            assertEquals(3, segs2[0].byteSize());
            assertArrayEquals("efg".getBytes(), segs2[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=0, ePos=4, len=4}, {sPos=4, ePos=7, len=3}]", committer.toString());

            committer.commit(segs1[0]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}]", committer.toString());

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("hijkl")));
            var segs3 = committer.read();
            assertEquals(2, segs3.length);
            assertEquals(3, segs3[0].byteSize());
            assertEquals(2, segs3[1].byteSize());
            assertArrayEquals("hij".getBytes(), segs3[0].toArray(ValueLayout.JAVA_BYTE));
            assertArrayEquals("kl".getBytes(), segs3[1].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}, {sPos=7, ePos=0, len=3}, {sPos=0, ePos=2, len=2}]", committer.toString());

            committer.commit(segs2[0]);
            committer.commit(segs3[0]);
            committer.commit(segs3[1]);

            committerIdleCheck(committer);
        } finally {
            rb.clean();
        }
    }

    @Test
    public void ringBufferReaderCommitterUnorderedCommit() {
        var rb = SimpleRingBuffer.allocateDirect(10);
        try {
            var committer = new SimpleRingBufferReaderCommitter(rb);

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("abcd")));
            var segs1 = committer.read();
            assertEquals(1, segs1.length);
            assertEquals(4, segs1[0].byteSize());
            assertArrayEquals("abcd".getBytes(), segs1[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=0, ePos=4, len=4}]", committer.toString());

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("efg")));
            var segs2 = committer.read();
            assertEquals(1, segs2.length);
            assertEquals(3, segs2[0].byteSize());
            assertArrayEquals("efg".getBytes(), segs2[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=0, ePos=4, len=4}, {sPos=4, ePos=7, len=3}]", committer.toString());

            committer.commit(segs1[0]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}]", committer.toString());

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("hijkl")));
            var segs3 = committer.read();
            assertEquals(2, segs3.length);
            assertEquals(3, segs3[0].byteSize());
            assertEquals(2, segs3[1].byteSize());
            assertArrayEquals("hij".getBytes(), segs3[0].toArray(ValueLayout.JAVA_BYTE));
            assertArrayEquals("kl".getBytes(), segs3[1].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}, {sPos=7, ePos=0, len=3}, {sPos=0, ePos=2, len=2}]", committer.toString());

            int n = rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("mn[...]")));
            assertEquals(2, n);

            var segs4 = committer.read();
            assertEquals(1, segs4.length);
            assertEquals(2, segs4[0].byteSize());
            assertArrayEquals("mn".getBytes(), segs4[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}, {sPos=7, ePos=0, len=3}, {sPos=0, ePos=2, len=2}, {sPos=2, ePos=4, len=2}]", committer.toString());

            committer.commit(segs3[1]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}, {sPos=7, ePos=0, len=3}, {sPos=2, ePos=4, len=2}]", committer.toString());

            committer.commit(segs2[0]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=7, ePos=0, len=3}, {sPos=2, ePos=4, len=2}]", committer.toString());

            committer.commit(segs3[0]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=2, ePos=4, len=2}]", committer.toString());

            committer.commit(segs4[0]);
            committerIdleCheck(committer);
        } finally {
            rb.clean();
        }
    }

    @Test
    public void ringBufferReaderCommitterUnorderedCommit2() {
        var rb = SimpleRingBuffer.allocateDirect(10);
        try {
            var committer = new SimpleRingBufferReaderCommitter(rb);

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("abcd")));
            var segs1 = committer.read();
            assertEquals(1, segs1.length);
            assertEquals(4, segs1[0].byteSize());
            assertArrayEquals("abcd".getBytes(), segs1[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=0, ePos=4, len=4}]", committer.toString());

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("efg")));
            var segs2 = committer.read();
            assertEquals(1, segs2.length);
            assertEquals(3, segs2[0].byteSize());
            assertArrayEquals("efg".getBytes(), segs2[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=0, ePos=4, len=4}, {sPos=4, ePos=7, len=3}]", committer.toString());

            committer.commit(segs1[0]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}]", committer.toString());

            rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("hijkl")));
            var segs3 = committer.read();
            assertEquals(2, segs3.length);
            assertEquals(3, segs3[0].byteSize());
            assertEquals(2, segs3[1].byteSize());
            assertArrayEquals("hij".getBytes(), segs3[0].toArray(ValueLayout.JAVA_BYTE));
            assertArrayEquals("kl".getBytes(), segs3[1].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}, {sPos=7, ePos=0, len=3}, {sPos=0, ePos=2, len=2}]", committer.toString());

            int n = rb.storeBytesFrom(ByteArrayChannel.fromFull(ByteArray.from("mn[...]")));
            assertEquals(2, n);

            var segs4 = committer.read();
            assertEquals(1, segs4.length);
            assertEquals(2, segs4[0].byteSize());
            assertArrayEquals("mn".getBytes(), segs4[0].toArray(ValueLayout.JAVA_BYTE));
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}, {sPos=7, ePos=0, len=3}, {sPos=0, ePos=2, len=2}, {sPos=2, ePos=4, len=2}]", committer.toString());

            committer.commit(segs4[0]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}, {sPos=7, ePos=0, len=3}, {sPos=0, ePos=2, len=2}]", committer.toString());

            committer.commit(segs3[1]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}, {sPos=7, ePos=0, len=3}]", committer.toString());

            committer.commit(segs3[0]);
            assertEquals("SimpleRingBufferReaderCommitter[{sPos=4, ePos=7, len=3}]", committer.toString());

            committer.commit(segs2[0]);
            committerIdleCheck(committer);
        } finally {
            rb.clean();
        }
    }

    private Path tempPath; // will teardown
    private String prefix;

    private String readTempContent(String sequence) throws IOException {
        var bytes = Files.readAllBytes(Path.of(tempPath.toString(), prefix + sequence + ".log"));
        if (bytes[bytes.length - 1] != 0) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return MemorySegment.ofArray(bytes).getString(0);
    }

    private void assertFilesCnt(int cnt) {
        var files = tempPath.toFile().listFiles();
        assert files != null;
        assertEquals(cnt, files.length);

        var set = Arrays.stream(files).map(File::getName).collect(Collectors.toSet());
        for (int i = 0; i < cnt; ++i) {
            var s = "" + i;
            if (s.length() < 4) {
                s = "0".repeat(4 - s.length()) + s;
            }
            assertTrue(set.contains(prefix + s + ".log"));
        }
    }

    private String getCurrentTimestampForFileName() {
        return Utils.formatTimestampForFileName(System.currentTimeMillis());
    }

    @Test
    public void simpleMappedByteBufferLogger() throws Exception {
        tempPath = Files.createTempDirectory("test-simpleMappedByteBufferLogger");
        prefix = "test-" + getCurrentTimestampForFileName() + "-";

        var logger = new MappedByteBufferLogger(tempPath.toString(), prefix, ".log", 1048576, 65536);
        logger.writeAndBlock("hello world\n");

        Thread.sleep(500);

        logger.close();

        var files = tempPath.toFile().listFiles();
        assert files != null;

        assertFilesCnt(1);
        var content = readTempContent("0000");
        assertEquals("hello world\n", content);
    }

    @Test
    public void simpleMappedByteBufferLoggerWriteOrDrop() throws Throwable {
        tempPath = Files.createTempDirectory("test-simpleMappedByteBufferLoggerWriteOrDrop");
        prefix = "test-" + getCurrentTimestampForFileName() + "-";

        var logger = new MappedByteBufferLogger(tempPath.toString(), prefix, ".log", 1048576, 65536);
        var res = logger.writeOrDrop("hello world\n");
        assertEquals(MappedByteBufferLogger.WriteOrDropResultType.WRITTEN, res.type());
        assertNull(res.writablePromise());

        logger.close();

        var files = tempPath.toFile().listFiles();
        assert files != null;

        assertFilesCnt(1);
        var content = readTempContent("0000");
        assertEquals("hello world\n", content);
    }

    @Test
    public void splitMappedByteBufferLogger() throws Exception {
        tempPath = Files.createTempDirectory("test-splitMappedByteBufferLogger");
        prefix = "test-" + getCurrentTimestampForFileName() + "-";

        var logger = new MappedByteBufferLogger(tempPath.toString(), prefix, ".log", 8, 0);
        logger.writeAndBlock("hello world\n");

        Thread.sleep(500);

        logger.close();

        var files = tempPath.toFile().listFiles();
        assert files != null;

        assertFilesCnt(2);

        var content = readTempContent("0000");
        assertEquals("hello wo", content);

        content = readTempContent("0001");
        assertEquals("rld\n", content);
    }

    @Test
    public void splitMappedByteBufferLoggerWriteOrDrop() throws Throwable {
        tempPath = Files.createTempDirectory("test-splitMappedByteBufferLoggerWriteOrDrop");
        prefix = "test-" + getCurrentTimestampForFileName() + "-";

        var logger = new MappedByteBufferLogger(tempPath.toString(), prefix, ".log", 8, 0);
        var res = logger.writeOrDrop("hello world\n");
        assertEquals(MappedByteBufferLogger.WriteOrDropResultType.DROP_PENDING, res.type());
        res.writablePromise().block();
        res = logger.writeOrDrop("hello world\n");
        assertEquals(MappedByteBufferLogger.WriteOrDropResultType.WRITTEN, res.type());

        Thread.sleep(500);

        logger.close();

        var files = tempPath.toFile().listFiles();
        assert files != null;

        assertFilesCnt(2);

        var content = readTempContent("0000");
        assertEquals("hello wo", content);

        content = readTempContent("0001");
        assertEquals("rld\n", content);
    }

    @Test
    public void createMappedByteBufferLogger() throws Exception {
        tempPath = Files.createTempDirectory("test-createMappedByteBufferLogger");
        prefix = "test-" + getCurrentTimestampForFileName() + "-";

        var logger = new MappedByteBufferLogger(tempPath.toString(), prefix, ".log", 8, 1);
        logger.writeAndBlock("1234567");
        logger.writeAndBlock("abcdefg");
        logger.writeAndBlock("ABCDEFG");

        Thread.sleep(500);

        logger.close();

        var files = tempPath.toFile().listFiles();
        assert files != null;

        assertFilesCnt(4);

        var content = readTempContent("0000");
        assertEquals("1234567", content);

        content = readTempContent("0001");
        assertEquals("abcdefg", content);

        content = readTempContent("0002");
        assertEquals("ABCDEFG", content);
    }

    @Test
    public void createMappedByteBufferLoggerWriteOrDrop() throws Throwable {
        for (int i = 0; i < 1000; i++) {
            createMappedByteBufferLoggerWriteOrDrop0();
        }
    }

    private void createMappedByteBufferLoggerWriteOrDrop0() throws Throwable {
        tempPath = Files.createTempDirectory("test-createMappedByteBufferLoggerWriteOrDrop");
        prefix = "test-" + getCurrentTimestampForFileName() + "-";

        var logger = new MappedByteBufferLogger(tempPath.toString(), prefix, ".log", 8, 1);
        var res = logger.writeOrDrop("1234567");
        assertEquals(MappedByteBufferLogger.WriteOrDropResultType.WRITTEN, res.type());
        res = logger.writeOrDrop("abcdefg");
        if (res.type() == MappedByteBufferLogger.WriteOrDropResultType.DROP_PENDING) {
            res.writablePromise().block();
            res = logger.writeOrDrop("abcdefg");
        }
        assertEquals(MappedByteBufferLogger.WriteOrDropResultType.WRITTEN, res.type());
        res = logger.writeOrDrop("ABCDEFG");
        if (res.type() == MappedByteBufferLogger.WriteOrDropResultType.DROP_PENDING) {
            res.writablePromise().block();
            res = logger.writeOrDrop("ABCDEFG");
        }
        assertEquals(MappedByteBufferLogger.WriteOrDropResultType.WRITTEN, res.type());

        // wait for background file creation to complete
        //noinspection DataFlowIssue
        for (int j = 0; j < 100 && tempPath.toFile().listFiles().length < 4; j++) {
            //noinspection BusyWait
            Thread.sleep(1);
        }

        logger.close();

        var files = tempPath.toFile().listFiles();
        assert files != null;

        assertFilesCnt(4);

        var content = readTempContent("0000");
        assertEquals("1234567", content);

        content = readTempContent("0001");
        assertEquals("abcdefg", content);

        content = readTempContent("0002");
        assertEquals("ABCDEFG", content);
    }

    @Test
    public void randomTestMappedByteBufferLogger() throws Exception {
        tempPath = Files.createTempDirectory("test-randomTestMappedByteBufferLogger");
        prefix = "test-" + getCurrentTimestampForFileName() + "-";
        var logger = new MappedByteBufferLogger(tempPath.toString(), prefix, ".log", 16384, 4096);

        var expected = new StringBuilder();

        var random = new Random();
        for (int i = 0; i < 16384; ++i) {
            int length = random.nextInt(4096) + 1;
            var bytes = new byte[length];
            random.nextBytes(bytes);
            for (var x = 0; x < length; ++x) {
                bytes[x] = (byte) ((bytes[x] % 26) + 97);
            }

            var s = new String(bytes, StandardCharsets.UTF_8);
            expected.append(s);
            logger.writeAndBlock(s);
        }

        logger.close();

        var files = tempPath.toFile().listFiles();
        assert files != null;

        var ls = new ArrayList<>(List.of(files));
        ls.sort(Comparator.comparing(File::getName));

        var sb = new StringBuilder();
        for (var f : ls) {
            var bytes = Files.readAllBytes(f.toPath());
            if (bytes[bytes.length - 1] != 0) {
                sb.append(new String(bytes, StandardCharsets.UTF_8));
            } else {
                sb.append(MemorySegment.ofArray(bytes).getString(0));
            }
        }
        assertEquals(expected.toString(), sb.toString());
        assertTrue(expected.length() > (4096d / 2 * 16384 * 0.8));
        assertTrue(expected.length() < (4096d / 2 * 16384 * 1.2));
    }

    @Test
    public void randomAccessFileByteArray() throws Exception {
        var p = Files.createTempFile("randomAccessFileByteArray", ".txt");
        p.toFile().deleteOnExit();
        Files.write(p, new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11});
        try (var arr = new RandomAccessFileByteArray(p)) {
            for (int i = 0; i < arr.length(); ++i) {
                assertEquals(i + 1, arr.get(i));
            }
            for (int i = arr.length() - 1; i >= 0; --i) {
                assertEquals(i + 1, arr.get(i));
            }

            var helloworld = "hello world".getBytes();
            for (int i = 0; i < helloworld.length; ++i) {
                arr.set(i, helloworld[i]);
            }
            var str = Files.readString(p);
            assertEquals("hello world", str);

            for (int i = 0; i < helloworld.length; ++i) {
                arr.set(arr.length() - i - 1, helloworld[i]);
            }
            str = Files.readString(p);
            assertEquals("dlrow olleh", str);

            var jarr = arr.toJavaArray();
            assertArrayEquals("dlrow olleh".getBytes(), jarr);

            var buf = ByteBuffer.allocate(4);
            buf.position(1);
            arr.byteBufferPut(buf, 3, 2);
            assertEquals(3, buf.position());
            assertEquals(4, buf.limit());
            assertEquals('o', buf.array()[1]);
            assertEquals('w', buf.array()[2]);

            buf = ByteBuffer.allocate(11);
            buf.put("abcdefghijk".getBytes());
            buf.flip();
            arr.byteBufferGet(buf, 0, 11);
            str = Files.readString(p);
            assertEquals("abcdefghijk", str);
        }
    }

    @Test
    public void concurrentObjectPoolPoll() {
        var pool = new ConcurrentObjectPool<Integer>(65536, 16, 0);
        for (int i = 0; i < 65536; ++i) {
            assertTrue("add(" + i + ")", pool.add(i));
        }
        // pool is full, adding should fail now
        for (int i = 0; i < 10; ++i) {
            assertFalse(pool.add(1234));
        }
        var results = new ArrayList<Integer>();
        var pollCount = new ArrayList<Integer>();
        var threads = new ArrayList<Thread>();
        for (int i = 0; i < 16; ++i) {
            threads.add(new Thread(() -> testConcurrentObjectPoolPoll(pool, results, pollCount)));
        }
        for (var t : threads) {
            t.start();
        }
        for (var t : threads) {
            try {
                t.join();
            } catch (InterruptedException ignore) {
            }
        }
        assertEquals(65536, results.size());
        assertEquals(65536, new HashSet<>(results).size());
        results.sort(Integer::compareTo);
        assertEquals(0, results.getFirst().intValue());
        assertEquals(65535, results.getLast().intValue());
        int sum = 0;
        for (var n : pollCount) {
            sum += n;
        }
        assertEquals(65536, sum);
        for (int i = 0; i < 65536; ++i) {
            assertTrue("check:add(" + i + ")", pool.add(i));
        }
    }

    private void testConcurrentObjectPoolPoll(ConcurrentObjectPool<Integer> pool, List<Integer> results, List<Integer> pollCount) {
        var resultsOfThisThread = new ArrayList<Integer>();
        while (true) {
            var e = pool.poll();
            if (e == null) {
                break;
            }
            resultsOfThisThread.add(e);
            Thread.yield();
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (results) {
            results.addAll(resultsOfThisThread);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (pollCount) {
            pollCount.add(resultsOfThisThread.size());
        }
    }

    @Test
    public void concurrentObjectPoolAdd() {
        var pool = new ConcurrentObjectPool<Integer>(65536, 4, 0);

        var added = new ArrayList<Integer>();
        var addCount = new ArrayList<Integer>();
        var threads = new ArrayList<VProxyThread>();
        for (int i = 0; i < 64; ++i) {
            threads.add(VProxyThread.create(() -> testConcurrentObjectPoolAdd(pool, added, addCount), "add:" + i));
        }
        for (var t : threads) {
            t.start();
        }
        for (var t : threads) {
            try {
                t.join();
            } catch (InterruptedException ignore) {
            }
        }
        assertEquals(65536, added.size());
        added.sort(Integer::compareTo);
        var results = new ArrayList<Integer>();
        for (int i = 0; i < 65536; ++i) {
            var e = pool.poll();
            assertNotNull("check:poll():" + i, e);
            results.add(e);
        }
        // the pool is empty so nothing would be polled
        for (int i = 0; i < 10; ++i) {
            assertNull(pool.poll());
        }
        results.sort(Integer::compareTo);
        assertEquals(added, results);

        int cnt = 0;
        for (var n : addCount) {
            cnt += n;
        }
        assertEquals(65536, cnt);
    }

    private void testConcurrentObjectPoolAdd(ConcurrentObjectPool<Integer> pool, List<Integer> added, List<Integer> addCount) {
        var addedOfThisThread = new ArrayList<Integer>();
        while (true) {
            var e = ThreadLocalRandom.current().nextInt();
            if (!pool.add(e)) {
                break;
            }
            addedOfThisThread.add(e);
            Thread.yield();
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (added) {
            added.addAll(addedOfThisThread);
        }
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (addCount) {
            addCount.add(addedOfThisThread.size());
        }
    }
}
