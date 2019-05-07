package net.cassite.vproxy.component.proxy;

import net.cassite.vproxy.connection.*;
import net.cassite.vproxy.util.ByteArrayChannel;
import net.cassite.vproxy.util.LogType;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.RingBuffer;

import java.io.IOException;
import java.util.*;

@SuppressWarnings("unchecked")
class ProcessorConnectionHandler implements ConnectionHandler {
    private ProxyNetConfig config;
    private final Processor processor;
    private final Processor.Context topCtx;
    private final Connection frontendConnection;
    private final Processor.SubContext frontendSubCtx;
    private final NetEventLoop loop;

    private final Map<BackendConnectionHandler, Integer> conn2intMap = new HashMap<>();

    private int cursor = 0;
    private final BackendConnectionHandler[] conns = new BackendConnectionHandler[1024 + 1];
    // [0] will not be used
    // I believe that 1024 connections should be enough

    private ByteArrayChannel chnl = null;

    public ProcessorConnectionHandler(ProxyNetConfig config, NetEventLoop loop, Connection frontendConnection, Processor processor, Processor.Context topCtx, Processor.SubContext frontendSubCtx) {
        this.config = config;
        this.processor = processor;
        this.topCtx = topCtx;
        this.frontendConnection = frontendConnection;
        this.frontendSubCtx = frontendSubCtx;
        this.loop = loop;
    }

    void recordBackend(BackendConnectionHandler backend, int connId) {
        conn2intMap.put(backend, connId);
        conns[connId] = backend;
    }

    void removeBackend(BackendConnectionHandler backend) {
        int connId = conn2intMap.remove(backend);
        conns[connId] = null;
    }

    /**
     * a util function. NOTE: this method should be called in a while loop until no data to send or buffer is full
     *
     * @param flow             the target flow
     * @param sourceConnection source connection when proxying
     * @param targetConnection target connection when proxying
     * @param subCtx           sub context of the source connection
     */
    void utilWriteData(BackendConnectionHandler.ByteFlow flow,
                       Connection sourceConnection,
                       Connection targetConnection,
                       Processor.SubContext subCtx) {
        assert Logger.lowLevelDebug("calling utilWriteData, writing from " + sourceConnection + " to " + targetConnection);

        if (flow.runningProxy) {
            assert Logger.lowLevelDebug("running proxy, flow.bytesToProxy = " + flow.bytesToProxy);
            if (flow.bytesToProxy == 0) {
                // nothing to write
                return;
            }
            int n = sourceConnection.getInBuffer()
                .writeTo(targetConnection.getOutBuffer(), flow.bytesToProxy);
            flow.bytesToProxy -= n;
            assert Logger.lowLevelDebug("proxied " + n + " bytes, still have " + flow.bytesToProxy + " left");
            assert flow.bytesToProxy >= 0;
            // proxy is done
            if (flow.bytesToProxy == 0) {
                assert Logger.lowLevelDebug("proxy done");
                flow.runningProxy = false;
                processor.proxyDone(topCtx, subCtx);
            }
        } else {
            assert Logger.lowLevelDebug("sending bytes, flow.chnl.used = " + (flow.chnl == null ? "null" : flow.chnl.used()));
            if (flow.chnl == null) {
                if (flow.bytesToSend.isEmpty()) {
                    // nothing to write
                    return;
                }
                byte[] data = flow.bytesToSend.peek(); // do not remove from list for now <--#1
                flow.chnl = ByteArrayChannel.fromFull(data);
                assert Logger.lowLevelDebug("peek data from list, the length is " + data.length);
            }
            targetConnection.getOutBuffer().storeBytesFrom(flow.chnl);
            // sending this batch is done
            assert flow.chnl != null;
            assert Logger.lowLevelDebug("now flow.chnl.used == " + flow.chnl.used());
            if (flow.chnl.used() == 0) {
                flow.chnl = null;
                flow.bytesToSend.poll(); // remove from list when done <--#1
                if (flow.bytesToSend.isEmpty()) {
                    flow.runningProxy = true;
                }
            }
        }
    }

