package io.vproxy.vswitch.plugin;

import io.vproxy.vswitch.PacketBuffer;
import io.vproxy.vswitch.PacketFilterHelper;

public interface PacketFilter {
    FilterResult handle(PacketFilterHelper helper, PacketBuffer pkb);
}
