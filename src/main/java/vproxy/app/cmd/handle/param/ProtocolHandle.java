package vproxy.app.cmd.handle.param;

import vproxy.app.cmd.Command;
import vproxy.app.cmd.Param;
import vproxy.connection.Protocol;

public class ProtocolHandle {
    private ProtocolHandle() {
    }

    public static void check(Command cmd) throws Exception {
        try {
            get(cmd);
        } catch (Exception e) {
            throw new Exception("invalid format for " + Param.protocol.fullname);
        }
    }

    public static Protocol get(Command cmd) {
        String protocol = cmd.args.get(Param.protocol);
        switch (protocol) {
            case "TCP":
            case "tcp":
                return Protocol.TCP;
            case "UDP":
            case "udp":
                return Protocol.UDP;
            default:
                throw new IllegalArgumentException();
        }
    }
}
