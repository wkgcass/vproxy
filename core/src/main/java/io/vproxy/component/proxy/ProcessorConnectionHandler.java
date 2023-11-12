package io.vproxy.component.proxy;

import io.vproxy.base.connection.*;
import io.vproxy.base.processor.ConnectionDelegate;
import io.vproxy.base.processor.Hint;
import io.vproxy.base.processor.Processor;
import io.vproxy.base.util.*;
import io.vproxy.base.util.anno.ThreadSafe;
import io.vproxy.base.util.nio.ByteArrayChannel;
import io.vproxy.base.util.ringbuffer.ProxyOutputRingBuffer;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
class ProcessorConnectionHandler implements ConnectionHandler {
    private final ProxyNetConfig config;
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

    // the reading will be paused if this field is set to true
    // this field only works for the frontend connection
    private volatile boolean paused = false;

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
     */
    void utilWriteData(BackendConnectionHandler.ByteFlow flow,
                       Connection sourceConnection,
                       Connection targetConnection,
                       Runnable onZeroCopyProxyDone) {
        assert Logger.lowLevelDebug("calling utilWriteData, writing from " + sourceConnection + " to " + targetConnection);

        if (flow.currentSegment == null) {
            flow.pollSendingQueue();
        }
        if (flow.currentSegment == null) {
            return; // still null, which means no data to write now
        }

        if (flow.currentSegment.isProxy) {
            if (flow.currentSegment.calledProxyOnBuffer) {
                ((ProxyOutputRingBuffer) (targetConnection.getOutBuffer())).newDataFromProxiedBuffer();
                return;
            }

            assert Logger.lowLevelDebug("running proxy, flow.bytesToProxy = " + flow.currentSegment.bytesToProxy);

            if (flow.currentSegment.bytesToProxy > processor.PROXY_ZERO_COPY_THRESHOLD()
                && targetConnection.getOutBuffer() instanceof ProxyOutputRingBuffer
            ) {
                assert Logger.lowLevelDebug("choose to run with zero copy");
                flow.currentSegment.calledProxyOnBuffer = true;

                ((ProxyOutputRingBuffer) (targetConnection.getOutBuffer()))
                    .proxy(
                        sourceConnection.getInBuffer(),
                        flow.currentSegment.bytesToProxy, () -> {
                            backendProxyDone(flow);
                            onZeroCopyProxyDone.run();
                        });
            } else {
                assert Logger.lowLevelDebug("choose to run without zero copy");

                targetConnection.runNoQuickWrite(() -> {
                    int n = sourceConnection.getInBuffer()
                        .writeTo(targetConnection.getOutBuffer(), flow.currentSegment.bytesToProxy);
                    flow.currentSegment.bytesToProxy -= n;
                    assert Logger.lowLevelDebug("proxied " + n + " bytes, still have " + flow.currentSegment.bytesToProxy + " left");
                });
                assert flow.currentSegment.bytesToProxy >= 0;
                // proxy is done
                if (flow.currentSegment.bytesToProxy == 0) {
                    backendProxyDone(flow);
                }
            }
        } else {
            assert flow.currentSegment.chnl != null;
            assert Logger.lowLevelDebug("sending bytes, flow.chnl.used = " + flow.currentSegment.chnl.used());
            targetConnection.runNoQuickWrite(() ->
                targetConnection.getOutBuffer().storeBytesFrom(flow.currentSegment.chnl));
            // check whether this batch sending is done
            assert Logger.lowLevelDebug("now flow.chnl.used == " + flow.currentSegment.chnl.used());
            if (flow.currentSegment.chnl.used() == 0) {
                flow.frameEnds = flow.currentSegment.frameEndsAfterSending;
                flow.pollSendingQueue(); // poll for the next segment
            }
        }
    }

