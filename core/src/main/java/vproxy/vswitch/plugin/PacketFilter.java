package vproxy.vswitch.plugin;

import vproxy.vswitch.PacketBuffer;
import vproxy.vswitch.SwitchContext;

public interface PacketFilter {
    FilterResult handle(SwitchContext swCtx, PacketBuffer pkb);
}
