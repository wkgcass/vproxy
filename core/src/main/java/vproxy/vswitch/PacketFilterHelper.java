package vproxy.vswitch;

import vproxy.base.util.ratelimit.RateLimiter;
import vproxy.vpacket.AbstractPacket;
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

    public boolean ratelimitByBitsPerSecond(PacketBuffer pkb, RateLimiter rl) {
        int bytes;
        if (pkb.pktBuf != null) {
            bytes = pkb.pktBuf.length();
        } else {
            bytes = pkb.pkt.getRawPacket(AbstractPacket.FLAG_CHECKSUM_UNNECESSARY).length();
        }
        int bits = bytes * 8;
        return rl.acquire(bits);
    }

    public boolean ratelimitByPacketsPerSecond(@SuppressWarnings("unused") PacketBuffer pkb, RateLimiter rl) {
        return rl.acquire(1);
    }
}
