package vproxybase.processor.httpbin.frame;

import vproxybase.processor.httpbin.BinaryHttpSubContext;
import vproxybase.processor.httpbin.HttpFrame;
import vproxybase.processor.httpbin.HttpFrameType;
import vproxybase.util.ByteArray;

public class CancelPushFrame extends HttpFrame {
    public int pushId;

    public CancelPushFrame() {
        super(HttpFrameType.CANCEL_PUSH);
    }

    @Override
    public void setFlags(byte flags) {
        // TODO
    }

    @Override
    public void setPayload(BinaryHttpSubContext subCtx, ByteArray payload) {
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
 * 7.2.3.  CANCEL_PUSH
 *
 *    The CANCEL_PUSH frame (type=0x3) is used to request cancellation of a
 *    server push prior to the push stream being received.  The CANCEL_PUSH
 *    frame identifies a server push by Push ID (see Section 4.4), encoded
 *    as a variable-length integer.
 *
 *    When a client sends CANCEL_PUSH, it is indicating that it does not
 *    wish to receive the promised resource.  The server SHOULD abort
 *    sending the resource, but the mechanism to do so depends on the state
 *    of the corresponding push stream.  If the server has not yet created
 *    a push stream, it does not create one.  If the push stream is open,
 *    the server SHOULD abruptly terminate that stream.  If the push stream
 *    has already ended, the server MAY still abruptly terminate the stream
 *    or MAY take no action.
 *
 *    A server sends CANCEL_PUSH to indicate that it will not be fulfilling
 *    a promise which was previously sent.  The client cannot expect the
 *    corresponding promise to be fulfilled, unless it has already received
 *    and processed the promised response.  Regardless of whether a push
 *    stream has been opened, a server SHOULD send a CANCEL_PUSH frame when
 *    it determines that promise will not be fulfilled.  If a stream has
 *    already been opened, the server can abort sending on the stream with
 *    an error code of H3_REQUEST_CANCELLED.
 *
 *    Sending a CANCEL_PUSH frame has no direct effect on the state of
 *    existing push streams.  A client SHOULD NOT send a CANCEL_PUSH frame
 *    when it has already received a corresponding push stream.  A push
 *    stream could arrive after a client has sent a CANCEL_PUSH frame,
 *    because a server might not have processed the CANCEL_PUSH.  The
 *    client SHOULD abort reading the stream with an error code of
 *    H3_REQUEST_CANCELLED.
 *
 *    A CANCEL_PUSH frame is sent on the control stream.  Receiving a
 *    CANCEL_PUSH frame on a stream other than the control stream MUST be
 *    treated as a connection error of type H3_FRAME_UNEXPECTED.
 *
 *    CANCEL_PUSH Frame {
 *      Type (i) = 0x3,
 *      Length (i),
 *      Push ID (..),
 *    }
 *
 *                         Figure 6: CANCEL_PUSH Frame
 *
 *    The CANCEL_PUSH frame carries a Push ID encoded as a variable-length
 *    integer.  The Push ID identifies the server push that is being
 *    cancelled; see Section 4.4.  If a CANCEL_PUSH frame is received that
 *    references a Push ID greater than currently allowed on the
 *    connection, this MUST be treated as a connection error of type
 *    H3_ID_ERROR.
 *
 *    If the client receives a CANCEL_PUSH frame, that frame might identify
 *    a Push ID that has not yet been mentioned by a PUSH_PROMISE frame due
 *    to reordering.  If a server receives a CANCEL_PUSH frame for a Push
 *    ID that has not yet been mentioned by a PUSH_PROMISE frame, this MUST
 *    be treated as a connection error of type H3_ID_ERROR.
 */