package vproxybase.processor.httpbin.frame;

import vproxybase.processor.ExceptionWithoutStackTrace;
import vproxybase.processor.httpbin.BinaryHttpSubContext;
import vproxybase.processor.httpbin.HttpFrame;
import vproxybase.processor.httpbin.HttpFrameType;
import vproxybase.util.ByteArray;

public class RstStreamFrame extends HttpFrame {
    public int errorCode;

    public RstStreamFrame() {
        super(HttpFrameType.RST_STREAM);
    }

    @Override
    public void setFlags(byte flags) { // none
    }

    @Override
    public void setPayload(BinaryHttpSubContext subCtx, ByteArray payload) throws Exception {
        if (payload.length() != 4)
            throw new ExceptionWithoutStackTrace("rst stream frame payload length should be 4, but got " + payload.length());
        errorCode = payload.int32(0);
    }

    @Override
    public byte serializeFlags() {
        return 0;
    }

    @Override
    public ByteArray serializeH2Payload(BinaryHttpSubContext subCtx) {
        return ByteArray.allocate(4).int32(0, errorCode);
    }

    @Override
    protected void toString(StringBuilder sb) {
        sb.append("errorCode=").append(errorCode);
    }
}

/*
 * HTTP2:
 *
 * 6.4.  RST_STREAM
 *
 *    The RST_STREAM frame (type=0x3) allows for immediate termination of a
 *    stream.  RST_STREAM is sent to request cancellation of a stream or to
 *    indicate that an error condition has occurred.
 *
 *     +---------------------------------------------------------------+
 *     |                        Error Code (32)                        |
 *     +---------------------------------------------------------------+
 *
 *                     Figure 9: RST_STREAM Frame Payload
 *
 *    The RST_STREAM frame contains a single unsigned, 32-bit integer
 *    identifying the error code (Section 7).  The error code indicates why
 *    the stream is being terminated.
 *
 *    The RST_STREAM frame does not define any flags.
 *
 *    The RST_STREAM frame fully terminates the referenced stream and
 *    causes it to enter the "closed" state.  After receiving a RST_STREAM
 *    on a stream, the receiver MUST NOT send additional frames for that
 *    stream, with the exception of PRIORITY.  However, after sending the
 *    RST_STREAM, the sending endpoint MUST be prepared to receive and
 *    process additional frames sent on the stream that might have been
 *    sent by the peer prior to the arrival of the RST_STREAM.
 *
 *    RST_STREAM frames MUST be associated with a stream.  If a RST_STREAM
 *    frame is received with a stream identifier of 0x0, the recipient MUST
 *    treat this as a connection error (Section 5.4.1) of type
 *    PROTOCOL_ERROR.
 *
 *    RST_STREAM frames MUST NOT be sent for a stream in the "idle" state.
 *    If a RST_STREAM frame identifying an idle stream is received, the
 *    recipient MUST treat this as a connection error (Section 5.4.1) of
 *    type PROTOCOL_ERROR.
 *
 *    A RST_STREAM frame with a length other than 4 octets MUST be treated
 *    as a connection error (Section 5.4.1) of type FRAME_SIZE_ERROR.
 */