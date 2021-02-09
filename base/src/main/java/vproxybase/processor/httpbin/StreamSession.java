package vproxybase.processor.httpbin;

import vproxybase.util.Logger;

public class StreamSession {
    public final Stream frontend;
    public final Stream backend;

    public StreamSession(Stream frontend, Stream backend) {
        this.frontend = frontend;
        this.backend = backend;
        assert Logger.lowLevelDebug("stream session establishes: " + this);
        frontend.setSession(this);
        backend.setSession(this);
    }

    public Stream another(Stream stream) {
        if (stream == frontend) {
            return backend;
        }
        if (stream == backend) {
            return frontend;
        }
        String err = "stream " + stream + " is neither frontend nor backend";
        Logger.shouldNotHappen(err);
        throw new RuntimeException(err);
    }

    @Override
    public String toString() {
        return "StreamSession{" +
            "frontend=" + frontend.streamId + "/" + frontend.ctx.connId +
            ", backend=" + backend.streamId + "/" + backend.ctx.connId +
            '}';
    }
}
