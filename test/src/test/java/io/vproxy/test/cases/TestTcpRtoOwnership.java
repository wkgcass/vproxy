package io.vproxy.test.cases;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.time.TimeQueue;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.jdk.ChannelFDs;
import io.vproxy.vpacket.TcpPacket;
import io.vproxy.vpacket.conntrack.tcp.*;
import io.vproxy.vswitch.*;
import io.vproxy.vswitch.node.*;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class TestTcpRtoOwnership {
    @Test
    public void continuousSackFeedbackCannotStarveRenegingRecovery() throws Exception {
        try (var f = new Fixture(3000, 3000, 200)) {
            f.tcp.sendingQueue.markSackRetransmitSegments(List.of(new SAckTuple(f.origin + 1000, f.origin + 3000)));
            f.tcp.sendingQueue.ack(f.origin + 1000, 3000);
            f.stack.packets.clear();
            var result = new CompletableFuture<Boolean>();
            class Feedback implements Runnable {
                int count;
                @Override public void run() {
                    try {
                        f.ack(1000, 3000, List.of(new SAckTuple(f.origin + 1000, f.origin + 3000)));
                        if (++count < 70) f.loop.delay(10, this);
                    } catch (Throwable t) { result.completeExceptionally(t); }
                }
            }
            f.loop.runOnLoop(new Feedback());
            f.loop.delay(650, () -> {
                boolean recovered = f.stack.packets.stream().anyMatch(p ->
                    p.tcpPkt.getData() != null && p.tcpPkt.getData().length() > 0
                    && (p.tcpPkt.getSeqNum() & 0xffffffffL) == ((f.origin + 1000) & 0xffffffffL));
                f.tcp.destroy();
                result.complete(recovered);
            });
            f.loop.loop(r -> VProxyThread.create(r, "sack-feedback-regression"));
            assertTrue("ordinary RTO must transmit the reneged left edge while SACK feedback is still arriving",
                result.get(3, TimeUnit.SECONDS));
        }
    }

    @Test
    public void sackReportsMustPreserveTheOrdinaryTimeout() throws Exception {
        try (var f = new Fixture(3000, 3000)) {
            var rto = f.tcp.retransmissionTimer;
            for (int i = 0; i < 20; i++) {
                f.ack(0, 3000, List.of(new SAckTuple(f.origin + 1000, f.origin + 3000)));
                assertSame("selective receipt must not replace the cumulative-ACK RTO", rto, f.tcp.retransmissionTimer);
            }
        }
    }

    @Test
    public void freshSackBlocksStillCannotPostponeTheOrdinaryTimeout() throws Exception {
        try (var f = new Fixture(3000, 3000)) {
            var rto = f.tcp.retransmissionTimer;
            f.ack(0, 3000, List.of(new SAckTuple(f.origin + 1000, f.origin + 2000)));
            f.ack(0, 3000, List.of(new SAckTuple(f.origin + 1000, f.origin + 3000)));
            assertSame(rto, f.tcp.retransmissionTimer);
        }
    }

    @Test
    public void additionalWritesMustNotMoveTheFirstOutstandingDeadline() throws Exception {
        try (var f = new Fixture(1000, 3000)) {
            var rto = f.tcp.retransmissionTimer;
            assertEquals(1000, f.tcp.sendingQueue.apiWrite(ByteBuffer.wrap(new byte[1000])));
            f.stack.tcpStartRetransmission(f.network, f.tcp);
            assertEquals(f.origin + 2000, f.tcp.sendingQueue.getFetchSeq());
            assertSame(rto, f.tcp.retransmissionTimer);
        }
    }

    @Test
    public void windowOnlyUpdateMustNotMoveTheOutstandingDeadline() throws Exception {
        try (var f = new Fixture(3000, 1000)) {
            var rto = f.tcp.retransmissionTimer;
            f.ack(0, 3000, List.of());
            assertEquals(f.origin + 3000, f.tcp.sendingQueue.getFetchSeq());
            assertSame(rto, f.tcp.retransmissionTimer);
        }
    }

    @Test
    public void duplicateAckFastRecoveryMustNotRestartTheOrdinaryTimer() throws Exception {
        try (var f = new Fixture(3000, 3000)) {
            var rto = f.tcp.retransmissionTimer;
            for (int i = 0; i < 3; i++) f.ack(0, 3000, List.of());
            assertSame(rto, f.tcp.retransmissionTimer);
        }
    }

    @Test
    public void cumulativeAckProgressRestartsTheOrdinaryTimer() throws Exception {
        try (var f = new Fixture(3000, 3000)) {
            var rto = f.tcp.retransmissionTimer;
            f.ack(1000, 3000, List.of());
            assertNotNull(f.tcp.retransmissionTimer);
            assertNotSame(rto, f.tcp.retransmissionTimer);
            assertEquals(2000, f.tcp.sendingQueue.getBytesInFlight());
        }
    }

    @Test
    public void allAcknowledgedDataReleasesEveryRecoveryTimer() throws Exception {
        try (var f = new Fixture(3000, 3000)) {
            f.ack(0, 3000, List.of(new SAckTuple(f.origin + 1000, f.origin + 3000)));
            f.ack(3000, 3000, List.of());
            assertNull(f.tcp.retransmissionTimer);
            assertTrue(f.timers().isEmpty());
        }
    }

    @Test
    public void repeatedSackReportsHaveAtMostTwoQueuedTimers() throws Exception {
        try (var f = new Fixture(3000, 3000)) {
            for (int i = 0; i < 100; i++) f.ack(0, 3000, List.of(new SAckTuple(f.origin + 1000, f.origin + 3000)));
            var queue = f.timers();
            int count = 0;
            while (!queue.isEmpty()) { queue.poll(); count++; }
            assertEquals("one ordinary timeout plus one bounded SACK repair", 2, count);
        }
    }

    @Test
    public void cumulativeAckCancelsPendingSelectiveRepair() throws Exception {
        try (var f = new Fixture(3000, 3000)) {
            f.ack(0, 3000, List.of(new SAckTuple(f.origin + 1000, f.origin + 3000)));
            f.ack(1000, 3000, List.of());
            assertEquals(1, f.timerCount());
        }
    }

    @Test
    public void ordinaryTimeoutCancelsRepairAndRetransmitsAllOutstandingData() throws Exception {
        try (var f = new Fixture(3000, 3000, 100)) {
            f.ack(0, 3000, List.of(new SAckTuple(f.origin + 1000, f.origin + 3000)));
            f.stack.packets.clear();
            f.timers().poll().run();
            assertEquals(3000, f.stack.packets.stream().mapToInt(p -> p.tcpPkt.getData().length()).sum());
            assertEquals(3000, f.tcp.sendingQueue.getBytesInFlight());
            assertEquals(1, f.timerCount());
        }
    }

    @Test
    public void destroyCancelsBothRecoveryTimers() throws Exception {
        try (var f = new Fixture(3000, 3000)) {
            f.ack(0, 3000, List.of(new SAckTuple(f.origin + 1000, f.origin + 3000)));
            f.tcp.destroy();
            assertTrue(f.timers().isEmpty());
        }
    }

    @Test
    public void timeWaitCancelsRepairAndKeepsOnlyItsCloseDeadline() throws Exception {
        try (var f = new Fixture(3000, 3000)) {
            f.ack(0, 3000, List.of(new SAckTuple(f.origin + 1000, f.origin + 3000)));
            f.tcp.setState(TcpState.TIME_WAIT);
            var packet = PacketBuffer.fromPacket(f.network, TcpUtils.buildIpResponse(f.tcp, TcpUtils.buildAckResponse(f.tcp)));
            packet.tcp = f.tcp;
            var method = TcpStack.class.getDeclaredMethod("scheduleTimeWaitClose", PacketBuffer.class);
            method.setAccessible(true);
            method.invoke(f.stack, packet);
            var queue = f.timers();
            var callback = queue.poll();
            assertTrue(queue.isEmpty());
            callback.run();
            assertEquals(TcpState.CLOSED, f.tcp.getState());
        }
    }

    private static class Stack extends TcpStack {
        final List<PacketBuffer> packets = new ArrayList<>();
        Stack(SwitchDelegate delegate) { super(delegate); }
        @Override protected void _schedule(NodeGraphScheduler scheduler, PacketBuffer packet, NodeEgress egress) { packets.add(packet); }
    }

    private static class Fixture implements AutoCloseable {
        final SelectorEventLoop loop = SelectorEventLoop.open(ChannelFDs.get());
        final TcpEntry tcp = new TcpEntry(null, new IPPort("192.0.2.2", 1234), new IPPort("192.0.2.1", 80), 12345);
        final VirtualNetwork network;
        final Stack stack;
        final long origin;

        Fixture(int bytes, int window) throws Exception { this(bytes, window, -1); }

        Fixture(int bytes, int window, int rto) throws Exception {
            var constructor = SwitchDelegate.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Object[] args = new Object[constructor.getParameterCount()];
            args[1] = new NodeGraphScheduler(new NodeGraph());
            args[6] = (SwitchDelegate.GetSelectorEventLoop) () -> loop;
            var delegate = (SwitchDelegate) constructor.newInstance(args);
            network = new VirtualNetwork(delegate, 1, loop.ensureNetEventLoop(), Network.from("192.0.2.0/24"), null, 30000, 30000, null);
            stack = new Stack(delegate);
            tcp.setState(TcpState.ESTABLISHED);
            tcp.setRemoteSackPermitted(true);
            tcp.sendingQueue.init(window, 1000, 1);
            if (rto >= 0) {
                var field = tcp.sendingQueue.getClass().getDeclaredField("rto");
                field.setAccessible(true);
                field.setLong(tcp.sendingQueue, rto);
            }
            assertEquals(bytes, tcp.sendingQueue.apiWrite(ByteBuffer.wrap(new byte[bytes])));
            origin = tcp.sendingQueue.getAckSeq();
            stack.tcpStartRetransmission(network, tcp);
        }

        void ack(int offset, int window, List<SAckTuple> sacks) throws Exception {
            var packet = TcpUtils.buildCommonTcpResponse(tcp);
            packet.setSeqNum(tcp.receivingQueue.getExpectingSeq());
            packet.setAckNum((origin + offset) & 0xffffffffL);
            packet.setWindow(window);
            packet.setFlags(Consts.TCP_FLAGS_ACK);
            packet.setData(ByteArray.allocate(0));
            if (!sacks.isEmpty()) {
                var option = new TcpPacket.TcpOption(packet);
                option.setKind(Consts.TCP_OPTION_SACK);
                var bytes = ByteArray.allocate(sacks.size() * 8);
                for (int i = 0; i < sacks.size(); i++) {
                    bytes.int32(i * 8, (int) sacks.get(i).seqBeginInclusive);
                    bytes.int32(i * 8 + 4, (int) sacks.get(i).seqEndExclusive);
                }
                option.setData(bytes);
                packet.setOptions(new ArrayList<>(List.of(option)));
            }
            var buffer = PacketBuffer.fromPacket(network, TcpUtils.buildIpResponse(tcp, packet));
            buffer.tcp = tcp;
            var method = TcpStack.class.getDeclaredMethod("handleTcpGeneralReturnFalse", PacketBuffer.class);
            method.setAccessible(true);
            method.invoke(stack, buffer);
        }

        @SuppressWarnings("unchecked")
        TimeQueue<Runnable> timers() throws Exception {
            var method = SelectorEventLoop.class.getDeclaredMethod("handleRunOnLoopEvents");
            method.setAccessible(true);
            method.invoke(loop);
            var field = SelectorEventLoop.class.getDeclaredField("timeQueue");
            field.setAccessible(true);
            return (TimeQueue<Runnable>) field.get(loop);
        }

        int timerCount() throws Exception {
            var queue = timers();
            int count = 0;
            while (!queue.isEmpty()) { queue.poll(); count++; }
            return count;
        }

        @Override public void close() throws Exception { tcp.destroy(); loop.close(); }
    }
}
