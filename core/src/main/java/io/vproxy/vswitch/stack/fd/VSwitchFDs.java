package io.vproxy.vswitch.stack.fd;

import io.vproxy.vfd.*;

public class VSwitchFDs implements FDs {
    private final VSwitchFDContext ctx;

    public VSwitchFDs(VSwitchFDContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public SocketFD openSocketFD() {
        return new VSwitchSocketFD(ctx);
    }

    @Override
    public ServerSocketFD openServerSocketFD() {
        return new VSwitchServerSocketFD(ctx);
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
