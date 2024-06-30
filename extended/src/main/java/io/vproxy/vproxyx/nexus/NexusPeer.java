package io.vproxy.vproxyx.nexus;

import io.vproxy.base.selector.PeriodicEvent;
import io.vproxy.base.selector.wrap.quic.QuicSocketFD;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.coll.RingQueue;
import io.vproxy.msquic.*;
import io.vproxy.msquic.callback.ConnectionCallback;
import io.vproxy.msquic.callback.ConnectionCallbackList;
import io.vproxy.msquic.wrap.Connection;
import io.vproxy.msquic.wrap.Listener;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.array.IntArray;
import io.vproxy.vfd.IPPort;
import io.vproxy.vproxyx.nexus.entity.LinkReq;

import java.io.IOException;

public class NexusPeer {
    public final NexusContext nctx;
    public final IPPort remoteAddress;
    private boolean isServer = false;

    private volatile boolean isInitialized = false; // controls whether quicConn is returned
    private PeriodicEvent periodicEvent = null;
    private NexusNode node;
    private Connection quicConn;

    private final RingQueue<LinkReq> linkUpdateEvents = new RingQueue<>();

    private NexusPeer(NexusContext nctx, IPPort remoteAddress) {
        this.nctx = nctx;
        this.remoteAddress = remoteAddress;
    }

    public static NexusPeer create(NexusContext nctx, IPPort connectTo) {
        return new NexusPeer(nctx, connectTo);
    }

    public static int createAccepted(NexusContext nctx, IPPort remote,
                                     QuicConnection connQ, Listener listener, QuicListenerEventNewConnection data, Allocator allocator) {
        var peer = new NexusPeer(nctx, remote);
        peer.isServer = true;
        ConnectionCallback cb = peer.new NexusNodeConnectionCallback();
        if (nctx.debug) {
            cb = ConnectionCallbackList.withLog(cb, true);
        }
        peer.quicConn = new Connection(new Connection.Options(listener, allocator, cb, connQ));
        peer.quicConn.setConnectionInfo(data);
        if (nctx.debug) {
            peer.quicConn.enableTlsSecretDebug();
        }
        connQ.setCallbackHandler(MsQuicUpcall.connectionCallback, peer.quicConn.ref.MEMORY);
        var err = connQ.setConfiguration(nctx.serverConfiguration.opts.configurationQ);
        if (err != 0) {
            peer.quicConn.close();
        }
        return err;
    }

    public void start() {
        if (isServer) {
            throw new UnsupportedOperationException();
        }
        doConnect();
        periodicEvent =
            nctx.loop.getSelectorEventLoop().period(5_000, () -> {
                if (quicConn != null) {
                    // already connected
                    return;
                }
                synchronized (this) {
                    if (quicConn != null) {
                        return;
                    }
                    doConnect();
                }
            });
    }

    private void doConnect() {
        var allocator = Allocator.ofUnsafe();
        Connection conn;
        try (var tmpAllocator = Allocator.ofConfined()) {
            var returnStatus = new IntArray(tmpAllocator, 1);
            ConnectionCallback cb = new NexusNodeConnectionCallback();
            if (nctx.debug) {
                cb = ConnectionCallbackList.withLog(cb, true);
            }
            conn = new Connection(new Connection.Options(nctx.registration, allocator, cb,
                ref -> nctx.registration.opts.registrationQ.openConnection(
                    MsQuicUpcall.connectionCallback, ref.MEMORY, returnStatus, allocator
                )));
            if (returnStatus.get(0) != 0) {
                Logger.error(LogType.CONN_ERROR, "creating quic connection failed, errcode=" + returnStatus.get(0));
                conn.close();
                return;
            }
        }
        if (nctx.debug) {
            conn.enableTlsSecretDebug();
        }
        int errcode = conn.start(nctx.clientConfiguration, remoteAddress);
        if (errcode != 0) {
            Logger.error(LogType.CONN_ERROR, "starting quic connection to " + remoteAddress + " failed, errcode=" + errcode);
            conn.close();
            return;
        }
        quicConn = conn;
        Logger.warn(LogType.ALERT, "trying to connect to " + remoteAddress.formatToIPPortString() + " ...");
    }

    public void linkUpdateEvent(LinkReq req) {
        if (!isInitialized) {
            return;
        }
        linkUpdateEvents.add(req);
    }

    public RingQueue<LinkReq> getAndClearLinkUpdateEvents() {
        var ret = new RingQueue<LinkReq>();
        for (var e : linkUpdateEvents) {
            ret.add(e);
        }
        linkUpdateEvents.clear();
        return ret;
    }

    public NexusNode getNode() {
        return node;
    }