    private void backendProxyDone(BackendConnectionHandler.ByteFlow flow) {
        assert Logger.lowLevelDebug("proxy done");
        assert flow.currentSegment.proxyTODO != null;
        Processor.ProxyDoneTODO proxyDoneTODO = flow.currentSegment.proxyTODO.proxyDone.get();
        if (proxyDoneTODO != null) {
            flow.frameEnds = proxyDoneTODO.frameEnds;
        }
        flow.pollSendingQueue(); // poll for the next segment
    }

    // -----------------------------
    // --- START backend handler ---
    class BackendConnectionHandler implements ConnectableConnectionHandler {
        class ByteFlow {
            class Segment {
                // this field records the current running mode
                // true = proxy, false = bytes
                final boolean isProxy;
                // this field records whether the buffer mode is proxy
                // true = already called, false = not called yet
                boolean calledProxyOnBuffer = false;
                final ByteArrayChannel chnl;
                final boolean frameEndsAfterSending;
                int bytesToProxy;
                final Processor.ProxyTODO proxyTODO;

                Segment(ByteArray byteArray, boolean frameEndsAfterSending) {
                    this.isProxy = false;
                    this.chnl = byteArray.toFullChannel();
                    this.frameEndsAfterSending = frameEndsAfterSending;
                    this.proxyTODO = null;
                }

                Segment(int bytesToProxy, Processor.ProxyTODO proxyTODO) {
                    this.isProxy = true;
                    this.chnl = null;
                    this.frameEndsAfterSending = false;
                    this.bytesToProxy = bytesToProxy;
                    this.proxyTODO = proxyTODO;
                }
            }

            Segment currentSegment = null;
            private final LinkedList<Segment> sendingQueue = new LinkedList<>();
            boolean frameEnds; // in the frontendByteFlow, if it's set to true, another backend will be able to respond to the frontend
            //                    this field is ignored when it's in backendByteFlow
            public boolean closed; // only work in backendByteFlow, if it's set to true, the connection will be closed after all data flushed

            void pollSendingQueue() {
                Segment seg = sendingQueue.poll();
                if (seg == null) {
                    currentSegment = null;
                    if (closed) {
                        closed(null);
                    }
                    return;
                }
                if (seg.isProxy && seg.proxyTODO == null) { // a special segment indicating the frame ends
                    if (sendingQueue.isEmpty()) {
                        frameEnds = true;
                        currentSegment = null;
                        return;
                    }
                    // poll recursively to get the next element
                    pollSendingQueue();
                    return;
                }
                currentSegment = seg;
                frameEnds = false;
            }

            void write(ByteArray data, boolean frameEndsAfterSending) {
                Segment seg = new Segment(data, frameEndsAfterSending);
                if (currentSegment == null) {
                    currentSegment = seg;
                } else {
                    sendingQueue.add(seg);
                }
                frameEnds = false;
            }

            void proxy(int len, Processor.ProxyTODO proxyTODO) {
                {
                    // this method might be called for the same bunch of data to be proxied
                    // so it should not attach any proxy segment here
                    if (currentSegment != null && currentSegment.isProxy) {
                        return;
                    }
                    for (Segment seg : sendingQueue) {
                        if (seg.isProxy)
                            return;
                    }
                }
                Segment seg = new Segment(len, proxyTODO);
                if (currentSegment == null) {
                    currentSegment = seg;
                } else {
                    sendingQueue.add(seg);
                }
                frameEnds = false;
            }

            void informFrameEnds() {
                if (currentSegment == null) {
                    frameEnds = true;
                } else {
                    sendingQueue.add(new Segment(0, null));
                    frameEnds = false;
                }
            }

            void informClose() {
                closed = true;
                if (currentSegment == null) {
                    closed(null);
                }
            }
        }

        private final Processor.SubContext subCtx;
        private final ConnectableConnection conn;
        private boolean isConnected = false;

        private ByteArrayChannel chnl = null;
        private final BackendConnectionHandler.ByteFlow backendByteFlow = new BackendConnectionHandler.ByteFlow();
        private final BackendConnectionHandler.ByteFlow frontendByteFlow = new BackendConnectionHandler.ByteFlow();

