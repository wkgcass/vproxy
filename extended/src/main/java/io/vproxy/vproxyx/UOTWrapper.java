package io.vproxy.vproxyx;

import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.connection.Protocol;
import io.vproxy.base.selector.SelectorEventLoop;
import io.vproxy.base.util.Utils;
import io.vproxy.base.util.thread.VProxyThread;
import io.vproxy.vfd.IPPort;
import io.vproxy.vproxyx.uot.FromTcpToUdp;
import io.vproxy.vproxyx.uot.FromUdpToTcp;

public class UOTWrapper {
    private static final String HELP_STR = """
        Usage:
            from=<tcp|udp>:[ip:]<port>
            to=  <udp|tcp>:<ip>:<port>
        """.trim();

    public static void main0(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println(HELP_STR);
            return;
        }
        Protocol fromProtocol = null;
        IPPort fromIPPort = null;
        Protocol toProtocol = null;
        IPPort toIPPort = null;
        for (var arg : args) {
            if (arg.equals("-h") || arg.equals("--help") || arg.equals("-help") || arg.equals("help")) {
                System.out.println(HELP_STR);
                return;
            }
            if (arg.startsWith("from=")) {
                var value = arg.substring("from=".length());
                if (value.startsWith("tcp:")) {
                    fromProtocol = Protocol.TCP;
                } else if (value.startsWith("udp:")) {
                    fromProtocol = Protocol.UDP;
                } else {
                    throw new IllegalArgumentException("unknown protocol of `from=" + value + "`");
                }
                value = value.substring(4);
                if (!Utils.isPortInteger(value) && !IPPort.validL4AddrStr(value)) {
                    throw new IllegalArgumentException(value + " is not a valid port number nor a valid ipport in `from=...`");
                }
                if (Utils.isPortInteger(value)) {
                    fromIPPort = new IPPort("127.0.0.1", Integer.parseInt(value));
                } else {
                    fromIPPort = new IPPort(value);
                }
            } else if (arg.startsWith("to=")) {
                var value = arg.substring("to=".length());
                if (value.startsWith("tcp:")) {
                    toProtocol = Protocol.TCP;
                } else if (value.startsWith("udp:")) {
                    toProtocol = Protocol.UDP;
                } else {
                    throw new IllegalArgumentException("unknown protocol of `to=" + value + "`");
                }
                value = value.substring(4);
                if (!IPPort.validL4AddrStr(value)) {
                    throw new IllegalArgumentException(value + " is not a valid ipport int `to=...`");
                }
                toIPPort = new IPPort(value);
            } else {
                throw new IllegalArgumentException("unknown option `" + arg + "`");
            }
        }

        if (fromProtocol == null) {
            throw new IllegalArgumentException("`from=...` is not specified");
        }
        if (toProtocol == null) {
            throw new IllegalArgumentException("`to=...` is not specified");
        }
        if (fromProtocol == toProtocol) {
            throw new IllegalArgumentException("from and to protocols should not be the same");
        }

        var loop = SelectorEventLoop.open();
        var netLoop = new NetEventLoop(loop);
        loop.loop(r -> VProxyThread.create(r, "uot-wrapper"));

        if (fromProtocol == Protocol.UDP) {
            new FromUdpToTcp(netLoop, fromIPPort, toIPPort).start();
        } else {
            new FromTcpToUdp(netLoop, fromIPPort, toIPPort).start();
        }
    }
}
