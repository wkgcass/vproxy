package vproxybase.processor.httpbin;

import vproxybase.processor.Hint;
import vproxybase.util.Logger;

public class Stream {
    public final long streamId; // quic requires 'long'
    public final BinaryHttpSubContext ctx;
    private StreamSession session;
    public int sendingWindow;
    public int receivingWindow;
    public boolean end;
    public boolean endWhenHeadersTransmitted; // set by headers frame, checked by headers/continuation frames
    public long removeTime; // the timestamp when this stream will be removed

    private String path;
    private String host;

    public Stream(long streamId, BinaryHttpSubContext ctx, int sendingWindow, int receivingWindow) {
        this.streamId = streamId;
        this.ctx = ctx;
        this.sendingWindow = sendingWindow;
        this.receivingWindow = receivingWindow;
    }

    public void setSession(StreamSession session) {
        if (this.session != null) {
            String err = "stream " + streamId + " already bond to session " + this.session;
            Logger.shouldNotHappen(err);
            throw new RuntimeException(err);
        }
        this.session = session;
    }

    public StreamSession getSession() {
        return session;
    }


    public void updatePathAndHost(String path, String host) {
        if (path != null) {
            this.path = path;
        }
        if (host != null) {
            this.host = host;
        }
    }

    public Hint generateHint() {
        return Hint.ofHostUri(host, path);
    }

    @Override
    public String toString() {
        return "Stream{" +
            "streamId=" + streamId +
            ", ctx=" + ctx +
            ", session=" + session +
            ", sendingWindow=" + sendingWindow +
            ", receivingWindow=" + receivingWindow +
            '}';
    }
}
