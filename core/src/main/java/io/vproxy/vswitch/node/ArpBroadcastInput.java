package io.vproxy.vswitch.node;

import io.vproxy.vswitch.PacketBuffer;

public class ArpBroadcastInput extends AbstractArpInput {
    public ArpBroadcastInput() {
        super("arp-broadcast-input");
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }
}
