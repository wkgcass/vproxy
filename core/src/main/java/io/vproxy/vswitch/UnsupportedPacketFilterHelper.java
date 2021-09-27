package io.vproxy.vswitch;

import io.vproxy.base.util.ratelimit.RateLimiter;
import io.vproxy.vswitch.iface.Iface;
import io.vproxy.vswitch.plugin.FilterResult;

public class UnsupportedPacketFilterHelper extends PacketFilterHelper {
    public static final UnsupportedPacketFilterHelper instance = new UnsupportedPacketFilterHelper();

    private UnsupportedPacketFilterHelper() {
        super(null);
    }

    @Override
    public void sendPacket(PacketBuffer pkb, Iface toIface) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FilterResult redirect(PacketBuffer pkb, Iface iface) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean ratelimitByBitsPerSecond(PacketBuffer pkb, RateLimiter rl) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean ratelimitByPacketsPerSecond(@SuppressWarnings("unused") PacketBuffer pkb, RateLimiter rl) {
        throw new UnsupportedOperationException();
    }
}
