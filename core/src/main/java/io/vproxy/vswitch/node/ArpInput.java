package io.vproxy.vswitch.node;

import io.vproxy.vswitch.PacketBuffer;

public class ArpInput extends AbstractArpInput {
    public ArpInput() {
        super("arp-input");
    }

    @Override
    protected HandleResult preHandle(PacketBuffer pkb) {
        return HandleResult.PASS;
    }
}
