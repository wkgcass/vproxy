package vproxy.vswitch.plugin;

import vproxy.vswitch.PacketBuffer;
import vproxy.vswitch.PacketFilterHelper;

public interface PacketFilter {
    FilterResult handle(PacketFilterHelper helper, PacketBuffer pkb);
}
