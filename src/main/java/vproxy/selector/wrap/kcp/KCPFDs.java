package vproxy.selector.wrap.kcp;

import vproxy.selector.SelectorEventLoop;
import vproxy.selector.wrap.udp.UDPBasedFDs;
import vproxy.selector.wrap.udp.UDPFDs;

import java.io.IOException;

public class KCPFDs implements UDPBasedFDs {
    private static final KCPFDs instance = new KCPFDs();

    public static KCPFDs get() {
        return instance;
    }

    @Override
    public KCPServerSocketFD openServerSocketFD(SelectorEventLoop loop) throws IOException {
        return new KCPServerSocketFD(
            UDPFDs.get().openServerSocketFD(loop),
            loop
        );
    }

    @Override
    public KCPSocketFD openSocketFD(SelectorEventLoop loop) throws IOException {
        return new KCPSocketFD(
            UDPFDs.get().openSocketFD(loop),
            loop
        );
    }
}
