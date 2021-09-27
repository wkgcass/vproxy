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

public class HeadersFrame extends HttpFrame implements WithHeaders, WithPriority {
    public boolean endStream;
    public boolean endHeaders;
    public boolean padded;
    public boolean priority;

    public int padLength;
    public int streamDependency;
    public int weight;
    public List<Header> headers;
    public ByteArray padding;

    public HeadersFrame() {
        super(HttpFrameType.HEADERS);
    }

    public static HeadersFrame newRequest(String scheme, String method, String path, Header... headers) {
        return newRequest(scheme, method, path, Arrays.asList(headers));
    }

    public static HeadersFrame newRequest(String scheme, String method, String path, List<Header> headers) {
        HeadersFrame frame = new HeadersFrame();
        frame.endHeaders = true;
        frame.headers = new ArrayList<>(headers.size() + 2);
        frame.headers.add(new Header(":scheme", scheme));
        frame.headers.add(new Header(":method", method));
        frame.headers.add(new Header(":path", path));
        frame.headers.addAll(headers);
        return frame;
    }

    public static HeadersFrame newResponse(int status, Header... headers) {
        return newResponse(status, Arrays.asList(headers));
    }

    public static HeadersFrame newResponse(int status, List<Header> headers) {
        HeadersFrame frame = new HeadersFrame();
        frame.endHeaders = true;
        frame.headers = new ArrayList<>(headers.size() + 1);
        frame.headers.add(new Header(":status", "" + status));
        frame.headers.addAll(headers);
        return frame;
    }

    @Override
    public void setFlags(byte flags) {
        endStream = (flags & 0x1) == 0x1;
        endHeaders = (flags & 0x4) == 0x4;
        padded = (flags & 0x8) == 0x8;
        priority = (flags & 0x20) == 0x20;
    }

    @Override
    public void setPayload(BinaryHttpSubContext subCtx, ByteArray payload) throws Exception {
        int headerBlockOffset;
        int headerBlockLen;
        int paddingOffset;
        int priorityOffset;

        if (padded && priority) {
            headerBlockOffset = 1 + 4 + 1;
            padLength = payload.uint8(0);
            paddingOffset = length - padLength;
            headerBlockLen = paddingOffset - headerBlockOffset;
            priorityOffset = 1;
        } else if (padded) { // no priority
            headerBlockOffset = 1;
            padLength = payload.uint8(0);
            paddingOffset = length - padLength;
            headerBlockLen = paddingOffset - headerBlockOffset;
            priorityOffset = -1;
        } else if (priority) {
            headerBlockOffset = 4 + 1;
            headerBlockLen = length - headerBlockOffset;
            paddingOffset = -1;
            priorityOffset = 0;
        } else {
            headerBlockOffset = 0;
            headerBlockLen = length;
            paddingOffset = -1;
            priorityOffset = -1;
        }

        if (headerBlockLen == length && headerBlockOffset == 0) {
            headers = subCtx.getHPack().decode(payload);
        } else {
            headers = subCtx.getHPack().decode(payload.sub(headerBlockOffset, headerBlockLen));
        }
        if (paddingOffset != -1) {
            padding = payload.sub(paddingOffset, length - paddingOffset);
        }
        if (priorityOffset != -1) {
            streamDependency = payload.int32(priorityOffset);
            weight = payload.uint8(priorityOffset + 4);
        }
    }

    @Override
    public byte serializeFlags() {
        byte ret = 0;
        if (endStream) {
            ret |= 0x1;
        }
        if (endHeaders) {
            ret |= 0x4;
        }
        if (padded) {
            if (padding == null || padding.length() == 0) {
                throw new IllegalArgumentException("headers.padded is set but padding not specified");
            }
            ret |= 0x8;
        }
        if (priority) {
            ret |= 0x20;
        }
        return ret;
    }

    @Override
    public ByteArray serializeH2Payload(BinaryHttpSubContext subCtx) {
        ByteArray headers = subCtx.getHPack().encode(this.headers);
        if (padded && priority) {
            return ByteArray.allocate(6).set(0, (byte) padding.length())
                .int32(1, streamDependency)
                .set(5, (byte) weight)
                .concat(headers)
                .concat(padding);
        } else if (padded) { // no priority
            return ByteArray.allocate(1).set(0, (byte) padding.length())
                .concat(headers)
                .concat(padding);
        } else if (priority) { // no padded
            return ByteArray.allocate(5)
                .int32(0, streamDependency)
                .set(4, (byte) weight)
                .concat(headers);
        } else {
            return headers;
        }
    }

    @Override
    protected void toString(StringBuilder sb) {
        sb.append("endStream=").append(endStream)
            .append(",").append("endHeaders=").append(endHeaders)
            .append(",").append("padded=").append(padded)
            .append(",").append("priority=").append(priority)
            .append(",").append("padLength=").append(padLength)
            .append(",").append("streamDependency=").append(streamDependency)
            .append(",").append("weight=").append(weight)
            .append(",").append("headers=").append(headers)
            .append(",").append("padding=").append(padding);
    }

    @Override
    public boolean endHeaders() {
        return endHeaders;
    }

    @Override
    public List<Header> headers() {
        return headers;
    }

