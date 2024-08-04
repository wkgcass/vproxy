package io.vproxy.vproxyx.uot;

import io.vproxy.base.connection.ConnectionHandlerContext;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.ByteBufferEx;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.direct.DirectMemoryUtils;
import io.vproxy.base.util.nio.ByteArrayChannel;

import java.nio.ByteBuffer;

public class UOTHeaderParser {
    private int state;
    // 0: idle, expecting header
    // 1: reading header, expecting value
    // 2: value
    private ByteBufferEx _buf = DirectMemoryUtils.allocateDirectBuffer(65536);
    public final ByteBuffer buf = _buf.realBuffer();
    private final ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(UOTUtils.HEADER_LEN);
    public byte type;
    public int len;

    public UOTHeaderParser() {
    }

    public boolean parse(ConnectionHandlerContext ctx) {
        int n;
        switch (state) {
            case 0:
                chnl.reset();
                state = 1;
            case 1:
                n = ctx.connection.getInBuffer().writeTo(chnl);
                if (n == 0) {
                    // nothing read
                    return false;
                }
                if (chnl.free() != 0) {
                    // not fully read yet
                    return false;
                }
                type = chnl.getArray().get(0);
                len = chnl.getArray().uint16(2);
                buf.limit(len).position(0);
                state = 2;
            case 2:
                if (len == 0) {
                    state = 0;
                    break;
                }
                ctx.connection.getInBuffer().writeTo(buf);
                if (buf.limit() == buf.position()) {
                    if (buf.limit() != len) {
                        Logger.shouldNotHappen("buf.limit() == " + buf.limit() + ", but len == " + len);
                    }
                    state = 0;
                    buf.position(0);
                    break;
                }
                return false;
        }
        return true;
    }

    public void logInvalidExternalData(String msg) {
        Logger.error(LogType.INVALID_EXTERNAL_DATA, msg +
                                                    ", type=" + type + ", len=" + len +
                                                    ", data[" + buf.position() + ":" + buf.limit() + "]\n" +
                                                    ByteArray.from(buf).hexDump());
    }

    public void clean() {
        if (_buf != null) {
            _buf.clean();
            _buf = null;
        }
    }
}
