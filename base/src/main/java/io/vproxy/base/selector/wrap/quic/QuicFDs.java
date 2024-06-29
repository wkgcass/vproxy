package io.vproxy.base.selector.wrap.quic;

import io.vproxy.base.util.Logger;
import io.vproxy.msquic.QuicConnectionEventConnected;
import io.vproxy.msquic.QuicConnectionEventConnectionShutdownComplete;
import io.vproxy.msquic.callback.ConnectionCallback;
import io.vproxy.msquic.callback.ConnectionCallbackList;
import io.vproxy.msquic.wrap.Configuration;
import io.vproxy.msquic.wrap.Connection;
import io.vproxy.msquic.wrap.Registration;
import io.vproxy.vfd.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class QuicFDs implements FDs {
    private final boolean withLog;

    public final Registration reg;
    public final Configuration conf;
    public final List<String> alpn;
    private final Map<IPPort, QuicServerSocketFD> connections = new ConcurrentHashMap<>();

    public QuicFDs(Registration reg, Configuration conf, String... alpn) {
        this(false, reg, conf, alpn);
    }

    public QuicFDs(boolean withLog, Registration reg, Configuration conf, String... alpn) {
        this(withLog, reg, conf, Arrays.asList(alpn));
    }

    public QuicFDs(Registration reg, Configuration conf, List<String> alpn) {
        this(false, reg, conf, alpn);
    }

    public QuicFDs(boolean withLog, Registration reg, Configuration conf, List<String> alpn) {
        this.withLog = withLog;
        this.reg = reg;
        this.conf = conf;
        this.alpn = Collections.unmodifiableList(alpn);
    }

    public boolean isWithLog() {
        return withLog;
    }

    @Override
    public SocketFD openSocketFD() throws IOException {
        return new QuicDelegateSocketFD(this);
    }

    @Override
    public ServerSocketFD openServerSocketFD() throws IOException {
        return new QuicListenerServerSocketFD(this);
    }

    @Override
    public DatagramFD openDatagramFD() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public FDSelector openSelector() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isV4V6DualStack() {
        return true;
    }

    public void lookupOrCreateConnection(IPPort target,
                                         Consumer<QuicServerSocketFD> readyCallback,
                                         Consumer<QuicServerSocketFD> shutdownCallback) throws IOException {
        assert Logger.lowLevelDebug("lookupOrCreateConnection: " + target);

        var fd = connections.get(target);
        boolean notFound = false;
        if (fd == null) {
            synchronized (connections) {
                fd = connections.get(target);
                if (fd == null) {
                    assert Logger.lowLevelDebug("connection not found, need to create one");
                    notFound = true;

                    fd = QuicServerSocketFD.newConnection(isWithLog(), reg, conf, target);
                    connections.put(target, fd);

                    ((ConnectionCallbackList) fd.conn.opts.callback).add(new ConnectionCallback() {
                        @Override
                        public int shutdownComplete(Connection conn, QuicConnectionEventConnectionShutdownComplete data) {
                            //noinspection resource
                            connections.remove(target);
                            return 0;
                        }
                    });
                }
            }
        }

        //noinspection RedundantIfStatement
        if (!notFound) {
            assert Logger.lowLevelDebug("connection is found, reuse it");
        }

        if (fd.isReady()) {
            assert Logger.lowLevelDebug("the connection is 'ready', directly invoke the callback");
            readyCallback.accept(fd);
            return;
        }
        assert Logger.lowLevelDebug("the connection is not ready yet ...");
        final var ffd = fd;
        ((ConnectionCallbackList) fd.conn.opts.callback).add(new ConnectionCallback() {
            boolean isConnected = false;

            @Override
            public boolean remove(Connection conn) {
                return isConnected;
            }

            @Override
            public int connected(Connection conn, QuicConnectionEventConnected data) {
                isConnected = true;
                readyCallback.accept(ffd);
                return 0;
            }

            @Override
            public int shutdownComplete(Connection conn, QuicConnectionEventConnectionShutdownComplete data) {
                shutdownCallback.accept(ffd);
                return 0;
            }
        });
    }
}
