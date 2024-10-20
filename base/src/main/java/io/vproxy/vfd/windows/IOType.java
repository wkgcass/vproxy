package io.vproxy.vfd.windows;

public enum IOType {
    READ(1), // ref = WinSocket
    WRITE(2),
    ACCEPT(3),
    CONNECT(4),
    DISCARD(5), // ref = Tuple<Allocator, WinSocket>
    NOTIFY(6), // ref = null
    ;
    public final int code;

    IOType(int code) {
        this.code = code;
    }
}