    public void terminate(Connection terminateQuicConn, String reason) {
        var quicConn = this.quicConn;
        if (quicConn != terminateQuicConn) {
            return;
        }
        var node = this.node;
        this.node = null;
        this.quicConn = null;

        isInitialized = false;

        if (node != null) {
            nctx.nexus.remove(node);
        }
        if (quicConn != null) {
            if (quicConn.isConnected()) {
                Logger.warn(LogType.ALERT, "quic connection " + remoteAddress.formatToIPPortString() + " terminated: " + reason);
            } else {
                Logger.warn(LogType.ALERT, "quic connection " + remoteAddress.formatToIPPortString() + " terminated before connected");
            }
            quicConn.close();
        }
        linkUpdateEvents.clear();
    }

    public void initialize(Connection quicConn, String nodeName) {
        if (this.quicConn != quicConn) {
            Logger.error(LogType.INVALID_STATE, "the old connection is initialized, old=" + quicConn + ", now=" + quicConn);
            quicConn.close();
            return;
        }
        var node = nctx.nexus.getNode(nodeName);
        if (node != null) {
            if (node.peer != null || node == nctx.nexus.getSelfNode()) {
                Logger.warn(LogType.INVALID_EXTERNAL_DATA,
                    "received node " + nodeName + " from " + quicConn.getRemoteAddress() +
                    ", but the node already exists");
                terminate(quicConn, "invalid node " + nodeName);
                return;
            }
            Logger.warn(LogType.ALERT, "peer " + nodeName + " replaces a non-peer node");
            nctx.nexus.remove(node);
        }
        node = new NexusNode(nodeName, this);
        this.node = node;
        var self = nctx.nexus.getSelfNode();
        nctx.nexus.addNode(self, node, Integer.MAX_VALUE);

        isInitialized = true;
        Logger.alert("connection " + remoteAddress.formatToIPPortString() + " is initialized");

        if (isServer) {
            initializeServerActiveControlStream();
        }
    }

    private void initializeServerActiveControlStream() {
        var quicConn = this.quicConn;
        if (quicConn == null) {
            return;
        }
        QuicSocketFD fd;
        try {
            fd = QuicSocketFD.newStream(nctx.debug, quicConn);
        } catch (IOException e) {
            Logger.error(LogType.CONN_ERROR, "failed to initiate quic stream to " + quicConn.getRemoteAddress(), e);
            terminate(quicConn, "failed to initiate quic stream");
            return;
        }
        StreamHandlers.INSTANCE.handleActiveControlStream(nctx, this, fd, false);
    }

    private class NexusNodeConnectionCallback implements ConnectionCallback {
        @Override
        public int connected(Connection conn, QuicConnectionEventConnected data) {
            if (isServer) {
                Logger.alert("connection from " + remoteAddress.formatToIPPortString() + " established");
                return 0;
            }
            Logger.alert("connected to " + remoteAddress.formatToIPPortString());
            nctx.loop.getSelectorEventLoop().nextTick(() -> {
                QuicSocketFD fd;
                try {
                    fd = QuicSocketFD.newStream(nctx.debug, conn);
                } catch (IOException e) {
                    Logger.error(LogType.SOCKET_ERROR, "failed to create stream from " + conn, e);
                    conn.close();
                    return;
                }
                StreamHandlers.INSTANCE.handleActiveControlStream(nctx, NexusPeer.this, fd, true);
            });
            return 0;
        }

        @Override
        public int shutdownComplete(Connection conn, QuicConnectionEventConnectionShutdownComplete data) {
            terminate(conn, "shutdown complete");
            return 0;
        }

        @Override
        public int peerStreamStarted(Connection conn, QuicConnectionEventPeerStreamStarted data) {
            var fd = QuicSocketFD.wrapAcceptedStream(nctx.debug, conn, data.getStream());
            if (isInitialized) {
                StreamHandlers.INSTANCE.handleAccepted(nctx, NexusPeer.this, fd);
            } else if (isServer) {
                StreamHandlers.INSTANCE.handlePassiveControlStream(nctx, NexusPeer.this, fd);
            } else {
                Logger.error(LogType.INVALID_STATE, "the connection " + remoteAddress + " is not initialized yet, stream will be closed");
                nctx.loop.getSelectorEventLoop().nextTick(fd::close);
            }
            return 0;
        }
    }

    public Connection getQuicConnection() {
        if (isInitialized) {
            return quicConn;
        }
        return null;
    }

    public void close() {
        var periodicEvent = this.periodicEvent;
        this.periodicEvent = null;
        if (periodicEvent != null) {
            periodicEvent.cancel();
        }
        if (quicConn != null) {
            terminate(quicConn, "closed");
        }
    }
}