        // reading will be paused if this field is set to true
        private volatile boolean paused = false;
        // set to true when processor.disconnected(...) is called, this prevents being called multiple times in exception()/closed()/removed() event handlers
        private boolean disconnectedCalled = false;

        BackendConnectionHandler(Processor.SubContext subCtx, ConnectableConnection conn) {
            this.subCtx = subCtx;
            this.conn = conn;
        }

        void writeToBackend(ByteArray data) {
            backendByteFlow.write(data, false /*no frame slicing check when writing to backend, so set to false is fine*/);
            doBackendWrite();
        }

        void proxyToBackend(int len, Processor.ProxyTODO proxyTODO) {
            backendByteFlow.proxy(len, proxyTODO);
            doBackendWrite();
        }

        void informCloseToBackend() {
            assert Logger.lowLevelDebug("informCloseToBackend");
            backendByteFlow.informClose();
            if (backendByteFlow.currentSegment != null) doBackendWrite();
        }

        void writeToFrontend(ByteArray data, boolean frameEndsAfterSending) {
            frontendByteFlow.write(data, frameEndsAfterSending);
            frontendWrite(this);
        }

        void proxyToFrontend(int len, Processor.ProxyTODO proxyTODO) {
            frontendByteFlow.proxy(len, proxyTODO);
            frontendWrite(this);
        }

        void informFrameEndsToFrontend() {
            frontendByteFlow.informFrameEnds();
            if (frontendByteFlow.currentSegment != null) frontendWrite(this);
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
            while (backendByteFlow.currentSegment != null) {
                if (!backendByteFlow.currentSegment.calledProxyOnBuffer && conn.getOutBuffer().free() == 0) {
                    // if the output is full, should break the writing process and wait for the next signal
                    assert Logger.lowLevelDebug("the backend output buffer is full now, break the sending loop");
                    break;
                }
                // if it's running proxy and the frontend connection input is empty, break the loop
                if (backendByteFlow.currentSegment.isProxy && frontendConnection.getInBuffer().used() == 0) {
                    break;
                }

                //noinspection Convert2MethodRef
                utilWriteData(backendByteFlow, frontendConnection, conn, () -> readFrontend());

                // if it's running proxy and already called proxy on buffer, end the method
                // NOTE: this must be check AFTER the utilWriteData because the buffer should be alerted of the proxied data input
                if (backendByteFlow.currentSegment != null
                    && backendByteFlow.currentSegment.isProxy
                    && backendByteFlow.currentSegment.calledProxyOnBuffer) {
                    break;
                }
            }
            isWritingBackend = false; // writing done
            // if writing is done
            if (backendByteFlow.currentSegment == null) {
                // let the frontend read more data because there may still have some data in buffer
                readFrontend();
            }
        }

        @Override
        public void connected(ConnectableConnectionHandlerContext ctx) {
            isConnected = true;
            // no need to call processor.connected(...) here, it's already called when retrieving the connection
            doBackendWrite();
        }

