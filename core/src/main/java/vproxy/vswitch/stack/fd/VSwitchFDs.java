package vproxy.vswitch.stack.fd;

import vproxy.vfd.*;

public class VSwitchFDs implements FDs {
    private final VSwitchFDContext ctx;

    public VSwitchFDs(VSwitchFDContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public SocketFD openSocketFD() {
        throw new UnsupportedOperationException("not supported");
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
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
