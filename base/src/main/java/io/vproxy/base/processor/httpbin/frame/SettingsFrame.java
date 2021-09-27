package io.vproxy.base.processor.httpbin.frame;

import io.vproxy.base.processor.ExceptionWithoutStackTrace;
import io.vproxy.base.processor.httpbin.BinaryHttpSubContext;
import io.vproxy.base.processor.httpbin.HttpFrame;
import io.vproxy.base.processor.httpbin.HttpFrameType;
import io.vproxy.base.processor.httpbin.entity.Setting;
import io.vproxy.base.util.ByteArray;

import java.util.LinkedList;
import java.util.List;

public class SettingsFrame extends HttpFrame {
    public boolean ack;

    public static final int DEFAULT_HEADER_TABLE_SIZE = 4096;
    public int headerTableSize;
    public boolean headerTableSizeSet;

    public int enablePush;
    public boolean enablePushSet;

    public int maxConcurrentStreams;
    public boolean maxConcurrentStreamsSet;

    public static final int DEFAULT_WINDOW_SIZE = 65535;
    public int initialWindowSize;
    public boolean initialWindowSizeSet;

    public static final int DEFAULT_MAX_FRAME_SIZe = 16384;
    public int maxFrameSize;
    public boolean maxFrameSizeSet;

    public int maxHeaderListSize;
    public boolean maxHeaderListSizeSet;

    public int enableConnectProtocol;
    public boolean enableConnectProtocolSet;

    public List<Setting> otherSettings;

    public SettingsFrame() {
        super(HttpFrameType.SETTINGS);
    }

    @SuppressWarnings("DuplicatedCode")
    public static SettingsFrame newClientSettings() {
        SettingsFrame settings = new SettingsFrame();
        settings.streamId = 0;
        settings.headerTableSize = DEFAULT_HEADER_TABLE_SIZE;
        settings.headerTableSizeSet = true;
        settings.enablePush = 1;
        settings.enablePushSet = true;
        settings.maxConcurrentStreams = 128;
        settings.maxConcurrentStreamsSet = true;
        settings.initialWindowSize = DEFAULT_WINDOW_SIZE;
        settings.initialWindowSizeSet = true;
        settings.maxFrameSize = DEFAULT_MAX_FRAME_SIZe;
        settings.maxFrameSizeSet = true;
        // settings.maxHeaderListSize = unlimited;
        settings.maxHeaderListSizeSet = false; // unlimited
        return settings;
    }

    @SuppressWarnings("DuplicatedCode")
    public static SettingsFrame newServerSettings() {
        SettingsFrame settings = new SettingsFrame();
        settings.streamId = 0;
        settings.headerTableSize = DEFAULT_HEADER_TABLE_SIZE;
        settings.headerTableSizeSet = true;
        // settings.enablePush = 1;
        settings.enablePushSet = false; // client option
        settings.maxConcurrentStreams = 128;
        settings.maxConcurrentStreamsSet = true;
        settings.initialWindowSize = DEFAULT_WINDOW_SIZE;
        settings.initialWindowSizeSet = true;
        settings.maxFrameSize = DEFAULT_MAX_FRAME_SIZe;
        settings.maxFrameSizeSet = true;
        // settings.maxHeaderListSize = unlimited;
        settings.maxHeaderListSizeSet = false; // unlimited
        settings.enableConnectProtocolSet = true;
        settings.enableConnectProtocol = 1;
        return settings;
    }

    public static SettingsFrame newAck() {
        SettingsFrame settings = new SettingsFrame();
        settings.ack = true;
        return settings;
    }

    @Override
    public void setFlags(byte flags) {
        ack = (flags & 0x1) == 0x1;
    }

