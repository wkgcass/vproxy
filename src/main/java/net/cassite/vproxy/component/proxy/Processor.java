package net.cassite.vproxy.component.proxy;

import net.cassite.vproxy.util.ByteArray;

public interface Processor<CTX extends Processor.Context, SUB extends Processor.SubContext> {
    class Context {
    }

    class SubContext {
    }

    /**
     * create a context object
     *
     * @return the context
     */
    CTX init();

    /**
     * create a sub context object
     *
     * @param ctx context
     * @param id  connection id attached to the sub context, 0 for the frontend connection
     * @return the sub context
     */
    SUB initSub(CTX ctx, int id);

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
     * get current proxy mode for connection
     *
     * @param ctx context
     * @param sub sub context
     * @return the current proxy mode
     */
    Mode mode(CTX ctx, SUB sub);

    /**
     * get current wanted length
     *
     * @param ctx context
     * @param sub sub context
     * @return the current wanted length
     */
    int len(CTX ctx, SUB sub);

    /**
     * feed data to the processor and get data to send
     *
     * @param ctx  context
     * @param sub  sub context
     * @param data feed data
     * @return data to send, or null if nothing to send
     * @throws Exception raise exception if handling failed
     */
    ByteArray feed(CTX ctx, SUB sub, ByteArray data) throws Exception;

    /**
     * produce some data to the connection represented by the sub context<br>
     * this method will be checked after `feed` is called,
     * and will not be called for frontend connections
     *
     * @param ctx context
     * @param sub sub context
     * @return data to send, or null if got nothing to send
     */
    ByteArray produce(CTX ctx, SUB sub);

    /**
     * the mode used to be `proxy` and now proxy handling is done
     *
     * @param ctx context
     * @param sub sub context
     */
    void proxyDone(CTX ctx, SUB sub);

    /**
     * retrieve the connection id to proxy data to
     *
     * @param ctx context
     * @return connection id, -1 for creating new connections or connection reuse, 0 is invalid for now
     */
    int connection(CTX ctx, SUB front);

    /**
     * after the `connection` method return -1, the lib will choose a connection and
     * let the user code know through this method
     *
     * @param ctx context
     * @param sub the chosen sub context
     */
    void chosen(CTX ctx, SUB front, SUB sub);

    /**
     * new connection connected
     *
     * @param ctx context
     * @param sub sub context
     * @return data to send when connected, or null if nothing to send.
     * Note: should return null for sub context with connId = 0
     */
    ByteArray connected(CTX ctx, SUB sub);

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
    int PROXY_ZERO_COPY_THRESHOLD();
}
