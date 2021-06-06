package vproxy.base.selector.wrap.udp;

import vproxy.base.selector.SelectorEventLoop;
import vproxy.vfd.FDProvider;

import java.io.IOException;

public class UDPFDs implements UDPBasedFDs {
    private static final UDPFDs instance = new UDPFDs();

    public static UDPFDs get() {
        return instance;
    }

    @Override
    public ServerDatagramFD openServerSocketFD(SelectorEventLoop loop) throws IOException {
        return new ServerDatagramFD(FDProvider.get().openDatagramFD(), loop);
    }

    @Override
    public DatagramSocketFDWrapper openSocketFD(SelectorEventLoop loop) throws IOException {
        return new DatagramSocketFDWrapper(FDProvider.get().openDatagramFD());
    }
}
