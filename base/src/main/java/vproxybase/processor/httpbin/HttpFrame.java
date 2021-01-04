package vproxybase.processor.httpbin;

import vproxybase.util.ByteArray;

public abstract class HttpFrame {
    public int length;
    public final HttpFrameType type;
    public byte flags;
    public int streamId;

    public HttpFrame(HttpFrameType type) {
        this.type = type;
    }

    abstract public void setFlags(byte flags);

    abstract public void setPayload(BinaryHttpSubContext subCtx, ByteArray payload) throws Exception;

    public ByteArray serializeH2(BinaryHttpSubContext subCtx) {
        // serialize header
        ByteArray header = ByteArray.allocate(3 + 1 + 1 + 4);
        byte flags = serializeFlags();
        flags |= this.flags; // add custom flags
        ByteArray payload = serializeH2Payload(subCtx);
        header.int24(0, payload.length()).set(3, (byte) type.h2type).set(4, flags).int32(5, streamId);
        return header.concat(payload);
    }

    abstract public byte serializeFlags();

    abstract public ByteArray serializeH2Payload(BinaryHttpSubContext subCtx);

    abstract protected void toString(StringBuilder sb);

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append("{");
        sb.append("length=").append(length)
            .append(",").append("type=").append(type)
            .append(",").append("flags=").append(flags)
            .append(",").append("streamId=").append(streamId)
            .append(",");
        toString(sb);
        sb.append("}");
        return sb.toString();
    }
}

/*
 * HTTP/2 https://www.rfc-editor.org/rfc/rfc7540.html
 *
 * 4.1.  Frame Format
 *
 *    All frames begin with a fixed 9-octet header followed by a variable-
 *    length payload.
 *
 *     +-----------------------------------------------+
 *     |                 Length (24)                   |
 *     +---------------+---------------+---------------+
 *     |   Type (8)    |   Flags (8)   |
 *     +-+-------------+---------------+-------------------------------+
 *     |R|                 Stream Identifier (31)                      |
 *     +=+=============================================================+
 *     |                   Frame Payload (0...)                      ...
 *     +---------------------------------------------------------------+
 *
 *                           Figure 1: Frame Layout
 *
 *    The fields of the frame header are defined as:
 *
 *    Length:  The length of the frame payload expressed as an unsigned
 *       24-bit integer.  Values greater than 2^14 (16,384) MUST NOT be
 *       sent unless the receiver has set a larger value for
 *       SETTINGS_MAX_FRAME_SIZE.
 *
 *       The 9 octets of the frame header are not included in this value.
 *
 *    Type:  The 8-bit type of the frame.  The frame type determines the
 *       format and semantics of the frame.  Implementations MUST ignore
 *       and discard any frame that has a type that is unknown.
 *
 *    Flags:  An 8-bit field reserved for boolean flags specific to the
 *       frame type.
 *
 *       Flags are assigned semantics specific to the indicated frame type.
 *       Flags that have no defined semantics for a particular frame type
 *       MUST be ignored and MUST be left unset (0x0) when sending.
 *
 *    R: A reserved 1-bit field.  The semantics of this bit are undefined,
 *       and the bit MUST remain unset (0x0) when sending and MUST be
 *       ignored when receiving.
 *
 *    Stream Identifier:  A stream identifier (see Section 5.1.1) expressed
 *       as an unsigned 31-bit integer.  The value 0x0 is reserved for
 *       frames that are associated with the connection as a whole as
 *       opposed to an individual stream.
 *
 *    The structure and content of the frame payload is dependent entirely
 *    on the frame type.
 */

/*
 * HTTP/3 https://tools.ietf.org/html/draft-ietf-quic-http-33
 *
 * 7.1.  Frame Layout
 *
 *    All frames have the following format:
 *
 *    HTTP/3 Frame Format {
 *      Type (i),
 *      Length (i),
 *      Frame Payload (..),
 *    }
 *
 *                        Figure 3: HTTP/3 Frame Format
 *
 *    A frame includes the following fields:
 *
 *    Type:  A variable-length integer that identifies the frame type.
 *
 *    Length:  A variable-length integer that describes the length in bytes
 *       of the Frame Payload.
 *
 *    Frame Payload:  A payload, the semantics of which are determined by
 *       the Type field.
 *
 *    Each frame's payload MUST contain exactly the fields identified in
 *    its description.  A frame payload that contains additional bytes
 *    after the identified fields or a frame payload that terminates before
 *    the end of the identified fields MUST be treated as a connection
 *    error of type H3_FRAME_ERROR; see Section 8.
 *
 *    When a stream terminates cleanly, if the last frame on the stream was
 *    truncated, this MUST be treated as a connection error of type
 *    H3_FRAME_ERROR; see Section 8.  Streams that terminate abruptly may
 *    be reset at any point in a frame.
 */

/*
 * QUIC:
 *
 * 16.  Variable-Length Integer Encoding
 *
 *    QUIC packets and frames commonly use a variable-length encoding for
 *    non-negative integer values.  This encoding ensures that smaller
 *    integer values need fewer bytes to encode.
 *
 *    The QUIC variable-length integer encoding reserves the two most
 *    significant bits of the first byte to encode the base 2 logarithm of
 *    the integer encoding length in bytes.  The integer value is encoded
 *    on the remaining bits, in network byte order.
 *
 *    This means that integers are encoded on 1, 2, 4, or 8 bytes and can
 *    encode 6, 14, 30, or 62 bit values respectively.  Table 4 summarizes
 *    the encoding properties.
 *
 *           +======+========+=============+=======================+
 *           | 2Bit | Length | Usable Bits | Range                 |
 *           +======+========+=============+=======================+
 *           | 00   | 1      | 6           | 0-63                  |
 *           +------+--------+-------------+-----------------------+
 *           | 01   | 2      | 14          | 0-16383               |
 *           +------+--------+-------------+-----------------------+
 *           | 10   | 4      | 30          | 0-1073741823          |
 *           +------+--------+-------------+-----------------------+
 *           | 11   | 8      | 62          | 0-4611686018427387903 |
 *           +------+--------+-------------+-----------------------+
 *
 *                    Table 4: Summary of Integer Encodings
 *
 *    Examples and a sample decoding algorithm are shown in Appendix A.1.
 *
 *    Versions (Section 15) and packet numbers sent in the header
 *    (Section 17.1) are described using integers, but do not use this
 *    encoding.
 */