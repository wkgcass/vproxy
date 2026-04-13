package io.vproxy.vproxyx.websocks.unet;

import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.util.coll.Tuple;
import io.vproxy.vfd.FDs;
import io.vproxy.vfd.IPv4;
import io.vproxy.vfd.IPv6;
import io.vproxy.vproxyx.websocks.uot.UdpOverTcpSetup;

public class UNetSetup {
    private UNetSetup() {
    }

    public static FDs setup(boolean client, int port, String nicname, EventLoopGroup elg) throws Exception {
        return UdpOverTcpSetup.setup(client, port, nicname, elg, false);
    }

    public static Tuple<IPv4, IPv6> chooseIPs(String nicname) throws Exception {
        return UdpOverTcpSetup.chooseIPs(nicname);
    }
}