    @Override
    public void setPayload(BinaryHttpSubContext subCtx, ByteArray payload) throws Exception {
        if (payload.length() % 6 != 0)
            throw new ExceptionWithoutStackTrace("settings frame length should be multiples of 6, but got " + payload.length());
        List<Setting> settings = new LinkedList<>();
        for (int i = 0; i < payload.length(); i += 6) {
            ByteArray setting = payload.sub(i, 6);
            int id = setting.uint16(0);
            int value = setting.int32(2);

            switch (id) {
                case Setting.SETTINGS_HEADER_TABLE_SIZE:
                    headerTableSize = value;
                    headerTableSizeSet = true;
                    break;
                case Setting.SETTINGS_ENABLE_PUSH:
                    enablePush = value;
                    enablePushSet = true;
                    break;
                case Setting.SETTINGS_MAX_CONCURRENT_STREAMS:
                    maxConcurrentStreams = value;
                    maxConcurrentStreamsSet = true;
                    break;
                case Setting.SETTINGS_INITIAL_WINDOW_SIZE:
                    initialWindowSize = value;
                    initialWindowSizeSet = true;
                    break;
                case Setting.SETTINGS_MAX_FRAME_SIZE:
                    maxFrameSize = value;
                    maxFrameSizeSet = true;
                    break;
                case Setting.SETTINGS_MAX_HEADER_LIST_SIZE:
                    maxHeaderListSize = value;
                    maxHeaderListSizeSet = true;
                    break;
                case Setting.SETTINGS_ENABLE_CONNECT_PROTOCOL:
                    enableConnectProtocol = value;
                    enableConnectProtocolSet = true;
                    break;
                default:
                    settings.add(new Setting(id, value));
                    break;
            }
        }
        this.otherSettings = settings;
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
        ByteArray ret = null;
        if (headerTableSizeSet) {
            //noinspection ConstantConditions
            ret = append(ret, Setting.SETTINGS_HEADER_TABLE_SIZE, headerTableSize);
        }
        if (enablePushSet) {
            ret = append(ret, Setting.SETTINGS_ENABLE_PUSH, enablePush);
        }
        if (maxConcurrentStreamsSet) {
            ret = append(ret, Setting.SETTINGS_MAX_CONCURRENT_STREAMS, maxConcurrentStreams);
        }
        if (initialWindowSizeSet) {
            ret = append(ret, Setting.SETTINGS_INITIAL_WINDOW_SIZE, initialWindowSize);
        }
        if (maxFrameSizeSet) {
            ret = append(ret, Setting.SETTINGS_MAX_FRAME_SIZE, maxFrameSize);
        }
        if (maxHeaderListSizeSet) {
            ret = append(ret, Setting.SETTINGS_MAX_HEADER_LIST_SIZE, maxHeaderListSize);
        }
        if (enableConnectProtocolSet) {
            ret = append(ret, Setting.SETTINGS_ENABLE_CONNECT_PROTOCOL, enableConnectProtocol);
        }
        if (otherSettings != null) {
            for (Setting s : otherSettings) {
                ret = append(ret, s.identifier, s.value);
            }
        }
        if (ret == null)
            return ByteArray.allocate(0);
        return ret;
    }

    private ByteArray append(ByteArray ret, int id, int value) {
        ByteArray arr = ByteArray.allocate(6).int16(0, id).int32(2, value);
        if (ret == null) return arr;
        return ret.concat(arr);
    }

    @Override
    protected void toString(StringBuilder sb) {
        sb.append("ack=").append(ack);
        if (headerTableSizeSet) {
            sb.append(",").append("headerTableSize=").append(headerTableSize);
        }
        if (enablePushSet) {
            sb.append(",").append("enablePush=").append(enablePush);
        }
        if (maxConcurrentStreamsSet) {
            sb.append(",").append("maxConcurrentStreams=").append(maxConcurrentStreams);
        }
        if (initialWindowSizeSet) {
            sb.append(",").append("initialWindowSize=").append(initialWindowSize);
        }
        if (maxFrameSizeSet) {
            sb.append(",").append("maxFrameSize=").append(maxFrameSize);
        }
        if (maxHeaderListSizeSet) {
            sb.append(",").append("maxHeaderListSize=").append(maxHeaderListSize);
        }
        if (enableConnectProtocolSet) {
            sb.append(",").append("enableConnectProtocol=").append(enableConnectProtocol);
        }
        sb.append(",").append("otherSettings=").append(otherSettings);
    }
}

