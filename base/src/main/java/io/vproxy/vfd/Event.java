package vproxy.vfd;

public enum Event {
    /**
     * readable, for jdk impl, it's OP_READ and/or OP_ACCEPT
     */
    READABLE("R"),
    /**
     * writable, for jdk impl, it's OP_WRITE and/or OP_CONNECT
     */
    WRITABLE("W"),
    ;
    final String name;

    Event(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
