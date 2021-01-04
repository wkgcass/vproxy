package vproxybase.processor.httpbin;

import vproxybase.util.LogType;
import vproxybase.util.Logger;

import java.util.HashMap;
import java.util.Map;

public class StreamHolder {
    private final BinaryHttpSubContext ctx;
    private final Map<Long, Stream> streams = new HashMap<>();
    // for http/2:
    private int lastClientStreamId = 0;
    private int lastServerStreamId = 0;

    public StreamHolder(BinaryHttpSubContext ctx) {
        this.ctx = ctx;
    }

    public Stream register(long streamId, int sendingWindow, int receivingWindow) {
        assert Logger.lowLevelDebug("registering stream " + streamId + " of conn " + ctx.connId);
        if (streams.containsKey(streamId)) {
            String err = "cannot create stream " + streamId + " in conn " + ctx.connId + ": already exists";
            Logger.shouldNotHappen(err);
            throw new RuntimeException(err);
        }
        if (streamId % 2 == 0) { // initiated from server
            if (streamId > lastServerStreamId) {
                lastServerStreamId = (int) streamId;
            }
        } else { // initiated from client
            if (streamId > lastClientStreamId) {
                lastClientStreamId = (int) streamId;
            }
        }
        Stream stream = new Stream(streamId, ctx, sendingWindow, receivingWindow);
        streams.put(streamId, stream);
        return stream;
    }

    public Stream createClientStream(int sendingWindow, int receivingWindow) {
        if (lastClientStreamId == 0) {
            lastClientStreamId = 1;
        } else {
            lastClientStreamId += 2;
        }
        int streamId = lastClientStreamId;
        return register(streamId, sendingWindow, receivingWindow);
    }

    public Stream createServerStream(int sendingWindow, int receivingWindow) {
        lastServerStreamId += 2;
        int streamId = lastServerStreamId;
        return register(streamId, sendingWindow, receivingWindow);
    }

    public boolean contains(long streamId) {
        return streams.containsKey(streamId);
    }

    public Stream get(long streamId) {
        Stream stream = streams.get(streamId);
        if (stream == null) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA,
                "cannot get stream " + streamId + " of conn " + ctx.connId + ": not found");
        }
        return stream;
    }

    public void terminate(long streamId) {
        assert Logger.lowLevelDebug("terminating stream " + streamId + " of conn " + ctx.connId);
        Stream stream = streams.remove(streamId);
        if (stream == null) {
            Logger.warn(LogType.INVALID_EXTERNAL_DATA,
                "cannot terminate stream " + streamId + " of conn " + ctx.connId + ": not found");
        }
    }
}
