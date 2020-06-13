package vproxybase.processor;

import vproxybase.util.ByteArray;
import vproxybase.util.LogType;
import vproxybase.util.Logger;

public abstract class AbstractProcessor<CTX extends Processor.Context, SUB extends Processor.SubContext> implements Processor<CTX, SUB> {
    private Mode __mode = Mode.handle;
    private boolean __expectNewFrame = false;
    private int __len = 0;
    private ByteArray __sending = null;
    private ByteArray __producing = null;
    private int __connId = -1;
    private Hint __hint = null;
    private SUB __chosen = null;

    private ByteArray __received = null;

    protected abstract void handle(CTX ctx, SUB sub) throws Exception;

    /**
     * want to get some data from lib
     *
     * @param len length of data wanted, -1 for any length, 0 for nothing wanted
     * @return null if the lib cannot provide enough data, or return the data exactly wanted
     */
    protected final ByteArray wantData(int len) {
        if (len < 0) {
            // want any length of data
            if (__received == null) {
                __len = -1;
                return null;
            }
            ByteArray ret = __received;
            __received = null;
            return ret;
        }
        if (len == 0) {
            return ByteArray.from(0);
        }
        int hasLen = __received == null ? 0 : __received.length();
        if (hasLen < len) {
            __len = len - hasLen;
            return null;
        }
        if (hasLen == len) {
            ByteArray ret = __received;
            __received = null;
            return ret;
        }
        // should not reach here
        Logger.warn(LogType.IMPROPER_USE, "the user code should ask exactly same length of data when calling twice wantData: " +
            "now asks " + len + ", got " + __received.length());
        ByteArray ret = __received.sub(0, len);
        __received = __received.sub(len, __received.length() - len);
        return ret;
    }

    /**
     * send data to another connection
     *
     * @param data the data to send
     */
    protected final void send(ByteArray data) {
        __sending = data;
    }

    /**
     * want to proxy data
     *
     * @param len length of data to be proxied. 0 for nothing to proxy
     */
    protected final void wantProxy(int len) {
        __mode = Mode.proxy;
        __len = len;
    }

    /**
     * get a connection without hint
     *
     * @return null if the connection not retrieved yet, or the connection retrieved.
     */
    protected final SUB chooseConn() {
        return chooseConn(null);
    }

    /**
     * get a connection
     *
     * @param hint hint
     * @return null if the connection not retrieved yet, or the connection retrieved.
     */
    protected final SUB chooseConn(Hint hint) {
        if (__chosen != null) {
            SUB ret = __chosen;
            __chosen = null;
            return ret;
        }
        __hint = hint;
        __connId = -1;
        return null;
    }

    /**
     * reuse an already established connection
     *
     * @param connId connection id
     */
    protected final void reuseConn(int connId) {
        __connId = connId;
    }

    /**
     * a frame is fully handled
     */
    protected final void frameDone() {
        __expectNewFrame = true;
    }

    @Override
    public final Mode mode(CTX ctx, SUB sub) {
        Mode ret = __mode;
        __mode = Mode.handle;
        return ret;
    }

    @Override
    public final boolean expectNewFrame(CTX ctx, SUB sub) {
        boolean ret = __expectNewFrame;
        __expectNewFrame = false;
        return ret;
    }

    @Override
    public final int len(CTX ctx, SUB sub) {
        int ret = __len;
        __len = 0;
        return ret;
    }

    @Override
    public final ByteArray feed(CTX ctx, SUB sub, ByteArray data) throws Exception {
        if (__received == null || __received.length() == 0) {
            __received = data;
        } else {
            __received = __received.concat(data);
        }
        handle(ctx, sub);
        ByteArray ret = __sending;
        __sending = null;
        return ret;
    }

    @Override
    public final ByteArray produce(CTX ctx, SUB sub) {
        ByteArray ret = __producing;
        __producing = null;
        return ret;
    }

    @Override
    public final void proxyDone(CTX ctx, SUB sub) {
        __mode = Mode.handle; // set mode back before calling handle() because the field may be set in the handle() func
        try {
            handle(ctx, sub);
        } catch (Exception e) {
            Logger.error(LogType.IMPROPER_USE, "handle should not throw exception in proxyDone()");
            throw new RuntimeException(e);
        }
    }

    @Override
    public final int connection(CTX ctx, SUB front) {
        int ret = __connId;
        __connId = -1;
        return ret;
    }

    @Override
    public final Hint connectionHint(CTX ctx, SUB front) {
        Hint ret = __hint;
        __hint = null;
        return ret;
    }

    @Override
    public final void chosen(CTX ctx, SUB front, SUB sub) {
        __chosen = sub;
        try {
            handle(ctx, front);
        } catch (Exception e) {
            Logger.error(LogType.IMPROPER_USE, "handle should not throw exception in chosen()");
            throw new RuntimeException(e);
        }
    }

    @Override
    public final ByteArray connected(CTX ctx, SUB sub) {
        ByteArray ret = __producing;
        __producing = null;
        return ret;
    }
}
