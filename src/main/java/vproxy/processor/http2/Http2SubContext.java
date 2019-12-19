package vproxy.processor.http2;

import vproxy.processor.OOSubContext;
import vproxy.processor.Processor;
import vproxy.util.ByteArray;
import vproxy.util.Logger;

import java.util.HashMap;
import java.util.Map;

// the impl corresponds to rfc7540
/*
 * A simple explanation about how it works.
 *
 * Can do:
 * 1. dispatch one stream to one backend (full duplexing)
 * 2. redirect push_promise from any backend to the client
 * 3. standard http2 protocol for both frontend and backend
 * 4. raw tcp, handshake with prior knowledge
 * 5. any application level interaction between a client and a server
 * 6. handshake with tls alpn
 *
 * Cannot do (limitations):
 * 1. stream dependency and priority
 * 2. header dynamic table for backend (however, dynamic table for frontend is supported)
 * 3. exchange settings (except the first exchange, which is forced according to rfc)
 * 4. http clear text upgrade
 * These limitations will not affect how user uses http/2.
 * The not-supported-frames will be modified, dropped, or faked
 * and will not affect the application level code.
 * And NOTHING for the client/server is required to be changed because of these limitations.
 *
 * How vproxy handles the limitations:
 * 1. stream dependency and priority
 * The stream dependency and priority is not supported, but
 * both client and server can send HEADERS frames with priority, or PRIORITY frames.
 * The dependency and weight part will be removed from HEADERS frames, and
 * the PRIORITY frames will be dropped. In this way, both client and server
 * can use implementation with stream dependency and priority.
 *
 * 2. header dynamic table for backend
 * We only support to decompress headers from frontend connection, and
 * the header (de)compression for backend connection is not supported.
 * So vproxy will attach a setting to the first SETTINGS frame
 * which must be sent by both client and server.
 * SETTINGS_HEADER_TABLE_SIZE would be set to 0 for backend connections.
 * In this way, the backend will not compress the headers, and can work well with vproxy.
 *
 * 3. exchange settings
 * The first exchange is forced according to rfc, so it's supported.
 * And vproxy will drop SETTINGS frames after the first exchange.
 *
 * 4. http clear text upgrade
 * The user can use "with-prior-knowledge" way to connect to vproxy.
 * e.g. for curl, add the "--http2 --http2-prior-knowledge" flags.
 * ---- for vertx, call httpClientOptions.setHttp2ClearTextUpgrade(false)
 *
 * How it works:
 * Assume the client sends a request, the server responses the request, and makes a push-promise.
 *
 * 1. Client sends preface and SETTINGS frame (maybe along with the first request HEADERS frame,
 * -- but we discuss it later).
 * 2. Vproxy parses the preface and SETTINGS frame, and add two settings to the SETTINGS frame:
 * -- SETTINGS_HEADER_TABLE_SIZE=0
 * -- SETTINGS_INITIAL_WINDOW_SIZE=2^30-1
 * -- then proxy the whole bunch of data to the first selected backend A.
 * -- Also, at the same time, vproxy would record the preface and the SETTINGS frame (after
 * -- modified), let's call it the "clientHandshake".
 * 3. The backend A returns a SETTINGS frame, and an "ack-SETTINGS" frame.
 * 4. Vproxy parses the first SETTINGS frame from backend A, and add two settings to the frame:
 * -- SETTINGS_HEADER_TABLE_SIZE=4096
 * -- SETTINGS_INITIAL_WINDOW_SIZE=2^30-1
 * -- then proxy the whole bunch of data (including the "ack-SETTINGS" frame) to the client.
 * 5. The client sends an "ack-SETTINGS" frame, and vproxy proxies it to the backend A.
 * -- Now, the handshake part is done, and no SETTINGS frame would be allowed between frontend and backend.
 * 6. The client would have sent a HEADERS frame, so vproxy would try to proxy the frame.
 * -- Vproxy selects a new backend B (might be the same as A if there's only one available backend)
 * -- and sends the previously recorded "clientHandshake" data to the backend B.
 * -- Then, vproxy would parse the frontend headers frame, decompress and encode it, and sent to B.
 * 7. The backend B returns a SETTINGS frame and an "ack-SETTINGS" frame, vproxy will send an
 * -- "ack-SETTINGS" frame to the backend B when receiving the first SETTINGS frame from backend B,
 * -- and drop these received SETTINGS frames.
 * 8. The backend B responds with the following frames: "PUSH_PROMISE", "HEADERS", "DATA", where the
 * -- HEADERS frame is the response headers, and data frame is the response data. The stream id in
 * -- these frames are the request stream id, so vproxy simply proxies the HEADERS and DATA frames,
 * -- but for the PUSH_PROMISE frame, vproxy would parse its payload, and generate a valid
 * -- "server stream id", to replace the PROMISED_STREAM_ID, then proxies the data to client.
 * -- The frames are proxied in an order the same as they are received.
 * 9. The backend B will then send a "HEADERS" frame and a "DATA" frame, both with the stream id
 * -- which recorded in the original PUSH_PROMISE frame. Vproxy modifies the stream id to the
 * -- generated one, and then proxies the frames to frontend.
 * 10. Done.
 *
 * besides, vproxy will handle window size of both the client and the server
 * the WINDOW_UPDATE from client and server will be ignored,
 * vproxy will make its own WINDOW_UPDATE frames
 *
 * You may check the Http2Proxy poc program for more info. Change the buffer sizes to a bigger one,
 * then you can use Wireshark to view the netflow (otherwise the segments would be
 * separated into very small pieces, which would be too small for Wireshark to decode).
 */
