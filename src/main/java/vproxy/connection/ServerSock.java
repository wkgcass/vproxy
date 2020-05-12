package vproxy.connection;

import vfd.*;
import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.udp.ServerDatagramFD;
import vproxy.selector.wrap.udp.UDPBasedFDs;
import vproxy.util.LogType;
import vproxy.util.Logger;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.BindException;
import java.net.StandardSocketOptions;
import java.util.concurrent.atomic.LongAdder;

public class ServerSock implements NetFlowRecorder {
    private static int supportReusePort = -1; // 1:true 0:false -1:not decided yet
    private static int supportTransparent = -1; // 1:true 0:false -1:not decided yet

    public final IPPort bind;
    private final String _id;
    public final ServerSocketFD channel;

    // statistics
    private final LongAdder fromRemoteBytes = new LongAdder();
    private final LongAdder toRemoteBytes = new LongAdder();
    private long historyAcceptedConnectionCount = 0; // no concurrency when accepting connections

    NetEventLoop _eventLoop = null;

    private boolean closed;

    public static boolean supportReusePort() {
        if (supportReusePort == 1) return true;
        if (supportReusePort == 0) return false;
        ServerSocketFD chnl;
        try {
            chnl = FDProvider.get().openServerSocketFD();
        } catch (IOException e) {
            // should not happen
            Logger.shouldNotHappen("creating channel failed", e);
            return false; // return false as default
        }
        try {
            chnl.setOption(StandardSocketOptions.SO_REUSEPORT, true);
            chnl.bind(new IPPort(IP.from("0.0.0.0"), 0));
        } catch (UnsupportedOperationException ignore) {
            Logger.warn(LogType.SYS_ERROR, "the operating system does not support SO_REUSEPORT");
            supportReusePort = 0;
            return false;
        } catch (IOException e) {
            // should not happen
            Logger.shouldNotHappen("setting SO_REUSEPORT throws IOException", e);
            return false; // return false as default
        } finally {
            try {
                chnl.close();
            } catch (IOException e) {
                Logger.shouldNotHappen("closing channel failed", e);
            }
        }
        supportReusePort = 1;
        return true;
    }

    public static boolean supportTransparent() {
        if (supportTransparent == 1) return true;
        if (supportTransparent == 0) return false;
        ServerSocketFD chnl;
        try {
            chnl = FDProvider.get().openServerSocketFD();
        } catch (IOException e) {
            // should not happen
            Logger.shouldNotHappen("creating channel failed", e);
            return false; // return false as default
        }
        try {
            chnl.setOption(SocketOptions.IP_TRANSPARENT, true);
            chnl.bind(new IPPort(IP.from("100.66.77.88"), 11223));
        } catch (UnsupportedOperationException ignore) {
            Logger.warn(LogType.SYS_ERROR, "the operating system or implementation does not support IP_TRANSPARENT");
            supportTransparent = 0;
            return false;
        } catch (IOException e) {
            // should not happen
            Logger.shouldNotHappen("setting IP_TRANSPARENT throws IOException", e);
            return false; // return false as default
        } finally {
            try {
                chnl.close();
            } catch (IOException e) {
                Logger.shouldNotHappen("closing channel failed", e);
            }
        }
        supportTransparent = 1;
        return true;
    }

    public static class BindOptions {
        public boolean transparent = false;

        public BindOptions setTransparent(boolean transparent) {
            this.transparent = transparent;
            return this;
        }
    }

    public static void checkBind(IPPort bindAddress) throws IOException {
        try (ServerSocketFD foo = FDProvider.get().openServerSocketFD()) {
            foo.bind(bindAddress);
        } catch (BindException ex) {
            throw new IOException("bind failed for " + bindAddress, ex);
        }
    }

    public static void checkBind(Protocol protocol, IPPort bindAddress) throws IOException {
        if (protocol == Protocol.TCP) {
            checkBind(bindAddress);
        } else {
            try (DatagramFD foo = FDProvider.get().openDatagramFD()) {
                foo.bind(bindAddress);
            } catch (BindException ex) {
                throw new IOException("bind failed for " + bindAddress, ex);
            }
        }
    }

    private static ServerSock create(ServerSocketFD channel, IPPort bindAddress, BindOptions opts) throws IOException {
        channel.configureBlocking(false);
        if (supportReusePort()) {
            channel.setOption(StandardSocketOptions.SO_REUSEPORT, true);
        }
        if (opts.transparent) {
            if (!supportTransparent()) {
                throw new UnsupportedEncodingException("IP_TRANSPARENT not supported");
            }
            channel.setOption(SocketOptions.IP_TRANSPARENT, true);
        }
        channel.bind(bindAddress);
        try {
            return new ServerSock(channel);
        } catch (IOException e) {
            channel.close(); // close the channel if create ServerSock failed
            throw e;
        }
    }

    public static ServerSock create(IPPort bindAddress) throws IOException {
        return create(bindAddress, new BindOptions());
    }

    public static ServerSock create(IPPort bindAddress, BindOptions opts) throws IOException {
        ServerSocketFD channel = FDProvider.get().openServerSocketFD();
        return create(channel, bindAddress, opts);
    }

    // note: the input loop should be the same that would be added
    public static ServerSock createUDP(IPPort bindAddress, SelectorEventLoop loop) throws IOException {
        DatagramFD channel = FDProvider.get().openDatagramFD();
        ServerDatagramFD fd = new ServerDatagramFD(channel, loop);
        return create(fd, bindAddress, new BindOptions());
    }

    public static ServerSock createUDP(IPPort bindAddress, SelectorEventLoop loop, UDPBasedFDs fds) throws IOException {
        return create(fds.openServerSocketFD(loop), bindAddress, new BindOptions());
    }

    private ServerSock(ServerSocketFD channel) throws IOException {
        this.channel = channel;
        bind = channel.getLocalAddress();
        _id = bind.formatToIPPortString();
    }

    // --- START statistics ---
    public long getFromRemoteBytes() {
        return fromRemoteBytes.longValue();
    }

    public long getToRemoteBytes() {
        return toRemoteBytes.longValue();
    }

    @Override
    public void incFromRemoteBytes(long bytes) {
        fromRemoteBytes.add(bytes);
    }

    @Override
    public void incToRemoteBytes(long bytes) {
        toRemoteBytes.add(bytes);
    }

    public void incHistoryAcceptedConnectionCount() {
        ++historyAcceptedConnectionCount;
    }

    public long getHistoryAcceptedConnectionCount() {
        return historyAcceptedConnectionCount;
    }
    // --- END statistics ---

    public boolean isClosed() {
        return closed;
    }

    // make it synchronized to prevent fields being inconsistent
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        NetEventLoop eventLoop = _eventLoop;
        if (eventLoop != null) {
            eventLoop.removeServer(this);
        }

        _eventLoop = null;
        try {
            channel.close();
        } catch (IOException e) {
            // we can do nothing about it
            Logger.error(LogType.CONN_ERROR, "got error when closing server channel " + e);
        }
    }

    public String id() {
        return _id;
    }

    @Override
    public String toString() {
        return "ServerSock(" + id() + ")[" + (closed ? "closed" : "open") + "]";
    }
}
