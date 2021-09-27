package io.vproxy.base.selector.wrap.udp;

import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.vfd.FDProvider;
import io.vproxy.vfd.FDs;

import java.io.IOException;

public class UDPFDs implements UDPBasedFDs {
    private static final UDPFDs instance = new UDPFDs(FDProvider.get().getProvided());

    public static UDPFDs getDefault() {
        return instance;
    }

    private final FDs fds;

    public UDPFDs(FDs fds) {
        this.fds = fds;
    }

    @Override
    public ServerDatagramFD openServerSocketFD(SelectorEventLoop loop) throws IOException {
        return new ServerDatagramFD(fds.openDatagramFD(), loop);
    }

    @Override
    public DatagramSocketFDWrapper openSocketFD(SelectorEventLoop loop) throws IOException {
        return new DatagramSocketFDWrapper(fds.openDatagramFD());
    }
}
