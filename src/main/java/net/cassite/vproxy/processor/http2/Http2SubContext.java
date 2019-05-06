package net.cassite.vproxy.processor.http2;

import net.cassite.vproxy.component.proxy.Processor;
import net.cassite.vproxy.util.Logger;

import java.util.Arrays;

// the impl corresponds to rfc7540
/*
 * preface magic:
 * 0x505249202a20485454502f322e300d0a0d0a534d0d0a0d0a
 *
 *
 * frame:
 *  +-----------------------------------------------+
 *  |                 Length (24)                   |
 *  +---------------+---------------+---------------+
 *  |   Type (8)    |   Flags (8)   |
 *  +-+-------------+---------------+-------------------------------+
 *  |R|                 Stream Identifier (31)                      |
 *  +=+=============================================================+
 *  |                   Frame Payload (0...)                      ...
 *  +---------------------------------------------------------------+
 * HEADERS: remove priority
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
 * SETTINGS: proxy and record for the handshake, then simply ignore
 *  +-------------------------------+
 *  |       Identifier (16)         |
 *  +-------------------------------+-------------------------------+
 *  |                        Value (32)                             |
 *  +---------------------------------------------------------------+
 *
 * DATA: proxy
 * PRIORITY: ignore
 * PUSH_PROMISE: proxy
 * RST_STREAM: proxy
 * PING: proxy
 * GOAWAY: proxy
 * WINDOW_UPDATE: ignore
 * CONTINUATION: proxy
 *
 * the lib will remove all stream dependency and drop priority packets
 */

public class Http2SubContext extends Processor.SubContext {
    private static final byte[] PREFACE_MAGIC = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes();
    private static final int FRAME_HEAD_LEN = 9; // 72
    private static final int PADDING = 1; // 8
    private static final int E_STREAMDEPENDENCY_WEIGHT = 5; // 1 + 31 + 8

    public final Http2Context ctx;
    public final int connId;

    private Http2Frame frame;

    private int state;
    /*
     * 0 -> frontend handshake -> 1
     * 1 -> (idle) reading stream and length -> 2/3/4/-1
     * 2 -> proxy -> 1
     * 3 -> (headers) remove stream dependency in headers -> 5
     * 4 -> settings: we should record the first settings frame, so set to a special state -> 1
     * 5 -> (headers) the header part after stream dependency -> 1
     * -1 -> ignore the frame -> 1
     */

    public Http2SubContext(Http2Context ctx, int connId) {
        this.ctx = ctx;
        this.connId = connId;

        if (connId == 0) {
            state = 0;
        } else {
            state = 1;
        }
    }

    public Processor.Mode mode() {
        switch (state) {
            case 0:
            case 1:
            case 3:
            case 4:
            case -1:
                return Processor.Mode.handle;
            case 2:
            case 5:
                return Processor.Mode.proxy;
            default:
                throw new Error("should not reach here");
        }
    }

    public int len() {
        switch (state) {
            case 0:
                return PREFACE_MAGIC.length + FRAME_HEAD_LEN;
            case 1:
                return FRAME_HEAD_LEN;
            case 3:
                assert frame.priority; // only reach here if the priority flag is set
                return (frame.padded ? PADDING : 0) + E_STREAMDEPENDENCY_WEIGHT;
            case 5:
                assert frame.priority; // only reach here if the priority flag is set
                return frame.length - (frame.padded ? PADDING : 0) - E_STREAMDEPENDENCY_WEIGHT;
            case -1:
            case 2:
            case 4:
                return frame.length; // the frame itself
            default:
                throw new Error("should not reach here");
        }
    }

    public byte[] feed(byte[] data) throws Exception {
        switch (state) {
            case 0:
                assert data.length == PREFACE_MAGIC.length + FRAME_HEAD_LEN;
                // check the preface
                byte[] prefacePart = new byte[PREFACE_MAGIC.length];
                System.arraycopy(data, 0, prefacePart, 0, PREFACE_MAGIC.length);
                if (0 != Arrays.compare(prefacePart, PREFACE_MAGIC)) {
                    throw new Exception("the preface magic is wrong! " + new String(data));
                }
                byte[] framePart = new byte[FRAME_HEAD_LEN];
                System.arraycopy(data, PREFACE_MAGIC.length, framePart, 0, FRAME_HEAD_LEN);
                parseFrame(framePart);
                // ignore the result from handleFrame.
                // the first frame is always settings frame and should be proxied
                handleFrame(data);
                state = 2;
                return data;
            case 1:
                parseFrame(data);
                return handleFrame(data);
            case 3:
                state = 5; // set to proxy anything left in the header
                if (frame.padded) {
                    assert Logger.lowLevelDebug("the frame is padded, the padding length is " + data[0]);
                    return new byte[data[0]]; // only return the PADDING part
                } else {
                    assert Logger.lowLevelDebug("the frame is not padded, so ignore this part");
                    return null; // not padded, so return nothing
                }
            case 4:
                handleSettings(data);
                frame = null;
                state = 1;
                return data;
            case -1:
                frame = null;
                state = 1;
                return null; // ignore
            case 2:
            case 5:
            default:
                throw new Error("should not reach here");
        }
    }

