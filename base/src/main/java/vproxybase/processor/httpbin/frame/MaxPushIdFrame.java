package vproxybase.processor.httpbin.frame;

import vproxybase.processor.httpbin.BinaryHttpSubContext;
import vproxybase.processor.httpbin.HttpFrame;
import vproxybase.processor.httpbin.HttpFrameType;
import vproxybase.util.ByteArray;

public class MaxPushIdFrame extends HttpFrame {
    public int pushId;

    public MaxPushIdFrame() {
        super(HttpFrameType.MAX_PUSH_ID);
    }

    @Override
    public void setFlags(byte flags) {
        // TODO
    }

    @Override
    public void setPayload(BinaryHttpSubContext subCtx, ByteArray payload) throws Exception {
        // TODO
    }

    @Override
    public byte serializeFlags() {
        return 0; // TODO
    }

    @Override
    public ByteArray serializeH2Payload(BinaryHttpSubContext subCtx) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void toString(StringBuilder sb) {
        // TODO
    }
}

/*
 * HTTP/3:
 *
 * 7.2.7.  MAX_PUSH_ID
 *
 *    The MAX_PUSH_ID frame (type=0xd) is used by clients to control the
 *    number of server pushes that the server can initiate.  This sets the
 *    maximum value for a Push ID that the server can use in PUSH_PROMISE
 *    and CANCEL_PUSH frames.  Consequently, this also limits the number of
 *    push streams that the server can initiate in addition to the limit
 *    maintained by the QUIC transport.
 *
 *    The MAX_PUSH_ID frame is always sent on the control stream.  Receipt
 *    of a MAX_PUSH_ID frame on any other stream MUST be treated as a
 *    connection error of type H3_FRAME_UNEXPECTED.
 *
 *    A server MUST NOT send a MAX_PUSH_ID frame.  A client MUST treat the
 *    receipt of a MAX_PUSH_ID frame as a connection error of type
 *    H3_FRAME_UNEXPECTED.
 *
 *    The maximum Push ID is unset when an HTTP/3 connection is created,
 *    meaning that a server cannot push until it receives a MAX_PUSH_ID
 *    frame.  A client that wishes to manage the number of promised server
 *    pushes can increase the maximum Push ID by sending MAX_PUSH_ID frames
 *    as the server fulfills or cancels server pushes.
 *
 *    MAX_PUSH_ID Frame {
 *      Type (i) = 0xd,
 *      Length (i),
 *      Push ID (i),
 *    }
 *
 *                         Figure 10: MAX_PUSH_ID Frame
 *
 *    The MAX_PUSH_ID frame carries a single variable-length integer that
 *    identifies the maximum value for a Push ID that the server can use;
 *    see Section 4.4.  A MAX_PUSH_ID frame cannot reduce the maximum Push
 *    ID; receipt of a MAX_PUSH_ID frame that contains a smaller value than
 *    previously received MUST be treated as a connection error of type
 *    H3_ID_ERROR.
 */
