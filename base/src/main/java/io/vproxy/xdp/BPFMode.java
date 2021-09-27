package vproxy.xdp;

public enum BPFMode {
    SKB(1 << 1),
    DRIVER(1 << 2),
    HARDWARE(1 << 3),
    ;
    public final int mode;

    BPFMode(int mode) {
        this.mode = mode;
    }
}
