package io.vproxy.base.processor.httpbin.frame;

import io.vproxy.base.processor.httpbin.entity.Header;
import io.vproxy.base.processor.httpbin.BinaryHttpSubContext;
import io.vproxy.base.processor.httpbin.HttpFrame;
import io.vproxy.base.processor.httpbin.HttpFrameType;
import io.vproxy.base.processor.httpbin.entity.Header;
import io.vproxy.base.util.ByteArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PushPromiseFrame extends HttpFrame implements WithHeaders {
    public boolean endHeaders;
    public boolean padded;

    public int padLength;
    public int promisedStreamId;
    public List<Header> headers;
    public ByteArray padding;

    public int pushId;

    public PushPromiseFrame() {
        super(HttpFrameType.PUSH_PROMISE);
    }

    public static PushPromiseFrame newSimple(String scheme, String method, String path, Header... headers) {
        return newSimple(scheme, method, path, Arrays.asList(headers));
    }

    public static PushPromiseFrame newSimple(String scheme, String method, String path, List<Header> headers) {
        PushPromiseFrame frame = new PushPromiseFrame();
        frame.endHeaders = true;
        frame.headers = new ArrayList<>(headers.size() + 3);
        frame.headers.add(new Header(":scheme", scheme));
        frame.headers.add(new Header(":method", method));
        frame.headers.add(new Header(":path", path));
        frame.headers.addAll(headers);
        return frame;
    }

    @Override
    public void setFlags(byte flags) {
        endHeaders = (flags & 0x4) == 0x4;
        padded = (flags & 0x8) == 0x8;
    }

    @Override
    public void setPayload(BinaryHttpSubContext subCtx, ByteArray payload) throws Exception {
        if (padded) {
            padLength = payload.uint8(0);
            promisedStreamId = payload.int32(1);
            headers = subCtx.getHPack().decode(payload.sub(5, length - 5 - padLength));
            padding = payload.sub(length - padLength, padLength);
        } else {
            promisedStreamId = payload.int32(0);
            headers = subCtx.getHPack().decode(payload.sub(4, length - 4));
        }
    }

    @Override
    public byte serializeFlags() {
        byte ret = 0;
        if (endHeaders) {
            ret |= 0x4;
        }
        if (padded) {
            if (padding == null || padding.length() == 0) {
                throw new IllegalArgumentException("pushPromise.padded is set but padding not specified");
            }
            ret |= 0x8;
        }
        return ret;
    }

    @Override
    public ByteArray serializeH2Payload(BinaryHttpSubContext subCtx) {
        ByteArray headers = subCtx.getHPack().encode(this.headers);
        if (padded) {
            return ByteArray.allocate(5).set(0, (byte) padding.length())
                .int32(1, promisedStreamId)
                .concat(headers)
                .concat(padding);
        } else {
            return ByteArray.allocate(4).int32(0, promisedStreamId)
                .concat(headers);
        }
    }

    @Override
    protected void toString(StringBuilder sb) {
        sb.append("endHeaders=").append(endHeaders)
            .append(",").append("padded=").append(padded)
            .append(",").append("padLength=").append(padLength)
            .append(",").append("promisedStreamId=").append(promisedStreamId)
            .append(",").append("headers=").append(headers)
            .append(",").append("padding=").append(padding)
            .append(",").append("pushId=").append(pushId);
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
 * 6.6.  PUSH_PROMISE
 *
 *    The PUSH_PROMISE frame (type=0x5) is used to notify the peer endpoint
 *    in advance of streams the sender intends to initiate.  The
 *    PUSH_PROMISE frame includes the unsigned 31-bit identifier of the
 *    stream the endpoint plans to create along with a set of headers that
 *    provide additional context for the stream.  Section 8.2 contains a
 *    thorough description of the use of PUSH_PROMISE frames.
 *
 *     +---------------+
 *     |Pad Length? (8)|
 *     +-+-------------+-----------------------------------------------+
 *     |R|                  Promised Stream ID (31)                    |
 *     +-+-----------------------------+-------------------------------+
 *     |                   Header Block Fragment (*)                 ...
 *     +---------------------------------------------------------------+
 *     |                           Padding (*)                       ...
 *     +---------------------------------------------------------------+
 *
 *                   Figure 11: PUSH_PROMISE Payload Format
 *
 *    The PUSH_PROMISE frame payload has the following fields:
 *
 *    Pad Length:  An 8-bit field containing the length of the frame
 *       padding in units of octets.  This field is only present if the
 *       PADDED flag is set.
 *
 *    R: A single reserved bit.
 *
 *    Promised Stream ID:  An unsigned 31-bit integer that identifies the
 *       stream that is reserved by the PUSH_PROMISE.  The promised stream
 *       identifier MUST be a valid choice for the next stream sent by the
 *       sender (see "new stream identifier" in Section 5.1.1).
 *
 *    Header Block Fragment:  A header block fragment (Section 4.3)
 *       containing request header fields.
 *
 *    Padding:  Padding octets.
 *
 *    The PUSH_PROMISE frame defines the following flags:
 *
 *    END_HEADERS (0x4):  When set, bit 2 indicates that this frame
 *       contains an entire header block (Section 4.3) and is not followed
 *       by any CONTINUATION frames.
 *
 *       A PUSH_PROMISE frame without the END_HEADERS flag set MUST be
 *       followed by a CONTINUATION frame for the same stream.  A receiver
 *       MUST treat the receipt of any other type of frame or a frame on a
 *       different stream as a connection error (Section 5.4.1) of type
 *       PROTOCOL_ERROR.
 *
 *    PADDED (0x8):  When set, bit 3 indicates that the Pad Length field
 *       and any padding that it describes are present.
 *
 *    PUSH_PROMISE frames MUST only be sent on a peer-initiated stream that
 *    is in either the "open" or "half-closed (remote)" state.  The stream
 *    identifier of a PUSH_PROMISE frame indicates the stream it is
 *    associated with.  If the stream identifier field specifies the value
 *    0x0, a recipient MUST respond with a connection error (Section 5.4.1)
 *    of type PROTOCOL_ERROR.
 *
 *    Promised streams are not required to be used in the order they are
 *    promised.  The PUSH_PROMISE only reserves stream identifiers for
 *    later use.
 *
 *    PUSH_PROMISE MUST NOT be sent if the SETTINGS_ENABLE_PUSH setting of
 *    the peer endpoint is set to 0.  An endpoint that has set this setting
 *    and has received acknowledgement MUST treat the receipt of a
 *    PUSH_PROMISE frame as a connection error (Section 5.4.1) of type
 *    PROTOCOL_ERROR.
 *
 *    Recipients of PUSH_PROMISE frames can choose to reject promised
 *    streams by returning a RST_STREAM referencing the promised stream
 *    identifier back to the sender of the PUSH_PROMISE.
 *
 *    A PUSH_PROMISE frame modifies the connection state in two ways.
 *    First, the inclusion of a header block (Section 4.3) potentially
 *    modifies the state maintained for header compression.  Second,
 *    PUSH_PROMISE also reserves a stream for later use, causing the
 *    promised stream to enter the "reserved" state.  A sender MUST NOT
 *    send a PUSH_PROMISE on a stream unless that stream is either "open"
 *    or "half-closed (remote)"; the sender MUST ensure that the promised
 *    stream is a valid choice for a new stream identifier (Section 5.1.1)
 *    (that is, the promised stream MUST be in the "idle" state).
 *
 *    Since PUSH_PROMISE reserves a stream, ignoring a PUSH_PROMISE frame
 *    causes the stream state to become indeterminate.  A receiver MUST
 *    treat the receipt of a PUSH_PROMISE on a stream that is neither
 *    "open" nor "half-closed (local)" as a connection error
 *    (Section 5.4.1) of type PROTOCOL_ERROR.  However, an endpoint that
 *    has sent RST_STREAM on the associated stream MUST handle PUSH_PROMISE
 *    frames that might have been created before the RST_STREAM frame is
 *    received and processed.
 *
 *    A receiver MUST treat the receipt of a PUSH_PROMISE that promises an
 *    illegal stream identifier (Section 5.1.1) as a connection error
 *    (Section 5.4.1) of type PROTOCOL_ERROR.  Note that an illegal stream
 *    identifier is an identifier for a stream that is not currently in the
 *    "idle" state.
 *
 *    The PUSH_PROMISE frame can include padding.  Padding fields and flags
 *    are identical to those defined for DATA frames (Section 6.1).
 */

/*
 * HTTP/3:
 *
 * 7.2.5.  PUSH_PROMISE
 *
 *    The PUSH_PROMISE frame (type=0x5) is used to carry a promised request
 *    header field section from server to client on a request stream, as in
 *    HTTP/2.
 *
 *    PUSH_PROMISE Frame {
 *      Type (i) = 0x5,
 *      Length (i),
 *      Push ID (i),
 *      Encoded Field Section (..),
 *    }
 *
 *                         Figure 8: PUSH_PROMISE Frame
 *
 *    The payload consists of:
 *
 *    Push ID:  A variable-length integer that identifies the server push
 *      operation.  A Push ID is used in push stream headers (Section 4.4)
 *       and CANCEL_PUSH frames (Section 7.2.3).
 *
 *    Encoded Field Section:  QPACK-encoded request header fields for the
 *       promised response.  See [QPACK] for more details.
 *
 *    A server MUST NOT use a Push ID that is larger than the client has
 *    provided in a MAX_PUSH_ID frame (Section 7.2.7).  A client MUST treat
 *    receipt of a PUSH_PROMISE frame that contains a larger Push ID than
 *    the client has advertised as a connection error of H3_ID_ERROR.
 *
 *    A server MAY use the same Push ID in multiple PUSH_PROMISE frames.
 *    If so, the decompressed request header sets MUST contain the same
 *    fields in the same order, and both the name and the value in each
 *    field MUST be exact matches.  Clients SHOULD compare the request
 *    header sections for resources promised multiple times.  If a client
 *    receives a Push ID that has already been promised and detects a
 *    mismatch, it MUST respond with a connection error of type
 *    H3_GENERAL_PROTOCOL_ERROR.  If the decompressed field sections match
 *    exactly, the client SHOULD associate the pushed content with each
 *    stream on which a PUSH_PROMISE frame was received.
 *
 *    Allowing duplicate references to the same Push ID is primarily to
 *    reduce duplication caused by concurrent requests.  A server SHOULD
 *    avoid reusing a Push ID over a long period.  Clients are likely to
 *    consume server push responses and not retain them for reuse over
 *    time.  Clients that see a PUSH_PROMISE frame that uses a Push ID that
 *    they have already consumed and discarded are forced to ignore the
 *    promise.
 *
 *    If a PUSH_PROMISE frame is received on the control stream, the client
 *    MUST respond with a connection error of type H3_FRAME_UNEXPECTED; see
 *    Section 8.
 *
 *    A client MUST NOT send a PUSH_PROMISE frame.  A server MUST treat the
 *    receipt of a PUSH_PROMISE frame as a connection error of type
 *    H3_FRAME_UNEXPECTED; see Section 8.
 *
 *    See Section 4.4 for a description of the overall server push
 *    mechanism.
 */
