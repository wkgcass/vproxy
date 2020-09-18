package vswitch.stack;

import vpacket.AbstractIpPacket;

public class InputPacketL4Context extends InputPacketL3Context {
    public final AbstractIpPacket inputIpPacket;

    public InputPacketL4Context(InputPacketL3Context l3ctx) {
        super(l3ctx.l2ctx, l3ctx.matchedIps, l3ctx.isUnicast);
        this.inputIpPacket = (AbstractIpPacket) l3ctx.inputPacket.getPacket();
    }
}