        void readBackend() {
            if (paused) {
                assert Logger.lowLevelDebug("backend " + conn + " is paused, so read nothing");
                return;
            }

            Processor.ProcessorTODO processorTODO = processor.process(topCtx, subCtx);
            if (conn.getInBuffer().used() == 0
                // if returned len == 0, the process.feed should be called without any data
                && processorTODO.len != 0) {
                return; // ignore the event if got nothing to read
            }

            assert Logger.lowLevelDebug("calling readBackend() of " + conn);

            // check whether to proxy the data or to receive the data
            Processor.Mode mode = processorTODO.mode;
            assert Logger.lowLevelDebug("the current mode is " + mode);

            if (mode == Processor.Mode.proxy) {
                int len = processorTODO.len;
                assert Logger.lowLevelDebug("the proxy length is " + len);
                if (len == 0) { // 0 bytes to proxy, so it's already done
                    Processor.ProxyDoneTODO proxyDone = processorTODO.proxyTODO.proxyDone.get();
                    if (proxyDone != null && proxyDone.frameEnds) {
                        informFrameEndsToFrontend();
                    }
                    readBackend(); // recursively call to handle more input data
                } else {
                    proxyToFrontend(len, processorTODO.proxyTODO);
                }
            } else {
                assert mode == Processor.Mode.handle;

                ByteArray data = null; // data to feed into processor
                if (chnl == null) {
                    int len = processorTODO.len;
                    assert Logger.lowLevelDebug("the expected message length is " + len);
                    if (len == 0) { // if nothing to read, then directly feed empty data to the processor
                        data = ByteArray.allocate(0);
                    } else if (len < 0) {
                        chnl = ByteArrayChannel.fromEmpty(conn.getInBuffer().used()); // consume all data
                    } else {
                        chnl = ByteArrayChannel.fromEmpty(len);
                    }
                }
                if (data == null) {
                    conn.getInBuffer().writeTo(chnl);
                    if (chnl.free() != 0) {
                        assert Logger.lowLevelDebug("not fulfilled yet, expecting " + chnl.free() + " length of data");
                        // expecting more data
                        return;
                    }
                    assert Logger.lowLevelDebug("the message is totally read, feeding to processor");
                    data = chnl.getArray();
                    chnl = null;
                }
                Processor.HandleTODO handleTODO;
                try {
                    handleTODO = processorTODO.feed.apply(data);
                } catch (Exception e) {
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA, "user code cannot handle data from " + conn + ", which corresponds to " + frontendConnection + ".", e);
                    frontendConnection.close();
                    return;
                }
                ByteArray dataToSend = handleTODO == null ? null : handleTODO.send;
                assert Logger.lowLevelDebug("the processor return a message of length " + (dataToSend == null ? "null" : dataToSend.length()));

                // check data to write back
                {
                    ByteArray writeBackBytes = handleTODO == null ? null : handleTODO.produce;
                    if (writeBackBytes != null && writeBackBytes.length() != 0) {
                        assert Logger.lowLevelDebug("got bytes to write back, len = " + writeBackBytes.length() + ", conn = " + conn);
                        writeToBackend(writeBackBytes);
                    }
                }

                if (dataToSend == null || dataToSend.length() == 0) {
                    // nothing written, we have to check whether the frame ends
                    if (handleTODO != null && handleTODO.frameEnds) {
                        informFrameEndsToFrontend();
                    }

                    readBackend(); // recursively call to handle more data
                } else {
                    writeToFrontend(dataToSend, handleTODO.frameEnds);
                }
            }
        }

        @ThreadSafe
        public void pause() {
            if (paused) {
                assert Logger.lowLevelDebug("backend conn " + conn + " already paused");
                return;
            }
            assert Logger.lowLevelDebug("pausing backend conn " + conn);
            paused = true;
        }

        @ThreadSafe
        public void resume() {
            boolean old = paused;
            paused = false;
            loop.getSelectorEventLoop().nextTick(() -> {
                if (old) {
                    assert Logger.lowLevelDebug("resuming backend conn " + conn);
                    readFrontend();
                } else {
                    assert Logger.lowLevelDebug("backend conn " + conn + " is not paused");
                }
            });
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
            if (disconnectedCalled) {
                assert Logger.lowLevelDebug("disconnected already called");
                return;
            }
            disconnectedCalled = true;
            Processor.DisconnectTODO disconnectTODO = processor.disconnected(topCtx, subCtx, true);
            if (disconnectTODO != null && disconnectTODO.silent) {
                assert Logger.lowLevelDebug("silently close the backend");
                ctx.connection.close();
                return;
            }

            String errMsg = "got exception when handling backend connection " + conn + ", closing frontend " + frontendConnection;
            if (Utils.isTerminatedIOException(err)) {
                assert Logger.lowLevelDebug(errMsg + ", " + err);
            } else {
                Logger.error(LogType.CONN_ERROR, errMsg, err);
            }
            frontendConnection.close(true);
            closeAll();
        }

