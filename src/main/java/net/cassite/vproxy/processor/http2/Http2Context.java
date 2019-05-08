package net.cassite.vproxy.processor.http2;

import net.cassite.vproxy.processor.OOContext;
import net.cassite.vproxy.util.Logger;
import net.cassite.vproxy.util.ByteArray;

import java.util.HashMap;
import java.util.Map;

public class Http2Context extends OOContext<Http2SubContext> {
    boolean frontendHandshaking = true;
    boolean backendHandshaking = true;
    ByteArray clientHandshake = null; // PRI * ..... and SETTINGS frame as well

    // the streamMap keys are the ids seen by the frontend
    private Map<Integer, Http2SubContext> streamMap = new HashMap<>();

    private int backendStreamId = 0;
    // the streamIdBack2Front is recorded in subCtx of the backend connection sub context
    Map<Integer, Integer> streamIdFront2Back = new HashMap<>();

    ByteArray settingsFrameHeader = null; // this is a temporary field

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

    void tryRecordStream(Integer streamId, Http2SubContext subCtx) {
        if (!streamMap.containsKey(streamId)) {
            streamMap.put(streamId, subCtx);
        }
    }

    Integer nextServerStreamId() {
        backendStreamId += 2;
        return backendStreamId;
    }
}
