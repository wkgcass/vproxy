package vproxy.selector.wrap.h2streamed;

import vproxy.selector.wrap.streamed.StreamedFDHandler;
import vproxy.util.ByteArray;
import vproxy.util.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class H2StreamedFDHandler extends StreamedFDHandler {
    private static final ByteArray HEAD = ByteArray.from(
        0, 0, 0 /*LEN(24)*/,
        0 /*TYPE(8)*/, 0 /*FLAGS(8)*/,
        0, 0, 0, 0 /*STREAM_ID(1+31)*/
    );
    private static final byte TYPE_DATA = 0x0;
    private static final byte TYPE_HEADER = 0x1;
    private static final byte TYPE_RST = 0x3;
    private static final byte TYPE_SETTINGS = 0x4;
    private static final byte TYPE_PING = 0x6;
    private static final byte TYPE_GOAWAY = 0x7;
    private static final List<Byte> validTypes = Arrays.asList(
        TYPE_DATA, TYPE_HEADER, TYPE_RST, TYPE_PING, TYPE_GOAWAY
    );
    private static final List<Byte> validTypesWithPayload = Collections.singletonList(TYPE_DATA);

    private static ByteArray getEmptySettings() {
        return HEAD.copy().set(3, TYPE_SETTINGS);
    }

    private static ByteArray getEmptyHeader(int streamId) {
        return HEAD.copy().set(3, TYPE_HEADER).int32(5, streamId);
    }

    private static ByteArray getEmptyPing() {
        return HEAD.copy().set(3, TYPE_PING);
    }

    private static ByteArray getEmptyGoAway(int streamId) {
        return HEAD.copy().set(3, TYPE_GOAWAY).int32(5, streamId);
    }

    private static ByteArray getEmptyRst(int streamId) {
        return HEAD.copy().set(3, TYPE_RST).int32(5, streamId);
    }

    private static ByteArray getData(int streamId, ByteArray array) {
        return HEAD.copy().int24(0, array.length()).set(3, TYPE_DATA).int32(5, streamId).concat(array);
    }

    protected H2StreamedFDHandler(boolean client) {
        super(client);
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

    private int handleGeneralFeedData(int len, byte type, int streamId, ByteArray array) throws IOException {
        switch (type) {
            case TYPE_DATA:
                if (len == 0) {
                    return HEAD.length();
                }
                dataForStream(streamId, array.sub(HEAD.length(), len));
                return HEAD.length() + len;
            case TYPE_HEADER:
                synReceived(streamId);
                return HEAD.length();
            case TYPE_RST:
                rstReceived(streamId);
                return HEAD.length();
            case TYPE_PING:
                // keep-alive packet
                // ignore
                return HEAD.length();
            case TYPE_GOAWAY:
                finReceived(streamId);
                return HEAD.length();
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
        int streamId = streamId(array);
        checkStreamId(streamId);

        return handleGeneralFeedData(len, type, streamId, array);
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

    @Override
    protected ByteArray formatPSH(int streamId, ByteArray data) {
        return getData(streamId, data);
    }

    @Override
    protected ByteArray formatFIN(int streamId) {
        return getEmptyGoAway(streamId);
    }

    @Override
    protected ByteArray keepaliveMessage() {
        return getEmptyPing();
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
        return getEmptyRst(streamId);
    }
}
