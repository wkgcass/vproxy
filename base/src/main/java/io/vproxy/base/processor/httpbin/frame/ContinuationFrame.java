package io.vproxy.base.processor.httpbin.frame;

import io.vproxy.base.processor.httpbin.BinaryHttpSubContext;
import io.vproxy.base.processor.httpbin.HttpFrame;
import io.vproxy.base.processor.httpbin.HttpFrameType;
import io.vproxy.base.processor.httpbin.entity.Header;
import io.vproxy.base.util.ByteArray;

import java.util.List;

public class ContinuationFrame extends HttpFrame implements WithHeaders {
    public boolean endHeaders;

    public List<Header> headers;

    public ContinuationFrame() {
        super(HttpFrameType.CONTINUATION);
    }

    @Override
    public void setFlags(byte flags) {
        endHeaders = (flags & 0x4) == 0x4;
    }

    @Override
    public void setPayload(BinaryHttpSubContext subCtx, ByteArray payload) throws Exception {
        headers = subCtx.getHPack().decode(payload);
    }

    @Override
    public byte serializeFlags() {
        byte ret = 0;
        if (endHeaders) {
            ret |= 0x4;
        }
        return ret;
    }

    @Override
    public ByteArray serializeH2Payload(BinaryHttpSubContext subCtx) {
        return subCtx.getHPack().encode(headers);
    }

    @Override
    protected void toString(StringBuilder sb) {
        sb.append("endHeaders=").append(endHeaders)
            .append(",").append("headers=").append(headers);
    }

    @Override
    public boolean endHeaders() {
        return endHeaders;
    }

    @Override
    public List<Header> headers() {
        return headers;
    }
}

/*
 * HTTP/2:
 *
 * 6.10.  CONTINUATION
 *
 *    The CONTINUATION frame (type=0x9) is used to continue a sequence of
 *    header block fragments (Section 4.3).  Any number of CONTINUATION
 *    frames can be sent, as long as the preceding frame is on the same
 *    stream and is a HEADERS, PUSH_PROMISE, or CONTINUATION frame without
 *    the END_HEADERS flag set.
 *
 *     +---------------------------------------------------------------+
 *     |                   Header Block Fragment (*)                 ...
 *     +---------------------------------------------------------------+
 *
 *                    Figure 15: CONTINUATION Frame Payload
 *
 *    The CONTINUATION frame payload contains a header block fragment
 *    (Section 4.3).
 *
 *    The CONTINUATION frame defines the following flag:
 *
 *    END_HEADERS (0x4):  When set, bit 2 indicates that this frame ends a
 *       header block (Section 4.3).
 *
 *       If the END_HEADERS bit is not set, this frame MUST be followed by
 *       another CONTINUATION frame.  A receiver MUST treat the receipt of
 *       any other type of frame or a frame on a different stream as a
 *       connection error (Section 5.4.1) of type PROTOCOL_ERROR.
 *
 *    The CONTINUATION frame changes the connection state as defined in
 *    Section 4.3.
 *
 *    CONTINUATION frames MUST be associated with a stream.  If a
 *    CONTINUATION frame is received whose stream identifier field is 0x0,
 *    the recipient MUST respond with a connection error (Section 5.4.1) of
 *    type PROTOCOL_ERROR.
 *
 *    A CONTINUATION frame MUST be preceded by a HEADERS, PUSH_PROMISE or
 *    CONTINUATION frame without the END_HEADERS flag set.  A recipient
 *    that observes violation of this rule MUST respond with a connection
 *    error (Section 5.4.1) of type PROTOCOL_ERROR.
 */