/*
 * HTTP/2:
 *
 * 6.5.  SETTINGS
 *
 *    The SETTINGS frame (type=0x4) conveys configuration parameters that
 *    affect how endpoints communicate, such as preferences and constraints
 *    on peer behavior.  The SETTINGS frame is also used to acknowledge the
 *    receipt of those parameters.  Individually, a SETTINGS parameter can
 *    also be referred to as a "setting".
 *
 *    SETTINGS parameters are not negotiated; they describe characteristics
 *    of the sending peer, which are used by the receiving peer.  Different
 *    values for the same parameter can be advertised by each peer.  For
 *    example, a client might set a high initial flow-control window,
 *    whereas a server might set a lower value to conserve resources.
 *
 *    A SETTINGS frame MUST be sent by both endpoints at the start of a
 *    connection and MAY be sent at any other time by either endpoint over
 *    the lifetime of the connection.  Implementations MUST support all of
 *    the parameters defined by this specification.
 *
 *    Each parameter in a SETTINGS frame replaces any existing value for
 *    that parameter.  Parameters are processed in the order in which they
 *    appear, and a receiver of a SETTINGS frame does not need to maintain
 *    any state other than the current value of its parameters.  Therefore,
 *    the value of a SETTINGS parameter is the last value that is seen by a
 *    receiver.
 *
 *    SETTINGS parameters are acknowledged by the receiving peer.  To
 *    enable this, the SETTINGS frame defines the following flag:
 *
 *    ACK (0x1):  When set, bit 0 indicates that this frame acknowledges
 *       receipt and application of the peer's SETTINGS frame.  When this
 *       bit is set, the payload of the SETTINGS frame MUST be empty.
 *       Receipt of a SETTINGS frame with the ACK flag set and a length
 *       field value other than 0 MUST be treated as a connection error
 *       (Section 5.4.1) of type FRAME_SIZE_ERROR.  For more information,
 *       see Section 6.5.3 ("Settings Synchronization").
 *
 *    SETTINGS frames always apply to a connection, never a single stream.
 *    The stream identifier for a SETTINGS frame MUST be zero (0x0).  If an
 *    endpoint receives a SETTINGS frame whose stream identifier field is
 *    anything other than 0x0, the endpoint MUST respond with a connection
 *    error (Section 5.4.1) of type PROTOCOL_ERROR.
 *
 *    The SETTINGS frame affects connection state.  A badly formed or
 *    incomplete SETTINGS frame MUST be treated as a connection error
 *    (Section 5.4.1) of type PROTOCOL_ERROR.
 *
 *    A SETTINGS frame with a length other than a multiple of 6 octets MUST
 *    be treated as a connection error (Section 5.4.1) of type
 *    FRAME_SIZE_ERROR.
 *
 * 6.5.1.  SETTINGS Format
 *
 *    The payload of a SETTINGS frame consists of zero or more parameters,
 *    each consisting of an unsigned 16-bit setting identifier and an
 *    unsigned 32-bit value.
 *
 *     +-------------------------------+
 *     |       Identifier (16)         |
 *     +-------------------------------+-------------------------------+
 *     |                        Value (32)                             |
 *     +---------------------------------------------------------------+
 *
 *                          Figure 10: Setting Format
 *
 * 6.5.2.  Defined SETTINGS Parameters
 *
 *    The following parameters are defined:
 *
 *    SETTINGS_HEADER_TABLE_SIZE (0x1):  Allows the sender to inform the
 *       remote endpoint of the maximum size of the header compression
 *       table used to decode header blocks, in octets.  The encoder can
 *       select any size equal to or less than this value by using
 *       signaling specific to the header compression format inside a
 *       header block (see [COMPRESSION]).  The initial value is 4,096
 *       octets.
 *
 *    SETTINGS_ENABLE_PUSH (0x2):  This setting can be used to disable
 *       server push (Section 8.2).  An endpoint MUST NOT send a
 *       PUSH_PROMISE frame if it receives this parameter set to a value of
 *       0.  An endpoint that has both set this parameter to 0 and had it
 *       acknowledged MUST treat the receipt of a PUSH_PROMISE frame as a
 *       connection error (Section 5.4.1) of type PROTOCOL_ERROR.
 *
 *       The initial value is 1, which indicates that server push is
 *       permitted.  Any value other than 0 or 1 MUST be treated as a
 *       connection error (Section 5.4.1) of type PROTOCOL_ERROR.
 *
 *    SETTINGS_MAX_CONCURRENT_STREAMS (0x3):  Indicates the maximum number
 *       of concurrent streams that the sender will allow.  This limit is
 *       directional: it applies to the number of streams that the sender
 *       permits the receiver to create.  Initially, there is no limit to
 *       this value.  It is recommended that this value be no smaller than
 *       100, so as to not unnecessarily limit parallelism.
 *
 *       A value of 0 for SETTINGS_MAX_CONCURRENT_STREAMS SHOULD NOT be
 *       treated as special by endpoints.  A zero value does prevent the
 *       creation of new streams; however, this can also happen for any
 *
 *       limit that is exhausted with active streams.  Servers SHOULD only
 *       set a zero value for short durations; if a server does not wish to
 *       accept requests, closing the connection is more appropriate.
 *
 *    SETTINGS_INITIAL_WINDOW_SIZE (0x4):  Indicates the sender's initial
 *       window size (in octets) for stream-level flow control.  The
 *       initial value is 2^16-1 (65,535) octets.
 *
 *       This setting affects the window size of all streams (see
 *       Section 6.9.2).
 *
 *       Values above the maximum flow-control window size of 2^31-1 MUST
 *       be treated as a connection error (Section 5.4.1) of type
 *       FLOW_CONTROL_ERROR.
 *
 *    SETTINGS_MAX_FRAME_SIZE (0x5):  Indicates the size of the largest
 *       frame payload that the sender is willing to receive, in octets.
 *
 *       The initial value is 2^14 (16,384) octets.  The value advertised
 *       by an endpoint MUST be between this initial value and the maximum
 *       allowed frame size (2^24-1 or 16,777,215 octets), inclusive.
 *       Values outside this range MUST be treated as a connection error
 *       (Section 5.4.1) of type PROTOCOL_ERROR.
 *
 *    SETTINGS_MAX_HEADER_LIST_SIZE (0x6):  This advisory setting informs a
 *       peer of the maximum size of header list that the sender is
 *       prepared to accept, in octets.  The value is based on the
 *       uncompressed size of header fields, including the length of the
 *       name and value in octets plus an overhead of 32 octets for each
 *       header field.
 *
 *       For any given request, a lower limit than what is advertised MAY
 *       be enforced.  The initial value of this setting is unlimited.
 *
 *    An endpoint that receives a SETTINGS frame with any unknown or
 *    unsupported identifier MUST ignore that setting.
 *
 * 6.5.3.  Settings Synchronization
 *
 *    Most values in SETTINGS benefit from or require an understanding of
 *    when the peer has received and applied the changed parameter values.
 *    In order to provide such synchronization timepoints, the recipient of
 *    a SETTINGS frame in which the ACK flag is not set MUST apply the
 *    updated parameters as soon as possible upon receipt.
 *
 *    The values in the SETTINGS frame MUST be processed in the order they
 *    appear, with no other frame processing between values.  Unsupported
 *    parameters MUST be ignored.  Once all values have been processed, the
 *
 *    recipient MUST immediately emit a SETTINGS frame with the ACK flag
 *    set.  Upon receiving a SETTINGS frame with the ACK flag set, the
 *    sender of the altered parameters can rely on the setting having been
 *    applied.
 *
 *    If the sender of a SETTINGS frame does not receive an acknowledgement
 *    within a reasonable amount of time, it MAY issue a connection error
 *    (Section 5.4.1) of type SETTINGS_TIMEOUT.
 */

