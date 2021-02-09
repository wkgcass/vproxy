package vproxybase.processor.httpbin.frame;

import vproxybase.processor.ExceptionWithoutStackTrace;
import vproxybase.processor.httpbin.BinaryHttpSubContext;
import vproxybase.processor.httpbin.HttpFrame;
import vproxybase.processor.httpbin.HttpFrameType;
import vproxybase.util.ByteArray;

public class GoAwayFrame extends HttpFrame {
    public int lastStreamId;
    public int errorCode;
    public ByteArray additionalDebugData;

    public GoAwayFrame() {
        super(HttpFrameType.GOAWAY);
    }

    @Override
    public void setFlags(byte flags) { // none
    }

    @Override
    public void setPayload(BinaryHttpSubContext subCtx, ByteArray payload) throws Exception {
        lastStreamId = payload.int32(0);
        if (lastStreamId < 0) throw new ExceptionWithoutStackTrace("invalid lastStreamId: " + lastStreamId);
        errorCode = payload.int32(4);
        additionalDebugData = payload.sub(4 + 4, length - 4 - 4);
    }

    @Override
    public byte serializeFlags() {
        return 0;
    }

    @Override
    public ByteArray serializeH2Payload(BinaryHttpSubContext subCtx) {
        return ByteArray.allocate(4 + 4).int32(0, lastStreamId).int32(4, errorCode).concat(additionalDebugData);
    }

    @Override
    protected void toString(StringBuilder sb) {
        sb.append("lastStreamId=").append(lastStreamId)
            .append(",").append("errorCode=").append(errorCode)
            .append(",").append("additionalDebugData=").append(additionalDebugData);
    }
}

/*
 * HTTP/2:
 *
 * 6.8.  GOAWAY
 *
 *    The GOAWAY frame (type=0x7) is used to initiate shutdown of a
 *    connection or to signal serious error conditions.  GOAWAY allows an
 *    endpoint to gracefully stop accepting new streams while still
 *    finishing processing of previously established streams.  This enables
 *    administrative actions, like server maintenance.
 *
 *    There is an inherent race condition between an endpoint starting new
 *    streams and the remote sending a GOAWAY frame.  To deal with this
 *    case, the GOAWAY contains the stream identifier of the last peer-
 *    initiated stream that was or might be processed on the sending
 *    endpoint in this connection.  For instance, if the server sends a
 *    GOAWAY frame, the identified stream is the highest-numbered stream
 *    initiated by the client.
 *
 *    Once sent, the sender will ignore frames sent on streams initiated by
 *    the receiver if the stream has an identifier higher than the included
 *    last stream identifier.  Receivers of a GOAWAY frame MUST NOT open
 *    additional streams on the connection, although a new connection can
 *    be established for new streams.
 *
 *    If the receiver of the GOAWAY has sent data on streams with a higher
 *    stream identifier than what is indicated in the GOAWAY frame, those
 *    streams are not or will not be processed.  The receiver of the GOAWAY
 *    frame can treat the streams as though they had never been created at
 *    all, thereby allowing those streams to be retried later on a new
 *    connection.
 *
 *    Endpoints SHOULD always send a GOAWAY frame before closing a
 *    connection so that the remote peer can know whether a stream has been
 *    partially processed or not.  For example, if an HTTP client sends a
 *    POST at the same time that a server closes a connection, the client
 *    cannot know if the server started to process that POST request if the
 *    server does not send a GOAWAY frame to indicate what streams it might
 *    have acted on.
 *
 *    An endpoint might choose to close a connection without sending a
 *    GOAWAY for misbehaving peers.
 *
 *  A GOAWAY frame might not immediately precede closing of the
 *    connection; a receiver of a GOAWAY that has no more use for the
 *    connection SHOULD still send a GOAWAY frame before terminating the
 *    connection.
 *
 *     +-+-------------------------------------------------------------+
 *     |R|                  Last-Stream-ID (31)                        |
 *     +-+-------------------------------------------------------------+
 *     |                      Error Code (32)                          |
 *     +---------------------------------------------------------------+
 *     |                  Additional Debug Data (*)                    |
 *     +---------------------------------------------------------------+
 *
 *                      Figure 13: GOAWAY Payload Format
 *
 *    The GOAWAY frame does not define any flags.
 *
 *    The GOAWAY frame applies to the connection, not a specific stream.
 *    An endpoint MUST treat a GOAWAY frame with a stream identifier other
 *    than 0x0 as a connection error (Section 5.4.1) of type
 *    PROTOCOL_ERROR.
 *
 *    The last stream identifier in the GOAWAY frame contains the highest-
 *    numbered stream identifier for which the sender of the GOAWAY frame
 *    might have taken some action on or might yet take action on.  All
 *    streams up to and including the identified stream might have been
 *    processed in some way.  The last stream identifier can be set to 0 if
 *    no streams were processed.
 *
 *       Note: In this context, "processed" means that some data from the
 *       stream was passed to some higher layer of software that might have
 *       taken some action as a result.
 *
 *    If a connection terminates without a GOAWAY frame, the last stream
 *    identifier is effectively the highest possible stream identifier.
 *
 *    On streams with lower- or equal-numbered identifiers that were not
 *    closed completely prior to the connection being closed, reattempting
 *    requests, transactions, or any protocol activity is not possible,
 *    with the exception of idempotent actions like HTTP GET, PUT, or
 *    DELETE.  Any protocol activity that uses higher-numbered streams can
 *    be safely retried using a new connection.
 *
 *    Activity on streams numbered lower or equal to the last stream
 *    identifier might still complete successfully.  The sender of a GOAWAY
 *    frame might gracefully shut down a connection by sending a GOAWAY
 *    frame, maintaining the connection in an "open" state until all in-
 *    progress streams complete.
 *
 *    An endpoint MAY send multiple GOAWAY frames if circumstances change.
 *    For instance, an endpoint that sends GOAWAY with NO_ERROR during
 *    graceful shutdown could subsequently encounter a condition that
 *    requires immediate termination of the connection.  The last stream
 *    identifier from the last GOAWAY frame received indicates which
 *    streams could have been acted upon.  Endpoints MUST NOT increase the
 *    value they send in the last stream identifier, since the peers might
 *    already have retried unprocessed requests on another connection.
 *
 *    A client that is unable to retry requests loses all requests that are
 *    in flight when the server closes the connection.  This is especially
 *    true for intermediaries that might not be serving clients using
 *    HTTP/2.  A server that is attempting to gracefully shut down a
 *    connection SHOULD send an initial GOAWAY frame with the last stream
 *    identifier set to 2^31-1 and a NO_ERROR code.  This signals to the
 *    client that a shutdown is imminent and that initiating further
 *    requests is prohibited.  After allowing time for any in-flight stream
 *    creation (at least one round-trip time), the server can send another
 *    GOAWAY frame with an updated last stream identifier.  This ensures
 *    that a connection can be cleanly shut down without losing requests.
 *
 *    After sending a GOAWAY frame, the sender can discard frames for
 *    streams initiated by the receiver with identifiers higher than the
 *    identified last stream.  However, any frames that alter connection
 *    state cannot be completely ignored.  For instance, HEADERS,
 *    PUSH_PROMISE, and CONTINUATION frames MUST be minimally processed to
 *    ensure the state maintained for header compression is consistent (see
 *    Section 4.3); similarly, DATA frames MUST be counted toward the
 *    connection flow-control window.  Failure to process these frames can
 *    cause flow control or header compression state to become
 *    unsynchronized.
 *
 *    The GOAWAY frame also contains a 32-bit error code (Section 7) that
 *    contains the reason for closing the connection.
 *
 *    Endpoints MAY append opaque data to the payload of any GOAWAY frame.
 *    Additional debug data is intended for diagnostic purposes only and
 *    carries no semantic value.  Debug information could contain security-
 *    or privacy-sensitive data.  Logged or otherwise persistently stored
 *    debug data MUST have adequate safeguards to prevent unauthorized
 *    access.
 */

