package vproxy.selector.wrap.udp;

import vfd.*;
import vproxy.app.Config;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.TimerEvent;
import vproxy.selector.wrap.VirtualFD;
import vproxy.selector.wrap.WrappedSelector;
import vproxy.selector.wrap.WritableAware;
import vproxy.util.Logger;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.util.*;

public final class ServerDatagramFD implements FD, ServerSocketFD, WritableAware {
    private final DatagramFD server;
    private final SelectorEventLoop loop;
    private final WrappedSelector selector;

    private final ByteBuffer buf = ByteBuffer.allocate(Config.udpMtu); // enough for any udp packet
    private final Deque<VirtualDatagramFD> acceptQ = new LinkedList<>();
    private final Map<SocketAddress, VirtualDatagramFD> acceptMap = new HashMap<>();
    private final Map<SocketAddress, VirtualDatagramFD> conns = new HashMap<>();

    public ServerDatagramFD(DatagramFD server, SelectorEventLoop loop) {
        this.server = server;
        this.loop = loop;
        selector = (WrappedSelector) loop.selector;
    }

    @Override
    public void bind(InetSocketAddress l4addr) throws IOException {
        server.bind(l4addr);
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        return server.getLocalAddress();
    }

    @SuppressWarnings("Java8MapApi")
    @Override
    public VirtualDatagramFD accept() throws IOException {
        while (true) {
            try {
                SocketAddress addr = server.receive(buf);
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
                ByteBuffer b = ByteBuffer.allocate(buf.limit() - buf.position());
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
    public void close() throws IOException {
        server.close();
        for (VirtualDatagramFD fd : conns.values()) {
            fd.release();
        }
        VirtualDatagramFD fd;
        while ((fd = acceptQ.poll()) != null) {
            fd.release();
        }
        acceptMap.clear();
        conns.clear();
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

        private final SocketAddress remoteAddress;
        private TimerEvent expireTimer;
        private long lastTimestamp;

        private boolean closed = false;

        VirtualDatagramFD(SocketAddress remoteAddress) {
            this.remoteAddress = remoteAddress;
            lastTimestamp = Config.currentTimestamp;
            expireTimer = loop.delay(Config.udpTimeout, this::checkAndHandleExpire);
        }

        private void checkAndHandleExpire() {
            long now = Config.currentTimestamp;
            if (now - lastTimestamp > Config.udpTimeout) {
                // expired
                expireTimer = null;
                close();
            } else {
                // not expired, reset the timer
                int wait = (int) (lastTimestamp + Config.udpTimeout - now);
                expireTimer = loop.delay(wait, this::checkAndHandleExpire);
            }
        }


        @Override
        public void connect(InetSocketAddress l4addr) {
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
        public SocketAddress getLocalAddress() throws IOException {
            return serverSelf.getLocalAddress();
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return remoteAddress;
        }

        @Override
        public boolean finishConnect() {
            return true;
        }

        @Override
        public int read(ByteBuffer dst) {
            lastTimestamp = Config.currentTimestamp;

            int ret = Utils.writeFromFIFOQueueToBuffer(bufs, dst);

            if (bufs.isEmpty()) {
                cancelReadable();
            } else {
                setReadable();
            }
            return ret;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            lastTimestamp = Config.currentTimestamp;
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
            ((WrappedSelector) loop.selector).registerVirtualReadable(this);
        }

        private void setWritable() {
            assert Logger.lowLevelDebug("setWritable in " + VirtualDatagramFD.this);
            ((WrappedSelector) loop.selector).registerVirtualWritable(this);
        }

        private void cancelReadable() {
            ((WrappedSelector) loop.selector).removeVirtualReadable(this);
        }

        private void cancelWritable(boolean addWritableEvent) {
            assert Logger.lowLevelDebug("cancelWritable in " + VirtualDatagramFD.this + ", addWritableEvent=" + addWritableEvent);
            ((WrappedSelector) loop.selector).removeVirtualWritable(this);
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
            closed = true;
            if (expireTimer != null) {
                expireTimer.cancel();
                expireTimer = null;
            }
            cancelReadable();
            cancelWritable(false);

            bufs.clear();
        }

        @Override
        public void close() {
            release();
            conns.values().remove(this);
            var x = acceptMap.remove(remoteAddress);
            assert x == null || x == this;
            if (x != null) {
                acceptQ.remove(this);
            }
        }

        @Override
        public void onRegister() {
            // do nothing
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