/*
 * preface magic:
 * 0x505249202a20485454502f322e300d0a0d0a534d0d0a0d0a
 *
 *
 * frame: change the stream id if needed
 *  +-----------------------------------------------+
 *  |                 Length (24)                   |
 *  +---------------+---------------+---------------+
 *  |   Type (8)    |   Flags (8)   |
 *  +-+-------------+---------------+-------------------------------+
 *  |R|                 Stream Identifier (31)                      |
 *  +=+=============================================================+
 *  |                   Frame Payload (0...)                      ...
 *  +---------------------------------------------------------------+
 * HEADERS: remove priority for both frontend and backend,
 *          and handle hpack for frontend
 *  +---------------+
 *  |Pad Length? (8)|
 *  +-+-------------+-----------------------------------------------+
 *  |E|                 Stream Dependency? (31)                     |
 *  +-+-------------+-----------------------------------------------+
 *  |  Weight? (8)  |
 *  +-+-------------+-----------------------------------------------+
 *  |                   Header Block Fragment (*)                 ...
 *  +---------------------------------------------------------------+
 *  |                           Padding (*)                       ...
 *  +---------------------------------------------------------------+
 * CONTINUATION: proxy for backend, handle hpack for frontend
 *  +---------------------------------------------------------------+
 *  |                   Header Block Fragment (*)                 ...
 *  +---------------------------------------------------------------+
 * SETTINGS: proxy and record for the handshake, then simply ignore
 * --------- NOTE: SETTINGS_HEADER_TABLE_SIZE will be set to 0
 *  +-------------------------------+
 *  |       Identifier (16)         |
 *  +-------------------------------+-------------------------------+
 *  |                        Value (32)                             |
 *  +---------------------------------------------------------------+
 * PUSH_PROMISE: change stream id if it's to be initiated by backend
 *  +---------------+
 *  |Pad Length? (8)|
 *  +-+-------------+-----------------------------------------------+
 *  |R|                  Promised Stream ID (31)                    |
 *  +-+-----------------------------+-------------------------------+
 *  |                   Header Block Fragment (*)                 ...
 *  +---------------------------------------------------------------+
 *  |                           Padding (*)                       ...
 *  +---------------------------------------------------------------+
 * WINDOW_UPDATE: vproxy makes its own WINDOW_UPDATE frames
 *  +-+-------------------------------------------------------------+
 *  |R|              Window Size Increment (31)                     |
 *  +-+-------------------------------------------------------------+
 *
 * DATA: proxy, and record the window size
 * PRIORITY: ignore
 * RST_STREAM: proxy
 * PING: proxy
 * GOAWAY: proxy
 * WINDOW_UPDATE: ignore, and we send our own window_update frames
 *   (the rfc says: Intermediaries do not forward WINDOW_UPDATE frames between dependent connections.)
 */

