package io.vproxy.vswitch.stack.fd;

import io.vproxy.vfd.*;

public class VSwitchFDs implements FDs {
    private final VSwitchFDContext ctx;

    private int pshMultiplier = 1;
    private int ackMultiplier = 1;

    public VSwitchFDs(VSwitchFDContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Set the PSH packet send multiplier.
     * Each PSH packet will be sent this many times.
     * Default is 1 (no duplication).
     */
    public void setPshMultiplier(int pshMultiplier) {
        this.pshMultiplier = pshMultiplier;
    }

    public int getPshMultiplier() {
        return pshMultiplier;
    }

    /**
     * Set the ACK packet send multiplier.
     * Each ACK packet will be sent this many times.
     * Default is 1 (no duplication).
     */
    public void setAckMultiplier(int ackMultiplier) {
        this.ackMultiplier = ackMultiplier;
    }

    public int getAckMultiplier() {
        return ackMultiplier;
    }

    @Override
    public SocketFD openSocketFD() {
        return new VSwitchSocketFD(ctx, this);
    }

    @Override
    public ServerSocketFD openServerSocketFD() {
        return new VSwitchServerSocketFD(ctx, this);
    }

    @Override
    public DatagramFD openDatagramFD() {
        return new VSwitchDatagramFD(ctx);
    }

    @Override
    public FDSelector openSelector() {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public boolean isV4V6DualStack() {
        return false;
    }
}
