package io.vproxy.vproxyx.uot;

import io.vproxy.base.connection.ConnectionHandlerContext;
import io.vproxy.base.util.ByteBufferEx;
import io.vproxy.base.util.direct.DirectMemoryUtils;
import io.vproxy.base.util.nio.ByteArrayChannel;

import java.nio.ByteBuffer;

public class TLVParser {
    private int state = 0;
    // 0 -> idle, expecting type
    // 1 -> type, expecting len
    // 2 -> len[0], expecting len[1]
    // 3 -> len[1], expecting data
    private final ByteBufferEx _buf = DirectMemoryUtils.allocateDirectBuffer(65536);
    public final ByteBuffer buf = _buf.realBuffer();
    private final ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(1);
    public int type;
    public int len;

    public TLVParser() {
    }

    public boolean parse(ConnectionHandlerContext ctx) {
        int n;
        switch (state) {
            case 0:
                chnl.reset();
                n = ctx.connection.getInBuffer().writeTo(chnl);
                if (n == 0) {
                    // nothing read ...
                    return false;
                }
                type = chnl.getArray().get(0) & 0xff;
                state = 1;
            case 1:
                chnl.reset();
                n = ctx.connection.getInBuffer().writeTo(chnl);
                if (n == 0) {
                    // nothing read ...
                    return false;
                }
                len = chnl.getArray().get(0) & 0xff;
                state = 2;
            case 2:
                chnl.reset();
                n = ctx.connection.getInBuffer().writeTo(chnl);
                if (n == 0) {
                    // nothing read ...
                    return false;
                }
                len = len << 8;
                len |= chnl.getArray().get(0) & 0xff;
                state = 3;
            case 3:
                if (len == 0) {
                    state = 0;
                    break;
                }
                buf.limit(len).position(0);
                ctx.connection.getInBuffer().writeTo(buf);
                if (buf.limit() == buf.position()) {
                    state = 0;
                    break;
                }
                return false;
        }
        return true;
    }

    public void clean() {
        _buf.clean();
    }
}