    private void parseFrame(byte[] data) {
        Http2Frame frame = new Http2Frame();
        frame.length = 0x0fffffff & (data[0] << 16 | data[1] << 8 | data[2]);
        byte type = data[3];
        switch (type) {
            case 0x1:
                frame.type = Http2Frame.Type.HEADERS;
                break;
            case 0x4:
                frame.type = Http2Frame.Type.SETTINGS;
                break;
            case 0x5: // PUSH_PROMISE
                frame.type = Http2Frame.Type.PUSH_PROMISE;
                break;
            case 0x2: // PRIORITY
            case 0x8: // WINDOW_UPDATE
                frame.type = Http2Frame.Type.IGNORE;
                break;
            default:
                frame.type = Http2Frame.Type.PROXY;
                break;
        }
        byte flags = data[4];
        if (0 != (flags & 0x8)) frame.padded = true;
        if (0 != (flags & 0x20)) frame.priority = true;
        if (0 != (flags & 0x1)) frame.ack = true; // maybe it means "end stream", but we don't care
        frame.streamIdentifier = data[5] << 24 | data[6] << 16 | data[7] << 8 | data[8];

        this.frame = frame;
        assert Logger.lowLevelDebug("get http2 frame: " + frame + " in connection = " + connId);
        ctx.tryRecordStream(this); // whether it's a new stream will be checked in the method
    }

    private byte[] handleFrame(byte[] frameBytes) {
        if (frame.type == Http2Frame.Type.HEADERS && frame.priority) {
            assert Logger.lowLevelDebug("got HEADERS frame with priority, we should remove the priority");
            state = 3;
            {
                // reset the length
                int forwardLen = frame.length - E_STREAMDEPENDENCY_WEIGHT;
                assert Logger.lowLevelDebug("the old length was " + frame.length + " new length is " + forwardLen);
                byte b0 = (byte) ((forwardLen >> 16) & 0xff);
                byte b1 = (byte) ((forwardLen >> 8) & 0xff);
                byte b2 = (byte) ((forwardLen) & 0xff);
                frameBytes[0] = b0;
                frameBytes[1] = b1;
                frameBytes[2] = b2;
            }
            {
                // unset the priority bit
                frameBytes[4] = (byte) (frameBytes[4] & 0b1101_1111);
            }
            return frameBytes;
        } else if (frame.type == Http2Frame.Type.SETTINGS) {
            if (ctx.handshaking) {
                if (connId == 0) { // it's the frontend connection that's sending the frame
                    // we should record the settings frame, so set to a special state
                    state = 4;
                    ctx.settingsFrameHeader = frameBytes;
                    return frameBytes;
                } else { // backend
                    state = 2; // proxy
                    if (frame.ack) { // get SETTINGS frame with ack set for the first time, means that the handshake is done
                        ctx.handshaking = false; // handshake done
                    }
                    return frameBytes;
                }
            } else {
                if (frame.ack && connId == 0) { // if it's a setting frame from frontend and is ack, proxy it
                    state = 2; // proxy
                    return frameBytes;
                } else { // otherwise should ignore the frame
                    assert Logger.lowLevelDebug("dropping the SETTINGS frame " +
                        "because it's not handshaking and not ack of the frontend connection");
                    state = -1;
                    return null;
                }
            }
        } else if (frame.type == Http2Frame.Type.IGNORE) {
            assert Logger.lowLevelDebug("got an ignored frame of length " + frame.length);
            state = -1;
            return null;
        } else {
            state = 2; // default: do proxy
            return frameBytes;
        }
    }

    private void handleSettings(byte[] payload) {
        byte[] head = ctx.settingsFrameHeader;
        byte[] handshake = new byte[PREFACE_MAGIC.length + head.length + payload.length];
        System.arraycopy(PREFACE_MAGIC, 0, handshake, 0, PREFACE_MAGIC.length);
        System.arraycopy(head, 0, handshake, PREFACE_MAGIC.length, head.length);
        System.arraycopy(payload, 0, handshake, PREFACE_MAGIC.length + head.length, payload.length);

        ctx.settingsFrameHeader = null;
        ctx.clientHandshake = handshake;
    }

    Integer currentStreamId() {
        assert frame != null;
        return frame.streamIdentifier;
    }

    public void proxyDone() {
        // all proxy states goes to state 1
        // so simply set the frame to null and state 1 here
        state = 1;
        frame = null;
    }

    public byte[] connected() {
        if (connId == 0) {
            return null;
        }
        return ctx.clientHandshake;
    }
}