        @Override
        public void remoteClosed(ConnectionHandlerContext ctx) {
            assert Logger.lowLevelDebug("backend connection " + ctx.connection + " remoteClosed");
            // backend FIN
            Processor.HandleTODO handleTODO = processor.remoteClosed(topCtx, subCtx);
            if (handleTODO != null) {
                if (handleTODO.send != null) {
                    writeToFrontend(handleTODO.send, true /* no more packets will be received from the connection, so always set this to true */);
                }
                if (handleTODO.produce != null) {
                    writeToBackend(handleTODO.produce);
                }
            }
            informCloseToBackend();
        }

        @Override
        public void closed(ConnectionHandlerContext ctx) {
            if (disconnectedCalled) {
                assert Logger.lowLevelDebug("disconnected already called");
                return;
            }
            disconnectedCalled = true;
            Processor.DisconnectTODO disconnectTODO = processor.disconnected(topCtx, subCtx, false);
            if (disconnectTODO != null && disconnectTODO.silent) {
                assert Logger.lowLevelDebug("silently close the backend");
                conn.close();
                return;
            }

            if (frontendConnection.isClosed()) {
                assert Logger.lowLevelDebug("backend connection " + conn + " closed, corresponding frontend is " + frontendConnection);
            } else {
                Logger.warn(LogType.CONN_ERROR, "backend connection " + conn + " closed before frontend connection " + frontendConnection);
            }
            closeAll();
        }

