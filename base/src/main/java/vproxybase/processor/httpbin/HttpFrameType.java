package vproxybase.processor.httpbin;

public enum HttpFrameType {
    PREFACE(-1, -1),

    DATA(0, 0),
    HEADERS(1, 1),
    PRIORITY(2, -1),
    RST_STREAM(3, -1),
    CANCEL_PUSH(-1, 3),
    SETTINGS(4, 4),
    PUSH_PROMISE(5, 5),
    PING(6, -1),
    GOAWAY(7, 7),
    WINDOW_UPDATE(8, -1),
    CONTINUATION(9, -1),
    MAX_PUSH_ID(-1, 0xd),
    ;
    public final int h2type;
    public final int h3type;

    HttpFrameType(int h2type, int h3type) {
        this.h2type = h2type;
        this.h3type = h3type;
    }

    public int type(HttpVersion version) {
        if (version == HttpVersion.HTTP2) {
            return h2type;
        } else if (version == HttpVersion.HTTP3) {
            return h3type;
        } else {
            throw new IllegalArgumentException("unknown http version " + version);
        }
    }
}
