package io.vproxy.test.cases;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Consts;
import io.vproxy.vfd.*;
import io.vproxy.vpacket.*;
import io.vproxy.vswitch.iface.TunIface;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class TestTunWriteDiagnostics {
    static class Output implements TapDatagramFD {
        int limit = Integer.MAX_VALUE;
        boolean error;
        boolean inconsistent;
        int calls;
        final List<byte[]> offered = new ArrayList<>();
        final List<byte[]> accepted = new ArrayList<>();

        public int write(ByteBuffer src) throws IOException {
            ++calls;
            byte[] bytes = new byte[src.remaining()];
            src.duplicate().get(bytes);
            offered.add(bytes);
            if (error) throw new IOException("controlled TUN write failure");
            if (inconsistent) return bytes.length; // violates position contract
            int n = Math.min(src.remaining(), limit);
            accepted.add(Arrays.copyOf(bytes, n));
            src.position(src.position() + n);
            return n;
        }

        public int read(ByteBuffer dst) { return 0; }
        public int send(ByteBuffer src, NoSockAddr addr) throws IOException { return write(src); }
        public NoSockAddr receive(ByteBuffer dst) { return null; }
        public void connect(NoSockAddr addr) {}
        public void bind(NoSockAddr addr) {}
        public NoSockAddr getLocalAddress() { return null; }
        public NoSockAddr getRemoteAddress() { return null; }
        public TapInfo getTap() { return new TapInfo("test-output", -1); }
        public boolean isTun() { return true; }
        public boolean isOpen() { return true; }
        public void close() {}
        public void configureBlocking(boolean value) {}
        public <T> void setOption(SocketOption<T> key, T value) {}
        public FD real() { return this; }
        public boolean contains(FD fd) { return this == fd; }
    }

    static class Iface extends TunIface {
        final boolean af;
        Iface(Output output, boolean af) throws Exception {
            super("test-output", 0, new MacAddress("00:00:00:00:00:01"), null, output);
            this.af = af;
            var field = TunIface.class.getDeclaredField("operateTun");
            field.setAccessible(true);
            field.set(this, output);
        }
        protected boolean requireAFHeader() { return af; }
        void send(AbstractIpPacket packet) throws Exception {
            Method m = TunIface.class.getDeclaredMethod("sendPacket", AbstractIpPacket.class);
            m.setAccessible(true);
            m.invoke(this, packet);
        }
        void write(ByteBuffer data) throws IOException { super.sendPacket(data); }
    }

    static AbstractIpPacket packet(boolean ipv6, int marker) {
        var udp = new UdpPacket();
        udp.setSrcPort(12345);
        udp.setDstPort(54321);
        udp.setData(new PacketBytes(ByteArray.from(1, 2, 3, marker, 5, 6, 7)));
        // UDP needs its IP parent to construct the pseudo-header checksum;
        // do not call its unsupported generic getRawPacket before attaching.
        if (!ipv6) {
            var ip = new Ipv4Packet();
            ip.setVersion(4); ip.setIhl(5); ip.setTotalLength(35); ip.setTtl(64);
            ip.setSrc((IPv4) IP.from("192.0.2.1")); ip.setDst((IPv4) IP.from("192.0.2.2"));
            ip.setProtocol(Consts.IP_PROTOCOL_UDP); ip.setOptions(ByteArray.allocate(0)); ip.setPacket(udp);
            return ip;
        }
        var ip = new Ipv6Packet();
        ip.setVersion(6); ip.setPayloadLength(15); ip.setHopLimit(64);
        ip.setSrc((IPv6) IP.from("2001:db8::1")); ip.setDst((IPv6) IP.from("2001:db8::2"));
        ip.setNextHeader(Consts.IP_PROTOCOL_UDP); ip.setExtHeaders(Collections.emptyList()); ip.setPacket(udp);
        return ip;
    }

    @Test
    public void zeroWriteCountsFailedPacketWithoutSendingAnEmptyTail() throws Exception {
        var out = new Output(); out.limit = 0;
        var iface = new Iface(out, true);
        iface.send(packet(false, 4));
        assertEquals(1, out.calls);
        assertEquals(0, out.accepted.get(0).length);
        assertEquals("an unaccepted datagram is a transmit error", 1, iface.statistics.getTxErr());
        out.limit = Integer.MAX_VALUE;
        iface.send(packet(false, 9));
        assertEquals(2, out.calls);
        assertEquals(1, iface.statistics.getTxErr());
        assertEquals(Consts.AF_INET, ByteBuffer.wrap(out.accepted.get(1)).getInt());
    }

    @Test
    public void partialWriteCountsFailedPacketWithoutEmittingHeaderlessRemainder() throws Exception {
        var out = new Output(); out.limit = 7;
        var iface = new Iface(out, true);
        iface.send(packet(true, 8));
        assertEquals(1, out.calls);
        assertEquals(7, out.accepted.get(0).length);
        assertEquals(1, iface.statistics.getTxErr());
        out.limit = Integer.MAX_VALUE;
        iface.send(packet(true, 3));
        byte[] raw = packet(true, 3).getRawPacket(0).toJavaArray();
        byte[] wire = out.accepted.get(1);
        assertEquals(Consts.AF_INET6, ByteBuffer.wrap(wire).getInt());
        assertArrayEquals(raw, Arrays.copyOfRange(wire, 4, wire.length));
    }

    @Test
    public void inconsistentFullReturnCannotCountAsSuccessfulWrite() throws Exception {
        var out = new Output(); out.inconsistent = true;
        var iface = new Iface(out, false);
        iface.send(packet(false, 4));
        assertEquals(1, out.calls);
        assertEquals(1, iface.statistics.getTxErr());
    }

    @Test
    public void ipv4Ipv6PayloadAndAfHeadersRemainExactOnFullWrite() throws Exception {
        for (boolean af : new boolean[]{false, true}) {
            var out = new Output(); var iface = new Iface(out, af);
            long bytes = 0;
            for (boolean v6 : new boolean[]{false, true}) {
                var ip = packet(v6, v6 ? 6 : 4);
                byte[] expected = ip.getRawPacket(0).toJavaArray();
                iface.send(ip); bytes += expected.length;
                byte[] wire = out.accepted.get(out.accepted.size() - 1);
                if (af) assertEquals(v6 ? Consts.AF_INET6 : Consts.AF_INET, ByteBuffer.wrap(wire).getInt());
                assertArrayEquals(expected, Arrays.copyOfRange(wire, af ? 4 : 0, wire.length));
            }
            assertEquals(2, iface.statistics.getTxPkts());
            assertEquals(bytes, iface.statistics.getTxBytes());
            assertEquals(0, iface.statistics.getTxErr());
        }
    }

    @Test
    public void ioExceptionIsCountedOnlyOnceByPacketOwner() throws Exception {
        var out = new Output(); out.error = true;
        var iface = new Iface(out, true);
        iface.send(packet(false, 4));
        assertEquals(1, out.calls);
        assertEquals(1, iface.statistics.getTxErr());
    }

    static class Diagnostics {
        final Class<?> type = Class.forName("io.vproxy.vswitch.iface.TunIface$TunWriteDiagnostics");
        final Object value;
        final Method record;
        final Method finish;
        Diagnostics() throws Exception {
            var c = type.getDeclaredConstructor(String.class); c.setAccessible(true);
            value = c.newInstance("test-output");
            record = type.getDeclaredMethod("record", int.class, int.class, int.class, boolean.class, boolean.class, long.class);
            record.setAccessible(true);
            finish = type.getDeclaredMethod("finish"); finish.setAccessible(true);
        }
        void record(int n, int written, int advanced, boolean changedLimit, boolean error, long now) throws Exception {
            record.invoke(value, n, written, advanced, changedLimit, error, now);
        }
        long field(String name) throws Exception {
            var f = type.getDeclaredField(name); f.setAccessible(true);
            return ((Number) f.get(value)).longValue();
        }
        void attach(Iface iface) throws Exception {
            var f = TunIface.class.getDeclaredField("tunWriteDiagnostics"); f.setAccessible(true); f.set(iface, value);
        }
    }

    @Test
    public void realPacketWritesDriveIndependentOutcomeCounters() throws Exception {
        var out = new Output(); var iface = new Iface(out, true); var d = new Diagnostics(); d.attach(iface);
        int len = packet(false, 4).getRawPacket(0).length() + 4;
        iface.send(packet(false, 4));
        out.limit = 0; iface.send(packet(false, 4));
        out.limit = 7; iface.send(packet(false, 4));
        out.error = true; iface.send(packet(false, 4));
        out.error = false; out.inconsistent = true; iface.send(packet(false, 4));
        assertEquals(5, d.field("calls"));
        assertEquals(5L * len, d.field("attemptedBytes"));
        assertEquals(2L * len + 7, d.field("returnedBytes")); // includes reported inconsistent return
        assertEquals(2L * len - 7, d.field("unwrittenBytes")); // known zero/partial only
        for (String name : new String[]{"complete", "zero", "partial", "errors", "inconsistent"}) assertEquals(name, 1, d.field(name));
        assertEquals(4, iface.statistics.getTxErr());
        iface.destroy();
        assertEquals(2, d.field("reportCount")); // first + final, no per-error logging
        iface.destroy(); assertEquals(2, d.field("reportCount"));
    }

    @Test
    public void reportLimitReservesFinalSnapshotAndHandlesClockWrap() throws Exception {
        var d = new Diagnostics();
        long now = Long.MAX_VALUE - 1_000_000_000;
        for (int i = 0; i < 100; ++i) {
            d.record(64, 64, 64, false, false, now);
            now += 15_000_000_000L;
        }
        assertEquals(100, d.field("calls"));
        assertEquals(6400, d.field("attemptedBytes"));
        assertEquals(6400, d.field("returnedBytes"));
        assertEquals(31, d.field("reportCount"));
        d.finish.invoke(d.value); d.finish.invoke(d.value);
        assertEquals(32, d.field("reportCount"));
    }

    @Test
    public void disabledDiagnosticsAllocateNoCounter() throws Exception {
        // The suite's default JVM has no tun_write_diag override.
        var iface = new Iface(new Output(), false);
        var field = TunIface.class.getDeclaredField("tunWriteDiagnostics"); field.setAccessible(true);
        assertNull(field.get(iface));
    }

    @Test
    public void sourcePositionAndLimitDefinePacketBoundary() throws Exception {
        var out = new Output(); var iface = new Iface(out, false); var d = new Diagnostics(); d.attach(iface);
        ByteBuffer src = ByteBuffer.allocate(32);
        for (int i = 0; i < 32; ++i) src.put((byte) i);
        src.limit(23).position(9);
        iface.write(src);
        assertEquals(23, src.position()); assertEquals(23, src.limit());
        assertEquals(14, d.field("attemptedBytes")); assertEquals(14, d.field("returnedBytes"));
        assertArrayEquals(new byte[]{9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22}, out.accepted.get(0));
        assertEquals(0, iface.statistics.getTxErr());
    }

    @Test
    public void exceptionWithPositionProgressDoesNotInventKernelAcceptance() throws Exception {
        var out = new Output() {
            public int write(ByteBuffer src) throws IOException {
                src.position(src.position() + 3);
                throw new IOException("ambiguous partial progress");
            }
        };
        var iface = new Iface(out, false); var d = new Diagnostics(); d.attach(iface);
        iface.send(packet(false, 4));
        assertEquals(1, iface.statistics.getTxErr()); assertEquals(1, d.field("errors"));
        assertEquals(3, d.field("errorAdvancedBytes"));
        assertEquals(0, d.field("returnedBytes")); assertEquals(0, d.field("unwrittenBytes"));
    }
}
