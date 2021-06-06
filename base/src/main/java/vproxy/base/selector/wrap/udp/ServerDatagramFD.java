package vproxy.base.selector.wrap.udp;

import vproxy.base.Config;
import vproxy.base.GlobalInspection;
import vproxy.base.prometheus.GaugeF;
import vproxy.base.selector.SelectorEventLoop;
import vproxy.base.selector.wrap.VirtualFD;
import vproxy.base.selector.wrap.WrappedSelector;
import vproxy.base.selector.wrap.WritableAware;
import vproxy.base.util.Logger;
import vproxy.base.util.Utils;
import vproxy.vfd.*;
import vproxy.vfd.type.FDCloseReq;
import vproxy.vfd.type.FDCloseReturn;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public final class ServerDatagramFD implements FD, ServerSocketFD, WritableAware {
    private static final String server_datagram_fd_accept_queue_length_current = "server_datagram_fd_accept_queue_length_current";
    private static final String server_datagram_fd_established_count_current = "server_datagram_fd_established_count_current";

    static {
        GlobalInspection.getInstance().registerHelpMessage(server_datagram_fd_accept_queue_length_current,
            "The current accept queue length of virtual fds from server datagram fd");
        GlobalInspection.getInstance().registerHelpMessage(server_datagram_fd_established_count_current,
            "The current count of established virtual fds from server datagram fd");
    }

    private final DatagramFD server;
    private final SelectorEventLoop loop;
    private final WrappedSelector selector;

    private final ByteBuffer buf = Utils.allocateByteBuffer(Config.udpMtu); // enough for any udp packet
    private final Deque<VirtualDatagramFD> acceptQ = new LinkedList<>();
    private final Map<IPPort, VirtualDatagramFD> acceptMap = new HashMap<>();
    private final Map<IPPort, VirtualDatagramFD> conns = new HashMap<>();

    private GaugeF statisticsAcceptQueueLength;
    private GaugeF statisticsEstablishedCount;

    public ServerDatagramFD(DatagramFD server, SelectorEventLoop loop) {
        this.server = server;
        this.loop = loop;
        selector = loop.selector;
    }

    @Override
    public void bind(IPPort l4addr) throws IOException {
        server.bind(l4addr);

        // init statistics
        String local = l4addr.formatToIPPortString();
        statisticsAcceptQueueLength = GlobalInspection.getInstance().addMetric(server_datagram_fd_accept_queue_length_current,
            Map.of("listen", local),
            (m, l) -> new GaugeF(m, l, () -> (long) acceptMap.size()));
        statisticsEstablishedCount = GlobalInspection.getInstance().addMetric(server_datagram_fd_established_count_current,
            Map.of("listen", local),
            (m, l) -> new GaugeF(m, l, () -> (long) conns.size()));
    }

    @Override
    public IPPort getLocalAddress() throws IOException {
        return server.getLocalAddress();
    }

    @SuppressWarnings("Java8MapApi")
    @Override
    public VirtualDatagramFD accept() throws IOException {
        while (true) {
            try {
                IPPort addr = server.receive(buf);
                if (addr == null) {
                    // no data for now
                    break;
                }

                buf.flip();

                boolean fireReadable = false;
                VirtualDatagramFD fd;
                if (conns.containsKey(addr)) {
                    fd = conns.get(addr);
                    fireReadable = true;
                } else if (acceptMap.containsKey(addr)) {
                    fd = acceptMap.get(addr);
                } else {
                    fd = null;
                }
                if (fd == null) {
                    // new fd
                    fd = new VirtualDatagramFD(addr);
                    acceptMap.put(addr, fd);
                    acceptQ.add(fd);
                }
                // append to fd
                ByteBuffer b = Utils.allocateByteBuffer(buf.limit() - buf.position());
                b.put(buf);
                b.flip();
                fd.bufs.add(b);

                if (fireReadable) {
                    fd.setReadable();
                }
            } finally {
                // reset buf
                buf.limit(buf.capacity());
                buf.position(0);
            }
        }

        // retrieve
        VirtualDatagramFD fd = acceptQ.poll();
        if (fd == null) {
            return null;
        }
        fd.setReadable();
        fd.setWritable();
        acceptMap.values().remove(fd);
        conns.put(fd.remoteAddress, fd);
        return fd;
    }

    @Override
    public void configureBlocking(boolean b) throws IOException {
        server.configureBlocking(b);
    }

    @Override
    public <T> void setOption(SocketOption<T> name, T value) throws IOException {
        server.setOption(name, value);
    }

    @Override
    public FD real() {
        return server.real();
    }

    @Override
    public boolean isOpen() {
        return server.isOpen();
    }

    @Override
    public FDCloseReturn close(FDCloseReq req) throws IOException {
        server.close();
        for (VirtualDatagramFD fd : conns.values()) {
            // the fd is accepted by user code
            // it's user's responsibility to close it
            // so only release the fd but do not close it
            fd.release();
        }
        VirtualDatagramFD fd;
        while ((fd = acceptQ.poll()) != null) {
            // it's not accepted yet
            // so we can close the fd
            fd.close();
        }
        acceptMap.clear();
        conns.clear();

        if (statisticsAcceptQueueLength != null) {
            GlobalInspection.getInstance().removeMetric(statisticsAcceptQueueLength);
        }
        if (statisticsEstablishedCount != null) {
            GlobalInspection.getInstance().removeMetric(statisticsEstablishedCount);
        }
        return FDCloseReturn.nothing(req);
    }

    @Override
    public void writable() {
        // cancel writable
        selector.modify0(this,
            selector.events(this).reduce(EventSet.write()));
        for (var fd : conns.values()) {
            fd.setWritable();
        }
    }

    public class VirtualDatagramFD implements VirtualFD, SocketFD {
        private final ServerDatagramFD serverSelf = ServerDatagramFD.this;
        private final Deque<ByteBuffer> bufs = new LinkedList<>();

        private final IPPort remoteAddress;

        private boolean closed = false;

        VirtualDatagramFD(IPPort remoteAddress) {
            this.remoteAddress = remoteAddress;
        }

        @Override
        public void connect(IPPort l4addr) {
            throw new UnsupportedOperationException("not supported");
        }

        @Override
        public boolean isConnected() {
            return !closed;
        }

        @Override
        public void shutdownOutput() {
            // ignore, udp do not have connection statem
        }

        @Override
        public IPPort getLocalAddress() throws IOException {
            return serverSelf.getLocalAddress();
        }

        @Override
        public IPPort getRemoteAddress() {
            return remoteAddress;
        }

        @Override
        public boolean finishConnect() {
            return true;
        }

        @Override
        public int read(ByteBuffer dst) {
            if (bufs.isEmpty() && closed) {
                return -1;
            }

            int ret = Utils.writeFromFIFOQueueToBufferPacketBound(bufs, dst);

            if (bufs.isEmpty() && !closed) {
                cancelReadable();
            } else {
                setReadable();
            }
            return ret;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            int contained = src.limit() - src.position();
            int wrote = server.send(src, remoteAddress);
            if (wrote < contained) {
                assert Logger.lowLevelDebug("wrote(" + wrote + ") < contained(" + contained + "), cancelWritable");
                cancelWritable(true);
            } else {
                assert Logger.lowLevelDebug("wrote(" + wrote + ") >= contained(" + contained + "), is still writable");
                setWritable();
            }
            return wrote;
        }

        private void setReadable() {
            loop.selector.registerVirtualReadable(this);
        }

        private void setWritable() {
            assert Logger.lowLevelDebug("setWritable in " + VirtualDatagramFD.this);
            loop.selector.registerVirtualWritable(this);
        }

        private void cancelReadable() {
            loop.selector.removeVirtualReadable(this);
        }

        private void cancelWritable(boolean addWritableEvent) {
            assert Logger.lowLevelDebug("cancelWritable in " + VirtualDatagramFD.this + ", addWritableEvent=" + addWritableEvent);
            loop.selector.removeVirtualWritable(this);
            if (addWritableEvent) {
                selector.modify(ServerDatagramFD.this,
                    selector.events(ServerDatagramFD.this).combine(EventSet.write()));
            }
        }

        @Override
        public void configureBlocking(boolean b) {
            // do nothing
        }

        @Override
        public <T> void setOption(SocketOption<T> name, T value) {
            // do nothing
        }

        @Override
        public FD real() {
            return serverSelf.server.real();
        }

        @Override
        public boolean isOpen() {
            return !closed;
        }

        void release() {
            if (closed) {
                return;
            }
            setReadable(); // user code should be able to get -1 when calling read()
            cancelWritable(false);
            closed = true;
        }

        @Override
        public void close() {
            FDCloseReq.inst().wrapClose(this::close);
        }

        @Override
        public FDCloseReturn close(FDCloseReq req) {
            release();
            bufs.clear();
            conns.values().remove(this);
            var x = acceptMap.remove(remoteAddress);
            assert x == null || x == this;
            if (x != null) {
                acceptQ.remove(this);
            }
            return FDCloseReturn.nothing(req);
        }

        @Override
        public void onRegister() {
            if (!bufs.isEmpty()) {
                setReadable();
            }
            setWritable();
        }

        @Override
        public void onRemove() {
            // do nothing
        }

        @Override
        public String toString() {
            return "VirtualDatagramFD(" + serverSelf.server + ", remote=" + remoteAddress + ")";
        }
    }

    @Override
    public String toString() {
        return "ServerDatagramFD(" + server + ")";
    }
}
