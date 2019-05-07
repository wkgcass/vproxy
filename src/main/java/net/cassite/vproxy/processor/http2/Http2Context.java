package net.cassite.vproxy.processor.http2;

import net.cassite.vproxy.processor.OOContext;
import net.cassite.vproxy.util.Logger;

import java.util.HashMap;
import java.util.Map;

public class Http2Context extends OOContext<Http2SubContext> {
    boolean handshaking = true;
    byte[] clientHandshake = null; // PRI * ..... and SETTINGS frame as well
    private Map<Integer, Http2SubContext> streamMap = new HashMap<>();

    byte[] settingsFrameHeader = null; // temporary field

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
        int streamId = front.currentStreamId();
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
}
