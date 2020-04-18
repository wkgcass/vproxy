package vproxy.poc;

import vproxy.component.elgroup.EventLoopGroup;
import vproxy.selector.SelectorEventLoop;
import vswitch.Switch;
import vswitch.VXLanAdaptorHandlers;

import java.net.InetSocketAddress;

public class TestVxlan {
    private static final int swListen = 19881;
    private static final String pass = "p@sSw0rD";
    private static final int timeout = 10 * 3600 * 1000;
    private static final String vxlanAddr1 = "100.64.0.3";
    private static final int vxlanPort1 = 8472;
    private static final String vxlanAddr2 = "100.64.0.4";
    private static final int vxlanPort2 = 4789;

    public static void main(String[] args) throws Exception {
        EventLoopGroup elg = new EventLoopGroup("elg0");
        elg.add("el0");
        SelectorEventLoop loop = elg.next().getSelectorEventLoop();

        // Switch sw = new Switch("sw0", new InetSocketAddress(swListen), pass, elg, timeout, timeout);
        // sw.start();

        VXLanAdaptorHandlers.launchGeneralAdaptor(loop,
            new InetSocketAddress("127.0.0.1", swListen),
            new InetSocketAddress(vxlanAddr1, vxlanPort1),
            new InetSocketAddress(vxlanPort1),
            pass);
        VXLanAdaptorHandlers.launchGeneralAdaptor(loop,
            new InetSocketAddress("127.0.0.1", swListen),
            new InetSocketAddress(vxlanAddr2, vxlanPort2),
            new InetSocketAddress(vxlanPort2),
            pass);
    }
}