    // -----------------------------
    // --- START backend handler ---
    class BackendConnectionHandler implements ClientConnectionHandler {
        class ByteFlow {
            boolean runningProxy = false; // false = bytes, true = proxy
            ByteArrayChannel chnl = null;
            // NOTE: chnl always hold data of the first element in the list
            // and the list will only be poped when all data is sent in this chnl
            final LinkedList<byte[]> bytesToSend = new LinkedList<>();
            int bytesToProxy = 0;
            // the bytesToSend list may be added the same time the data in the list is sent
            // but when handling bytesToProxy, there will be no data adding to the bytesToSend list
            // until the proxy is done
            // This is because that the processor fetch data out from buffer and process them,
            // then add the processed data to the bytesToSend list.
            // However for the proxy operation, data will be directly proxied from
            // input buffer of connection A to output buffer of connection B.
            // Because of this, the `runningProxy` field is enough for the lib to know current running mode.
            // Still, when writing a Processor, keep this behavior in mind and
            // don't change mode from proxy to another until `proxyDone()` is called.

            void write(byte[] data) {
                bytesToSend.add(data);
                if (bytesToProxy == 0) {
                    runningProxy = false;
                }
            }

            void proxy(int len) {
                if (bytesToProxy == 0) {
                    bytesToProxy = len;
                }
                if (bytesToSend.isEmpty()) {
                    runningProxy = true;
                }
            }
        }

        private final Processor.SubContext subCtx;
        private final ClientConnection conn;
        private boolean isConnected = false;

        private ByteArrayChannel chnl = null;
        private final BackendConnectionHandler.ByteFlow backendByteFlow = new BackendConnectionHandler.ByteFlow();
        private final BackendConnectionHandler.ByteFlow frontendByteFlow = new BackendConnectionHandler.ByteFlow();

        BackendConnectionHandler(Processor.SubContext subCtx, ClientConnection conn) {
            this.subCtx = subCtx;
            this.conn = conn;
        }

        void writeToBackend(byte[] data) {
            backendByteFlow.write(data);
            doBackendWrite();
        }

        void proxyToBackend(int len) {
            backendByteFlow.proxy(len);
            doBackendWrite();
        }

        void writeToFrontend(byte[] data) {
            frontendByteFlow.write(data);
            frontendWrite(this);
        }

        void proxyToFrontend(int len) {
            frontendByteFlow.proxy(len);
            frontendWrite(this);
        }

        private boolean isWritingBackend = false;

        /**
         * write data from {@link #backendByteFlow} to backend connection
         */
        private void doBackendWrite() {
            if (!isConnected) {
                return; // do nothing if not connected yet
            }
            if (isWritingBackend) {
                assert Logger.lowLevelDebug("isWritingBackend exit the method");
                return; // it's already writing, should not call again
            }
            isWritingBackend = true;
            while (backendByteFlow.bytesToProxy != 0 || !backendByteFlow.bytesToSend.isEmpty()) {
                if (conn.getOutBuffer().free() == 0) {
                    // if the output is full, should break the writing process and wait for the next signal
                    assert Logger.lowLevelDebug("the backend output buffer is full now, break the sending loop");
                    break;
                }
                // if it's running proxy and the frontend connection input is empty, break the loop
                if (backendByteFlow.runningProxy && frontendConnection.getInBuffer().used() == 0) {
                    break;
                }

                utilWriteData(backendByteFlow, frontendConnection, conn, frontendSubCtx);
            }
            isWritingBackend = false; // writing done
            // if writing is done
            if (backendByteFlow.bytesToProxy == 0 && backendByteFlow.bytesToSend.isEmpty()) {
                // let the frontend read more data because there may still have some data in buffer
                readFrontend();
            }
        }

        @Override
        public void connected(ClientConnectionHandlerContext ctx) {
            isConnected = true;
            // no need to call processor.connected(...) here, it's already called when retrieving the connection
            doBackendWrite();
        }

