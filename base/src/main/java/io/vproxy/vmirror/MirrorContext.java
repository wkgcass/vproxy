package io.vproxy.vmirror;

import java.util.Random;

public class MirrorContext {
    private int seq = new Random().nextInt() & 0x00ffffff; // we only use 24bits when initializing;

    int getSeq() {
        return seq;
    }

    void incrSeq(int incr) {
        this.seq += incr;
    }
}
