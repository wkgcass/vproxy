package io.vproxy.base.processor;

import io.vproxy.base.Config;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.functional.FunctionEx;
import io.vproxy.vfd.IPPort;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface Processor<CTX extends Processor.Context, SUB extends Processor.SubContext> {
    class Context {
    }

    class SubContext {
        public final int connId;
        public int step = 0; // a helper field for the state machine, won't be used by the lib

        public SubContext(int connId) {
            this.connId = connId;
        }

        public boolean isFrontend() {
            return connId == 0;
        }

        public boolean isBackend() {
            return connId != 0;
        }

        public boolean isIdle() {
            return false;
        }
    }

    /**
     * @return name of the processor
     */
    String name();

    /**
     * @return null or a list of alpn strings
     */
    default String[] alpn() {
        return null;
    }

    record ContextInitParams(
        IPPort clientAddress
    ) {
    }

    /**
     * create a context object
     *
     * @param params init params
     * @return the context
     */
    CTX init(ContextInitParams params);

    /**
     * sub context init params
     *
     * @param ctx      context
     * @param id       connection id attached to the sub context, 0 for the frontend connection
     * @param delegate {@link ConnectionDelegate}
     * @param <CTX>    type of the context
     */
    record SubContextInitParams<CTX extends Processor.Context>(
        CTX ctx,
        int id,
        ConnectionDelegate delegate
    ) {
    }

    /**
     * create a sub context object
     *
     * @param params init params
     * @return the sub context
     */
    SUB initSub(SubContextInitParams<CTX> params);

    class ProcessorTODO {
        /**
         * current wanted length, -1 for want independent length of bytes, 0 for directly trigger
         */
        public int len;
        public Mode mode;
        /**
         * the function used to consume data and process the input bytes
         * must be non-null in 'handle' mode
         */
        public FunctionEx<ByteArray, HandleTODO, Exception> feed;
        /**
         * the proxy instructions
         * must be non-null in 'proxy' mode
         */
        public ProxyTODO proxyTODO;

        private ProcessorTODO() {
        }

        public static ProcessorTODO create() {
            return new ProcessorTODO();
        }

        public static ProcessorTODO createProxy() {
            ProcessorTODO ret = create();
            ret.mode = Mode.proxy;
            ret.proxyTODO = ProxyTODO.create();
            return ret;
        }
    }

    /**
     * handling mode: to process the bytes or to proxy the bytes
     */
    enum Mode {
        /**
         * take data and produce data to send to backend
         */
        handle,
        /**
         * directly send data to backend
         */
        proxy,
    }

    /**
     * the connection representation
     */
    class ConnectionTODO {
        /**
         * connection id for the lib to send data to, or -1 to let the lib choose or create a connection
         */
        public int connId;
        /**
         * the hint for retrieving new connection
         */
        public Hint hint;
        /**
         * the callback function when a connection is chosen
         */
        public Consumer<SubContext> chosen;

        private ConnectionTODO() {
        }

        public static ConnectionTODO create() {
            return new ConnectionTODO();
        }
    }

    /**
     * the 'handle' result
     */
    class HandleTODO {
        /**
         * can be null if send is null or of length 0 (except the ref {@link #REQUIRE_CONNECTION})
         */
        public ConnectionTODO connTODO;
        /**
         * data to send. may be length zero or null
         */
        public ByteArray send;
        /**
         * set by the processor library: true means all data sent
         */
        public boolean isSent;
        /**
         * all data sent (i.e. written into connection ring buffer)
         */
        public Consumer<HandleTODO> sendDone;
        /**
         * data produced to the connection which triggers the event.
         * may be length zero or null
         */
        public ByteArray produce;
        /**
         * set by the processor library: true means all data produced
         */
        public boolean isProduced;
        /**
         * all data produced (i.e. written into connection ring buffer)
         */
        public Consumer<HandleTODO> produceDone;
        /**
         * current frame ends
         */
        public boolean frameEnds;

        private HandleTODO() {
        }

        public static HandleTODO create() {
            return new HandleTODO();
        }

        public static void doneNoData(HandleTODO handleTODO) {
            if (handleTODO == null)
                return;
            if (handleTODO.send == null || handleTODO.send.length() == 0) {
                handleTODO.isSent = true;
                handleTODO.sendDone();
            }
            if (handleTODO.produce == null || handleTODO.produce.length() == 0) {
                handleTODO.isProduced = true;
                handleTODO.produceDone();
            }
        }

        public void sendDone() {
            if (sendDone == null)
                return;
            sendDone.accept(this);
        }

        public void produceDone() {
            if (produceDone == null)
                return;
            produceDone.accept(this);
        }
    }

    /**
     * the proxy handing
     */
    class ProxyTODO {
        public ConnectionTODO connTODO;
        /**
         * the callback function to run when proxy is done
         */
        public Supplier<ProxyDoneTODO> proxyDone;

        private ProxyTODO() {
        }

        public static ProxyTODO create() {
            return new ProxyTODO();
        }
    }

    class ProxyDoneTODO {
        /**
         * current frame ends
         */
        public boolean frameEnds;

        private ProxyDoneTODO() {
        }

        public static ProxyDoneTODO create() {
            return new ProxyDoneTODO();
        }

        public static ProxyDoneTODO createFrameEnds() {
            ProxyDoneTODO ret = create();
            ret.frameEnds = true;
            return ret;
        }
    }

    /**
     * the disconnecting handling
     */
    class DisconnectTODO {
        /**
         * true if the disconnecting event is properly handled, other connections won't be affected,
         * or false to close all connections
         */
        public boolean silent;

        private DisconnectTODO() {
        }

        public static DisconnectTODO create() {
            return new DisconnectTODO();
        }

        public static DisconnectTODO createSilent() {
            DisconnectTODO ret = create();
            ret.silent = true;
            return ret;
        }
    }

    /**
     * if {@link HandleTODO#send} value equals this byte array,
     * the lib will still try to find a connection even though it's empty.<br>
     * also, {@link ConnectionTODO#connId} should be -1 to let it happen.
     */
    ByteArray REQUIRE_CONNECTION = ByteArray.allocate(0).copy();

    /**
     * The entrance of the processor lib
     *
     * @return a non-null {@link ProcessorTODO} object which informs the lib how the protocol should be processed
     */
    ProcessorTODO process(CTX ctx, SUB sub);

    /**
     * new connection connected
     *
     * @param ctx context
     * @param sub sub context
     * @return null, or data produced, the {@link HandleTODO#send} from the return value won't be used for now
     */
    HandleTODO connected(CTX ctx, SUB sub);

    /**
     * the remote side closed the connection.
     *
     * @param ctx context
     * @param sub backend sub context
     * @return null, or data produced, the {@link HandleTODO#send} from the return value won't be used for now
     */
    HandleTODO remoteClosed(CTX ctx, SUB sub);

    /**
     * connection disconnected. This will only be invoked when backend connections terminate. If the frontend connection
     * is closed, the lib will close all related backend connections.
     *
     * @param ctx       context
     * @param sub       backend sub context
     * @param exception the connection disconnects with an exception
     * @return null or a {@link DisconnectTODO} object which informs the lib how to handle the connections
     */
    DisconnectTODO disconnected(CTX ctx, SUB sub, boolean exception);

    /**
     * zero copy is not free.
     * e.g. when processing http2 frames, the frame header is 9 bytes, and with uint24 payload length,
     * the processor would read 9 bytes first, and decides to proxy whats left in the frame,
     * if the payload is 7 bytes, the lib will send two tcp segments, one with 9 bytes payload and
     * one with 7 bytes payload. It's much faster if just send one tcp segment with 16 bytes payload
     * when zero copy is disabled.
     *
     * @return the threshold for enabling zero copy
     */
    default int PROXY_ZERO_COPY_THRESHOLD() {
        return Config.recommendedMinPayloadLength;
    }
}
