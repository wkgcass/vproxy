package io.vproxy.test.cases;

import io.vproxy.base.Config;
import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.ByteBufferEx;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.ringbuffer.AbstractUnwrapByteBufferRingBuffer;
import io.vproxy.vfd.ReadableByteStream;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.bytearray.ByteArrayBuilder;
import io.vproxy.base.util.bytearray.SimpleByteArray;
import io.vproxy.dns.DNSServer;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.jdk.ChannelFDs;
import io.vproxy.vpacket.conntrack.tcp.TcpEntry;
import io.vproxy.vpacket.conntrack.tcp.TcpState;
import io.vproxy.vpacket.conntrack.tcp.TcpUtils;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.SwitchDelegate;
import io.vproxy.vswitch.VirtualNetwork;
import io.vproxy.vswitch.stack.fd.VSwitchFDContext;
import io.vproxy.vswitch.stack.fd.VSwitchFDs;
import io.vproxy.vswitch.stack.fd.VSwitchSocketFD;
import io.vproxy.vswitch.node.*;
import io.vproxy.vproxyx.websocks.AgentDNSServer;
import io.vproxy.vproxyx.websocks.ConfigLoader;
import io.vproxy.vproxyx.websocks.ConfigProcessor;
import org.junit.Test;

import java.lang.reflect.Method;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class TestPerformanceRegressions {
    @Test
    public void memorySegmentChecksumMatchesIndependentWordsAcrossLanesAndFragments() throws Exception {
        var random = new java.util.Random(0x5041434b45444cL);
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            byte[] bytes = new byte[65544];
            random.nextBytes(bytes);
            var nativeBytes = arena.allocate(bytes.length, 1);
            nativeBytes.copyFrom(java.lang.foreign.MemorySegment.ofArray(bytes));
            for (var segment : new java.lang.foreign.MemorySegment[]{
                java.lang.foreign.MemorySegment.ofArray(bytes), nativeBytes, nativeBytes.asReadOnly()}) {
                for (int offset = 0; offset < 8; ++offset) {
                    for (int length : new int[]{0, 1, 2, 3, 7, 8, 9, 15, 16, 17, 20, 40, 64, 1500, 65535}) {
                        for (int seed : new int[]{0, 0xffff, 0x1ffff}) {
                            int expected = referenceChecksumWords(bytes, offset, length, seed);
                            var array = ByteArray.from(segment.asSlice(offset, length));
                            assertEquals(length == 0 ? seed : expected,
                                Utils.calculateChecksumIntermediate(seed, array, length));
                            long state = (long) seed << 9;
                            int position = 0;
                            while (position < length) {
                                int n = Math.min(length - position, random.nextInt(17) + 1);
                                state = array.updateChecksum(state, position, n);
                                // A zero-length fragment must preserve the
                                // odd-byte state before the next real fragment.
                                state = array.updateChecksum(state, position + n, 0);
                                position += n;
                            }
                            long sum = state >> 9;
                            if ((state & 256) != 0) sum += (state & 255) << 8;
                            while (sum > 65535) sum = (sum & 65535) + (sum >>> 16);
                            assertEquals(expected, sum);
                        }
                    }
                }
            }
        }
    }

    private static int referenceChecksumWords(byte[] bytes, int offset, int length, int seed) {
        long sum = seed;
        for (int i = 0; i < length; i += 2) {
            sum += (bytes[offset + i] & 255) << 8;
            if (i + 1 < length) sum += bytes[offset + i + 1] & 255;
            sum = (sum & 65535) + (sum >>> 16);
        }
        while (sum > 65535) sum = (sum & 65535) + (sum >>> 16);
        return (int) sum;
    }

    @Test
    public void memorySegmentChecksumKeepsScopeAndThreadChecks() throws Exception {
        var arena = java.lang.foreign.Arena.ofConfined();
        var array = ByteArray.from(arena.allocate(32));
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try {
                array.updateChecksum(0, 0, 32);
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        worker.start();
        worker.join(1000);
        assertFalse(worker.isAlive());
        assertTrue(failure.get() instanceof java.lang.WrongThreadException);
        arena.close();
        try {
            array.updateChecksum(0, 0, 32);
            fail("closed native scope must remain inaccessible");
        } catch (IllegalStateException expected) {
            // Both paths still use the standard checked MemorySegment access.
        }
    }

    @Test
    public void destroyedTcpWithPendingOutputReportsFailureInsteadOfZeroWrite() throws Exception {
        try (var fixture = new StackFixture()) {
            var ctx = new VSwitchFDContext(fixture.delegate, fixture.network);
            var fd = new VSwitchSocketFD(ctx, new VSwitchFDs(ctx), fixture.tcp) {};
            var output = RingBuffer.allocate(8);
            output.storeBytesFrom(ByteBuffer.wrap(new byte[]{1, 2, 3, 4}));
            fixture.tcp.destroy();
            assertTrue("entry destruction wakes the still-open fd", fd.isOpen());
            try {
                output.writeTo(fd);
                fail("a destroyed transport must fail, not return zero and remain writable");
            } catch (IOException expected) {
                assertEquals("unwritten output stays owned by the caller", 4, output.used());
            } finally {
                output.clean();
            }
        }
    }

    @Test
    public void closingDestroyedTcpDoesNotRestartQueuedOutput() throws Exception {
        try (var fixture = new StackFixture()) {
            var restarted = new AtomicBoolean();
            fixture.delegate.scheduler.graph.addNode(new TcpStack(fixture.delegate) {
                @Override
                public void tcpStartRetransmission(VirtualNetwork network, TcpEntry tcp) {
                    restarted.set(true);
                }
            });
            var ctx = new VSwitchFDContext(fixture.delegate, fixture.network);
            var fd = new VSwitchSocketFD(ctx, new VSwitchFDs(ctx), fixture.tcp) {};
            fixture.tcp.sendingQueue.init(1000, 1000, 1);
            assertEquals(4, fixture.tcp.sendingQueue.apiWrite(ByteBuffer.wrap(new byte[]{1, 2, 3, 4})));
            assertTrue(fixture.tcp.sendingQueue.hasMoreData());
            fixture.tcp.destroy();
            fd.close();
            assertFalse(fd.isOpen());
            assertFalse("closing a reset transport must not restart retransmission", restarted.get());
            assertEquals(TcpState.CLOSED, fixture.tcp.getState());
            assertNull(fixture.tcp.retransmissionTimer);
        }
    }

    @Test
    public void transportResetDoesNotDiscardDecodedBytesUnderBackpressure() throws Exception {
        decodedBytesDrainBeforeTransportEnd(true);
    }

    @Test
    public void transportEofDoesNotCloseBeforeDecodedBytesDrain() throws Exception {
        decodedBytesDrainBeforeTransportEnd(false);
    }

    private void decodedBytesDrainBeforeTransportEnd(boolean fails) throws Exception {
        // A transparent decoder isolates the transport/staging contract from
        // TLS: all staged bytes stand for already authenticated plaintext.
        var sharedPlainBuffer = RingBuffer.allocate(8);
        var unwrap = new AbstractUnwrapByteBufferRingBuffer(sharedPlainBuffer) {
            @Override
            protected void handleEncryptedBuffer(ByteBufferEx input, boolean[] underflow,
                                                  boolean[] errored, IOException[] exception) {
                ByteBuffer decoded = ByteBuffer.allocate(input.limit() - input.position());
                decoded.put(input.realBuffer()).flip();
                recordIntermediateBuffer(decoded);
            }
        };
        var reset = new IOException("Connection reset by peer");
        int[] reads = {0};
        ReadableByteStream source = dst -> {
            if (reads[0] == 64) {
                if (fails) throw reset;
                return -1;
            }
            int n = Math.min(dst.remaining(), 64 - reads[0]);
            for (int i = 0; i < n; ++i) dst.put((byte) reads[0]++);
            return n;
        };
        try {
            assertEquals(64, unwrap.storeBytesFrom(source));
            assertEquals(64, unwrap.pendingPlaintextBytes());
            var received = new ByteArrayOutputStream();
            for (int step = 0; step < 64 && unwrap.pendingPlaintextBytes() != 0; ++step) {
                assertEquals("do not read a failed transport again", 0, unwrap.storeBytesFrom(source));
                int[] budget = {2};
                // Proxy consumes this shared buffer directly, rather than
                // through the unwrap facade. Its writable edges must resume
                // staging and eventually make the terminal result observable.
                sharedPlainBuffer.writeTo(src -> {
                    int n = Math.min(src.remaining(), budget[0]);
                    for (int i = 0; i < n; ++i) received.write(src.get());
                    budget[0] -= n;
                    return n;
                });
            }
            byte[] expected = new byte[64];
            for (int i = 0; i < expected.length; ++i) expected[i] = (byte) i;
            assertArrayEquals(expected, received.toByteArray());
            assertEquals(0, unwrap.pendingPlaintextBytes());
            try {
                int result = unwrap.storeBytesFrom(source);
                if (fails) fail("transport reset must be reported after the decoded data drains");
                assertEquals(-1, result);
            } catch (IOException e) {
                assertTrue(fails);
                assertSame(reset, e);
            }
        } finally {
            unwrap.clean();
        }
    }

    @Test
    public void resetAfterWrappedReadDecodesTheCommittedFirstPart() throws Exception {
        var plain = RingBuffer.allocate(8);
        var unwrap = new AbstractUnwrapByteBufferRingBuffer(plain) {
            @Override
            protected void handleEncryptedBuffer(ByteBufferEx input, boolean[] underflow,
                                                  boolean[] errored, IOException[] exception) {
                if (input.limit() - input.position() < 4) {
                    // A decoder may suspend without requesting defragmentation.
                    errored[0] = true;
                    return;
                }
                byte[] block = new byte[4];
                input.get(block, 0, block.length);
                recordIntermediateBuffer(ByteBuffer.wrap(block));
            }
        };
        var reset = new IOException("Connection reset by peer");
        int[] phase = {0};
        ReadableByteStream source = dst -> {
            switch (phase[0]++) {
                case 0:
                    dst.put(new byte[]{0, 1, 2, 3, 4, 5});
                    return 6;
                case 1:
                    return 0;
                case 2:
                    assertEquals(2, dst.remaining());
                    dst.put(new byte[]{6, 7});
                    return 2;
                default:
                    throw reset;
            }
        };
        try {
            assertEquals(6, unwrap.storeBytesFrom(source));
            assertEquals(4, unwrap.pendingPlaintextBytes());
            assertEquals(2, unwrap.pendingEncryptedBytes());
            assertEquals(2, unwrap.storeBytesFrom(source));
            assertEquals(8, unwrap.pendingPlaintextBytes());
            var received = new ByteArrayOutputStream();
            plain.writeTo(src -> {
                int n = src.remaining();
                while (src.hasRemaining()) received.write(src.get());
                return n;
            });
            assertArrayEquals(new byte[]{0, 1, 2, 3, 4, 5, 6, 7}, received.toByteArray());
            try {
                unwrap.storeBytesFrom(source);
                fail("saved reset must be reported");
            } catch (IOException e) {
                assertSame(reset, e);
            }
        } finally {
            unwrap.clean();
        }
    }

    @Test
    public void rotationsPreserveCapturedMutableRanges() {
        for (boolean prepend : new boolean[]{false, true}) {
            for (int leaves = 2; leaves <= 32; ++leaves) {
                for (int position = 0; position < leaves; ++position) {
                    var builder = new ByteArrayBuilder().append(77);
                    ByteArray bytes = ByteArray.allocate(0);
                    for (int i = 0; i < leaves; ++i) {
                        ByteArray leaf = i == position ? builder : ByteArray.from(i);
                        bytes = prepend ? leaf.concat(bytes) : bytes.concat(leaf);
                    }
                    String expected = bytes.toHexString();
                    builder.append(99);
                    for (int i = 0; i < 16; ++i) {
                        bytes = prepend ? ByteArray.from(42).concat(bytes) : bytes.concat(ByteArray.from(42));
                        expected = prepend ? "2a" + expected : expected + "2a";
                        assertEquals("leaves=" + leaves + " position=" + position, expected, bytes.toHexString());
                    }
                    builder.set(0, (byte) 88);
                    int index = prepend ? 16 + leaves - position - 1 : position;
                    assertEquals("captured bytes must remain shared", 88, bytes.uint8(index));
                }
            }
        }
    }

    @Test
    public void concatenationDoesNotRescanAllLeavesForDepth() {
        class CountingLeaf extends SimpleByteArray {
            static long depthReads;
            CountingLeaf() { super(new byte[]{1}); }
            @Override public int treeDepth() { ++depthReads; return 1; }
        }
        ByteArray bytes = new CountingLeaf();
        int count = 4096;
        for (int i = 1; i < count; ++i) bytes = bytes.concat(new CountingLeaf());
        assertEquals(count, bytes.length());
        assertTrue("leaf depth reads=" + CountingLeaf.depthReads, CountingLeaf.depthReads < count * 64L);
        assertTrue(bytes.treeDepth() <= 16);
    }

    @Test
    public void checksumMustNotOverflowAcrossSmallLeaves() {
        byte[] payload = new byte[131073];
        java.util.Arrays.fill(payload, (byte) 0xff);
        for (boolean memory : new boolean[]{false, true}) {
            ByteArray segmented = ByteArray.allocate(0);
            for (int offset = 0; offset < payload.length; offset += 997) {
                byte[] leaf = java.util.Arrays.copyOfRange(payload, offset, Math.min(payload.length, offset + 997));
                ByteArray value = memory
                    ? ByteArray.from(java.lang.foreign.MemorySegment.ofArray(leaf)) : ByteArray.from(leaf);
                segmented = segmented.concat(value);
            }
            for (int length : new int[]{65535, 65536, payload.length}) {
                for (int seed : new int[]{0, 65535}) {
                    // Independently fold after every network-order word.
                    int sum = seed;
                    for (int i = 0; i < length; i += 2) {
                        sum += (payload[i] & 255) << 8;
                        if (i + 1 < length) sum += payload[i + 1] & 255;
                        sum = (sum & 65535) + (sum >>> 16);
                    }
                    sum = (sum & 65535) + (sum >>> 16);
                    assertEquals("memory=" + memory + " length=" + length + " seed=" + seed,
                        sum, io.vproxy.base.util.Utils.calculateChecksumIntermediate(seed, segmented, length));
                }
            }
        }
    }

    @Test
    public void zeroWindowAckCannotTriggerProbeBeforePersistDeadline() throws Exception {
        try (var fixture = new StackFixture()) {
            fixture.tcp.sendingQueue.init(0, 1000, 1);
            fixture.tcp.sendingQueue.apiWrite(ByteBuffer.wrap(new byte[3000]));
            fixture.stack.tcpStartRetransmission(fixture.network, fixture.tcp);
            int initial = fixture.stack.packets.size();
            for (int i = 0; i < 10; ++i) fixture.ack(fixture.tcp.sendingQueue.getAckSeq(), 0);
            assertEquals(initial, fixture.stack.packets.size());
            assertNotNull(fixture.tcp.persistTimer);
            assertNull(fixture.tcp.retransmissionTimer);
            fixture.fireNextTimer(TcpEntry.RTO_MIN);
            assertEquals(initial + 1, fixture.stack.packets.size());
            assertEquals(1, fixture.stack.packets.getLast().tcpPkt.getData().length());
            for (int i = 0; i < 10; ++i) fixture.ack(fixture.tcp.sendingQueue.getAckSeq(), 0);
            fixture.fireNextTimer(TcpEntry.RTO_MIN * 2);
            assertEquals(initial + 2, fixture.stack.packets.size());
            fixture.ack(fixture.tcp.sendingQueue.getAckSeq(), 3000);
            assertNull(fixture.tcp.persistTimer);
            assertEquals(3000, fixture.tcp.sendingQueue.getBytesInFlight());
            assertEquals(1000, fixture.stack.packets.getLast().tcpPkt.getData().length());
        }
    }

    @Test
    public void synSequenceShiftPreservesAdvertisedWindowCapacity() throws Exception {
        try (var fixture = new StackFixture()) {
            fixture.tcp.sendingQueue.init(1, 1000, 1);
            assertEquals(1, fixture.tcp.sendingQueue.getAvailableSendWindow());
            fixture.tcp.sendingQueue.incAllSeq();
            assertEquals("SYN must not consume the first data window slot", 1,
                fixture.tcp.sendingQueue.getAvailableSendWindow());
            fixture.tcp.sendingQueue.decAllSeq();
            assertEquals(1, fixture.tcp.sendingQueue.getAvailableSendWindow());
        }
    }

    @Test
    public void invalidAckMustNotRestartRetransmission() throws Exception {
        try (var fixture = new StackFixture()) {
            fixture.tcp.sendingQueue.init(3000, 1000, 1);
            fixture.tcp.sendingQueue.apiWrite(ByteBuffer.wrap(new byte[3000]));
            fixture.stack.tcpStartRetransmission(fixture.network, fixture.tcp);
            var timer = fixture.tcp.retransmissionTimer;
            long ack = fixture.tcp.sendingQueue.getAckSeq();
            fixture.ack(fixture.tcp.sendingQueue.getFetchSeq() + 100, 0);
            assertEquals(ack, fixture.tcp.sendingQueue.getAckSeq());
            assertSame("rejected ACK must not postpone loss recovery", timer, fixture.tcp.retransmissionTimer);
        }
    }

    @Test
    public void applicationCloseMustDrainWindowBlockedData() throws Exception {
        try (var fixture = new StackFixture()) {
            fixture.tcp.sendingQueue.init(0, 1000, 1);
            fixture.tcp.sendingQueue.apiWrite(ByteBuffer.wrap(new byte[1000]));
            fixture.tcp.doClose();
            fixture.stack.tcpStartRetransmission(fixture.network, fixture.tcp);
            assertEquals(TcpState.ESTABLISHED, fixture.tcp.getState());
            assertNotNull(fixture.tcp.persistTimer);
            fixture.ack(fixture.tcp.sendingQueue.getAckSeq(), 1000);
            assertEquals(1000, fixture.stack.packets.getLast().tcpPkt.getData().length());
            fixture.ack(fixture.tcp.sendingQueue.getFetchSeq(), 1000);
            assertEquals(TcpState.CLOSED, fixture.tcp.getState());
            assertTrue(fixture.stack.packets.getLast().tcpPkt.isRst());
        }
    }

    @Test
    public void zeroWindowFinMustKeepAWindowProbeScheduled() throws Exception {
        try (var fixture = new StackFixture()) {
            fixture.tcp.sendingQueue.init(0, 1000, 1);
            fixture.tcp.setState(TcpState.FIN_WAIT_1);
            fixture.stack.tcpStartRetransmission(fixture.network, fixture.tcp);
            assertNotNull("a lost reopening window update must not strand FIN", fixture.tcp.persistTimer);
            fixture.fireNextTimer(TcpEntry.RTO_MIN);
            var probe = fixture.stack.packets.getLast().tcpPkt;
            assertFalse(probe.isFin());
            assertEquals(0, probe.getData().length());
            assertEquals((fixture.tcp.sendingQueue.getFetchSeq() - 1) & 0xffffffffL, probe.getSeqNum());
            fixture.ack(fixture.tcp.sendingQueue.getAckSeq(), 1000);
            assertNull(fixture.tcp.persistTimer);
            assertTrue(fixture.stack.packets.getLast().tcpPkt.isFin());
        }
    }

    @Test
    public void sackRetransmissionMustNotSendFinBeforeUnsentData() throws Exception {
        try (var fixture = new StackFixture()) {
            fixture.tcp.sendingQueue.init(1000, 1000, 1);
            fixture.tcp.sendingQueue.apiWrite(ByteBuffer.wrap(new byte[3000]));
            fixture.tcp.setState(TcpState.FIN_WAIT_1);
            long start = fixture.tcp.sendingQueue.getAckSeq();
            fixture.stack.tcpStartRetransmission(fixture.network, fixture.tcp);
            fixture.tcp.sendingQueue.markSackRetransmitSegments(List.of(
                new io.vproxy.vpacket.conntrack.tcp.SAckTuple(start, start + 1000)));
            int sent = fixture.stack.packets.size();
            fixture.fireNextTimer((int) fixture.tcp.sendingQueue.getRto());
            assertEquals("RTO must recover the SACKed prefix", sent + 1, fixture.stack.packets.size());
            var retransmission = fixture.stack.packets.getLast().tcpPkt;
            assertFalse("untransmitted suffix must still precede FIN", retransmission.isFin());
            assertEquals(start & 0xffffffffL, retransmission.getSeqNum());
            assertEquals(1000, retransmission.getData().length());
            fixture.ack(start + 1000, 2000);
            assertEquals(start + 3000, fixture.tcp.sendingQueue.getFetchSeq());
            fixture.ack(start + 3000, 2000);
            var fin = fixture.stack.packets.getLast().tcpPkt;
            assertTrue(fin.isFin());
            assertEquals((start + 3000) & 0xffffffffL, fin.getSeqNum());
        }
    }

    @Test
    public void periodicDnsCacheClearUsesSharedStateLock() throws Exception {
        var group = new EventLoopGroup("cache-clear-review");
        try (var fixture = new StackFixture()) {
            var dns = new AgentDNSServer("cache-clear-review", new IPPort("127.0.0.1", 0), group,
                new ConfigProcessor(new ConfigLoader(), group, group), null, null, false);
            var lockField = AgentDNSServer.class.getDeclaredField("dnsStateLock");
            lockField.setAccessible(true);
            Object lock = lockField.get(dns);
            var cleared = new AtomicBoolean();
            var locked = new AtomicBoolean();
            var cacheField = AgentDNSServer.class.getDeclaredField("cache");
            cacheField.setAccessible(true);
            cacheField.set(dns, new LinkedHashMap<>() {
                @Override public void clear() {
                    cleared.set(true);
                    locked.set(Thread.holdsLock(lock));
                    super.clear();
                }
            });
            var loopField = DNSServer.class.getDeclaredField("loop");
            loopField.setAccessible(true);
            loopField.set(dns, fixture.loop.ensureNetEventLoop());
            Method schedule = AgentDNSServer.class.getDeclaredMethod("clearCacheTask");
            schedule.setAccessible(true);
            schedule.invoke(dns);
            fixture.fireNextTimer(5 * 60_000);
            assertTrue(cleared.get());
            assertTrue("periodic clear must participate in the same lock as get/put", locked.get());
        } finally {
            group.close();
        }
    }

    private static class CapturingStack extends TcpStack {
        final List<PacketBuffer> packets = new ArrayList<>();
        CapturingStack(SwitchDelegate delegate) { super(delegate); }
        @Override protected void _schedule(NodeGraphScheduler scheduler, PacketBuffer packet, NodeEgress egress) {
            packets.add(packet);
        }
    }

    private static class StackFixture implements AutoCloseable {
        final SelectorEventLoop loop = SelectorEventLoop.open(ChannelFDs.get());
        final TcpEntry tcp = new TcpEntry(null, new IPPort("192.168.1.2", 1234), new IPPort("192.168.1.1", 80), 12345);
        final VirtualNetwork network;
        final CapturingStack stack;
        final SwitchDelegate delegate;
        StackFixture() throws Exception {
            var constructor = SwitchDelegate.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Object[] args = new Object[constructor.getParameterCount()];
            args[1] = new NodeGraphScheduler(new NodeGraph());
            args[6] = (SwitchDelegate.GetSelectorEventLoop) () -> loop;
            delegate = (SwitchDelegate) constructor.newInstance(args);
            network = new VirtualNetwork(delegate, 1, loop.ensureNetEventLoop(), Network.from("192.168.1.0/24"), null, 30000, 30000, null);
            stack = new CapturingStack(delegate);
            tcp.setState(TcpState.ESTABLISHED);
        }
        void ack(long seq, int window) throws Exception {
            var packet = TcpUtils.buildCommonTcpResponse(tcp);
            packet.setSeqNum(tcp.receivingQueue.getExpectingSeq());
            packet.setAckNum(seq);
            packet.setWindow(window);
            packet.setFlags(Consts.TCP_FLAGS_ACK);
            packet.setData(ByteArray.allocate(0));
            var buffer = PacketBuffer.fromPacket(network, TcpUtils.buildIpResponse(tcp, packet));
            buffer.tcp = tcp;
            var method = TcpStack.class.getDeclaredMethod("handleTcpGeneralReturnFalse", PacketBuffer.class);
            method.setAccessible(true);
            method.invoke(stack, buffer);
        }
        void fireNextTimer(int expectedDelay) throws Exception {
            // Drain registration/cancellation tasks, then inspect and execute one
            // scheduled callback. Do not change Config's clock: other JVM loops
            // may be alive and updating it during a full-suite run.
            long before = Config.currentTimestamp;
            var method = SelectorEventLoop.class.getDeclaredMethod("handleRunOnLoopEvents");
            method.setAccessible(true);
            method.invoke(loop);
            long after = Config.currentTimestamp;
            var field = SelectorEventLoop.class.getDeclaredField("timeQueue");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var queue = (io.vproxy.base.util.time.TimeQueue<Runnable>) field.get(loop);
            assertFalse(queue.isEmpty());
            int delay = queue.nextTime(before);
            assertTrue("delay=" + delay, delay >= expectedDelay && delay <= expectedDelay + after - before);
            queue.poll().run();
        }
        @Override public void close() throws Exception {
            tcp.destroy();
            loop.close();
        }
    }
}
