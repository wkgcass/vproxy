package vproxy.connection;

import vfd.FDProvider;
import vfd.ServerSocketFD;
import vproxy.util.LogType;
import vproxy.util.Logger;
import vproxy.util.Utils;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.util.concurrent.atomic.LongAdder;

public class ServerSock implements NetFlowRecorder {
    private static int supportReusePort = -1; // 1:true 0:false -1:not decided yet

    public final InetSocketAddress bind;
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

    public static void checkBind(InetSocketAddress bindAddress) throws IOException {
        try (ServerSocketFD foo = FDProvider.get().openServerSocketFD()) {
            foo.bind(bindAddress);
        } catch (BindException ex) {
            throw new IOException("bind failed for " + bindAddress, ex);
        }
    }

    public static ServerSock create(InetSocketAddress bindAddress) throws IOException {
        ServerSocketFD channel = FDProvider.get().openServerSocketFD();
        channel.configureBlocking(false);
        if (supportReusePort()) {
            channel.setOption(StandardSocketOptions.SO_REUSEPORT, true);
        }
        channel.bind(bindAddress);
        try {
            return new ServerSock(channel);
        } catch (IOException e) {
            channel.close(); // close the channel if create ServerSock failed
            throw e;
        }
    }

    private ServerSock(ServerSocketFD channel) throws IOException {
        this.channel = channel;
        bind = (InetSocketAddress) channel.getLocalAddress();
        _id = Utils.ipStr(bind.getAddress().getAddress()) + ":" + bind.getPort();
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
