package vfd;

public enum Event {
    /**
     * readable, for jdk impl, it's OP_READ and/or OP_ACCEPT
     */
    READABLE,
    /**
     * writable, for jdk impl, it's OP_WRITE and/or OP_CONNECT
     */
    WRITABLE,
    ;
}
