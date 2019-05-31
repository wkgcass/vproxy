package net.cassite.vproxy.processor.http2;

public class Http2Frame {
    public int length;

    public enum Type {
        DATA, // 0x0
        HEADERS, // 0x1
        // PRIORITY, // 0x2
        // RST_STREAM, // 0x3
        SETTINGS, // 0x4
        PUSH_PROMISE, // 0x5
        // PING, // 0x6
        // GOAWAY, // 0x7
        // WINDOW_UPDATE, // 0x8
        CONTINUATION, // 0x9
        PROXY, // the frames that should be proxied
        IGNORE, // the frames that should be ignored
    }

    public Type type;

    boolean endHeaders; // 0x4
    boolean padded; // 0x8
    boolean priority; // 0x20
    boolean ack; // 0x1
    // other flags are ignored

    public Integer streamIdentifier;

    @Override
    public String toString() {
        return "Http2Frame{" +
            "length=" + length +
            ", type=" + type +
            ", flags=(padded=" + padded +
            ", priority=" + priority +
            ", ack=" + ack +
            "), streamIdentifier=" + streamIdentifier +
            '}';
    }
}
