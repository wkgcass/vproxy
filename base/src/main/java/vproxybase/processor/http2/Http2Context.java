package vproxybase.processor.http2;

import vfd.IP;
import vfd.IPPort;
import vproxybase.processor.Hint;
import vproxybase.processor.OOContext;
import vproxybase.util.ByteArray;
import vproxybase.util.Logger;

import java.util.HashMap;
import java.util.Map;

public class Http2Context extends OOContext<Http2SubContext> {
    boolean frontendHandshaking = true;
    boolean backendHandshaking = true;
    ByteArray clientHandshake = null; // PRI * ..... and SETTINGS frame as well

    boolean frontendSettingsSent = false;

    // the streamMap keys are the ids seen by the frontend
    final Map<Integer, Http2SubContext> streamMap = new HashMap<>(); // streamId => subCtx

    private int backendStreamId = 0;
    // the streamIdBack2Front is recorded in subCtx of the backend connection sub context
    final Map<Integer, Integer> streamIdFront2Back = new HashMap<>();

    final HPackTransformer hPackTransformer;

    ByteArray settingsFrameHeader = null; // this is a temporary field

    private String host;
    private boolean hintExists = false;
    private Hint hint;

    public Http2Context(IPPort clientAddress) {
        String clientIpStr = clientAddress.getAddress().formatToIPString();
        hPackTransformer = new HPackTransformer(Http2SubContext.SIZE_DEFAULT_HEADER_TABLE_SIZE,
            new Header[]{
                new Header("x-forwarded-for", clientIpStr),
                new Header("x-client-port", "" + clientAddress.getPort())
            }, host -> this.host = host);
    }

    @Override
    public int connection(Http2SubContext front) {
        Integer streamId = front.currentStreamId();
        Http2SubContext sub = streamMap.get(streamId);
        if (sub == null) {
            if (!frontendSettingsSent) { // the first settings frame should pass freely
                return -1;
            }
            if (front.hostHeaderRetrieved) {
                return -1;
            }
            return 0;
        } else {
            return sub.connId;
        }
    }

    @Override
    public Hint connectionHint(Http2SubContext front) {
        if (hintExists) {
            return hint;
        }
        String host = this.host;
        if (host == null) {
            return null;
        }
        assert Logger.lowLevelDebug("got Host header: " + host);
        if (host.contains(":")) { // remove port in Host header
            host = host.substring(0, host.lastIndexOf(":"));
        }
        if (IP.isIpLiteral(host)) {
            hintExists = true;
            return null; // no hint if requesting directly using ip
        }
        if (host.startsWith("www.")) { // remove www. convention
            host = host.substring("www.".length());
        }
        hintExists = true;
        hint = new Hint(host);
        return hint;
    }

    @Override
    public void chosen(Http2SubContext front, Http2SubContext subCtx) {
        Integer streamId = front.currentStreamId();
        assert Logger.lowLevelDebug("recording a stream " + streamId + " => " + subCtx.connId);
        streamMap.put(streamId, subCtx);
    }

    void tryRecordStream(Http2SubContext subCtx) {
        Integer streamId = subCtx.currentStreamId();
        if (subCtx.connId != 0 /* not the frontend connection */ && !streamMap.containsKey(streamId)) {
            assert Logger.lowLevelDebug("recording a new stream from sub context (backend)" + streamId + " => " + subCtx.connId);
            streamMap.put(streamId, subCtx);
        }
    }

    Integer nextServerStreamId() {
        backendStreamId += 2;
        return backendStreamId;
    }
}
