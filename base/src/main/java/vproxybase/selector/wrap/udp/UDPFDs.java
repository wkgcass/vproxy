package vproxybase.selector.wrap.udp;

import vfd.FDProvider;
import vproxybase.selector.SelectorEventLoop;

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
