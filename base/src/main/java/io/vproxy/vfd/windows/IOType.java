package io.vproxy.vfd.windows;

public enum IOType {
    ACCEPT(1),
    READ(2),
    WRITE(3),
    ;
    public final int code;

    IOType(int code) {
        this.code = code;
    }
}
