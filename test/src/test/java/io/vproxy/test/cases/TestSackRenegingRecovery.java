package io.vproxy.test.cases;

import io.vproxy.base.Config;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.time.TimeQueue;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.jdk.ChannelFDs;
import io.vproxy.vpacket.conntrack.tcp.*;
import io.vproxy.vswitch.*;
import io.vproxy.vswitch.node.*;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class TestSackRenegingRecovery {
    @Test
    public void ordinaryTimeoutStillRetransmitsUnacknowledgedData() throws Exception {
        try (var f = new Fixture(3000)) {
            f.fireNextTimer();
            f.assertData(0, 3000);
        }
    }

    @Test
    public void timeoutRecoversPreviouslySackedLeftEdgeAfterCumulativeAck() throws Exception {
        try (var f = new Fixture(3000)) {
            f.sackSuffix();
            f.ack(1000);
            f.fireNextTimer();
            f.assertData(1000, 3000);
        }
    }

    @Test
    public void finDoesNotReplaceRenegedDataOnTimeout() throws Exception {
        try (var f = new Fixture(3000)) {
            f.sackSuffix();
            f.tcp.setState(TcpState.FIN_WAIT_1);
            f.ack(1000);
            f.fireNextTimer();
            f.assertData(1000, 3000);
            assertTrue(f.stack.packets.stream().noneMatch(p -> p.tcpPkt.isFin()));
        }
    }

    @Test
    public void timeoutRecoversPartiallyAcknowledgedSackedSegment() throws Exception {
        try (var f = new Fixture(3000)) {
            f.sackSuffix();
            f.ack(1250);
            f.fireNextTimer();
            f.assertData(1250, 3000);
            assertEquals(1750, f.tcp.sendingQueue.getBytesInFlight());
            assertEquals(1750, f.tcp.sendingQueue.getCurrentSize());
        }
    }

    @Test
    public void partialAckAndTimeoutRecoverAcrossThe32BitSequenceBoundary() throws Exception {
        try (var f = new Fixture(3000, 0xfffffff0L)) {
            f.sackSuffix();
            f.ack(1250);
            f.fireNextTimer();
            f.assertData(1250, 3000);
            assertEquals(f.origin + 1250, f.tcp.sendingQueue.getAckSeq());
        }
    }

    @Test
    public void freshSackInformationCanBeUsedAfterATimeout() throws Exception {
        try (var f = new Fixture(3000)) {
            f.sackSuffix();
            f.fireNextTimer();
            f.assertData(0, 3000);
            f.stack.packets.clear();
            f.startSackRepair();
            f.fireNextTimer();
            f.assertData(0, 1000);
        }
    }

    @Test
    public void targetedSackRecoveryStillSkipsSelectivelyAcknowledgedSuffix() throws Exception {
        try (var f = new Fixture(3000)) {
            f.startSackRepair();
            f.fireNextTimer();
            f.assertData(0, 1000);
        }
    }

    @Test
    public void armedSackTimerKeepsItsTypeAfterContextFlagsReset() throws Exception {
        try (var f = new Fixture(3000)) {
            f.startSackRepair();
            f.fireNextTimer();
            f.assertData(0, 1000);
            // Bypass ACK dispatch here to retain the *same* timer/context that
            // output just reset. Its scheduled SACK callback must stay selective.
            f.tcp.sendingQueue.ack(f.origin + 1000, 3000);
            f.stack.packets.clear();
            f.fireNextTimer();
            f.assertData(1000, 1000);
            // The following ordinary timeout must recover the reneged suffix.
            f.fireNextTimer();
            f.assertData(1000, 3000);
        }
    }

    @Test
    public void emptySackRecoveryEventuallyArmsAnOrdinaryTimeout() throws Exception {
        try (var f = new Fixture(3000)) {
            f.startSackRepair();
            f.tcp.sendingQueue.ack(f.origin + 1000, 3000);
            f.fireNextTimer();
            f.assertData(1000, 1000);
            f.fireNextTimer();
            f.assertData(1000, 1000);
            f.fireNextTimer();
            f.assertData(1000, 3000);
        }
    }

    @Test
    public void timeoutDoesNotSendTheUnsentSuffixOrDoubleCountFlight() throws Exception {
        try (var f = new Fixture(2000)) {
            f.sackSuffix();
            f.tcp.sendingQueue.ack(f.origin + 1000, 2000);
            f.fireNextTimer();
            f.assertData(1000, 2000);
            assertEquals(f.origin + 2000, f.tcp.sendingQueue.getFetchSeq());
            assertEquals(1000, f.tcp.sendingQueue.getBytesInFlight());
            assertTrue(f.tcp.sendingQueue.hasUnsentData());
        }
    }

    @Test
    public void renegingDoesNotRefreshTheAbsoluteSegmentAgeLimit() throws Exception {
        try (var f = new Fixture(3000)) {
            f.sackSuffix();
            f.tcp.sendingQueue.ack(f.origin + 1000, 3000);
            var field = f.tcp.sendingQueue.getClass().getDeclaredField("q");
            field.setAccessible(true);
            for (var object : (Iterable<?>) field.get(f.tcp.sendingQueue)) {
                ((Segment) object).firstSentTime = Config.currentTimestamp - TcpEntry.RETRANSMISSION_TIMEOUT_MS;
            }
            f.fireNextTimer();
            assertEquals(TcpState.CLOSED, f.tcp.getState());
            assertTrue(f.stack.packets.stream().anyMatch(p -> p.tcpPkt.isRst()));
        }
    }

    private static class CapturingStack extends TcpStack {
        final List<PacketBuffer> packets = new ArrayList<>();
        CapturingStack(SwitchDelegate delegate) { super(delegate); }
        @Override protected void _schedule(NodeGraphScheduler scheduler, PacketBuffer packet, NodeEgress egress) {
            packets.add(packet);
        }
    }

    private static class Fixture implements AutoCloseable {
        final SelectorEventLoop loop = SelectorEventLoop.open(ChannelFDs.get());
        final TcpEntry tcp = new TcpEntry(null, new IPPort("192.0.2.2", 1234), new IPPort("192.0.2.1", 80), 12345);
        final VirtualNetwork network;
        final CapturingStack stack;
        final byte[] data = new byte[3000];
        final long origin;
        final int window;

        Fixture(int window) throws Exception { this(window, -1); }

        Fixture(int window, long initialSequence) throws Exception {
            this.window = window;
            var constructor = SwitchDelegate.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Object[] args = new Object[constructor.getParameterCount()];
            args[1] = new NodeGraphScheduler(new NodeGraph());
            args[6] = (SwitchDelegate.GetSelectorEventLoop) () -> loop;
            var delegate = (SwitchDelegate) constructor.newInstance(args);
            network = new VirtualNetwork(delegate, 1, loop.ensureNetEventLoop(), Network.from("192.0.2.0/24"), null, 30000, 30000, null);
            stack = new CapturingStack(delegate);
            tcp.setState(TcpState.ESTABLISHED);
            if (initialSequence >= 0) {
                for (var name : new String[]{"latestSeq", "ackSeq", "fetchSeq"}) {
                    var field = tcp.sendingQueue.getClass().getDeclaredField(name);
                    field.setAccessible(true);
                    field.setLong(tcp.sendingQueue, initialSequence);
                }
            }
            tcp.sendingQueue.init(window, 1000, 1);
            for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 31 + (i >>> 8));
            assertEquals(data.length, tcp.sendingQueue.apiWrite(ByteBuffer.wrap(data)));
            origin = tcp.sendingQueue.getAckSeq();
            stack.tcpStartRetransmission(network, tcp);
            assertData(0, window);
            stack.packets.clear();
        }

        void sackSuffix() {
            tcp.sendingQueue.markSackRetransmitSegments(List.of(new SAckTuple(origin + 1000, origin + window)));
        }

        void startSackRepair() throws Exception {
            var method = TcpStack.class.getDeclaredMethod("startSackRetransmit", VirtualNetwork.class, TcpEntry.class, List.class);
            method.setAccessible(true);
            method.invoke(stack, network, tcp, List.of(new SAckTuple(origin + 1000, origin + window)));
        }

        void ack(int offset) throws Exception {
            var packet = TcpUtils.buildCommonTcpResponse(tcp);
            packet.setSeqNum(tcp.receivingQueue.getExpectingSeq());
            packet.setAckNum((origin + offset) & 0xffffffffL);
            packet.setWindow(window);
            packet.setFlags(Consts.TCP_FLAGS_ACK);
            packet.setData(ByteArray.allocate(0));
            var buffer = PacketBuffer.fromPacket(network, TcpUtils.buildIpResponse(tcp, packet));
            buffer.tcp = tcp;
            var method = TcpStack.class.getDeclaredMethod("handleTcpGeneralReturnFalse", PacketBuffer.class);
            method.setAccessible(true);
            method.invoke(stack, buffer);
            stack.packets.clear();
        }

        void fireNextTimer() throws Exception {
            // Execute the actual queued callback without changing the shared
            // Config clock, which other loops may update during a full suite.
            var method = SelectorEventLoop.class.getDeclaredMethod("handleRunOnLoopEvents");
            method.setAccessible(true);
            method.invoke(loop);
            var field = SelectorEventLoop.class.getDeclaredField("timeQueue");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") var queue = (TimeQueue<Runnable>) field.get(loop);
            assertFalse("loss recovery must remain scheduled", queue.isEmpty());
            queue.poll().run();
        }

        void assertData(int from, int end) {
            int position = from;
            for (var packet : stack.packets) {
                var bytes = packet.tcpPkt.getData();
                if (bytes == null || bytes.length() == 0) continue;
                // Captured objects precede wire serialization and retain the
                // extended sequence; only their low 32 bits appear on the wire.
                assertEquals((origin + position) & 0xffffffffL, packet.tcpPkt.getSeqNum() & 0xffffffffL);
                assertTrue(position + bytes.length() <= end);
                assertArrayEquals(Arrays.copyOfRange(data, position, position + bytes.length()), bytes.toJavaArray());
                position += bytes.length();
            }
            assertEquals(end, position);
        }

        @Override public void close() throws Exception { tcp.destroy(); loop.close(); }
    }
}