/*
 * HTTP/3:
 *
 * 7.2.4.  SETTINGS
 *
 *    The SETTINGS frame (type=0x4) conveys configuration parameters that
 *    affect how endpoints communicate, such as preferences and constraints
 *    on peer behavior.  Individually, a SETTINGS parameter can also be
 *    referred to as a "setting"; the identifier and value of each setting
 *    parameter can be referred to as a "setting identifier" and a "setting
 *    value".
 *
 *    SETTINGS frames always apply to an entire HTTP/3 connection, never a
 *    single stream.  A SETTINGS frame MUST be sent as the first frame of
 *    each control stream (see Section 6.2.1) by each peer, and MUST NOT be
 *    sent subsequently.  If an endpoint receives a second SETTINGS frame
 *    on the control stream, the endpoint MUST respond with a connection
 *    error of type H3_FRAME_UNEXPECTED.
 *
 *    SETTINGS frames MUST NOT be sent on any stream other than the control
 *    stream.  If an endpoint receives a SETTINGS frame on a different
 *    stream, the endpoint MUST respond with a connection error of type
 *    H3_FRAME_UNEXPECTED.
 *
 *    SETTINGS parameters are not negotiated; they describe characteristics
 *    of the sending peer that can be used by the receiving peer.  However,
 *    a negotiation can be implied by the use of SETTINGS - each peer uses
 *    SETTINGS to advertise a set of supported values.  The definition of
 *    the setting would describe how each peer combines the two sets to
 *    conclude which choice will be used.  SETTINGS does not provide a
 *    mechanism to identify when the choice takes effect.
 *
 *    Different values for the same parameter can be advertised by each
 *    peer.  For example, a client might be willing to consume a very large
 *    response field section, while servers are more cautious about request
 *    size.
 *
 *    The same setting identifier MUST NOT occur more than once in the
 *    SETTINGS frame.  A receiver MAY treat the presence of duplicate
 *    setting identifiers as a connection error of type H3_SETTINGS_ERROR.
 *
 *    The payload of a SETTINGS frame consists of zero or more parameters.
 *    Each parameter consists of a setting identifier and a value, both
 *    encoded as QUIC variable-length integers.
 *
 *    Setting {
 *      Identifier (i),
 *      Value (i),
 *    }
 *
 *    SETTINGS Frame {
 *      Type (i) = 0x4,
 *      Length (i),
 *      Setting (..) ...,
 *    }
 *
 *                           Figure 7: SETTINGS Frame
 *
 *    An implementation MUST ignore the contents for any SETTINGS
 *    identifier it does not understand.
 *
 * 7.2.4.1.  Defined SETTINGS Parameters
 *
 *    The following settings are defined in HTTP/3:
 *
 *    SETTINGS_MAX_FIELD_SECTION_SIZE (0x6):  The default value is
 *       unlimited.  See Section 4.1.1.3 for usage.
 *
 *    Setting identifiers of the format "0x1f * N + 0x21" for non-negative
 *    integer values of N are reserved to exercise the requirement that
 *    unknown identifiers be ignored.  Such settings have no defined
 *    meaning.  Endpoints SHOULD include at least one such setting in their
 *    SETTINGS frame.  Endpoints MUST NOT consider such settings to have
 *    any meaning upon receipt.
 *
 *    Because the setting has no defined meaning, the value of the setting
 *    can be any value the implementation selects.
 *
 *    Setting identifiers which were used in HTTP/2 where there is no
 *    corresponding HTTP/3 setting have also been reserved
 *    (Section 11.2.2).  These settings MUST NOT be sent, and their receipt
 *    MUST be treated as a connection error of type H3_SETTINGS_ERROR.
 *
 *    Additional settings can be defined by extensions to HTTP/3; see
 *    Section 9 for more details.
 *
 * 7.2.4.2.  Initialization
 *
 *    An HTTP implementation MUST NOT send frames or requests that would be
 *    invalid based on its current understanding of the peer's settings.
 *
 *    All settings begin at an initial value.  Each endpoint SHOULD use
 *    these initial values to send messages before the peer's SETTINGS
 *    frame has arrived, as packets carrying the settings can be lost or
 *    delayed.  When the SETTINGS frame arrives, any settings are changed
 *    to their new values.
 *
 *    This removes the need to wait for the SETTINGS frame before sending
 *    messages.  Endpoints MUST NOT require any data to be received from
 *    the peer prior to sending the SETTINGS frame; settings MUST be sent
 *    as soon as the transport is ready to send data.
 *
 *    For servers, the initial value of each client setting is the default
 *    value.
 *
 *    For clients using a 1-RTT QUIC connection, the initial value of each
 *    server setting is the default value.  1-RTT keys will always become
 *    available prior to the packet containing SETTINGS being processed by
 *    QUIC, even if the server sends SETTINGS immediately.  Clients SHOULD
 *    NOT wait indefinitely for SETTINGS to arrive before sending requests,
 *    but SHOULD process received datagrams in order to increase the
 *    likelihood of processing SETTINGS before sending the first request.
 *
 *    When a 0-RTT QUIC connection is being used, the initial value of each
 *    server setting is the value used in the previous session.  Clients
 *    SHOULD store the settings the server provided in the HTTP/3
 *    connection where resumption information was provided, but MAY opt not
 *    to store settings in certain cases (e.g., if the session ticket is
 *    received before the SETTINGS frame).  A client MUST comply with
 *    stored settings - or default values, if no values are stored - when
 *    attempting 0-RTT.  Once a server has provided new settings, clients
 *    MUST comply with those values.
 *
 *    A server can remember the settings that it advertised, or store an
 *    integrity-protected copy of the values in the ticket and recover the
 *    information when accepting 0-RTT data.  A server uses the HTTP/3
 *    settings values in determining whether to accept 0-RTT data.  If the
 *    server cannot determine that the settings remembered by a client are
 *    compatible with its current settings, it MUST NOT accept 0-RTT data.
 *    Remembered settings are compatible if a client complying with those
 *    settings would not violate the server's current settings.
 *
 *    A server MAY accept 0-RTT and subsequently provide different settings
 *    in its SETTINGS frame.  If 0-RTT data is accepted by the server, its
 *    SETTINGS frame MUST NOT reduce any limits or alter any values that
 *    might be violated by the client with its 0-RTT data.  The server MUST
 *    include all settings that differ from their default values.  If a
 *    server accepts 0-RTT but then sends settings that are not compatible
 *    with the previously specified settings, this MUST be treated as a
 *    connection error of type H3_SETTINGS_ERROR.  If a server accepts
 *    0-RTT but then sends a SETTINGS frame that omits a setting value that
 *    the client understands (apart from reserved setting identifiers) that
 *    was previously specified to have a non-default value, this MUST be
 *    treated as a connection error of type H3_SETTINGS_ERROR.
 */