        void readBackend() {
            if (conn.getInBuffer().used() == 0)
                return; // ignore the event if got nothing to read

            assert Logger.lowLevelDebug("calling readBackend() of " + conn);

            // check whether to proxy the data or to receive the data
            Processor.Mode mode = processor.mode(topCtx, subCtx);
            assert Logger.lowLevelDebug("the current mode is " + mode);

            if (mode == Processor.Mode.proxy) {
                int len = processor.len(topCtx, subCtx);
                assert Logger.lowLevelDebug("the proxy length is " + len);
                if (len == 0) { // 0 bytes to proxy, so it's already done
                    processor.proxyDone(topCtx, subCtx);
                    readBackend(); // recursively call to handle more input data
                } else {
                    proxyToFrontend(len);
                }
            } else {
                if (chnl == null) {
                    int len = processor.len(topCtx, subCtx);
                    assert Logger.lowLevelDebug("the expected message length is " + len);
                    if (len == 0) { // if nothing to read, then directly feed empty data to the processor
                        try {
                            processor.feed(topCtx, subCtx, new byte[0]);
                        } catch (Exception e) {
                            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "user code cannot handle data from " + conn + ", which corresponds to " + frontendConnection + ". err=" + e);
                            frontendConnection.close();
                            return;
                        }
                        readBackend(); // recursively handle more data
                        return;
                    }
                    chnl = ByteArrayChannel.fromEmpty(new byte[len]);
                }
                conn.getInBuffer().writeTo(chnl);
                if (chnl.free() != 0) {
                    assert Logger.lowLevelDebug("not fulfilled yet, expecting " + chnl.free() + " length of data");
                    // expecting more data
                    return;
                }
                assert Logger.lowLevelDebug("the message is totally read, feeding to processor");
                byte[] data = chnl.get();
                chnl = null;
                byte[] dataToSend;
                try {
                    dataToSend = processor.feed(topCtx, subCtx, data);
                } catch (Exception e) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA, "user code cannot handle data from " + conn + ", which corresponds to " + frontendConnection + ". err=" + e);
                    frontendConnection.close();
                    return;
                }
                assert Logger.lowLevelDebug("the processor return a message of length " + (dataToSend == null ? "null" : dataToSend.length));
                if (dataToSend == null || dataToSend.length == 0) {
                    readBackend(); // recursively call to handle more data
                } else {
                    writeToFrontend(dataToSend);
                }
            }
        }

        @Override
        public void readable(ConnectionHandlerContext ctx) {
            readBackend();
        }

        @Override
        public void writable(ConnectionHandlerContext ctx) {
            doBackendWrite();
        }

        @Override
        public void exception(ConnectionHandlerContext ctx, IOException err) {
            Logger.error(LogType.CONN_ERROR, "got exception when handling backend connection " + conn + ", closing frontend " + frontendConnection);
            frontendConnection.close();
            closeAll();
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            if (frontendConnection.isClosed()) {
                assert Logger.lowLevelDebug("backend connection " + ctx.connection + " closed, corresponding frontend is " + frontendConnection);
            } else {
                Logger.warn(LogType.CONN_ERROR, "backend connection " + ctx.connection + " closed before frontend connection " + frontendConnection);
            }
            closeAll();
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            if (!ctx.connection.isClosed())
                Logger.error(LogType.IMPROPER_USE, "backend connection " + ctx.connection + " removed from event loop " + loop);
            closeAll();
        }
    }
    // --- END backend handler ---
    // ---------------------------

    private BackendConnectionHandler handlingConnection;

    void frontendWrite(BackendConnectionHandler handlingConnection) {
        if (this.handlingConnection == null) {
            this.handlingConnection = handlingConnection;
        }
        if (this.handlingConnection == handlingConnection) {
            doFrontendWrite();
        }
    }

    private boolean isWritingFrontend = false;

    private void doFrontendWrite() {
        if (isWritingFrontend) {
            assert Logger.lowLevelDebug("isWritingFrontend exit the method");
            return; // should exit because it's already handling
            // this might happen when the frontend connection output buffer calls writable()
            // here is already a loop in the _doFrontendWrite(), so we just exit the method when it's already handling
        }
        isWritingFrontend = true;
        _doFrontendWrite();
        isWritingFrontend = false;
    }

    private void _doFrontendWrite() {
        if (handlingConnection == null) {
            return; // nothing to write
        }
        {
            BackendConnectionHandler.ByteFlow flow = handlingConnection.frontendByteFlow;
            while (flow.bytesToProxy != 0 || !flow.bytesToSend.isEmpty()) {
                if (frontendConnection.getOutBuffer().free() == 0) {
                    return; // end the method, because the out buffer has no space left
                }

                utilWriteData(flow, handlingConnection.conn, frontendConnection, handlingConnection.subCtx);

                // if writing done:
                if (flow.bytesToProxy == 0 && flow.bytesToSend.isEmpty()) {
                    // then let the backend connection read more data
                    // because the connection may be holding some data in the buffer
                    handlingConnection.readBackend();
                } else { // writing not done yet,
                    // but if it's running proxy and the backend connection input buffer is empty
                    if (flow.runningProxy && handlingConnection.conn.getInBuffer().used() == 0) {
                        return; // cannot handle for now, end the method
                    }
                }
            }
            // now nothing to be handled for this connection
            handlingConnection = null;
        }

        // check for other connections
        // and keep writing if have some data to write in other connections
        {
            BackendConnectionHandler next = null;
            for (BackendConnectionHandler b : conn2intMap.keySet()) {
                BackendConnectionHandler.ByteFlow flow = b.frontendByteFlow;
                if (flow.bytesToProxy != 0 || !flow.bytesToSend.isEmpty()) {
                    next = b;
                    break;
                }
            }
            handlingConnection = next;
            doFrontendWrite();
        }
    }

    void readFrontend() {
        if (frontendConnection.getInBuffer().used() == 0) {
            return; // do nothing if the in buffer is empty
        }

        assert Logger.lowLevelDebug("calling readFrontend()");

        // check whether to proxy the data or to receive the data
        Processor.Mode mode = processor.mode(topCtx, frontendSubCtx);
        assert Logger.lowLevelDebug("the current mode is " + mode);

        if (mode == Processor.Mode.proxy) {
            int bytesToProxy = processor.len(topCtx, frontendSubCtx);
            int connId = processor.connection(topCtx, frontendSubCtx);
            assert Logger.lowLevelDebug("the bytesToProxy is " + bytesToProxy + ", and connId is " + connId);
            BackendConnectionHandler backend = getConnection(connId);
            if (backend == null) {
                // for now, we simply close the whole connection when a backend is missing
                Logger.error(LogType.CONN_ERROR, "failed to retrieve the backend connection for " + frontendConnection + "/" + connId);
                frontendConnection.close();
            } else {
                if (bytesToProxy == 0) { // 0 bytes to proxy, so it's already done
                    processor.proxyDone(topCtx, frontendSubCtx);
                    readFrontend(); // recursively call to read more data
                } else {
                    backend.proxyToBackend(bytesToProxy);
                }
            }
        } else {
            assert mode == Processor.Mode.handle;

            if (chnl == null) {
                int len = processor.len(topCtx, frontendSubCtx);
                assert Logger.lowLevelDebug("expecting message with the length of " + len);
                if (len == 0) { // if the length is 0, directly feed data to the processor
                    try {
                        processor.feed(topCtx, frontendSubCtx, new byte[0]);
                    } catch (Exception e) {
                        Logger.warn(LogType.INVALID_EXTERNAL_DATA, "user code cannot handle data from " + frontendConnection + ". err=" + e);
                        frontendConnection.close();
                        return;
                    }
                    readFrontend(); // recursively try to handle more data
                    return;
                }
                chnl = ByteArrayChannel.fromEmpty(new byte[len]);
            }
            frontendConnection.getInBuffer().writeTo(chnl);
            if (chnl.free() != 0) {
                // want to read more data
                assert Logger.lowLevelDebug("not fulfilled yet, waiting for data of length " + chnl.free());
                return;
            }
            assert Logger.lowLevelDebug("data reading is done now");
            byte[] data = chnl.get();
            chnl = null;
            // handle the data
            byte[] bytesToSend;
            try {
                bytesToSend = processor.feed(topCtx, frontendSubCtx, data);
            } catch (Exception e) {
                Logger.warn(LogType.INVALID_EXTERNAL_DATA, "user code cannot handle data from " + frontendConnection + ". err=" + e);
                frontendConnection.close();
                return;
            }

            int connId = processor.connection(topCtx, frontendSubCtx);
            assert Logger.lowLevelDebug("the processor return data of length " + (bytesToSend == null ? "null" : bytesToSend.length) + ", sending to connId=" + connId);
            BackendConnectionHandler backend = getConnection(connId);
            if (backend == null) {
                // for now, we simply close the whole connection when a backend is missing
                Logger.error(LogType.CONN_ERROR, "failed to retrieve the backend connection for " + frontendConnection + "/" + connId);
                frontendConnection.close();
            } else {
                if (bytesToSend == null || bytesToSend.length == 0) {
                    readFrontend(); // recursively call to handle more data
                } else {
                    backend.writeToBackend(bytesToSend);
                }
            }
        }
    }

    @Override
    public void readable(ConnectionHandlerContext ctx) {
        readFrontend();
    }

    private BackendConnectionHandler getConnection(int connId) {
        if (connId > 0 && conns[connId] != null)
            return conns[connId]; // get connection if it already exists

        assert connId == -1;

        // get connector
        Connector connector = config.connGen.genConnector(frontendConnection);
        if (connector == null) {
            Logger.info(LogType.NO_CLIENT_CONN, "the user code refuse to provide a remote endpoint");
            return null;
        }
        if (connector.loop() != null) {
            Logger.error(LogType.IMPROPER_USE, "it's not supported to specify event loop when running processors");
            return null;
        }

        // find a connection if possible
        for (int existingConnId : conn2intMap.values()) {
            if (conns[existingConnId].conn.remote.equals(connector.remote)) {
                BackendConnectionHandler bh = conns[existingConnId];
                processor.chosen(topCtx, frontendSubCtx, bh.subCtx);
                return bh;
            }
        }

        // get a new connection
        ClientConnection clientConnection;
        try {
            clientConnection = connector.connect(
                new ConnectionOpts().setTimeout(config.timeout),
                RingBuffer.allocateDirect(config.inBufferSize), RingBuffer.allocateDirect(config.outBufferSize));
        } catch (IOException e) {
            Logger.fatal(LogType.CONN_ERROR, "make passive connection failed, maybe provided endpoint info is invalid", e);
            return null;
        }

        // record in collections
        int newConnId = ++cursor;
        BackendConnectionHandler bh =
            new BackendConnectionHandler(processor.initSub(topCtx, newConnId), clientConnection);
        recordBackend(bh, newConnId);
        // register
        try {
            loop.addClientConnection(clientConnection, null, bh);
        } catch (IOException e) {
            Logger.fatal(LogType.EVENT_LOOP_ADD_FAIL, "add client connection " + clientConnection + " to loop failed");

            // remove from collection because it fails
            removeBackend(bh);
            clientConnection.close();

            return null;
        }

        byte[] bytes = processor.connected(topCtx, bh.subCtx);
        processor.chosen(topCtx, frontendSubCtx, bh.subCtx);

        if (bytes != null && bytes.length > 0) {
            bh.writeToBackend(bytes);
        }

        return bh;
    }

    @Override
    public void writable(ConnectionHandlerContext ctx) {
        doFrontendWrite();
    }

    @Override
    public void exception(ConnectionHandlerContext ctx, IOException err) {
        Logger.error(LogType.CONN_ERROR, "connection got exception", err);
        closeAll();
    }

    @Override
    public void closed(ConnectionHandlerContext ctx) {
        assert Logger.lowLevelDebug("frontend connection is closed: " + frontendConnection);
        closeAll();
    }

    @Override
    public void removed(ConnectionHandlerContext ctx) {
        if (!frontendConnection.isClosed())
            Logger.error(LogType.IMPROPER_USE, "frontend connection " + frontendConnection + " removed from event loop " + loop);
        closeAll();
    }

    private boolean closed = false;

    void closeAll() {
        if (closed) {
            return; // ignore if already closed
        }
        closed = true;

        assert Logger.lowLevelDebug("close all connections of " + frontendConnection);
        List<Integer> ints = new ArrayList<>(conn2intMap.values());
        for (int i : ints) {
            BackendConnectionHandler be = conns[i];
            removeBackend(be);
            be.conn.close();
            be.conn.getInBuffer().clean();
            be.conn.getOutBuffer().clean();
        }
        frontendConnection.close();
        frontendConnection.getInBuffer().clean();
        frontendConnection.getOutBuffer().clean();
    }
}
