package io.vproxy.base.processor.httpbin;

import io.vproxy.base.processor.httpbin.entity.Header;
import io.vproxy.base.processor.httpbin.frame.*;
import io.vproxy.base.processor.httpbin.hpack.HPack;
import io.vproxy.base.processor.ConnectionDelegate;
import io.vproxy.base.processor.ExceptionWithoutStackTrace;
import io.vproxy.base.processor.OOSubContext;
import io.vproxy.base.processor.Processor;
import io.vproxy.base.processor.httpbin.entity.Header;
import vproxy.base.processor.httpbin.frame.*;
import io.vproxy.base.processor.httpbin.hpack.HPack;
import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.base.util.nio.ByteArrayChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class BinaryHttpSubContext extends OOSubContext<BinaryHttpContext> implements BinaryHttpSubContextCaster {
    public static final ByteArray H2_PREFACE = ByteArray.from("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n");
    public static final int H2_HEADER_SIZE = 3 + 1 + 1 + 4; // header length: len+type+flags+streamId
    private static final ByteArray SERVER_SETTINGS = SettingsFrame.newServerSettings().serializeH2(null).arrange();
    private static final ByteArray CLIENT_FIRST_FRAME =
        H2_PREFACE.concat(SettingsFrame.newClientSettings().serializeH2(null)).arrange();
    private static final ByteArray ACK_SETTINGS = SettingsFrame.newAck().serializeH2(null).arrange();

    // 0: initiated, expecting preface => 1
    // 1: expecting first settings frame header => 2
    // 2: expecting first settings frame => 3
    // 3: expecting frame header => 4|5|6
    // 4: expecting headers frame payload => 7|done
    // 5: expecting data frame payload => done
    // 6: expecting other frame payload => done (more handling if it's settings frame)
    // 7: expecting continuation frame header => 8
    // 8: expecting continuation frame => done
    public static final int STATE_PREFACE = 0;
    public static final int STATE_FIRST_SETTINGS_FRAME_HEADER = 1;
    public static final int STATE_FIRST_SETTINGS_FRAME = 2;
    public static final int STATE_FRAME_HEADER = 3;
    public static final int STATE_HEADERS_FRAME = 4;
    public static final int STATE_DATA_FRAME = 5;
    public static final int STATE_OTHER_FRAME = 6;
    public static final int STATE_CONTINUATION_FRAME_HEADER = 7;
    public static final int STATE_CONTINUATION_FRAME = 8;
    private int state = 0;

    private final HPack hpack = new HPack(SettingsFrame.DEFAULT_HEADER_TABLE_SIZE, SettingsFrame.DEFAULT_HEADER_TABLE_SIZE);

    final StreamHolder streamHolder;
    Stream currentPendingStream = null;

    private int connectionSendingWindow = SettingsFrame.DEFAULT_WINDOW_SIZE;
    private int connectionReceivingWindow = SettingsFrame.DEFAULT_WINDOW_SIZE;
    private int initialSendingWindow = connectionSendingWindow;
    private final int initialReceivingWindow = connectionReceivingWindow; // will not modify

    public BinaryHttpSubContext(BinaryHttpContext binaryHttpContext, int connId, ConnectionDelegate delegate) {
        super(binaryHttpContext, connId, delegate);
        if (connId == 0) { // frontend
            ctx.frontend = this;
        } else { // backend
            state = STATE_FIRST_SETTINGS_FRAME_HEADER;
        }
        streamHolder = new StreamHolder(this);
        init();
    }

    @Override
    public Processor.ProcessorTODO process() {
        if (ctx.upgradedConnection) {
            return processorTODOProxy();
        }
        if (state == STATE_DATA_FRAME) {
            return processorTODOProxy();
        }
        return processorTODOHandle();
    }

    private boolean expectNewFrame = true; // initially expect new frame
    private boolean dataPendingToBeSent = false;
    private int len;

    private Processor.ProcessorTODO processorTODOProxy() {
        int len;
        if (ctx.upgradedConnection) {
            len = 0x00ffffffff;
        } else {
            len = this.len;
        }
        Processor.ProcessorTODO ret = Processor.ProcessorTODO.createProxy();
        ret.len = len;
        ret.proxyTODO.proxyDone = this::proxyDone;
        if (isFrontend()) {
            ret.proxyTODO.connTODO = ctx.connection();
        }
        return ret;
    }

    private Processor.ProcessorTODO processorTODOHandle() {
        int len;
        if (dataPendingToBeSent) {
            len = 0; // the lib should directly call feed
        } else {
            len = this.len;
        }
        Processor.ProcessorTODO ret = Processor.ProcessorTODO.create();
        ret.mode = Processor.Mode.handle;
        ret.len = len;
        ret.feed = this::feed0;
        return ret;
    }

    private void init() {
        if (state == STATE_FIRST_SETTINGS_FRAME_HEADER) {
            setLen(H2_HEADER_SIZE);
        } else {
            setLen(H2_PREFACE.length());
        }
    }

    private boolean expectNewFrame() {
        if (ctx.upgradedConnection) {
            return false;
        }
        return expectNewFrame;
    }

    private void setLen(int len) {
        this.len = len;
        if (parserMode) {
            this.chnl = ByteArrayChannel.fromEmpty(len);
        }
    }

    private ByteArray dataToProxy;

    private ByteArray returnDataToProxy() {
        var dataToProxy = this.dataToProxy;
        this.dataToProxy = null;
        return dataToProxy;
    }

    private Processor.HandleTODO feed0(ByteArray data) throws Exception {
        ByteArray send = feed(data);
        Processor.HandleTODO ret = Processor.HandleTODO.create();
        ret.send = send;
        if (isFrontend() && (
            (send != null && send.length() > 0) || send == Processor.REQUIRE_CONNECTION)) {
            ret.connTODO = ctx.connection();
        }
        ret.frameEnds = expectNewFrame();
        ret.produce = produce();
        return ret;
    }

    private ByteArray feed(ByteArray data) throws Exception {
        if (dataPendingToBeSent) {
            handlePendingFrame(data);
            return returnDataToProxy();
        }
        if (!parserMode) {
            streamHolder.removeEndStreams();
        }

        assert Logger.lowLevelDebug("state before handling frames: " + state);

        switch (state) {
            case STATE_PREFACE:
                readPreface(data);
                break;
            case STATE_FIRST_SETTINGS_FRAME_HEADER:
            case STATE_FRAME_HEADER:
            case STATE_CONTINUATION_FRAME_HEADER:
                readFrameHeader(data);
                if (state == STATE_FIRST_SETTINGS_FRAME_HEADER) {
                    if (parsingFrame.type != HttpFrameType.SETTINGS)
                        throw new ExceptionWithoutStackTrace("expecting settings frame, but got " + parsingFrame.type);
                } else if (state == STATE_CONTINUATION_FRAME) {
                    if (parsingFrame.type != HttpFrameType.CONTINUATION)
                        throw new ExceptionWithoutStackTrace("expecting continuation frame, but got " + parsingFrame.type);
                }
                break;
            default:
                readFramePayload(data);
                break;
        }
        assert Logger.lowLevelDebug("state after handling frames: " + state);
        assert Logger.lowLevelDebug("binary http parser current frame: " + parsingFrame);
        assert Logger.lowLevelNetDebug("proxy from feed(): " + (dataToProxy == null ? "null" : dataToProxy.toHexString()));
        return returnDataToProxy();
    }

    private void handlePendingFrame(ByteArray data) throws Exception {
        if (parserMode) {
            return;
        }

        assert Logger.lowLevelDebug("returning pending data");
        if (data.length() != 0) {
            String errMsg = "the feed data length must be 0 when data pending to be proxied";
            throw new Exception(errMsg);
        }
        assert Logger.lowLevelDebug("proxy pending frame: " + parsingFrame);
        serializeToProxy(false);
        dataPendingToBeSent = false; // un-setting must be after serialization done
        assert Logger.lowLevelNetDebug("dataPendingToBeProxied of feed(): " + dataToProxy.toHexString());
    }

    private void readPreface(ByteArray data) throws Exception {
        if (!data.equals(H2_PREFACE))
            throw new ExceptionWithoutStackTrace("not receiving preface: 0x" + data.toHexString());
        state = STATE_FIRST_SETTINGS_FRAME_HEADER;
        expectNewFrame = true;
        setLen(H2_HEADER_SIZE);
        parsingFrame = lastParsedFrame = new Preface();

        // preface is received, need send initial settings frame
        sendInitialFrame();
    }

    private void sendInitialFrame() {
        if (connId == 0) {
            // is server, need to send server settings
            produced = SERVER_SETTINGS;
        } else {
            // is client, need to send preface and settings
            produced = CLIENT_FIRST_FRAME;
        }
    }

    private static final Supplier<HttpFrame>[] h2frames;

    static {
        {
            Map<HttpFrame, Supplier<HttpFrame>> map = new HashMap<>();
            map.put(new ContinuationFrame(), ContinuationFrame::new);
            map.put(new DataFrame(), DataFrame::new);
            map.put(new GoAwayFrame(), GoAwayFrame::new);
            map.put(new HeadersFrame(), HeadersFrame::new);
            map.put(new PingFrame(), PingFrame::new);
            map.put(new PriorityFrame(), PriorityFrame::new);
            map.put(new PushPromiseFrame(), PushPromiseFrame::new);
            map.put(new RstStreamFrame(), RstStreamFrame::new);
            map.put(new SettingsFrame(), SettingsFrame::new);
            map.put(new WindowUpdateFrame(), WindowUpdateFrame::new);

            int max = 0;
            for (var f : map.keySet()) {
                if (f.type.h2type > max) {
                    max = f.type.h2type;
                }
            }
            //noinspection unchecked
            h2frames = new Supplier[max + 1];
            for (var en : map.entrySet()) {
                h2frames[en.getKey().type.h2type] = en.getValue();
            }
        }
    }

    private void readFrameHeader(ByteArray data) throws Exception {
        int len = data.uint24(0);
        byte type = data.get(3);
        byte flags = data.get(4);
        int streamId = data.int32(5);

        if (len > 1024 * 1024)
            throw new ExceptionWithoutStackTrace("frame too large, len: " + len);
        if (type < 0 || type >= h2frames.length || h2frames[type] == null)
            throw new ExceptionWithoutStackTrace("unknown h2 frame type: " + type);
        if (streamId < 0)
            throw new ExceptionWithoutStackTrace("invalid stream id: " + streamId);

        // new frame, so the proxy target must be removed before processing
        ctx.currentProxyTarget = null;

        parsingFrame = h2frames[type].get();
        parsingFrame.length = len;
        parsingFrame.flags = flags;
        parsingFrame.streamId = streamId;
        parsingFrame.setFlags(flags);

        unsetPriorityFrameHeaderBytes(data);

        if (state == STATE_FIRST_SETTINGS_FRAME_HEADER) {
            if (parsingFrame.type != HttpFrameType.SETTINGS)
                throw new ExceptionWithoutStackTrace("expecting settings frame header but got " + parsingFrame.type);
        } else if (state == STATE_CONTINUATION_FRAME_HEADER) {
            if (parsingFrame.type != HttpFrameType.CONTINUATION)
                throw new ExceptionWithoutStackTrace("expecting headers frame header but got " + parsingFrame.type);
        }

        handleFrameHeader(data);

        if (parsingFrame.length == 0) {
            readFramePayload(ByteArray.allocate(0));
        } else {
            expectNewFrame = false; // expecting payload
            setLen(parsingFrame.length);
        }
    }

    private void handleFrameHeader(ByteArray data) throws Exception {
        switch (parsingFrame.type) {
            case SETTINGS:
                if (state == STATE_FIRST_SETTINGS_FRAME_HEADER) {
                    state = STATE_FIRST_SETTINGS_FRAME;
                } else {
                    state = STATE_OTHER_FRAME;
                }
                break;
            case DATA:
                determineProxiedConnection();
                proxyFrameHeader(data);
                state = STATE_DATA_FRAME;
                break;
            case HEADERS:
                state = STATE_HEADERS_FRAME;
                break;
            case CONTINUATION:
                if (state != STATE_CONTINUATION_FRAME_HEADER) {
                    throw new ExceptionWithoutStackTrace("unexpected continuation frame");
                }
                state = STATE_CONTINUATION_FRAME;
                break;
            default:
                state = STATE_OTHER_FRAME;
                break;
        }
    }

    private void unsetPriorityFrameHeaderBytes(ByteArray frameHeader) {
        if (parserMode) {
            return;
        }

        int len = frameHeader.uint24(0);
        byte flags = frameHeader.get(4);
        if (parsingFrame instanceof WithPriority) {
            if (((WithPriority) parsingFrame).priority()) {
                len -= (4 + 1);
            }
            flags &= ~0x20;
        }
        frameHeader.int24(0, len);
        frameHeader.set(4, flags);
    }

    private boolean noSession() {
        if (parsingFrame.streamId == 0) {
            return true;
        }
        if (!streamHolder.contains(parsingFrame.streamId)) {
            return true;
        }
        if (streamHolder.get(parsingFrame.streamId).getSession() == null) {
            assert Logger.lowLevelDebug("no session for stream " + parsingFrame.streamId);
            return true;
        }
        return false;
    }

    private void determineProxiedConnection() {
        if (parserMode) {
            return;
        }

        if (connId != 0) { // do not determine for backend connections
            return;
        }

        int streamId = parsingFrame.streamId;
        if (streamHolder.contains(streamId)) {
            Stream stream = streamHolder.get(streamId);
            assert Logger.lowLevelDebug("stream " + streamId + " already registered: " + stream);
            StreamSession session = stream.getSession();
            if (session == null) {
                assert Logger.lowLevelDebug("stream " + streamId + " session not set yet");
                ctx.currentProxyTarget = null;
            } else {
                ctx.currentProxyTarget = session.another(stream);
            }
        } else {
            assert Logger.lowLevelDebug("stream " + streamId + " not found, register it");
            currentPendingStream = streamHolder.register(streamId, initialSendingWindow, initialReceivingWindow);
            ctx.currentProxyTarget = null;
        }
    }

    private int getProxiedStreamId() throws Exception {
        int streamId = parsingFrame.streamId;
        Stream stream = streamHolder.get(streamId);
        if (stream == null) {
            String errMsg = "cannot find stream " + streamId + " for proxying frame header";
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, errMsg);
            throw new Exception(errMsg);
        }
        StreamSession session = stream.getSession();
        // otherwise
        if (session == null) {
            // it is expected to be null if it's the first frontend frame on a new stream
            if (connId == 0) {
                assert Logger.lowLevelDebug("proxied stream id is set to 1 for frontend frame " + parsingFrame);
                return 1;
            }
            String errMsg = "the stream " + streamId + " is not bond to any session, frame header cannot be proxied";
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, errMsg);
            throw new Exception(errMsg);
        }
        Stream another = session.another(stream);
        int proxiedStreamId = (int) another.streamId;
        assert Logger.lowLevelDebug("session found for proxied stream id, set to " + proxiedStreamId + " for " + parsingFrame);
        return proxiedStreamId;
    }

    private void proxyFrameHeader(ByteArray data) throws Exception {
        if (parserMode) {
            return;
        }

        dataToProxy = data.int32(5, getProxiedStreamId());
    }

    private void readFramePayload(ByteArray data) throws Exception {
        parsingFrame.setPayload(this, data);

        unsetPriority();

        handleFrame();

        if (parsingFrame.type == HttpFrameType.HEADERS
            && !((HeadersFrame) parsingFrame).endHeaders) {
            state = STATE_CONTINUATION_FRAME_HEADER;
        } else if (parsingFrame.type == HttpFrameType.CONTINUATION
            && !((ContinuationFrame) parsingFrame).endHeaders) {
            state = STATE_CONTINUATION_FRAME_HEADER;
        } else {
            state = STATE_FRAME_HEADER;
        }

        expectNewFrame = true;
        setLen(H2_HEADER_SIZE);
        lastParsedFrame = parsingFrame;
    }

    private void unsetPriority() {
        if (parserMode) {
            return;
        }

        HttpFrame frame = parsingFrame;
        if (frame instanceof WithPriority) {
            ((WithPriority) frame).unsetPriority();
        }
    }

    private void handleFrame() throws Exception {
        if (parserMode) {
            return;
        }

        if (state == STATE_FIRST_SETTINGS_FRAME) {
            handleFirstSettingsFrame();
        } else {
            switch (parsingFrame.type) {
                case HEADERS:
                    determineProxiedConnection();
                    handleCommonHeaders();
                    HeadersFrame headersFrame = (HeadersFrame) parsingFrame;
                    if (headersFrame.endStream) {
                        handleHeadersEndStream();
                    }
                    if (headersFrame.endHeaders) {
                        handleEndHeaders();
                    }
                    serializeToProxy(false);
                    break;
                case CONTINUATION:
                    determineProxiedConnection();
                    handleCommonHeaders();
                    ContinuationFrame continuationFrame = (ContinuationFrame) parsingFrame;
                    if (continuationFrame.endHeaders) {
                        handleEndHeaders();
                    }
                    determineProxiedConnection();
                    serializeToProxy(false);
                    break;
                case DATA:
                    Logger.shouldNotHappen("DATA frame payload should be direct proxied");
                    serializeToProxy(true);
                    break;
                case PUSH_PROMISE:
                    allocateStreamForPushPromise();
                    serializeToProxy(false);
                    break;
                case SETTINGS:
                    handleSettingsFrame();
                    break;
                case WINDOW_UPDATE:
                    handleWindowUpdate();
                    break;
                case PING:
                    handlePingFrame();
                    break;
                case RST_STREAM:
                    handleRSTStream();
                    break;
                case GOAWAY:
                    handleGoAway();
                    break;
                case PRIORITY:
                    // black hole
                    break;
                default:
                    Logger.shouldNotHappen("unexpected h2 frame: " + parsingFrame);
                    break;
            }
        }
    }

    private void serializeToProxy(boolean onlyPayload) throws Exception {
        HttpFrame frame = parsingFrame;
        int streamId = frame.streamId;
        if (streamId == 0) {
            String err = "frames used to call serializeToProxy must be attached to a stream";
            Logger.error(LogType.IMPROPER_USE, err);
            throw new Exception(err);
        }
        Stream stream = streamHolder.get(streamId);
        if (stream == null) {
            String err = "cannot proxy frame " + frame + " because the stream is not registered";
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, err);
            throw new Exception(err);
        }

        var session = stream.getSession();
        if (session == null) { // not established
            if (currentPendingStream == null) {
                String err = "cannot proxy frame " + frame + " because the target stream is not established";
                Logger.warn(LogType.INVALID_EXTERNAL_DATA, err);
                throw new Exception(err);
            } else {
                assert Logger.lowLevelDebug("session not established yet, hold for now");
                if (dataPendingToBeSent) {
                    String err = "cannot proxy frame " + frame + " because the target stream is not established";
                    Logger.warn(LogType.INVALID_EXTERNAL_DATA, err);
                    throw new Exception(err);
                }
                dataPendingToBeSent = true;
                dataToProxy = Processor.REQUIRE_CONNECTION;
                return;
            }
        }

        var another = session.another(stream);
        assert Logger.lowLevelDebug(another.streamId + "/" + another.ctx.connId);
        frame.streamId = (int) another.streamId;
        if (onlyPayload) {
            dataToProxy = frame.serializeH2Payload(another.ctx);
        } else {
            dataToProxy = frame.serializeH2(another.ctx);
        }

        if (connId == 0) { // frontend
            ctx.currentProxyTarget = another;
        }
    }

    private void handleFirstSettingsFrame() throws ExceptionWithoutStackTrace {
        assert Logger.lowLevelDebug("got first settings frame:" + parsingFrame);
        SettingsFrame settings = (SettingsFrame) parsingFrame;
        if (settings.ack) {
            throw new ExceptionWithoutStackTrace("the first settings frame must not be an ack");
        }
        doHandleSettingsFrame(settings);
        // in addition to ordinary settings frame handling
        // more settings may be processed in the settings frame
        if (settings.initialWindowSizeSet) {
            connectionSendingWindow += settings.initialWindowSize - initialSendingWindow;
            assert Logger.lowLevelDebug("current sendingWindow = " + connectionSendingWindow);
        }
    }

    private void sendSettingsAck() {
        produced = ACK_SETTINGS;
    }

    private void handleCommonHeaders() {
        String path = null;
        String host = null;

        parsingFrame.flags = 0; // clear flags, they will be set after serializing

        var headers = ((WithHeaders) parsingFrame).headers();
        for (var ite = headers.iterator(); ite.hasNext(); ) {
            Header h = ite.next();
            if (h.keyStr.equalsIgnoreCase("x-forwarded-for")) {
                ite.remove();
            } else if (h.keyStr.equalsIgnoreCase("x-client-port")) {
                ite.remove();
            } else if (connId == 0) { // is frontend, need to dispatch request by path and host
                if (h.keyStr.equalsIgnoreCase(":path")) {
                    path = new String(h.value);
                } else if (h.keyStr.equalsIgnoreCase("host")) {
                    host = new String(h.value);
                } else if (!parserMode &&
                    h.keyStr.equalsIgnoreCase(":method") && ByteArray.from(h.value).equals(ByteArray.from("CONNECT"))) {
                    // https://tools.ietf.org/html/rfc8441
                    ctx.willUpgradeConnection = true;
                }
            } else { // connId != 0
                if (!parserMode && ctx.willUpgradeConnection && // will upgrade
                    h.keyStr.equalsIgnoreCase(":status") && ByteArray.from(h.value).equals(ByteArray.from("200"))) {
                    ctx.upgradedConnection = true;
                }
            }
        }

        if (connId == 0) { // frontend
            assert Logger.lowLevelDebug("retrieved path = " + path);
            assert Logger.lowLevelDebug("retrieved host = " + host);

            Stream s = streamHolder.get(parsingFrame.streamId);
            if (s != null) {
                s.updatePathAndHost(path, host);
            }
        }
    }

    private void handleHeadersEndStream() {
        var headersFrame = (HeadersFrame) parsingFrame;
        Stream stream = streamHolder.get(headersFrame.streamId);
        if (stream != null) {
            assert Logger.lowLevelDebug("end stream when headers end");
            stream.endWhenHeadersTransmitted = true;
        }
    }

    private void handleEndHeaders() {
        var headers = ((WithHeaders) parsingFrame).headers();

        // add x-forwarded-for and x-client-port
        headers.add(new Header("x-forwarded-for", ctx.clientAddress.getAddress().formatToIPString()));
        headers.add(new Header("x-client-port", ctx.clientAddress.getPort() + ""));

        if (connId == 0) { // is frontend
            Stream s = streamHolder.get(parsingFrame.streamId);
            if (s != null) {
                ctx.currentHint = s.generateHint();
            }
        }

        Stream stream = streamHolder.get(parsingFrame.streamId);
        if (stream != null) {
            if (stream.endWhenHeadersTransmitted) {
                streamHolder.endStream(stream.streamId);
            }
        }
    }

    private void allocateStreamForPushPromise() throws Exception {
        if (connId == 0) {
            String errMsg = "got push promise frame on a frontend connection: " + parsingFrame;
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, errMsg);
            throw new Exception(errMsg);
        }
        PushPromiseFrame pushPromise = (PushPromiseFrame) parsingFrame;
        int promisedStreamId = pushPromise.promisedStreamId;
        if (streamHolder.contains(promisedStreamId)) {
            String errMsg = "promised stream " + promisedStreamId + " already exists when processing push-promise frame: " + pushPromise;
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, errMsg);
            throw new Exception(errMsg);
        }
        Stream frontendStream = ctx.frontend.streamHolder.createServerStream(ctx.frontend.initialSendingWindow, ctx.frontend.initialReceivingWindow);
        assert Logger.lowLevelDebug("allocated frontend promised stream is " + frontendStream.streamId);
        Stream backendStream = streamHolder.register(promisedStreamId, initialSendingWindow, initialReceivingWindow);
        new StreamSession(frontendStream, backendStream);

        pushPromise.promisedStreamId = (int) frontendStream.streamId;
    }

    private void handleSettingsFrame() {
        assert Logger.lowLevelDebug("got settings frame: " + parsingFrame);
        SettingsFrame settings = (SettingsFrame) parsingFrame;
        if (settings.ack) {
            assert Logger.lowLevelDebug("is settings ack, ignore");
            return;
        }
        doHandleSettingsFrame(settings);
    }

    private void doHandleSettingsFrame(SettingsFrame settings) {
        if (settings.headerTableSizeSet) {
            int tableSize = settings.headerTableSize;
            hpack.setEncoderMaxHeaderTableSize(tableSize);
        }
        if (settings.initialWindowSizeSet) {
            initialSendingWindow = settings.initialWindowSize;
        }
        // since the settings frame is received, we need to send back an ack
        sendSettingsAck();
    }

    private void handleWindowUpdate() {
        assert Logger.lowLevelDebug("got window update frame: " + parsingFrame);
        WindowUpdateFrame windowUpdate = (WindowUpdateFrame) parsingFrame;
        int incr = windowUpdate.windowSizeIncrement;
        if (windowUpdate.streamId == 0) {
            connectionSendingWindow += incr;
            assert Logger.lowLevelDebug("current sendingWindow = " + connectionSendingWindow);
        } else {
            Stream stream = streamHolder.get(windowUpdate.streamId);
            if (stream == null) {
                return; // no need to update
            }
            stream.sendingWindow += incr;
        }
    }

    private void handlePingFrame() {
        assert Logger.lowLevelDebug("got ping frame: " + parsingFrame);
        PingFrame ping = (PingFrame) parsingFrame;
        if (ping.ack) {
            assert Logger.lowLevelDebug("ignore ping ack frames");
            return;
        }
        assert Logger.lowLevelDebug("need to write back ping ack");
        ping.ack = false;
        ping.flags = 0; // clear flags
        produced = ping.serializeH2(this);
    }

    private void handleRSTStream() throws Exception {
        assert Logger.lowLevelDebug("got rst stream: " + parsingFrame);
        if (parsingFrame.streamId == 0) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA, "rst stream on stream 0 is invalid: " + parsingFrame);
            return;
        }
        if (noSession()) {
            return;
        }
        determineProxiedConnection();
        streamHolder.resetStream(parsingFrame.streamId);
        serializeToProxy(false);
    }

    private void handleGoAway() throws Exception {
        assert Logger.lowLevelDebug("got go away stream: " + parsingFrame);
        GoAwayFrame goaway = (GoAwayFrame) parsingFrame;
        if (goaway.lastStreamId == 0) {
            assert Logger.lowLevelDebug("goaway frame with lastStreamId == 0");
            return;
        }
        if (goaway.streamId == 0) {
            assert Logger.lowLevelDebug("goaway frame with streamId == 0");
            return; // ignore it
        }
        if (noSession()) {
            return;
        }
        determineProxiedConnection();
        serializeToProxy(false);
    }

    private ByteArray produced;

    private ByteArray produce() {
        ByteArray produced = this.produced;
        this.produced = null;
        return produced;
    }

    private Processor.ProxyDoneTODO proxyDone() {
        if (ctx.upgradedConnection) {
            return null;
        }
        if (state == STATE_DATA_FRAME) {
            assert Logger.lowLevelDebug("data frame proxy done");
            DataFrame data = (DataFrame) parsingFrame;
            if (data.endStream) {
                streamHolder.endStream(data.streamId);
            }
            decreaseReceivingWindow(data.streamId, data.length);
            state = STATE_FRAME_HEADER;
            setLen(H2_HEADER_SIZE);
            return Processor.ProxyDoneTODO.createFrameEnds();
        } else {
            Logger.shouldNotHappen("not expecting proxyDone called in state " + state);
            return null;
        }
    }

    private void decreaseReceivingWindow(int streamId, int length) {
        connectionReceivingWindow -= length;
        assert Logger.lowLevelDebug("current connection rcv wnd: " + connectionReceivingWindow);
        if (connectionReceivingWindow < initialReceivingWindow / 2) {
            sendWindowUpdate(null);
        }

        Stream stream = streamHolder.get(streamId);
        if (stream == null) {
            assert Logger.lowLevelDebug("stream " + streamId + " not found");
        } else {
            stream.receivingWindow -= length;
            assert Logger.lowLevelDebug("stream " + streamId + " rcv wnd: " + stream.receivingWindow);
            if (stream.receivingWindow < initialReceivingWindow / 2) {
                sendWindowUpdate(stream);
            }
        }
    }

    private void sendWindowUpdate(Stream stream) {
        assert Logger.lowLevelDebug("send window update called on " + (stream == null ? 0 : stream.streamId));
        WindowUpdateFrame windowUpdate = new WindowUpdateFrame();
        if (stream == null) {
            windowUpdate.streamId = 0;
            windowUpdate.windowSizeIncrement = initialReceivingWindow - connectionReceivingWindow;
            connectionReceivingWindow = initialReceivingWindow;
        } else {
            windowUpdate.streamId = (int) stream.streamId;
            windowUpdate.windowSizeIncrement = initialReceivingWindow - stream.receivingWindow;
            stream.receivingWindow = initialReceivingWindow;
        }
        produced = windowUpdate.serializeH2(this);
    }

    @Override
    public Processor.HandleTODO connected() {
        if (connId == 0) {
            return null;
        }
        // backend connections
        sendInitialFrame();
        Processor.HandleTODO ret = Processor.HandleTODO.create();
        ret.produce = produce();
        return ret;
    }

    @Override
    public Processor.HandleTODO remoteClosed() {
        return null; // nothing to be sent
    }

    @Override
    public Processor.DisconnectTODO disconnected(boolean exception) {
        // check streams owned by this connection
        // if all streams have been closed, it's safe to close this connection
        streamHolder.flush();
        if (streamHolder.isEmpty()) {
            assert Logger.lowLevelDebug("no active streams in this connection, close silently");
            return Processor.DisconnectTODO.createSilent();
        } else {
            assert Logger.lowLevelDebug("still has streams in this connection, close all");
            return null;
        }
    }

    // fields and methods for parserMode
    private boolean parserMode = false;
    private ByteArrayChannel chnl;
    private HttpFrame parsingFrame;
    private HttpFrame lastParsedFrame;

    public HPack getHPack() {
        return hpack;
    }

    public void setParserMode() {
        if ((connId == 0 && state != STATE_PREFACE) || (connId != 0 && state != STATE_FIRST_SETTINGS_FRAME_HEADER))
            throw new IllegalStateException("the method must be called when initialization");
        this.parserMode = true;
        init();
    }

    public boolean skipFirstSettingsFrame() {
        if (state == 0) {
            state = STATE_FRAME_HEADER;
            return true;
        }
        return false;
    }

    public int getState() {
        return state;
    }

    public boolean isIdle() {
        return parsingFrame == lastParsedFrame;
    }

    public HttpFrame getFrame() {
        return lastParsedFrame;
    }

    // only used in parserMode
    public ByteArray feed(RingBuffer inBuffer) throws Exception {
        inBuffer.writeTo(chnl);
        if (chnl.free() == 0) {
            // fully read
            return feed(chnl.getArray());
        }
        // need to remove the frame
        if (parsingFrame == lastParsedFrame) {
            parsingFrame = null; // the frame is not fully read yet
        }
        return null;
    }

    @Override
    public BinaryHttpSubContext castToBinaryHttpSubContext() {
        return this;
    }
}
