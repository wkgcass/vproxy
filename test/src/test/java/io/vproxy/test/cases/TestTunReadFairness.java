package io.vproxy.test.cases;

import io.vproxy.base.Config;
import io.vproxy.base.selector.Handler;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.selector.wrap.VirtualFD;
import io.vproxy.base.util.Consts;
import io.vproxy.base.util.OS;
import io.vproxy.vfd.*;
import io.vproxy.vfd.jdk.ChannelFDs;
import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.iface.IfaceInitParams;
import io.vproxy.vswitch.iface.TunIface;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.ByteBuffer;

import static org.junit.Assert.*;

public class TestTunReadFairness {
    static class ClockFDs implements FDs {
        long nanos;
        public SocketFD openSocketFD() throws IOException { return ChannelFDs.get().openSocketFD(); }
        public ServerSocketFD openServerSocketFD() throws IOException { return ChannelFDs.get().openServerSocketFD(); }
        public DatagramFD openDatagramFD() throws IOException { return ChannelFDs.get().openDatagramFD(); }
        public FDSelector openSelector() throws IOException { return ChannelFDs.get().openSelector(); }
        public boolean isV4V6DualStack() { return true; }
        public long currentTimeMillis() { return nanos / 1_000_000; }
        public long nanoTime() { return nanos; }
    }

    static class Input implements TapDatagramFD, VirtualFD {
        final SelectorEventLoop loop;
        final ClockFDs clock;
        final byte[] packet = new byte[40];
        final int total;
        final long workNanos;
        int sent;
        int reads;
        int pauseAt = -1;
        int errorAt = -1;
        boolean open = true;

        Input(SelectorEventLoop loop, ClockFDs clock, int total, long workNanos) {
            this.loop = loop;
            this.clock = clock;
            this.total = total;
            this.workNanos = workNanos;
            // A valid IPv4/UDP datagram. Only its payload sequence varies.
            packet[0] = 0x45;
            packet[3] = 40;
            packet[8] = 64;
            packet[9] = 17;
            packet[12] = (byte) 192; packet[14] = 2; packet[15] = 1;
            packet[16] = (byte) 192; packet[18] = 2; packet[19] = 2;
            ByteBuffer.wrap(packet).putShort(20, (short) 12345).putShort(22, (short) 7777).putShort(24, (short) 20);
            int sum = 0;
            for (int i = 0; i < 20; i += 2) sum += (packet[i] & 255) << 8 | packet[i + 1] & 255;
            while (sum > 65535) sum = (sum & 65535) + (sum >>> 16);
            ByteBuffer.wrap(packet).putShort(10, (short) ~sum);
        }

        public int read(ByteBuffer dst) throws IOException {
            ++reads;
            if (sent == pauseAt) {
                pauseAt = -1;
                loop.selector.removeVirtualReadable(this);
                return 0; // emulate EAGAIN until explicitly rearmed
            }
            if (sent == errorAt) {
                errorAt = -1;
                throw new IOException("test TUN read failure");
            }
            if (sent == total) return 0;
            if (OS.isMac()) dst.putInt(Consts.AF_INET);
            ByteBuffer.wrap(packet).putInt(28, sent);
            dst.put(packet);
            ++sent;
            clock.nanos += workNanos;
            if (sent == total) loop.selector.removeVirtualReadable(this);
            return packet.length + (OS.isMac() ? 4 : 0);
        }

        public void onRegister() { if (sent < total) loop.selector.registerVirtualReadable(this); }
        public void onRemove() { loop.selector.removeVirtualReadable(this); }
        public boolean isOpen() { return open; }
        public void close() { open = false; }
        public void configureBlocking(boolean b) {}
        public <T> void setOption(SocketOption<T> key, T value) {}
        public FD real() { return this; }
        public boolean contains(FD fd) { return fd == this; }
        public NoSockAddr getLocalAddress() { return null; }
        public NoSockAddr getRemoteAddress() { return null; }
        public void connect(NoSockAddr address) {}
        public void bind(NoSockAddr address) {}
        public int write(ByteBuffer src) { throw new UnsupportedOperationException(); }
        public int send(ByteBuffer src, NoSockAddr address) { throw new UnsupportedOperationException(); }
        public NoSockAddr receive(ByteBuffer dst) throws IOException { read(dst); return null; }
        public TapInfo getTap() { return new TapInfo("test-tun", -1); }
        public boolean isTun() { return true; }
    }

    static class CountingIface extends TunIface {
        int packets;
        String error;
        final SelectorEventLoop loop;
        int taskAt = -1;

        CountingIface(SelectorEventLoop loop, Input input) {
            super("test-tun", 0, new MacAddress("00:00:00:00:00:01"), null, input);
            this.loop = loop;
            this.bondLoop = loop;
        }

        protected boolean customInitSteps() { return true; }

        protected void receivedPacket(PacketBuffer packet) {
            String parseError = packet.init();
            if (parseError != null) error = parseError;
            else if (packet.pktBuf.length() != 40 || packet.pktBuf.int32(28) != packets) error = "packet order/content";
            ++packets;
            if (packets == 1) loop.nextTick(() -> taskAt = packets);
        }
    }

