package vproxy.base.selector.wrap.h2streamed;

import vproxy.base.selector.wrap.streamed.StreamedFDHandler;
import vproxy.base.util.ByteArray;
import vproxy.base.util.LogType;
import vproxy.base.util.Logger;
import vproxy.base.util.Utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class H2StreamedFDHandler extends StreamedFDHandler {
    private static final ByteArray HEAD = ByteArray.from(
        0, 0, 0 /*LEN(24)*/,
        0 /*TYPE(8)*/, 0 /*FLAGS(8)*/,
        0, 0, 0, 0 /*STREAM_ID(1+31)*/
    );
    private static final byte TYPE_DATA = 0x0;
    private static final byte TYPE_HEADER = 0x1;
    private static final byte TYPE_SETTINGS = 0x4;
    private static final byte TYPE_PING = 0x6;
    private static final byte TYPE_GOAWAY = 0x7;
    private static final List<Byte> validTypes = Arrays.asList(
        TYPE_DATA, TYPE_HEADER, TYPE_PING, TYPE_GOAWAY // TYPE_SETTINGS is not valid after handshaking
    );
    private static final List<Byte> validTypesWithPayload = Arrays.asList(TYPE_DATA, TYPE_GOAWAY, TYPE_PING);

    private static final byte FLAG_CLOSE_STREAM = 0x1;
    private static final byte FLAG_ACK = 0x1;

    private static final byte FLAG_COMPRESSED = 0x4; // customized flag for compressed data payload
    // currently only 0x1(END_STREAM) and 0x8(PADDED) are used by RFC7540, we set this to 0x4

    private static ByteArray getEmptySettings() {
        return HEAD.copy().set(3, TYPE_SETTINGS);
    }

    private static ByteArray getEmptyHeader(int streamId) {
        return HEAD.copy().set(3, TYPE_HEADER).int32(5, streamId);
    }

    private static ByteArray getPing(long data, boolean isAck) {
        return HEAD.copy()
            .int24(0, 8)
            .set(3, TYPE_PING)
            .set(4, isAck ? FLAG_ACK : 0)
            .concat(ByteArray.allocate(8).int64(0, data));
    }

    @SuppressWarnings("SameParameterValue")
    private static ByteArray getGoAway(int lastStreamId, int errorCode, String msg) {
        byte[] bytes = msg.getBytes();
        return HEAD.copy()
            .int24(0, 4 /*R+Last-Stream-ID*/ + 4 /*Error Code*/ + bytes.length)
            .set(3, TYPE_GOAWAY)
            .concat(ByteArray.allocate(4).int32(0, lastStreamId))
            .concat(ByteArray.allocate(4).int32(0, errorCode))
            .concat(ByteArray.from(bytes));
    }

    private static ByteArray getEmptyCloseStreamHeader(int streamId) {
        return HEAD.copy().set(3, TYPE_HEADER).set(4, FLAG_CLOSE_STREAM).int32(5, streamId);
    }

    private static ByteArray getEmptyCloseStreamData(int streamId) {
        return HEAD.copy().set(3, TYPE_DATA).set(4, FLAG_CLOSE_STREAM).int32(5, streamId);
    }

    private static ByteArray getData(int streamId, ByteArray array, ByteArrayOutputStream bufForCompression) {
        byte flag = 0;
        int oldLen = array.length();
        if (oldLen > 1024) {
            // try to compress
            bufForCompression.reset();
            byte[] bytes = Utils.gzipCompress(bufForCompression, array.toJavaArray());
            if (bytes.length < oldLen) {
                assert Logger.lowLevelDebug("compress for packet: oldLen=" + oldLen + ", newLen=" + bytes.length);
                array = ByteArray.from(bytes);
                flag |= FLAG_COMPRESSED;
            }
        }
        return HEAD.copy().int24(0, array.length()).set(3, TYPE_DATA).set(4, flag).int32(5, streamId).concat(array);
    }

    protected H2StreamedFDHandler(boolean client) {
        super(client);
    }

    @Override
    protected ByteArray errorMessage(IOException err) {
        String msg = err.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = err.getClass().getName();
        }
        msg = msg.trim();
        return getGoAway(-1, -1, msg);
    }

    @Override
    protected ByteArray clientHandshakeMessage() {
        return getEmptySettings();
    }

    @Override
    protected int clientReceiveHandshakeMessage(ByteArray array) throws IOException {
        if (array.length() < HEAD.length()) {
            return 0;
        }
        if (!array.sub(0, HEAD.length()).equals(getEmptySettings())) {
            // check whether is GOAWAY
            byte type = type(array);
            if (type == TYPE_GOAWAY) {
                int len = len(array);
                if (array.length() < HEAD.length() + len) {
                    return 0; // the frame is not complete, wait for next call
                }
                handleGoAway(array);
            }
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "got invalid handshake message, type=" + type);
            throw new IOException("handshake message is invalid");
        }
        return HEAD.length();
    }

    @Override
    protected int serverReceiveHandshakeMessage(ByteArray array) throws IOException {
        return clientReceiveHandshakeMessage(array);
    }

    private int len(ByteArray array) throws IOException {
        int i = array.uint24(0);
        if (i < 0) {
            throw new IOException("invalid length: " + i);
        }
        return i;
    }

    private byte type(ByteArray array) {
        return array.get(3);
    }

    private byte flag(ByteArray array) {
        return array.get(4);
    }

    private int streamId(ByteArray array) {
        return array.int32(5);
    }

    private void checkType(byte type, int len) throws IOException {
        if (!validTypes.contains(type)) {
            throw new IOException("invalid frame type: " + type);
        }
        if (len != 0) {
            if (!validTypesWithPayload.contains(type)) {
                throw new IOException("invalid frame type with payload len: type=" + type + ", len=" + len);
            }
        }
    }

    private void checkStreamId(int streamId) throws IOException {
        if (streamId < 0) {
            throw new IOException("invalid streamId: " + streamId);
        }
    }

    private void handleGoAway(ByteArray array) throws IOException {
        int len = len(array);
        IOException error;
        if (len < 8) {
            // no error message present
            error = new IOException("GOAWAY");
        } else {
            error = new IOException("GOAWAY: " + new String(array.sub(HEAD.length() + 8, len - 8).toJavaArray()));
        }
        errorReceived(error);
    }

    private int handleGeneralFeedData(int len, byte type, byte flag, int streamId, ByteArray array) throws IOException {
        switch (type) {
            case TYPE_DATA:
                if (len == 0) {
                    if ((flag & FLAG_CLOSE_STREAM) == FLAG_CLOSE_STREAM) {
                        // similar to FIN
                        finReceived(streamId);
                    }
                    return HEAD.length();
                }
                ByteArray content = array.sub(HEAD.length(), len);
                if ((flag & FLAG_COMPRESSED) == FLAG_COMPRESSED) {
                    bufForCompression.reset();
                    byte[] bytes = Utils.gzipDecompress(bufForCompression, content.toJavaArray());
                    if (bytes == null) {
                        Logger.error(LogType.INVALID_EXTERNAL_DATA, "decompress data failed");
                        throw new IOException("decompress data failed");
                    }
                    content = ByteArray.from(bytes);
                }
                dataForStream(streamId, content);
                // maybe fin with data (similar to PSH,FIN)
                if ((flag & FLAG_CLOSE_STREAM) == FLAG_CLOSE_STREAM) {
                    finReceived(streamId);
                }
                return HEAD.length() + len;
            case TYPE_HEADER:
                if ((flag & FLAG_CLOSE_STREAM) == FLAG_CLOSE_STREAM) {
                    // reset
                    rstReceived(streamId);
                } else {
                    synReceived(streamId);
                }
                return HEAD.length();
            case TYPE_GOAWAY:
                handleGoAway(array);
                return HEAD.length() + len;
            case TYPE_PING:
                // keep-alive packet
                if (len < 8) {
                    // invalid ping frame, ignore it
                    return HEAD.length() + len;
                }
                boolean isAck = ((flag & FLAG_ACK) == FLAG_ACK);
                long kId = array.int64(HEAD.length());
                keepaliveReceived(kId, isAck);
                return HEAD.length() + len;
            default:
                throw new IOException("invalid frame type: " + type);
        }
    }

    @Override
    protected int clientFeed(ByteArray array) throws IOException {
        if (array.length() < HEAD.length()) {
            assert Logger.lowLevelDebug("no enough bytes to read the HEAD");
            return 0;
        }
        int len = len(array);
        if (array.length() < HEAD.length() + len) {
            assert Logger.lowLevelDebug("no enough bytes to read the HEAD+len(" + len + ")");
            return 0;
        }
        byte type = type(array);
        checkType(type, len);
        byte flag = flag(array);
        int streamId = streamId(array);
        checkStreamId(streamId);

        return handleGeneralFeedData(len, type, flag, streamId, array);
    }

    @Override
    protected int serverFeed(ByteArray array) throws IOException {
        return clientFeed(array);
    }

    @Override
    protected ByteArray serverHandshakeMessage() {
        return clientHandshakeMessage();
    }

    @Override
    protected ByteArray formatSYNACK(int streamId) {
        return getEmptyHeader(streamId);
    }

    private final ByteArrayOutputStream bufForCompression = new ByteArrayOutputStream(1024);

    @Override
    protected ByteArray formatPSH(int streamId, ByteArray data) {
        return getData(streamId, data, bufForCompression);
    }

    @Override
    protected ByteArray formatFIN(int streamId) {
        return getEmptyCloseStreamData(streamId);
    }

    @Override
    protected ByteArray keepaliveMessage(long kId, boolean isAck) {
        return getPing(kId, isAck);
    }

    private int streamId = 1;

    @Override
    protected int nextStreamId() {
        int n = streamId;
        streamId += 2;
        return n;
    }

    @Override
    protected ByteArray formatSYN(int streamId) {
        return getEmptyHeader(streamId);
    }

    @Override
    protected ByteArray formatRST(int streamId) {
        return getEmptyCloseStreamHeader(streamId);
    }
}
