package io.vproxy.test.cases;

import io.vproxy.base.connection.Connection;
import io.vproxy.base.connection.ConnectionOpts;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.vfd.IPPort;
import io.vproxy.vfd.jdk.ChannelFDs;
import io.vproxy.vpacket.TcpPacket;
import io.vproxy.vpacket.conntrack.tcp.*;
import io.vproxy.vswitch.*;
import io.vproxy.vswitch.node.*;
import io.vproxy.vswitch.stack.fd.*;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class TestTcpReset {
    @Test
    public void shutdownOutputAfterResetDoesNotRestartTransport() throws Exception {
        try (var f = new Fixture(false, false, 12345)) {
            var ctx = new VSwitchFDContext(f.delegate, f.network);
            var fd = new VSwitchSocketFD(ctx, new VSwitchFDs(ctx), f.tcp) {};
            f.receive(Consts.TCP_FLAGS_RST, f.tcp.receivingQueue.getExpectingSeq(), 0, new byte[0]);
            assertNull(f.network.conntrack.lookupTcp(f.tcp.remote, f.tcp.local));
            for (int i = 0; i < 2; ++i) {
                try { fd.shutdownOutput(); fail("reset output shutdown must fail"); }
                catch (IOException expected) { }
                assertEquals(TcpState.CLOSED, f.tcp.getState());
                assertNull(f.tcp.retransmissionTimer);
                assertTrue("shutdown must not emit a new SYN", f.stack.output.isEmpty());
                f.assertTimersRemoved();
            }
            fd.close();
        }
    }

    @Test
    public void shutdownOutputAfterDestroyDoesNotRestartTransport() throws Exception {
        try (var f = new Fixture(false, false, 12345)) {
            var ctx = new VSwitchFDContext(f.delegate, f.network);
            var fd = new VSwitchSocketFD(ctx, new VSwitchFDs(ctx), f.tcp) {};
            f.armTimers();
            f.network.conntrack.removeTcp(f.tcp.remote, f.tcp.local);
            f.tcp.destroy();
            try { fd.shutdownOutput(); fail("destroyed output shutdown must fail"); }
            catch (IOException expected) { }
            assertEquals(TcpState.CLOSED, f.tcp.getState());
            assertNull(f.tcp.retransmissionTimer);
            assertNull(f.tcp.persistTimer);
            assertNull(f.tcp.delayedAckTimer);
            assertTrue(f.stack.output.isEmpty());
            f.assertTimersRemoved();
            fd.close();
        }
    }

    @Test
    public void connectionCloseWriteAfterResetDoesNotRestartTransport() throws Exception {
        try (var f = new Fixture(false, false, 12345)) {
            var ctx = new VSwitchFDContext(f.delegate, f.network);
            var fd = new VSwitchSocketFD(ctx, new VSwitchFDs(ctx), f.tcp) {};
            var connection = Connection.wrap(fd, ConnectionOpts.getDefault(),
                RingBuffer.allocate(64), RingBuffer.allocate(64));
            try {
                f.receive(Consts.TCP_FLAGS_RST, f.tcp.receivingQueue.getExpectingSeq(), 0, new byte[0]);
                connection.closeWrite();
                assertTrue(connection.isRealWriteClosed());
                assertEquals(TcpState.CLOSED, f.tcp.getState());
                assertNull(f.tcp.retransmissionTimer);
                assertTrue(f.stack.output.isEmpty());
                f.assertTimersRemoved();
            } finally {
                connection.close();
            }
        }
    }

    @Test
    public void normalShutdownOutputStillSendsFin() throws Exception {
        for (var state : List.of(TcpState.ESTABLISHED, TcpState.CLOSE_WAIT)) {
            try (var f = new Fixture(false, false, 12345)) {
                var ctx = new VSwitchFDContext(f.delegate, f.network);
                var fd = new VSwitchSocketFD(ctx, new VSwitchFDs(ctx), f.tcp) {};
                f.tcp.setState(state);
                fd.shutdownOutput();
                assertEquals(state == TcpState.ESTABLISHED ? TcpState.FIN_WAIT_1 : TcpState.CLOSING,
                    f.tcp.getState());
                assertEquals(1, f.stack.output.size());
                assertTrue((f.stack.output.getFirst().tcpPkt.getFlags() & Consts.TCP_FLAGS_FIN) != 0);
                assertNotNull(f.tcp.retransmissionTimer);
                fd.close();
            }
        }
    }

    @Test
    public void exactResetClosesSynchronizedStatesAndCancelsAllTimers() throws Exception {
        for (TcpState state : List.of(TcpState.ESTABLISHED, TcpState.FIN_WAIT_1,
            TcpState.FIN_WAIT_2, TcpState.CLOSE_WAIT, TcpState.CLOSING, TcpState.LAST_ACK)) {
            try (var f = new Fixture(false, false, 12345)) {
                f.queueOutput();
                f.tcp.setState(state);
                f.armTimers();
                var destroyed = new AtomicInteger();
                f.tcp.setConnectionHandler(new ConnectionHandler() {
                    public void readable(TcpEntry e) { }
                    public void writable(TcpEntry e) { }
                    public void destroy(TcpEntry e) { destroyed.incrementAndGet(); }
                });
                // ACK, FIN and payload on a reset must not be processed as data.
                f.receive(Consts.TCP_FLAGS_RST | Consts.TCP_FLAGS_ACK | Consts.TCP_FLAGS_FIN,
                    f.tcp.receivingQueue.getExpectingSeq(), f.tcp.sendingQueue.getFetchSeq(), new byte[]{7});
                assertEquals(state.toString(), TcpState.CLOSED, f.tcp.getState());
                assertNull(f.network.conntrack.lookupTcp(f.tcp.remote, f.tcp.local));
                assertNull(f.tcp.retransmissionTimer);
                assertNull(f.tcp.persistTimer);
                assertNull(f.tcp.delayedAckTimer);
                f.assertTimersRemoved();
                assertEquals(1, destroyed.get());
                assertEquals(0, f.tcp.receivingQueue.getCurrentSize());
                assertTrue("no reset response or queued retransmission", f.stack.output.isEmpty());
                f.receive(Consts.TCP_FLAGS_RST, f.tcp.receivingQueue.getExpectingSeq(), 0, new byte[0]);
                assertEquals(1, destroyed.get());
                assertTrue(f.stack.output.isEmpty());
            }
        }
    }

    @Test
    public void resetInWindowChallengesWithoutAcknowledgingQueuedOutput() throws Exception {
        try (var f = new Fixture(false, false, 12345)) {
            f.queueOutput();
            f.armTimers();
            var retransmission = f.tcp.retransmissionTimer;
            var persist = f.tcp.persistTimer;
            long una = f.tcp.sendingQueue.getAckSeq();
            long next = f.tcp.receivingQueue.getExpectingSeq();
            f.receive(Consts.TCP_FLAGS_RST | Consts.TCP_FLAGS_ACK, next + 1,
                f.tcp.sendingQueue.getFetchSeq(), new byte[]{9});
            assertEquals(TcpState.ESTABLISHED, f.tcp.getState());
            assertEquals(una, f.tcp.sendingQueue.getAckSeq());
            assertEquals(4, f.tcp.sendingQueue.getCurrentSize());
            assertEquals(0, f.tcp.receivingQueue.getCurrentSize());
            assertSame(retransmission, f.tcp.retransmissionTimer);
            assertSame(persist, f.tcp.persistTimer);
            assertEquals(1, f.stack.output.size());
            var ack = f.stack.output.getFirst().tcpPkt;
            assertEquals(Consts.TCP_FLAGS_ACK, ack.getFlags());
            assertEquals(next & 0xffff_ffffL, ack.getAckNum() & 0xffff_ffffL);
        }
    }

    @Test
    public void resetOutsideWindowDoesNotReplyOrTouchTimers() throws Exception {
        try (var f = new Fixture(false, false, 12345)) {
            f.queueOutput();
            f.armTimers();
            var retransmission = f.tcp.retransmissionTimer;
            var delayed = f.tcp.delayedAckTimer;
            long next = f.tcp.receivingQueue.getExpectingSeq();
            for (long seq : new long[]{next - 1, next + f.tcp.receivingQueue.getWindow(), next + 0x7fff_ffffL}) {
                f.receive(Consts.TCP_FLAGS_RST | Consts.TCP_FLAGS_ACK, seq,
                    f.tcp.sendingQueue.getFetchSeq(), new byte[0]);
                assertEquals(TcpState.ESTABLISHED, f.tcp.getState());
                assertSame(retransmission, f.tcp.retransmissionTimer);
                assertSame(delayed, f.tcp.delayedAckTimer);
                assertTrue(f.stack.output.isEmpty());
                assertEquals(4, f.tcp.sendingQueue.getCurrentSize());
            }
        }
    }

    @Test
    public void exactResetAtZeroWindowAndWireWrapStillCloses() throws Exception {
        try (var f = new Fixture(true, false, 0xffff_ffffL)) {
            var field = f.tcp.receivingQueue.getClass().getDeclaredField("window");
            field.setAccessible(true);
            field.setInt(f.tcp.receivingQueue, 0);
            assertEquals(0x1_0000_0000L, f.tcp.receivingQueue.getExpectingSeq());
            f.receive(Consts.TCP_FLAGS_RST, 1, 0, new byte[0]);
            assertEquals(TcpState.ESTABLISHED, f.tcp.getState());
            assertTrue(f.stack.output.isEmpty());
            f.receive(Consts.TCP_FLAGS_RST, 0, 0, new byte[0]);
            assertEquals(TcpState.CLOSED, f.tcp.getState());
            assertNull(f.network.conntrack.lookupTcp(f.tcp.remote, f.tcp.local));
        }
    }

    @Test
    public void synSentResetMustAcknowledgeTheSyn() throws Exception {
        try (var f = new Fixture(false, false, 0)) {
            f.tcp.setState(TcpState.SYN_SENT);
            f.armTimers();
            long synAck = f.tcp.sendingQueue.getAckSeq();
            var timer = f.tcp.retransmissionTimer;
            for (int flags : new int[]{Consts.TCP_FLAGS_RST, Consts.TCP_FLAGS_RST | Consts.TCP_FLAGS_ACK}) {
                for (long ack : new long[]{synAck - 1, synAck + 1}) {
                    f.receive(flags, 888, ack, new byte[0]);
                    assertEquals(TcpState.SYN_SENT, f.tcp.getState());
                    assertSame(timer, f.tcp.retransmissionTimer);
                    assertTrue(f.stack.output.isEmpty());
                }
            }
            f.receive(Consts.TCP_FLAGS_RST | Consts.TCP_FLAGS_ACK, 888, synAck, new byte[0]);
            assertEquals(TcpState.CLOSED, f.tcp.getState());
            assertNull(f.tcp.retransmissionTimer);
            f.assertTimersRemoved();
        }
    }

    @Test
    public void resetRemovesUnacceptedChildButKeepsListener() throws Exception {
        for (TcpState state : List.of(TcpState.SYN_RECEIVED, TcpState.ESTABLISHED)) {
            try (var f = new Fixture(false, true, 12345)) {
                f.tcp.setState(state);
                var parent = f.tcp.getParent();
                if (state == TcpState.SYN_RECEIVED) parent.synBacklog.add(f.tcp);
                else parent.backlog.add(f.tcp);
                f.receive(Consts.TCP_FLAGS_RST, f.tcp.receivingQueue.getExpectingSeq(), 0, new byte[0]);
                assertEquals(TcpState.CLOSED, f.tcp.getState());
                assertTrue(parent.synBacklog.isEmpty());
                assertTrue(parent.backlog.isEmpty());
                assertSame(parent, f.network.conntrack.lookupTcpListen(f.tcp.local));
                assertNull(f.network.conntrack.lookupTcp(f.tcp.remote, f.tcp.local));
            }
        }
    }

    @Test
    public void resetCannotDeleteReplacementCreatedByDestroyCallback() throws Exception {
        try (var f = new Fixture(false, false, 12345)) {
            f.tcp.setConnectionHandler(new ConnectionHandler() {
                public void readable(TcpEntry e) { }
                public void writable(TcpEntry e) { }
                public void destroy(TcpEntry e) {
                    assertNull(f.network.conntrack.lookupTcp(e.remote, e.local));
                    f.network.conntrack.createTcp(null, e.remote, e.local, 8888).setState(TcpState.ESTABLISHED);
                }
            });
            f.receive(Consts.TCP_FLAGS_RST, f.tcp.receivingQueue.getExpectingSeq(), 0, new byte[0]);
            var replacement = f.network.conntrack.lookupTcp(f.tcp.remote, f.tcp.local);
            assertNotNull(replacement);
            assertNotSame(f.tcp, replacement);
            assertEquals(TcpState.ESTABLISHED, replacement.getState());
        }
    }

    @Test
    public void timeWaitResetIncludingFinDoesNotShortenWaitOrReply() throws Exception {
        try (var f = new Fixture(false, false, 12345)) {
            f.tcp.setState(TcpState.TIME_WAIT);
            f.tcp.retransmissionTimer = f.loop.delay(TcpEntry.TIME_WAIT_TIMEOUT_MS, () -> {});
            var timer = f.tcp.retransmissionTimer;
            f.receive(Consts.TCP_FLAGS_RST | Consts.TCP_FLAGS_FIN | Consts.TCP_FLAGS_ACK,
                f.tcp.receivingQueue.getExpectingSeq(), 0, new byte[0]);
            assertEquals(TcpState.TIME_WAIT, f.tcp.getState());
            assertSame(timer, f.tcp.retransmissionTimer);
            assertNull("RST must not schedule a FIN ACK", f.tcp.delayedAckTimer);
            assertTrue(f.stack.output.isEmpty());
        }
    }

    @Test
    public void virtualFdDrainsCommittedBytesAfterResetWithoutSchedulingAck() throws Exception {
        try (var f = new Fixture(false, false, 12345)) {
            var ctx = new VSwitchFDContext(f.delegate, f.network);
            var fd = new VSwitchSocketFD(ctx, new VSwitchFDs(ctx), f.tcp) {};
            f.tcp.receivingQueue.storeCopied(f.tcp.receivingQueue.getExpectingSeq(), ByteArray.from(new byte[]{1,2,3,4}));
            f.receive(Consts.TCP_FLAGS_RST, f.tcp.receivingQueue.getExpectingSeq(), 0, new byte[0]);
            var buf = ByteBuffer.allocate(8);
            assertEquals(4, fd.read(buf));
            assertArrayEquals(new byte[]{1,2,3,4,0,0,0,0}, buf.array());
            assertEquals(-1, fd.read(buf));
            assertNull(f.tcp.delayedAckTimer);
            assertTrue(f.stack.output.isEmpty());
            var pending = ByteBuffer.wrap(new byte[]{8,9});
            try { fd.write(pending); fail("reset output must fail"); }
            catch (IOException expected) { assertEquals(0, pending.position()); }
            fd.close();
            assertNull(f.tcp.retransmissionTimer);
        }
    }

    @Test
    public void destroyedConnectAttemptFailsInsteadOfStayingReadyForever() throws Exception {
        try (var f = new Fixture(false, false, 0)) {
            var ctx = new VSwitchFDContext(f.delegate, f.network);
            var fd = new VSwitchSocketFD(ctx, new VSwitchFDs(ctx), f.tcp) {};
            var connected = VSwitchSocketFD.class.getDeclaredField("connected");
            connected.setAccessible(true);
            connected.setBoolean(fd, false);
            f.tcp.destroy();
            try { fd.finishConnect(); fail("destroyed connection attempt must fail"); }
            catch (IOException expected) { }
            fd.close();
        }
    }

    @Test
    public void noResetRepliesToResetAndResetWireFieldsMatchInput() throws Exception {
        for (boolean ipv6 : new boolean[]{false, true}) {
            try (var f = new Fixture(ipv6, false, 12345)) {
                var reset = new CapturingReset();
                var incoming = f.packet(Consts.TCP_FLAGS_RST | Consts.TCP_FLAGS_ACK, 9, 10, new byte[0]);
                reset.receive(incoming);
                assertTrue("never reply to RST", reset.output.isEmpty());
                var ack = f.packet(Consts.TCP_FLAGS_ACK, 99, 0x1_0000_0002L, new byte[]{1,2,3});
                reset.receive(ack);
                var reply = reset.output.removeFirst();
                var response = reply.tcpPkt;
                assertEquals(Consts.TCP_FLAGS_RST, response.getFlags());
                assertEquals(2, response.getSeqNum() & 0xffff_ffffL);
                assertEquals(0, response.getAckNum());
                assertEquals(2, reply.ipPkt.getRawPacket(io.vproxy.vpacket.AbstractPacket.FLAG_CHECKSUM_UNNECESSARY)
                    .uint32((ipv6 ? 40 : 20) + 4));
                var synFin = f.packet(Consts.TCP_FLAGS_SYN | Consts.TCP_FLAGS_FIN, 0xffff_fffdL, 88, new byte[]{1,2,3});
                reset.receive(synFin);
                reply = reset.output.removeFirst();
                response = reply.tcpPkt;
                assertEquals(Consts.TCP_FLAGS_RST | Consts.TCP_FLAGS_ACK, response.getFlags());
                assertEquals(0, response.getSeqNum());
                assertEquals(2, response.getAckNum() & 0xffff_ffffL);
                assertEquals(2, reply.ipPkt.getRawPacket(io.vproxy.vpacket.AbstractPacket.FLAG_CHECKSUM_UNNECESSARY)
                    .uint32((ipv6 ? 40 : 20) + 8));
                assertEquals(f.tcp.remote.getPort(), response.getSrcPort());
                assertEquals(f.tcp.local.getPort(), response.getDstPort());
            }
        }
    }

    static class CapturingStack extends TcpStack {
        final List<PacketBuffer> output = new ArrayList<>();
        CapturingStack(SwitchDelegate delegate) { super(delegate); }
        void receive(PacketBuffer pkb) { handle(pkb, null); }
        @Override protected void _schedule(NodeGraphScheduler s, PacketBuffer p, NodeEgress e) { output.add(p); }
        @Override protected HandleResult _returnnext(PacketBuffer p, NodeEgress e) { output.add(p); return HandleResult.STOLEN; }
    }

    static class CapturingReset extends TcpReset {
        final List<PacketBuffer> output = new ArrayList<>();
        void receive(PacketBuffer pkb) { handle(pkb, null); }
        @Override protected HandleResult _returnnext(PacketBuffer p, NodeEgress e) { output.add(p); return HandleResult.STOLEN; }
    }

    static class Fixture implements AutoCloseable {
        final SelectorEventLoop loop = SelectorEventLoop.open(ChannelFDs.get());
        final SwitchDelegate delegate;
        final VirtualNetwork network;
        final TcpEntry tcp;
        final CapturingStack stack;
        Fixture(boolean ipv6, boolean listen, long seq) throws Exception {
            var constructor = SwitchDelegate.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            Object[] args = new Object[constructor.getParameterCount()];
            args[1] = new NodeGraphScheduler(new NodeGraph());
            args[6] = (SwitchDelegate.GetSelectorEventLoop) () -> loop;
            delegate = (SwitchDelegate) constructor.newInstance(args);
            network = new VirtualNetwork(delegate, 1, loop.ensureNetEventLoop(), Network.from("192.0.2.0/24"), null, 30000, 30000, null);
            IPPort remote = new IPPort(ipv6 ? "2001:db8::2" : "192.0.2.2", 1234);
            IPPort local = new IPPort(ipv6 ? "2001:db8::1" : "192.0.2.1", 80);
            var parent = listen ? network.conntrack.listenTcp(local, e -> {}) : null;
            tcp = network.conntrack.createTcp(parent, remote, local, seq);
            tcp.setState(TcpState.ESTABLISHED);
            tcp.sendingQueue.init(65535, 1400, 1);
            stack = new CapturingStack(delegate);
            delegate.scheduler.graph.addNode(stack);
        }
        void queueOutput() throws Exception {
            assertEquals(4, tcp.sendingQueue.apiWrite(ByteBuffer.wrap(new byte[]{3,4,5,6})));
            tcp.sendingQueue.fetch();
        }
        void armTimers() throws Exception {
            tcp.retransmissionTimer = loop.delay(1000, () -> fail("cancelled RTO fired"));
            tcp.persistTimer = loop.delay(1000, () -> fail("cancelled persist fired"));
            tcp.delayedAckTimer = loop.delay(1000, () -> fail("cancelled ACK fired"));
            flushTasks();
        }
        void flushTasks() throws Exception {
            var method = SelectorEventLoop.class.getDeclaredMethod("handleRunOnLoopEvents");
            method.setAccessible(true);
            method.invoke(loop);
        }
        void assertTimersRemoved() throws Exception {
            flushTasks();
            var field = SelectorEventLoop.class.getDeclaredField("timeQueue");
            field.setAccessible(true);
            assertTrue("cancelled timers must leave the real loop heap",
                ((io.vproxy.base.util.time.TimeQueue<?>) field.get(loop)).isEmpty());
        }
        PacketBuffer packet(int flags, long seq, long ack, byte[] payload) {
            var pkt = TcpUtils.buildCommonTcpResponse(tcp);
            pkt.setSeqNum(seq & 0xffff_ffffL);
            pkt.setAckNum(ack & 0xffff_ffffL);
            pkt.setFlags(flags);
            pkt.setData(ByteArray.from(payload));
            var pkb = PacketBuffer.fromPacket(network, TcpUtils.buildIpResponse(tcp, pkt));
            pkb.tcp = tcp;
            return pkb;
        }
        void receive(int flags, long seq, long ack, byte[] payload) { stack.receive(packet(flags, seq, ack, payload)); }
        public void close() throws Exception {
            network.conntrack.destroy();
            loop.close();
        }
    }
}