    static class Fixture implements AutoCloseable {
        final long previousTimestamp = Config.currentTimestamp;
        final ClockFDs clock = new ClockFDs();
        final SelectorEventLoop loop;
        final Input input;
        final CountingIface iface;

        @SuppressWarnings("unchecked")
        Fixture(int total, long workNanos) throws Exception {
            loop = SelectorEventLoop.open(clock);
            loop.setBeforePoll(() -> 0); // deterministic manual polls, no wall-clock waiting
            input = new Input(loop, clock, total, workNanos);
            iface = new CountingIface(loop, input);
            iface.init(new IfaceInitParams(1, null, loop, null, null));
            var constructor = Class.forName("io.vproxy.vswitch.iface.TunIface$TunHandler").getDeclaredConstructor(TunIface.class);
            constructor.setAccessible(true);
            loop.add(input, EventSet.read(), null, (Handler<Input>) constructor.newInstance(iface));
        }

        public void close() throws IOException {
            try { loop.close(); input.close(); }
            finally { Config.currentTimestamp = previousTimestamp; }
        }

        void drain(int total) {
            // A lost readiness event must fail, rather than hang the suite.
            for (int polls = 0; polls < total + 2 && iface.packets < total; ++polls) loop.onePoll();
            assertNull(iface.error);
            assertEquals(total, iface.packets);
            assertEquals(total, input.sent);
            assertFalse(loop.selector.firingEvents(input).have(Event.READABLE));
        }
    }

    @Test
    public void continuousInputYieldsToTimerAndNextTickBeforeEntireFloodDrains() throws Exception {
        try (var f = new Fixture(257, 2_000_000)) {
            var timerAt = new int[]{-1};
            f.loop.delay(5, () -> timerAt[0] = f.iface.packets);
            f.loop.onePoll();
            f.loop.onePoll();
            assertTrue("timer must run before continuous ingress drains: " + timerAt[0], timerAt[0] > 0 && timerAt[0] < 257);
            assertTrue("nextTick must not wait for all ingress: " + f.iface.taskAt, f.iface.taskAt > 0 && f.iface.taskAt < 257);
            assertEquals(4, timerAt[0]);
            assertEquals(4, f.iface.taskAt);
            f.drain(257);
            System.out.println("tun_fairness timer_at_packet=" + timerAt[0] + " task_at_packet=" + f.iface.taskAt);
        }
    }

    @Test
    public void packetBudgetPreservesReadinessAndFinalTailWithFastInput() throws Exception {
        try (var f = new Fixture(65, 0)) {
            f.loop.onePoll();
            assertEquals("finite packet budget", 32, f.iface.packets);
            assertTrue(f.loop.selector.firingEvents(f.input).have(Event.READABLE));
            f.drain(65);
            f.loop.onePoll();
            assertEquals(65, f.iface.packets);
        }
    }

    @Test
    public void slowPacketFinishesBeforeYieldAndTaskRunsAtNextPoll() throws Exception {
        try (var f = new Fixture(3, 11_000_000)) {
            f.loop.onePoll();
            assertEquals(1, f.iface.packets);
            assertEquals(11_000_000, f.clock.nanos);
            assertEquals(-1, f.iface.taskAt);
            f.loop.onePoll();
            assertEquals(1, f.iface.taskAt);
            assertEquals(2, f.iface.packets);
            f.drain(3);
        }
    }

    @Test
    public void eagainDoesNotSpinAndFreshReadinessDeliversRemainingPackets() throws Exception {
        try (var f = new Fixture(67, 0)) {
            f.input.pauseAt = 33;
            f.loop.onePoll();
            assertEquals(32, f.iface.packets);
            f.loop.onePoll();
            assertEquals(33, f.iface.packets);
            assertFalse(f.loop.selector.firingEvents(f.input).have(Event.READABLE));
            int reads = f.input.reads;
            f.loop.onePoll();
            assertEquals(reads, f.input.reads);
            f.loop.selector.registerVirtualReadable(f.input);
            f.drain(67);
        }
    }

    @Test
    public void readExceptionAfterYieldDoesNotLoseLaterReadiness() throws Exception {
        try (var f = new Fixture(65, 0)) {
            f.input.errorAt = 32;
            f.loop.onePoll();
            assertEquals(32, f.iface.packets);
            f.loop.onePoll();
            assertEquals(32, f.iface.packets);
            assertEquals(33, f.input.reads);
            assertTrue(f.loop.selector.firingEvents(f.input).have(Event.READABLE));
            f.drain(65);
        }
    }

    @Test
    public void timeBudgetSurvivesNanoTimeWraparound() throws Exception {
        try (var f = new Fixture(9, 2_000_000)) {
            f.clock.nanos = Long.MAX_VALUE - 3_000_000;
            f.loop.onePoll();
            assertEquals(4, f.iface.packets);
            assertTrue(f.clock.nanos < 0);
            f.drain(9);
        }
    }
}