        @Override
        public void removed(ConnectionHandlerContext ctx) {
            if (!ctx.connection.isClosed()) {
                Logger.error(LogType.IMPROPER_USE, "backend connection " + ctx.connection + " removed from event loop " + loop);
                closeAll();
            }
        }
    }
    // --- END backend handler ---
    // ---------------------------

    private boolean frontendIsHandlingConnection = false; // true: consider handlingConnection, false: consider frontendByteFlow
    private BackendConnectionHandler handlingConnection;
    private final FrontendByteFlow frontendByteFlow = new FrontendByteFlow();

    class FrontendByteFlow {
        class Segment {
            public final ByteArrayChannel chnl;

            Segment(ByteArray byteArray) {
                this.chnl = byteArray.toFullChannel();
            }
        }

        Segment currentSegment;
        final LinkedList<Segment> sendingQueue = new LinkedList<>();

        void write(ByteArray byteArray) {
            Segment seg = new Segment(byteArray);
            if (currentSegment == null) {
                currentSegment = seg;
            } else {
                sendingQueue.add(seg);
            }
            frontendWrite(null);
        }
    }

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
        if (frontendIsHandlingConnection && handlingConnection == null) {
            frontendIsHandlingConnection = false;
        }
        if (!frontendIsHandlingConnection && frontendByteFlow.currentSegment == null && frontendByteFlow.sendingQueue.isEmpty()) {
            frontendIsHandlingConnection = true;
        }
        if (frontendIsHandlingConnection && handlingConnection == null) {
            return; // nothing to write
        }

        if (frontendIsHandlingConnection) {

            BackendConnectionHandler.ByteFlow flow = handlingConnection.frontendByteFlow;
            while (flow.currentSegment != null) {
                if (!flow.currentSegment.calledProxyOnBuffer && frontendConnection.getOutBuffer().free() == 0) {
                    return; // end the method, because the out buffer has no space left
                }
                // if it's running proxy and the backend connection input buffer is empty
                if (flow.currentSegment.isProxy && handlingConnection.conn.getInBuffer().used() == 0) {
                    return; // cannot handle for now, end the method
                }

                utilWriteData(flow, handlingConnection.conn, frontendConnection, () -> handlingConnection.readBackend());

                // if writing done:
                if (flow.currentSegment == null) {
                    // then let the backend connection read more data
                    // because the connection may be holding some data in the buffer
                    handlingConnection.readBackend();
                } else {
                    // if it's running proxy and already called proxy on buffer, end the method
                    // NOTE: this must be check AFTER the utilWriteData because the buffer should be alerted of the proxied data input
                    if (flow.currentSegment.isProxy && flow.currentSegment.calledProxyOnBuffer) {
                        return;
                    }
                }
            }
            // now nothing to be handled for this connection
            if (handlingConnection.frontendByteFlow.frameEnds) {
                handlingConnection = null; // is done, set to null and go on
            } else {
                return; // no data for now, exit the method
            }
        }

        // check whether to handle the frontendByteFlow
        if (frontendByteFlow.currentSegment != null || !frontendByteFlow.sendingQueue.isEmpty()) {
            frontendIsHandlingConnection = false;
        }

        // check and handle the frontendByteFlow
        if (!frontendIsHandlingConnection) {
            FrontendByteFlow flow = frontendByteFlow;
            while (true) {
                if (flow.currentSegment != null) {
                    if (flow.currentSegment.chnl.used() == 0) {
                        flow.currentSegment = null;
                    }
                }
                if (flow.currentSegment == null) {
                    if (!flow.sendingQueue.isEmpty()) {
                        flow.currentSegment = flow.sendingQueue.poll();
                    }
                }
                if (flow.currentSegment == null) {
                    break;
                }
                int n = frontendConnection.getOutBuffer().storeBytesFrom(flow.currentSegment.chnl);
                if (n == 0) {
                    break; // break when the frontend connection buffer is full
                    // wait until the buffer is not full (writable)
                }
            }
        }

        // check whether to handle the connection
        if (frontendByteFlow.currentSegment == null && frontendByteFlow.sendingQueue.isEmpty()) {
            frontendIsHandlingConnection = true;
        }

        // check for other connections
        // and keep writing if have some data to write in other connections
        if (frontendIsHandlingConnection) {
            BackendConnectionHandler next = null;
            for (BackendConnectionHandler b : conn2intMap.keySet()) {
                BackendConnectionHandler.ByteFlow flow = b.frontendByteFlow;
                if (flow.currentSegment != null) {
                    next = b;
                    break;
                }
            }
            handlingConnection = next;
            _doFrontendWrite();
        }
    }

    private ByteArrayChannel chnl = null;

    void readFrontend() {
        if (paused) {
            assert Logger.lowLevelDebug("frontend " + frontendConnection + " is paused, so read nothing");
            return;
        }

        Processor.ProcessorTODO processorTODO = processor.process(topCtx, frontendSubCtx);
        if (frontendConnection.getInBuffer().used() == 0
            // if returned len == 0, the process.feed should be called without any data
            && processorTODO.len != 0) {
            return; // do nothing if the in buffer is empty
        }

        assert Logger.lowLevelDebug("calling readFrontend()");

        // check whether to proxy the data or to receive the data
        assert Logger.lowLevelDebug("the current mode is " + processorTODO.mode);

        if (processorTODO.mode == Processor.Mode.proxy) {
            int bytesToProxy = processorTODO.len;
            int connId = processorTODO.proxyTODO.connTODO.connId;
            Hint connHint = processorTODO.proxyTODO.connTODO.hint;
            assert Logger.lowLevelDebug("the bytesToProxy is " + bytesToProxy + ", connId is " + connId + ", hint is " + connHint);
            BackendConnectionHandler backend = getConnection(connId, connHint, processorTODO.proxyTODO.connTODO.chosen);
            if (backend == null) {
                // for now, we simply close the whole connection when a backend is missing
                Logger.error(LogType.CONN_ERROR, "failed to retrieve the backend connection for " + frontendConnection + "/" + connId);
                frontendConnection.close(true);
            } else {
                if (bytesToProxy == 0) { // 0 bytes to proxy, so it's already done
                    processorTODO.proxyTODO.proxyDone.get(); // return value is ignored for frontend
                    readFrontend(); // recursively call to read more data
                } else {
                    backend.proxyToBackend(bytesToProxy, processorTODO.proxyTODO);
                }
            }
        } else {
            assert processorTODO.mode == Processor.Mode.handle;

            ByteArray data = null; // data to feed into processor
            if (chnl == null) {
                int len = processorTODO.len;
                assert Logger.lowLevelDebug("expecting message with the length of " + len);
                if (len == 0) { // if the length is 0, directly feed data to the processor
                    data = ByteArray.allocate(0);
                } else if (len < 0) {
                    chnl = ByteArrayChannel.fromEmpty(frontendConnection.getInBuffer().used()); // consume all data
                } else {
                    chnl = ByteArrayChannel.fromEmpty(len);
                }
            }
            if (data == null) {
                frontendConnection.getInBuffer().writeTo(chnl);
                if (chnl.free() != 0) {
                    // want to read more data
                    assert Logger.lowLevelDebug("not fulfilled yet, waiting for data of length " + chnl.free());
                    return;
                }
                assert Logger.lowLevelDebug("data reading is done now");
                data = chnl.getArray();
                chnl = null;
            }
            // handle the data
            Processor.HandleTODO handleTODO;
            try {
                handleTODO = processorTODO.feed.apply(data);
            } catch (Exception e) {
                Logger.warn(LogType.INVALID_EXTERNAL_DATA, "user code cannot handle data from " + frontendConnection + ". err=", e);
                frontendConnection.close(true);
                return;
            }
            ByteArray bytesToSend = handleTODO == null ? null : handleTODO.send;
            {
                ByteArray produced = handleTODO == null ? null : handleTODO.produce;
                if (produced != null && produced.length() != 0) {
                    frontendByteFlow.write(produced);
                }
            }

            if (bytesToSend == null || (bytesToSend.length() == 0 && bytesToSend != Processor.REQUIRE_CONNECTION)) {
                readFrontend();
                return;
            }

            int connId = handleTODO.connTODO.connId;
            Hint hint = handleTODO.connTODO.hint;
            assert Logger.lowLevelDebug("the processor return data of length " + bytesToSend.length() + ", sending to connId=" + connId + ", hint=" + hint);
            if (connId == 0) {
                Logger.error(LogType.IMPROPER_USE, "When you return connection()==0, you must guarantee that the former feed() calling result was null or an array with length 0");
                // ignore and fall through
            }
            BackendConnectionHandler backend = getConnection(connId, hint, handleTODO.connTODO.chosen);
            if (backend == null) {
                // for now, we simply close the whole connection when a backend is missing
                Logger.error(LogType.CONN_ERROR, "failed to retrieve the backend connection for " + frontendConnection + "/" + connId);
                frontendConnection.close(true);
            } else {
                if (bytesToSend.length() == 0) {
                    readFrontend(); // recursively call to handle more data
                } else {
                    backend.writeToBackend(bytesToSend);
                }
            }
        }
    }

    @ThreadSafe
    public void pause() {
        if (paused) {
            assert Logger.lowLevelDebug("frontend conn " + frontendConnection + " already paused");
            return;
        }
        assert Logger.lowLevelDebug("pausing frontend conn " + frontendConnection);
        paused = true;
    }

    @ThreadSafe
    public void resume() {
        boolean old = paused;
        paused = false;
        loop.getSelectorEventLoop().nextTick(() -> {
            if (old) {
                assert Logger.lowLevelDebug("resuming frontend conn " + frontendConnection);
                readFrontend();
            } else {
                assert Logger.lowLevelDebug("frontend conn " + frontendConnection + " is not paused");
            }
        });
    }

    @Override
    public void readable(ConnectionHandlerContext ctx) {
        readFrontend();
    }

    private BackendConnectionHandler getConnection(int connId, Hint hint, Consumer<Processor.SubContext> chosen) {
        if (connId > 0 && conns[connId] != null)
            return conns[connId]; // get connection if it already exists

        assert connId == -1;

        // get connector
        Connector connector = config.connGen.genConnector(frontendConnection, hint);
        if (connector == null) {
            Logger.error(LogType.NO_CLIENT_CONN, "the user code refuse to provide a remote endpoint");
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
                chosen.accept(bh.subCtx);
                return bh;
            }
        }

        // get a new connection
        ConnectableConnection connectableConnection;
        try {
            connectableConnection = connector.connect(
                new ConnectionOpts().setTimeout(config.timeout),
                RingBuffer.allocateDirect(config.inBufferSize), ProxyOutputRingBuffer.allocateDirect(config.outBufferSize));
        } catch (IOException e) {
            Logger.fatal(LogType.CONN_ERROR, "make passive connection failed, maybe provided endpoint info is invalid", e);
            return null;
        }

        // record in collections
        int newConnId = ++cursor;
        BackendConnectionHandler[] handlerPtr = new BackendConnectionHandler[]{null};
        //noinspection DuplicatedCode
        Processor.SubContext subCtx = processor.initSub(topCtx, newConnId, new ConnectionDelegate(connector.remote) {
            @Override
            public void pause() {
                assert handlerPtr[0] != null;
                handlerPtr[0].pause();
            }

            @Override
            public void resume() {
                assert handlerPtr[0] != null;
                handlerPtr[0].resume();
            }
        });
        BackendConnectionHandler bh = new BackendConnectionHandler(subCtx, connectableConnection);
        handlerPtr[0] = bh;
        recordBackend(bh, newConnId);
        // register
        try {
            loop.addConnectableConnection(connectableConnection, null, bh);
        } catch (IOException e) {
            Logger.fatal(LogType.EVENT_LOOP_ADD_FAIL, "add connectable connection " + connectableConnection + " to loop failed");

            // remove from collection because it fails
            removeBackend(bh);
            connectableConnection.close(true);

            return null;
        }

        Processor.HandleTODO handleTODO = processor.connected(topCtx, bh.subCtx);
        chosen.accept(bh.subCtx);

        if (handleTODO != null) {
            if (handleTODO.produce != null && handleTODO.produce.length() > 0) {
                bh.writeToBackend(handleTODO.produce);
            }
            if (handleTODO.send != null && handleTODO.send.length() > 0) {
                Logger.warn(LogType.IMPROPER_USE, "currently we do not support sending data to frontend when backend connection establishes");
            }
        }

        return bh;
    }

    @Override
    public void writable(ConnectionHandlerContext ctx) {
        doFrontendWrite();
    }

    @Override
    public void exception(ConnectionHandlerContext ctx, IOException err) {
        String errMsg = "connection got exception";
        if (Utils.isTerminatedIOException(err)) {
            assert Logger.lowLevelDebug(errMsg + ": " + err);
        } else {
            Logger.error(LogType.CONN_ERROR, errMsg, err);
        }
        closeAll();
    }

    @Override
    public void remoteClosed(ConnectionHandlerContext ctx) {
        assert Logger.lowLevelDebug("frontend connection " + ctx.connection + " remoteClosed");
        // frontend FIN
        // we should send FIN to all backends
        assert Logger.lowLevelDebug("send FIN to all backend");

        List<Integer> ints = new ArrayList<>(conn2intMap.values());
        boolean allBackendRemoteClosed = true;
        for (int i : ints) {
            BackendConnectionHandler be = conns[i];
            be.conn.closeWrite();
            if (be.conn.getOutBuffer().used() != 0 || !be.conn.isRemoteClosed()) {
                allBackendRemoteClosed = false;
            }
        }
        if (allBackendRemoteClosed) {
            assert Logger.lowLevelDebug("" +
                "all backend remote closed, " +
                "and no current backend, " +
                "so close the session");
            // close the session
            ctx.connection.close();
            closed(ctx);
        }
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
            return;
        }
        closed = true;

        assert Logger.lowLevelDebug("close all connections of " + frontendConnection);
        List<Integer> ints = new ArrayList<>(conn2intMap.values());
        for (int i : ints) {
            BackendConnectionHandler be = conns[i];
            removeBackend(be);
            be.conn.close();
        }
        frontendConnection.close();
    }
}