    @Override
    public boolean priority() {
        return priority;
    }

    @Override
    public int streamDependency() {
        return streamDependency;
    }

    @Override
    public int weight() {
        return weight;
    }

    @Override
    public void unsetPriority() {
        priority = false;
    }
}

/*
 * 6.2.  HEADERS
 *
 *    The HEADERS frame (type=0x1) is used to open a stream (Section 5.1),
 *    and additionally carries a header block fragment.  HEADERS frames can
 *    be sent on a stream in the "idle", "reserved (local)", "open", or
 *    "half-closed (remote)" state.
 *
 *     +---------------+
 *     |Pad Length? (8)|
 *     +-+-------------+-----------------------------------------------+
 *     |E|                 Stream Dependency? (31)                     |
 *     +-+-------------+-----------------------------------------------+
 *     |  Weight? (8)  |
 *     +-+-------------+-----------------------------------------------+
 *     |                   Header Block Fragment (*)                 ...
 *     +---------------------------------------------------------------+
 *     |                           Padding (*)                       ...
 *     +---------------------------------------------------------------+
 *
 *                       Figure 7: HEADERS Frame Payload
 *
 *    The HEADERS frame payload has the following fields:
 *
 *    Pad Length:  An 8-bit field containing the length of the frame
 *       padding in units of octets.  This field is only present if the
 *       PADDED flag is set.
 *
 *    E: A single-bit flag indicating that the stream dependency is
 *       exclusive (see Section 5.3).  This field is only present if the
 *       PRIORITY flag is set.
 *
 *    Stream Dependency:  A 31-bit stream identifier for the stream that
 *       this stream depends on (see Section 5.3).  This field is only
 *       present if the PRIORITY flag is set.
 *
 *    Weight:  An unsigned 8-bit integer representing a priority weight for
 *       the stream (see Section 5.3).  Add one to the value to obtain a
 *       weight between 1 and 256.  This field is only present if the
 *       PRIORITY flag is set.
 *
 *    Header Block Fragment:  A header block fragment (Section 4.3).
 *
 *    Padding:  Padding octets.
 *
 *    The HEADERS frame defines the following flags:
 *
 *    END_STREAM (0x1):  When set, bit 0 indicates that the header block
 *       (Section 4.3) is the last that the endpoint will send for the
 *       identified stream.
 *
 *       A HEADERS frame carries the END_STREAM flag that signals the end
 *       of a stream.  However, a HEADERS frame with the END_STREAM flag
 *       set can be followed by CONTINUATION frames on the same stream.
 *       Logically, the CONTINUATION frames are part of the HEADERS frame.
 *
 *    END_HEADERS (0x4):  When set, bit 2 indicates that this frame
 *       contains an entire header block (Section 4.3) and is not followed
 *       by any CONTINUATION frames.
 *
 *       A HEADERS frame without the END_HEADERS flag set MUST be followed
 *       by a CONTINUATION frame for the same stream.  A receiver MUST
 *       treat the receipt of any other type of frame or a frame on a
 *       different stream as a connection error (Section 5.4.1) of type
 *       PROTOCOL_ERROR.
 *
 *    PADDED (0x8):  When set, bit 3 indicates that the Pad Length field
 *       and any padding that it describes are present.
 *
 *    PRIORITY (0x20):  When set, bit 5 indicates that the Exclusive Flag
 *       (E), Stream Dependency, and Weight fields are present; see
 *       Section 5.3.
 *
 *    The payload of a HEADERS frame contains a header block fragment
 *    (Section 4.3).  A header block that does not fit within a HEADERS
 *    frame is continued in a CONTINUATION frame (Section 6.10).
 *
 *    HEADERS frames MUST be associated with a stream.  If a HEADERS frame
 *    is received whose stream identifier field is 0x0, the recipient MUST
 *    respond with a connection error (Section 5.4.1) of type
 *    PROTOCOL_ERROR.
 *
 *    The HEADERS frame changes the connection state as described in
 *    Section 4.3.
 *
 *    The HEADERS frame can include padding.  Padding fields and flags are
 *    identical to those defined for DATA frames (Section 6.1).  Padding
 *    that exceeds the size remaining for the header block fragment MUST be
 *    treated as a PROTOCOL_ERROR.
 *
 *    Prioritization information in a HEADERS frame is logically equivalent
 *    to a separate PRIORITY frame, but inclusion in HEADERS avoids the
 *    potential for churn in stream prioritization when new streams are
 *    created.  Prioritization fields in HEADERS frames subsequent to the
 *    first on a stream reprioritize the stream (Section 5.3.3).
 */

/*
 * 7.2.2.  HEADERS
 *
 *    The HEADERS frame (type=0x1) is used to carry an HTTP field section,
 *    encoded using QPACK.  See [QPACK] for more details.
 *
 *    HEADERS Frame {
 *      Type (i) = 0x1,
 *      Length (i),
 *      Encoded Field Section (..),
 *    }
 *
 *                           Figure 5: HEADERS Frame
 *
 *    HEADERS frames can only be sent on request or push streams.  If a
 *    HEADERS frame is received on a control stream, the recipient MUST
 *    respond with a connection error (Section 8) of type
 *    H3_FRAME_UNEXPECTED.
 */