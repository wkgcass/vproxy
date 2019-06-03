package net.cassite.vproxy.processor.http2;

import net.cassite.vproxy.processor.OOContext;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.ByteArray;
import net.cassite.vproxy.util.Utils;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class Http2Context extends OOContext<Http2SubContext> {
    boolean frontendHandshaking = true;
    boolean backendHandshaking = true;
    ByteArray clientHandshake = null; // PRI * ..... and SETTINGS frame as well

    // the streamMap keys are the ids seen by the frontend
    final Map<Integer, Http2SubContext> streamMap = new HashMap<>(); // streamId => subCtx

    private int backendStreamId = 0;
    // the streamIdBack2Front is recorded in subCtx of the backend connection sub context
    final Map<Integer, Integer> streamIdFront2Back = new HashMap<>();

    final HPackTransformer hPackTransformer;

    ByteArray settingsFrameHeader = null; // this is a temporary field

    final String clientIpStr;

    public Http2Context(InetSocketAddress clientAddress) {
        clientIpStr = Utils.ipStr(clientAddress.getAddress().getAddress());
        hPackTransformer = new HPackTransformer(Http2SubContext.SIZE_DEFAULT_HEADER_TABLE_SIZE,
            new Header[]{
                new Header("x-forwarded-for", clientIpStr)
            });
    }

    @Override
    public int connection(Http2SubContext front) {
        Integer streamId = front.currentStreamId();
        Http2SubContext sub = streamMap.get(streamId);
        if (sub == null)
            return -1;
        else
            return sub.connId;
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
