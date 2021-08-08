package vproxy.vswitch;

import vproxy.vswitch.iface.Iface;
import vproxy.vswitch.plugin.FilterResult;

public class PacketFilterHelper {
    public PacketFilterHelper(
        SwitchContext.SendingPacket sendPacketFunc
    ) {
        this.sendPacketFunc = sendPacketFunc;
    }

    public interface SendingPacket {
        void send(PacketBuffer pkb, Iface iface);
    }

    private final SwitchContext.SendingPacket sendPacketFunc;

    public void sendPacket(PacketBuffer pkb, Iface toIface) {
        if (toIface == null) {
            return;
        }
        sendPacketFunc.send(pkb, toIface);
    }

    public FilterResult redirect(PacketBuffer pkb, Iface iface) {
        if (iface == null) return FilterResult.DROP;
        pkb.devredirect = iface;
        return FilterResult.REDIRECT;
    }
}
