package io.vproxy.base.processor.httpbin.frame;

import io.vproxy.base.processor.httpbin.BinaryHttpSubContext;
import io.vproxy.base.processor.httpbin.HttpFrame;
import io.vproxy.base.processor.httpbin.HttpFrameType;
import io.vproxy.base.util.ByteArray;

public class DataFrame extends HttpFrame {
    public boolean endStream;
    public boolean padded;

    public int padLength;
    public ByteArray data;
    public ByteArray padding;

    public DataFrame() {
        super(HttpFrameType.DATA);
    }

    @Override
    public void setFlags(byte flags) {
        endStream = (flags & 0x1) == 0x1;
        padded = (flags & 0x8) == 0x8;
    }

    @Override
    public void setPayload(BinaryHttpSubContext subCtx, ByteArray payload) {
        if (padded) {
            padLength = payload.uint8(0);
            data = payload.sub(1, length - 1 - padLength);
            padding = payload.sub(1 + data.length(), padLength);
        } else {
            data = payload;
        }
    }

    @Override
    public byte serializeFlags() {
        byte ret = 0;
        if (endStream) {
            ret |= 0x1;
        }
        if (padded) {
            if (padding == null || padding.length() == 0) {
                throw new IllegalArgumentException("data.padded set but padding not specified");
            }
            ret |= 0x8;
        }
        return ret;
    }

    @Override
    public ByteArray serializeH2Payload(BinaryHttpSubContext subCtx) {
        ByteArray payload;
        if (padded) {
            payload = ByteArray.allocate(1).set(0, (byte) padding.length())
                .concat(data).concat(padding);
        } else {
            payload = data;
        }
        return payload;
    }

    @Override
    protected void toString(StringBuilder sb) {
        sb.append("endStream=").append(endStream)
            .append(",").append("padded=").append(padded)
            .append(",").append("padLength=").append(padLength)
            .append(",").append("data=").append(data)
            .append(",").append("padding=").append(padding);
    }
}

/*
 * HTTP/2
 *
 * 6.1.  DATA
 *
 *    DATA frames (type=0x0) convey arbitrary, variable-length sequences of
 *    octets associated with a stream.  One or more DATA frames are used,
 *    for instance, to carry HTTP request or response payloads.
 *
 *    DATA frames MAY also contain padding.  Padding can be added to DATA
 *    frames to obscure the size of messages.  Padding is a security
 *    feature; see Section 10.7.
 *
 *     +---------------+
 *     |Pad Length? (8)|
 *     +---------------+-----------------------------------------------+
 *     |                            Data (*)                         ...
 *     +---------------------------------------------------------------+
 *     |                           Padding (*)                       ...
 *     +---------------------------------------------------------------+
 *
 *                        Figure 6: DATA Frame Payload
 *
 *    The DATA frame contains the following fields:
 *
 *    Pad Length:  An 8-bit field containing the length of the frame
 *       padding in units of octets.  This field is conditional (as
 *       signified by a "?" in the diagram) and is only present if the
 *       PADDED flag is set.
 *
 *    Data:  Application data.  The amount of data is the remainder of the
 *       frame payload after subtracting the length of the other fields
 *       that are present.
 *
 *    Padding:  Padding octets that contain no application semantic value.
 *       Padding octets MUST be set to zero when sending.  A receiver is
 *       not obligated to verify padding but MAY treat non-zero padding as
 *       a connection error (Section 5.4.1) of type PROTOCOL_ERROR.
 *
 *    The DATA frame defines the following flags:
 *
 *    END_STREAM (0x1):  When set, bit 0 indicates that this frame is the
 *       last that the endpoint will send for the identified stream.
 *       Setting this flag causes the stream to enter one of the "half-
 *       closed" states or the "closed" state (Section 5.1).
 *
 *    PADDED (0x8):  When set, bit 3 indicates that the Pad Length field
 *       and any padding that it describes are present.
 *
 *    DATA frames MUST be associated with a stream.  If a DATA frame is
 *    received whose stream identifier field is 0x0, the recipient MUST
 *    respond with a connection error (Section 5.4.1) of type
 *    PROTOCOL_ERROR.
 *
 *    DATA frames are subject to flow control and can only be sent when a
 *    stream is in the "open" or "half-closed (remote)" state.  The entire
 *    DATA frame payload is included in flow control, including the Pad
 *    Length and Padding fields if present.  If a DATA frame is received
 *    whose stream is not in "open" or "half-closed (local)" state, the
 *    recipient MUST respond with a stream error (Section 5.4.2) of type
 *    STREAM_CLOSED.
 *
 *    The total number of padding octets is determined by the value of the
 *    Pad Length field.  If the length of the padding is the length of the
 *    frame payload or greater, the recipient MUST treat this as a
 *    connection error (Section 5.4.1) of type PROTOCOL_ERROR.
 *
 *       Note: A frame can be increased in size by one octet by including a
 *       Pad Length field with a value of zero.
 */

/*
 * HTTP/3
 *
 * 7.2.1.  DATA
 *
 *    DATA frames (type=0x0) convey arbitrary, variable-length sequences of
 *    bytes associated with HTTP request or response payload data.
 *
 *    DATA frames MUST be associated with an HTTP request or response.  If
 *    a DATA frame is received on a control stream, the recipient MUST
 *    respond with a connection error of type H3_FRAME_UNEXPECTED; see
 *    Section 8.
 *
 *    DATA Frame {
 *      Type (i) = 0x0,
 *      Length (i),
 *      Data (..),
 *    }
 *
 *                             Figure 4: DATA Frame
 */