public class Http2SubContext extends OOSubContext<Http2Context> {
    public static final ByteArray SEQ_PREFACE_MAGIC = ByteArray.from("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes());
    private static final ByteArray SEQ_SETTINGS_ACK = ByteArray.from(0, 0, 0, 4, 1, 0, 0, 0, 0);

    public static final int LEN_FRAME_HEAD = 9; // 72
    private static final int LEN_PADDING = 1; // 8
    private static final int LEN_E_STREAMDEPENDENCY_WEIGHT = 5; // 1 + 31 + 8
    private static final int LEN_R_PROMISED_STREAM_ID = 4; // 1 + 31
    private static final int LEN_SETTING = 6; // 2 + 4

    // we do not set 2^31 - 1, in case the flow control method went wrong and exceeds 2^31-1
    // will send window increase of size SIZE_CONNECTION_WINDOW - SIZE_DEFAULT_CONNECTION_WINDOW
    private static final int SIZE_DEFAULT_CONNECTION_WINDOW = 65536;
    private static final int SIZE_CONNECTION_WINDOW = (int) (Math.pow(2, 30) - 1);
    private static final int SIZE_STREAM_WINDOW = (int) (Math.pow(2, 30) - 1);
    private static final int INCR_WINDOW_THRESHOLD = SIZE_CONNECTION_WINDOW - (int) Math.pow(2, 26); // update every 64MBytes
    // and, we do not update the stream window size because 2^30-1 would be enough

    static final int SIZE_DEFAULT_HEADER_TABLE_SIZE;

    private static final byte VALUE_SETTINGS_HEADER_TABLE_SIZE = 0x1; // will be set to 0
    private static final byte VALUE_SETTINGS_INITIAL_WINDOW_SIZE = 0x4; // will be set to SIZE_STREAM_WINDOW

    static {
        // this is only for debug purpose
        {
            int headerTableSize = 4096;
            String tableSizeStr = System.getProperty("HTTP2_DEFAULT_HEADER_TABLE_SIZE");
            if (tableSizeStr != null) {
                headerTableSize = Integer.parseInt(tableSizeStr);
                Logger.alert("HTTP2_DEFAULT_HEADER_TABLE_SIZE is set to " + headerTableSize);
            }
            if (headerTableSize < 0)
                throw new RuntimeException("-DHTTP2_DEFAULT_HEADER_TABLE_SIZE value < 0");
            SIZE_DEFAULT_HEADER_TABLE_SIZE = headerTableSize;
        }
    }

    private Http2Frame frame;
    private Http2Frame lastFrame;
    // The frame field holds the current processing frame, when the frame head part comes, the frame object will generate
    // when the whole payload of the frame is processed, the frame field will be set to null
    // The lastFrame field holds the last frame, it's used to retrieve the streamId
    // some frame process (such as the settings frame) will consume all payload from the frame, and the frame field
    // will be set to null. In this case, the streamId could not be retrieved. So we store the lastFrame when needed,
    // and set this field to null after streamId is retrieved.

    private int windowSize = SIZE_DEFAULT_CONNECTION_WINDOW;

    private int state;
    /*
     * -1 -> ignore the frame -> 1
     * 0 -> frontend handshake -> 1
     * 1 -> (idle) reading stream and length -> 2/3/4/-1
     * 2 -> proxy -> 1
     * 3 -> (headers) remove stream dependency in headers -> 5
     * 4 -> settings: we should manipulate and record the first settings frame, so set to a special state -> 1
     * 5 -> (headers) the header part after stream dependency -> 1
     * 6 -> (push-promise) the first few bits of a push-promise frame -> 7
     * 7 -> (push-promise) proxy the bits after first few bits -> 1
     * 8 -> (hpack) content of headers or continuation for hpack to process -> 1
     */

    private Map<Integer, Integer> streamIdBack2Front = new HashMap<>();
    private Integer backendIdForStreamToRemove = null;

    // the ack of settings frame
    private ByteArray syntheticAck = null;
    private boolean syntheticAckFlag;
    // When receiving the first settings frame from backend and this field is true,
    // this field will be set to false and syntheticAck will be set
    // also, this field will be set to false for the frontend connection and the first backend connection
    // In this way, syntheticAck will only be sent when doing the backend handshaking except the first one
    //
    // The first handshaking is fully proxied between frontend and backend,
    // so no need to make synthetic ack for the first backend connection
    //
    // this field will be used by handleSettingsFramePart()

    // record the first settings frame head and send all when the whole frame is received
    private ByteArray theSettingsFrameHead;

    // set this field to true to respond data to the processor lib, otherwise data would be cached
    // only accessed when it's a frontend sub context
    // if it's a backend sub context, the field will be set but never used
    // when this field is set to true, it will not be set to false again
    boolean hostHeaderRetrieved;

    public Http2SubContext(Http2Context ctx, int connId) {
        super(ctx, connId);

        if (connId == 0) {
            state = 0;
            syntheticAckFlag = false; // this field will not be used if it's frontend connection
            hostHeaderRetrieved = false;
        } else {
            state = 1;
            syntheticAckFlag = !ctx.backendHandshaking; // this field will only be used when the first backend handshaking is done
            hostHeaderRetrieved = true; // backend can always respond data to the frontend
        }
    }

    private static ByteArray utilBuildWindowUpdate(int len) {
        ByteArray SEQ_WINDOW_UPDATE = ByteArray.from(new byte[]{
            0, 0, 4, // length
            8, // type
            0, // flags
            0, 0, 0, 0, // stream id
            0, 0, 0, 0 // payload
        });
        SEQ_WINDOW_UPDATE.int32(9, len);
        return SEQ_WINDOW_UPDATE;
    }

    @Override
    public Processor.Mode mode() {
        switch (state) {
            case 0:
            case 1:
            case 3:
            case 4:
            case 6:
            case 8:
            case -1:
                return Processor.Mode.handle;
            case 2:
            case 5:
            case 7:
                return Processor.Mode.proxy;
            default:
                throw new Error("should not reach here");
        }
    }

    @Override
    public boolean expectNewFrame() {
        // when this method is called, it means the frame is sent to the frontend
        // and all types of frames are ended with a proxy (otherwise it will not be sent to frontend at all)
        // so we return true when it's in initial states, or proxy states that has data length 0 to proxy

        // initial states
        if (state == 0 || state == 1)
            return true;

        // proxy states
        Processor.Mode mode = mode();
        if (mode == Processor.Mode.proxy) {
            return len() == 0;
        }

        return false;
    }

    @Override
    public int len() {
        switch (state) {
            case 0:
                return SEQ_PREFACE_MAGIC.length() + LEN_FRAME_HEAD;
            case 1:
                return LEN_FRAME_HEAD;
            case 3:
                // only reach here if the priority flag is set (but it's already removed by the handle method)
                return (frame.padded ? LEN_PADDING : 0) + LEN_E_STREAMDEPENDENCY_WEIGHT;
            case 4:
                return frame.length;
            case 5:
                // only reach here if the priority flag is set (but it's already removed by the handle method)
                // the frame.length already subtracted the stream dependency weight
                // so only minus PADDING here would be ok
                return frame.length - (frame.padded ? LEN_PADDING : 0);
            case 6:
                return (frame.padded ? LEN_PADDING : 0) + LEN_R_PROMISED_STREAM_ID;
            case 7:
                return frame.length - (frame.padded ? LEN_PADDING : 0) - LEN_R_PROMISED_STREAM_ID;
            case 8:
                //noinspection DuplicateBranchesInSwitch
                return frame.length;
            case -1:
            case 2:
                //noinspection DuplicateBranchesInSwitch
                return frame.length; // the frame itself
            default:
                throw new Error("should not reach here");
        }
    }

    private ByteArray storedBytes = null;

    @Override
    public ByteArray feed(ByteArray data) throws Exception {
        boolean frontendSettingsSent = ctx.frontendSettingsSent; // this value may be changed in the handling process, so we need to cache it before handling
        ByteArray arr = _feed(data);
        if (hostHeaderRetrieved || !frontendSettingsSent) { // first settings frame should pass freely
            if (storedBytes == null) {
                return arr;
            } else if (arr == null) {
                ByteArray stored = storedBytes;
                storedBytes = null;
                return stored;
            } else {
                ByteArray stored = storedBytes;
                storedBytes = null;
                return stored.concat(arr);
            }
        } else {
            if (storedBytes == null) {
                storedBytes = arr;
            } else if (arr != null) {
                storedBytes = storedBytes.concat(data);
            }
            return null;
        }
    }

    public ByteArray _feed(ByteArray data) throws Exception {
        switch (state) {
            case 0:
                assert data.length() == SEQ_PREFACE_MAGIC.length() + LEN_FRAME_HEAD;
                // check the preface
                ByteArray prefacePart = data.sub(0, SEQ_PREFACE_MAGIC.length());
                if (!prefacePart.equals(SEQ_PREFACE_MAGIC)) {
                    throw new Exception("the preface magic is wrong! " + data);
                }
                ByteArray framePart = data.sub(SEQ_PREFACE_MAGIC.length(), LEN_FRAME_HEAD);
                parseFrame(framePart);
                // ignore the result from handleFrame.
                // the first frame is always settings frame and should set state to 4
                if (frame.type != Http2Frame.Type.SETTINGS) {
                    throw new Exception("invalid http2 protocol, no settings after preface. current frame is " + frame);
                }
                handleSettingsFramePart(framePart);
                assert state == 4;
                return SEQ_PREFACE_MAGIC; // only send preface for now, ignore the frame head
            case 1:
                parseFrame(data);
                return handleFrame(data);
            case 3:
                state = 5; // set to proxy anything left in the header
                if (frame.padded) {
                    assert Logger.lowLevelDebug("the frame is padded, the padding length is " + data.get(0));
                    return data.sub(0, 1); // only return the PADDING part
                } else {
                    assert Logger.lowLevelDebug("the frame is not padded, so ignore this part");
                    return null; // not padded, so return nothing
                }
            case 4:
                data = handleSettings(data);
                ctx.frontendSettingsSent = true;
                lastFrame = frame;
                frame = null;
                state = 1;
                return data;
            case 6:
                translatePromisedStreamId(data, frame.padded ? LEN_PADDING : 0);
                state = 7;
                return data;
            case -1:
                lastFrame = frame;
                frame = null;
                state = 1;
                return null; // ignore
            case 8:
                return handleHeaderHPack(data);
            case 2:
            case 5:
            case 7:
            default:
                throw new Error("should not reach here");
        }
    }

    @Override
    public ByteArray produce() {
        ByteArray ret = null;

        // may update window
        if (!ctx.frontendHandshaking && !ctx.backendHandshaking && windowSize < INCR_WINDOW_THRESHOLD) {
            ret = utilBuildWindowUpdate(SIZE_CONNECTION_WINDOW - windowSize);
            windowSize = SIZE_CONNECTION_WINDOW;
        }

        // ack for settings
        if (syntheticAck != null) {
            if (ret == null) ret = syntheticAck;
            else ret = ret.concat(syntheticAck);

            syntheticAck = null;
        }

        return ret;
    }

    private void parseFrame(ByteArray data) {
        Http2Frame frame = new Http2Frame();
        frame.length = data.uint24(0);
        byte type = data.get(3);
        frame.typeNum = type;
        switch (type) {
            case 0x0:
                frame.type = Http2Frame.Type.DATA;
                break;
            case 0x1:
                frame.type = Http2Frame.Type.HEADERS;
                break;
            case 0x4:
                frame.type = Http2Frame.Type.SETTINGS;
                break;
            case 0x5: // PUSH_PROMISE
                frame.type = Http2Frame.Type.PUSH_PROMISE;
                break;
            case 0x9:
                frame.type = Http2Frame.Type.CONTINUATION;
                break;
            case 0x2: // PRIORITY
            case 0x8: // WINDOW_UPDATE
                frame.type = Http2Frame.Type.IGNORE;
                break;
            default:
                frame.type = Http2Frame.Type.PROXY;
                break;
        }
        byte flags = data.get(4);
        if (0 != (flags & 0x4)) frame.endHeaders = true;
        if (0 != (flags & 0x8)) frame.padded = true;
        if (0 != (flags & 0x20)) frame.priority = true;
        if (0 != (flags & 0x1)) frame.ack = true; // maybe it means "end stream"
        frame.endStream = (frame.ack && (frame.type == Http2Frame.Type.DATA || frame.type == Http2Frame.Type.HEADERS));
        frame.streamIdentifier = data.int32(5);

        // check whether the stream is about to end
        //noinspection ConstantConditions
        if (frame.endStream &&
            (frame.type == Http2Frame.Type.DATA
                || (frame.type == Http2Frame.Type.HEADERS && frame.endHeaders))
            && connId != 0) {
            // record the stream to be removed
            // for now, we only handle the DATA frames and headers frames that are marked with endHeaders
            // those headers with continuation frames are not handled, let it leak, will be GC-ed when connection closes
            backendIdForStreamToRemove = frame.streamIdentifier;
        }

        this.frame = frame;
        assert Logger.lowLevelDebug("get http2 frame: " + frame + " in connection = " + connId);
    }

    private ByteArray handleFrame(ByteArray frameBytes) throws Exception {
        if (frame.type != Http2Frame.Type.IGNORE) { // only transform and record if it's not ignored
            // check (and modify) the stream id
            // translate the streamIdentifier
            if (frame.streamIdentifier != 0 && frame.streamIdentifier % 2 == 0) {
                // not 0 and is even
                // so it's started by a backend
                assert Logger.lowLevelDebug("modify streamIdentifier of the frame. " +
                    "streamId=" + frame.streamIdentifier + ", connId=" + connId);

                Integer translatedStreamId;
                if (connId == 0) {
                    translatedStreamId = ctx.streamIdFront2Back.get(frame.streamIdentifier);
                } else {
                    translatedStreamId = this.streamIdBack2Front.get(frame.streamIdentifier);
                }
                if (translatedStreamId == null) {
                    assert Logger.lowLevelDebug("the translatedStreamId is null, which is invalid." +
                        "The HTTP/2 protocol does not allow a server start new streams before push-promise, " +
                        "and the streamId should already been recorded when parsing the push-promise frame. " +
                        "But we allow this condition for possible 'HTTP/2-like' protocols.");
                    if (connId != 0) {
                        // this will only happen when data is coming from backend
                        // otherwise, it's invalid
                        throw new Exception("cannot get translated stream id for " + frame.streamIdentifier);
                    }
                    translatedStreamId = ctx.nextServerStreamId();
                    recordStreamMapping(translatedStreamId, frame.streamIdentifier);
                }

                assert Logger.lowLevelDebug("the translatedStreamId is " + translatedStreamId);
                if (!translatedStreamId.equals(frame.streamIdentifier)) {
                    utilModifyStreamId(frameBytes, 5, translatedStreamId);
                    frame.streamIdentifier = translatedStreamId;
                }
            }
            // record the stream after translated the streamId
            ctx.tryRecordStream(this);
        }

        if (connId == 0 // frontend
            && (frame.type == Http2Frame.Type.HEADERS || frame.type == Http2Frame.Type.CONTINUATION) // headers/continuation
            && SIZE_DEFAULT_HEADER_TABLE_SIZE != 0 // would be compressed
        ) {
            assert Logger.lowLevelDebug("got HEADERS frame from frontend");
            state = 8;
            return null; // send nothing for now
        } else if (frame.type == Http2Frame.Type.HEADERS && frame.priority) {
            assert Logger.lowLevelDebug("got HEADERS frame with priority, we should remove the priority");
            state = 3;
            {
                // reset the length
                int forwardLen = frame.length - LEN_E_STREAMDEPENDENCY_WEIGHT;
                assert Logger.lowLevelDebug("the old length was " + frame.length + " new length is " + forwardLen);
                utilModifyFrameLength(frameBytes, forwardLen);
                frame.length = forwardLen;
            }
            {
                // unset the priority bit
                frameBytes.set(4, (byte) (frameBytes.get(4) & 0b1101_1111));
                frame.priority = false;
            }
            return frameBytes;
        } else if (frame.type == Http2Frame.Type.SETTINGS) {
            return handleSettingsFramePart(frameBytes);
        } else if (frame.type == Http2Frame.Type.PUSH_PROMISE) {
            state = 6;
            return frameBytes;
        } else if (frame.type == Http2Frame.Type.IGNORE) {
            assert Logger.lowLevelDebug("got an ignored frame of length " + frame.length);
            state = -1;
            return null;
        } else if (frame.type == Http2Frame.Type.DATA) {
            assert Logger.lowLevelDebug("got data frame of length " + frame.length + ", window before recording is " + windowSize);
            windowSize -= frame.length;
            // do proxy
            state = 2;
            return frameBytes;
        } else {
            state = 2; // default: do proxy
            return frameBytes;
        }
    }

    // NOTE: this method should only return the frameBytes object or null
    // should not create a new object when returning
    private ByteArray handleSettingsFramePart(ByteArray frameBytes) {
        if (connId == 0) {
            // frontend
            if (ctx.frontendHandshaking) {
                // we should manipulate and record the settings frame, so set to a special state
                state = 4;
                ctx.settingsFrameHeader = frameBytes;
                ctx.frontendHandshaking = false; // the frontend handshaking is considered done
                theSettingsFrameHead = frameBytes;
                return null; // do not send for now, only record
            } else if (frame.ack) { // if it's a setting frame from frontend and is ack, proxy it
                state = 2; // proxy
                return frameBytes;
            }
        } else {
            // backend
            if (ctx.backendHandshaking) {
                if (frame.ack) { // get SETTINGS frame with ack set for the first time, means that the backend handshake is done
                    state = 2; // proxy the settings
                    ctx.backendHandshaking = false; // handshake done
                    return frameBytes;
                } else {
                    // we should manipulate and record the settings frame, so set to a special state
                    state = 4;
                    theSettingsFrameHead = frameBytes;
                    return null; // do not send for now, only record
                }
            }
        }
        if (syntheticAckFlag) {
            syntheticAckFlag = false;
            syntheticAck = SEQ_SETTINGS_ACK;
            // though it's only reading the frame part, but it is absolutely followed by a payload part
            // so sending ack here is fine and will correspond to the remote server state machine
        }
        { // otherwise should ignore the frame, both for frontend and backend
            assert Logger.lowLevelDebug("dropping the SETTINGS frame " +
                "because it's not handshaking and not ack of the frontend connection");
            state = -1;
            return null;
        }
    }

    // concat a setting SETTINGS_HEADER_TABLE_SIZE = 0 to the frame, or change the value if it already exists
    // concat a setting SETTINGS_INITIAL_WINDOW_SIZE to the frame, or change the value if it already exists
    private ByteArray handleSettings(ByteArray payload) {
        int extraLength = 0;

        // try to find the SETTINGS_HEADER_TABLE_SIZE and change the value
        {
            int offsetOfSetting = payload.length(); // default: add to the end of the frames
            for (int i = 0; i < payload.length(); i += LEN_SETTING) {
                // the identifier takes 2 bytes
                if (payload.uint16(i) == VALUE_SETTINGS_HEADER_TABLE_SIZE) {
                    offsetOfSetting = i;
                    assert Logger.lowLevelDebug("found setting for the HEADER_TABLE_SIZE");
                    break;
                }
            }
            assert Logger.lowLevelDebug("writing HEADER_TABLE_SIZE at offset " + offsetOfSetting);
            if (offsetOfSetting == payload.length()) {
                extraLength += LEN_SETTING;
                assert Logger.lowLevelDebug("add LEN_SETTING to the frame coming from frontend, " +
                    "now the frame length is " + frame.length + extraLength);
                payload = payload.concat(ByteArray.from(new byte[LEN_SETTING]));
            }
            // the identifier part
            payload.int16(offsetOfSetting, VALUE_SETTINGS_HEADER_TABLE_SIZE);
            // the value part
            payload.int32(offsetOfSetting + 2,
                connId == 0 // connId == 0 means the data is transferred from frontend to backend
                    ? 0 // the backend do not use dynamic table
                    : SIZE_DEFAULT_HEADER_TABLE_SIZE // the frontend uses dynamic table
            );
        }
        // try to find the SETTINGS_INITIAL_WINDOW_SIZE and change the value
        {
            int offsetOfSetting = payload.length(); // default: add to the end of the frames
            for (int i = 0; i < payload.length(); i += LEN_SETTING) {
                // the identifier takes 2 bytes
                if (payload.uint16(i) == VALUE_SETTINGS_INITIAL_WINDOW_SIZE) {
                    offsetOfSetting = i;
                    assert Logger.lowLevelDebug("found setting for the INITIAL_WINDOW_SIZE");
                    break;
                }
            }
            assert Logger.lowLevelDebug("writing INITIAL_WINDOW_SIZE at offset " + offsetOfSetting);
            if (offsetOfSetting == payload.length()) {
                extraLength += LEN_SETTING;
                assert Logger.lowLevelDebug("add LEN_SETTING to the frame coming from frontend, " +
                    "now the frame length is " + frame.length + extraLength);
                payload = payload.concat(ByteArray.from(new byte[LEN_SETTING]));
            }
            // the identifier part
            payload.int16(offsetOfSetting, VALUE_SETTINGS_INITIAL_WINDOW_SIZE);
            // the value part
            payload.int32(offsetOfSetting + 2, SIZE_STREAM_WINDOW);
        }

        // set the length in frame head
        utilModifyFrameLength(theSettingsFrameHead, frame.length + extraLength);

        // record the handshake if it's connection from client
        if (connId == 0) {
            ByteArray head = ctx.settingsFrameHeader;
            ByteArray handshake = SEQ_PREFACE_MAGIC.concat(head).concat(payload).arrange();
            ctx.settingsFrameHeader = null;
            ctx.clientHandshake = handshake;
        }

        return theSettingsFrameHead.concat(payload);
    }

    private void translatePromisedStreamId(ByteArray data, int offset) {
        Integer promisedStreamId = data.int32(offset);
        Integer translatedStreamId = ctx.nextServerStreamId();
        assert Logger.lowLevelDebug("push-promise frame > promised stream id is " + promisedStreamId +
            " translated stream id is " + translatedStreamId);
        recordStreamMapping(translatedStreamId, promisedStreamId);
        if (!promisedStreamId.equals(translatedStreamId)) {
            utilModifyStreamId(data, offset, translatedStreamId);
        }
    }

    private ByteArray handleHeaderHPack(ByteArray data) throws Exception {
        byte frameType;
        ByteArray transformed;
        if (frame.type == Http2Frame.Type.HEADERS) {
            frameType = 1;
            // get the actual data part
            if (frame.padded && frame.priority) {
                data = data.sub(1 + 5, data.length() - (1 + 5) - data.get(0));
            } else if (frame.padded) {
                data = data.sub(1, data.length() - 1 - data.get(0));
            } else if (frame.priority) {
                data = data.sub(5, data.length() - 5);
            }
            transformed = ctx.hPackTransformer.transform(data, frame.endHeaders && connId == 0);
        } else {
            assert frame.type == Http2Frame.Type.CONTINUATION;
            frameType = 9; // type = continuation
            // data is simple and can be directly transformed for continuation frames
            transformed = ctx.hPackTransformer.transform(data, frame.endHeaders && connId == 0);
        }
        ByteArray result = ByteArray.from(new byte[]{
            0, 0, 0, // length, will be set later
            frameType,
            (byte) (frame.endHeaders ? 4 : 0), // flags
            0, 0, 0, 0 // stream id, will be set later
        }).concat(transformed);
        result.int24(0, transformed.length());
        result.int32(5, frame.streamIdentifier);

        // set header end before return the result
        if (frame.endHeaders) {
            ctx.hPackTransformer.endHeaders();
            hostHeaderRetrieved = true; // headers frame ends, connection related headers must have been retrieved, so send data
        }
        // set state to idle
        state = 1;

        return result;
    }

    private static void utilModifyStreamId(ByteArray data, int offset, int streamId) {
        data.int32(offset, streamId);
    }

    private static void utilModifyFrameLength(ByteArray data, int length) {
        data.int24(0, length);
    }

    Integer currentStreamId() {
        if (frame == null && lastFrame == null) {
            // check whether this is a bug
            boolean isBug = true;
            StackTraceElement[] elems = Thread.currentThread().getStackTrace();
            //noinspection ConstantConditions
            if (elems == null || elems.length == 0) { // in case some vm doesn't support stacktrace
                isBug = false;
            } else {
                for (StackTraceElement e : elems) {
                    if ("remoteClosed".equals(e.getMethodName())) {
                        isBug = false;
                        break;
                    }
                }
            }
            if (isBug) {
                Logger.shouldNotHappen("failed to retrieve current stream id, this method should not be called here");
            } else {
                assert Logger.lowLevelDebug("trying to retrieve current stream id while no frame is being handled, the frontend connection might be closing, so return -1 here");
            }
            return -1;
        }

        if (frame != null) {
            return frame.streamIdentifier;
        }
        Http2Frame f = lastFrame;
        lastFrame = null;
        return f.streamIdentifier;
    }

    void recordStreamMapping(Integer front, Integer back) {
        this.streamIdBack2Front.put(back, front);
        ctx.streamIdFront2Back.put(front, back);
    }

    void removeStreamMappingByBackendId(Integer back) {
        Integer front = this.streamIdBack2Front.remove(back);
        ctx.streamIdFront2Back.remove(front); // it's ok to remove a non-exist element
        if (front == null) {
            front = back;
        }
        ctx.streamMap.remove(front);
    }

    @Override
    public void proxyDone() {
        // check whether the stream can be removed
        // NOTE: the removal is placed before resetting state and frame
        // is because that it's easier when debugging to see the old status
        if (backendIdForStreamToRemove != null) {
            removeStreamMappingByBackendId(backendIdForStreamToRemove);
            backendIdForStreamToRemove = null;
        }
        // all proxy states goes to state 1
        // so simply set the frame to null and state 1 here
        state = 1;
        lastFrame = frame;
        frame = null;
    }

    @Override
    public ByteArray connected() {
        if (connId == 0) {
            return null;
        }
        return ctx.clientHandshake;
    }
}
