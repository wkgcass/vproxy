package io.vproxy.vfd.windows;

public enum IOType {
    READ(1),
    WRITE(2),
    ACCEPT(3),
    CONNECT(4),
    NOTIFY(5),
    ;
    public final int code;

    IOType(int code) {
        this.code = code;
    }
}
