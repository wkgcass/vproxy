package vproxy.selector.wrap.udp;

import vfd.DatagramFD;
import vfd.FD;
import vfd.ServerSocketFD;
import vfd.SocketFD;
import vproxy.app.Config;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.TimerEvent;
import vproxy.selector.wrap.WrappedSelector;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.util.*;

public final class ServerDatagramFD implements FD, ServerSocketFD {
    private static final int EXPIRE_TIME = 15 * 60 * 1000;

    private final DatagramFD server;
    private final SelectorEventLoop loop;

    private final ByteBuffer buf = ByteBuffer.allocateDirect(65536); // enough for any udp packet
    private final Deque<VirtualDatagramFD> acceptQ = new LinkedList<>();
    private final Map<SocketAddress, VirtualDatagramFD> acceptMap = new HashMap<>();
    private final Map<SocketAddress, VirtualDatagramFD> conns = new HashMap<>();

    public ServerDatagramFD(DatagramFD server, SelectorEventLoop loop) {
        this.server = server;
        this.loop = loop;
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

                VirtualDatagramFD fd;
                if (conns.containsKey(addr)) {
                    fd = conns.get(addr);
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
                ByteBuffer b = ByteBuffer.allocateDirect(buf.limit() - buf.position());
                b.put(buf);
                b.flip();
                fd.bufs.add(b);
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
        acceptMap.values().remove(fd);
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
        Utils.clean(buf);
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

    public class VirtualDatagramFD implements SocketFD {
        private final ServerDatagramFD serverSelf = ServerDatagramFD.this;
        private final Deque<ByteBuffer> bufs = new LinkedList<>();

        private final SocketAddress remoteAddress;
        private TimerEvent expireTimer;
        private long lastTimestamp;

        private boolean connected = false;
        private boolean closed = false;

        VirtualDatagramFD(SocketAddress remoteAddress) {
            this.remoteAddress = remoteAddress;
            lastTimestamp = Config.currentTimestamp;
            expireTimer = loop.delay(EXPIRE_TIME, this::checkAndHandleExpire);
        }

        private void checkAndHandleExpire() {
            long now = Config.currentTimestamp;
            if (now - lastTimestamp > EXPIRE_TIME) {
                // expired
                expireTimer = null;
                close();
            } else {
                // not expired, reset the timer
                int wait = (int) (lastTimestamp + EXPIRE_TIME - now);
                expireTimer = loop.delay(wait, this::checkAndHandleExpire);
            }
        }


        @Override
        public void connect(InetSocketAddress l4addr) {
            throw new UnsupportedOperationException("not supported");
        }

        @Override
        public boolean isConnected() {
            return connected;
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
            connected = true;
            return true;
        }

        @Override
        public int read(ByteBuffer dst) {
            lastTimestamp = Config.currentTimestamp;

            int ret = 0;
            while (true) {
                if (bufs.isEmpty()) {
                    // src is empty
                    break;
                }
                ByteBuffer b = bufs.peek();
                int oldLim = b.limit();
                int oldPos = b.position();
                if (oldLim - oldPos == 0) {
                    Utils.clean(bufs.poll());
                    continue;
                }
                int dstLim = dst.limit();
                int dstPos = dst.position();

                if (dstLim - dstPos == 0) {
                    // dst is full
                    break;
                }

                if (dstLim - dstPos < oldLim - oldPos) {
                    b.limit(oldPos + (dstLim - dstPos));
                }
                ret += (b.limit() - b.position());
                dst.put(b);
                b.limit(oldLim);
            }
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
                setWritable();
            } else {
                cancelWritable();
            }
            return wrote;
        }

        private void setReadable() {
            ((WrappedSelector) loop.selector).registerVirtualReadable(this);
        }

        private void setWritable() {
            ((WrappedSelector) loop.selector).registerVirtualWritable(this);
        }

        private void cancelReadable() {
            ((WrappedSelector) loop.selector).removeVirtualReadable(this);
        }

        private void cancelWritable() {
            ((WrappedSelector) loop.selector).removeVirtualWritable(this);
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
            cancelWritable();

            ByteBuffer b;
            while ((b = bufs.poll()) != null) {
                Utils.clean(b);
            }
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
    }
}
