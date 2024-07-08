package io.vproxy.vfd.windows;

public enum IOType {
    READ(1),
    WRITE(2),
    ACCEPT(3),
    CONNECT(4),
    SEND_DISCARD(5), // ref = Tuple<Allocator, WinSocket>
    NOTIFY(6), // ref = Allocator
    ;
    public final int code;

    IOType(int code) {
        this.code = code;
    }
}