/*
 * HTTP/3:
 *
 * 7.2.6.  GOAWAY
 *
 *    The GOAWAY frame (type=0x7) is used to initiate graceful shutdown of
 *    an HTTP/3 connection by either endpoint.  GOAWAY allows an endpoint
 *    to stop accepting new requests or pushes while still finishing
 *    processing of previously received requests and pushes.  This enables
 *    administrative actions, like server maintenance.  GOAWAY by itself
 *    does not close a connection.
 *
 *    GOAWAY Frame {
 *      Type (i) = 0x7,
 *      Length (i),
 *      Stream ID/Push ID (..),
 *    }
 *
 *                            Figure 9: GOAWAY Frame
 *
 *    The GOAWAY frame is always sent on the control stream.  In the server
 *    to client direction, it carries a QUIC Stream ID for a client-
 *    initiated bidirectional stream encoded as a variable-length integer.
 *    A client MUST treat receipt of a GOAWAY frame containing a Stream ID
 *    of any other type as a connection error of type H3_ID_ERROR.
 *
 *    In the client to server direction, the GOAWAY frame carries a Push ID
 *    encoded as a variable-length integer.
 *
 *    The GOAWAY frame applies to the entire connection, not a specific
 *    stream.  A client MUST treat a GOAWAY frame on a stream other than
 *    the control stream as a connection error of type H3_FRAME_UNEXPECTED;
 *    see Section 8.
 *
 *    See Section 5.2 for more information on the use of the GOAWAY frame.
 *
 * 5.2.  Connection Shutdown
 *
 *    Even when a connection is not idle, either endpoint can decide to
 *    stop using the connection and initiate a graceful connection close.
 *    Endpoints initiate the graceful shutdown of an HTTP/3 connection by
 *    sending a GOAWAY frame (Section 7.2.6).  The GOAWAY frame contains an
 *    identifier that indicates to the receiver the range of requests or
 *    pushes that were or might be processed in this connection.  The
 *    server sends a client-initiated bidirectional Stream ID; the client
 *    sends a Push ID (Section 4.4).  Requests or pushes with the indicated
 *    identifier or greater are rejected (Section 4.1.2) by the sender of
 *    the GOAWAY.  This identifier MAY be zero if no requests or pushes
 *    were processed.
 *
 *    The information in the GOAWAY frame enables a client and server to
 *    agree on which requests or pushes were accepted prior to the shutdown
 *    of the HTTP/3 connection.  Upon sending a GOAWAY frame, the endpoint
 *    SHOULD explicitly cancel (see Section 4.1.2 and Section 7.2.3) any
 *    requests or pushes that have identifiers greater than or equal to
 *    that indicated, in order to clean up transport state for the affected
 *    streams.  The endpoint SHOULD continue to do so as more requests or
 *    pushes arrive.
 *
 *    Endpoints MUST NOT initiate new requests or promise new pushes on the
 *    connection after receipt of a GOAWAY frame from the peer.  Clients
 *    MAY establish a new connection to send additional requests.
 *
 *    Some requests or pushes might already be in transit:
 *
 *    *  Upon receipt of a GOAWAY frame, if the client has already sent
 *       requests with a Stream ID greater than or equal to the identifier
 *       contained in the GOAWAY frame, those requests will not be
 *       processed.  Clients can safely retry unprocessed requests on a
 *       different HTTP connection.  A client that is unable to retry
 *       requests loses all requests that are in flight when the server
 *       closes the connection.
 *
 *       Requests on Stream IDs less than the Stream ID in a GOAWAY frame
 *       from the server might have been processed; their status cannot be
 *       known until a response is received, the stream is reset
 *       individually, another GOAWAY is received, or the connection
 *       terminates.
 *
 *       Servers MAY reject individual requests on streams below the
 *       indicated ID if these requests were not processed.
 *
 *    *  If a server receives a GOAWAY frame after having promised pushes
 *       with a Push ID greater than or equal to the identifier contained
 *       in the GOAWAY frame, those pushes will not be accepted.
 *
 *    Servers SHOULD send a GOAWAY frame when the closing of a connection
 *    is known in advance, even if the advance notice is small, so that the
 *    remote peer can know whether a request has been partially processed
 *    or not.  For example, if an HTTP client sends a POST at the same time
 *    that a server closes a QUIC connection, the client cannot know if the
 *    server started to process that POST request if the server does not
 *    send a GOAWAY frame to indicate what streams it might have acted on.
 *
 *    An endpoint MAY send multiple GOAWAY frames indicating different
 *    identifiers, but the identifier in each frame MUST NOT be greater
 *    than the identifier in any previous frame, since clients might
 *    already have retried unprocessed requests on another HTTP connection.
 *    Receiving a GOAWAY containing a larger identifier than previously
 *    received MUST be treated as a connection error of type H3_ID_ERROR;
 *    see Section 8.
 *
 *    An endpoint that is attempting to gracefully shut down a connection
 *    can send a GOAWAY frame with a value set to the maximum possible
 *    value (2^62-4 for servers, 2^62-1 for clients).  This ensures that
 *    the peer stops creating new requests or pushes.  After allowing time
 *    for any in-flight requests or pushes to arrive, the endpoint can send
 *    another GOAWAY frame indicating which requests or pushes it might
 *    accept before the end of the connection.  This ensures that a
 *    connection can be cleanly shut down without losing requests.
 *
 *    A client has more flexibility in the value it chooses for the Push ID
 *    in a GOAWAY that it sends.  A value of 2^62 - 1 indicates that the
 *    server can continue fulfilling pushes that have already been
 *    promised.  A smaller value indicates the client will reject pushes
 *    with Push IDs greater than or equal to this value.  Like the server,
 *    the client MAY send subsequent GOAWAY frames so long as the specified
 *    Push ID is no greater than any previously sent value.
 *
 *    Even when a GOAWAY indicates that a given request or push will not be
 *    processed or accepted upon receipt, the underlying transport
 *    resources still exist.  The endpoint that initiated these requests
 *    can cancel them to clean up transport state.
 *
 *    Once all accepted requests and pushes have been processed, the
 *    endpoint can permit the connection to become idle, or MAY initiate an
 *    immediate closure of the connection.  An endpoint that completes a
 *    graceful shutdown SHOULD use the H3_NO_ERROR error code when closing
 *    the connection.
 *
 *    If a client has consumed all available bidirectional stream IDs with
 *    requests, the server need not send a GOAWAY frame, since the client
 *    is unable to make further requests.
 */
