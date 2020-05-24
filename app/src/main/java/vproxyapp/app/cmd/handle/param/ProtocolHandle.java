package vproxyapp.app.cmd.handle.param;

import vproxyapp.app.cmd.Command;
import vproxyapp.app.cmd.Param;
import vproxybase.connection.Protocol;
import vproxybase.util.exception.XException;

public class ProtocolHandle {
    private ProtocolHandle() {
    }

    public static void check(Command cmd) throws Exception {
        try {
            get(cmd);
        } catch (Exception e) {
            throw new XException("invalid format for " + Param.protocol.fullname);
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
