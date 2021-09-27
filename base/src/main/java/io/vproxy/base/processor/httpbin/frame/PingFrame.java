package io.vproxy.base.processor.httpbin.frame;

import io.vproxy.base.processor.httpbin.BinaryHttpSubContext;
import io.vproxy.base.processor.httpbin.HttpFrame;
import io.vproxy.base.processor.httpbin.HttpFrameType;
import io.vproxy.base.util.ByteArray;

public class PingFrame extends HttpFrame {
    public boolean ack;

    public long data;

    public PingFrame() {
        super(HttpFrameType.PING);
    }

    @Override
    public void setFlags(byte flags) {
        ack = (flags & 0x1) == 0x1;
    }

    @Override
    public void setPayload(BinaryHttpSubContext subCtx, ByteArray payload) {
        data = payload.int64(0);
    }

    @Override
    public byte serializeFlags() {
        byte ret = 0;
        if (ack) {
            ret |= 0x1;
        }
        return ret;
    }

    @Override
    public ByteArray serializeH2Payload(BinaryHttpSubContext subCtx) {
        return ByteArray.allocate(8).int64(0, data);
    }

    @Override
    protected void toString(StringBuilder sb) {
        sb.append("ack=").append(ack)
            .append(",").append("data=").append(data);
    }
}

/*
 * HTTP/2:
 *
 * 6.7.  PING
 *
 *    The PING frame (type=0x6) is a mechanism for measuring a minimal
 *    round-trip time from the sender, as well as determining whether an
 *    idle connection is still functional.  PING frames can be sent from
 *    any endpoint.
 *
 *     +---------------------------------------------------------------+
 *     |                                                               |
 *     |                      Opaque Data (64)                         |
 *     |                                                               |
 *     +---------------------------------------------------------------+
 *
 *                       Figure 12: PING Payload Format
 *
 *    In addition to the frame header, PING frames MUST contain 8 octets of
 *    opaque data in the payload.  A sender can include any value it
 *    chooses and use those octets in any fashion.
 *
 *    Receivers of a PING frame that does not include an ACK flag MUST send
 *    a PING frame with the ACK flag set in response, with an identical
 *    payload.  PING responses SHOULD be given higher priority than any
 *    other frame.
 *
 *    The PING frame defines the following flags:
 *
 *    ACK (0x1):  When set, bit 0 indicates that this PING frame is a PING
 *       response.  An endpoint MUST set this flag in PING responses.  An
 *       endpoint MUST NOT respond to PING frames containing this flag.
 *
 *    PING frames are not associated with any individual stream.  If a PING
 *    frame is received with a stream identifier field value other than
 *    0x0, the recipient MUST respond with a connection error
 *    (Section 5.4.1) of type PROTOCOL_ERROR.
 *
 *    Receipt of a PING frame with a length field value other than 8 MUST
 *    be treated as a connection error (Section 5.4.1) of type
 *    FRAME_SIZE_ERROR.
 */
