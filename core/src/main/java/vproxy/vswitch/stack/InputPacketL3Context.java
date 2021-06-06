package vproxy.vswitch.stack;

import vproxy.vfd.IP;

import java.util.Collection;

public class InputPacketL3Context extends InputPacketL2Context {
    public final Collection<IP> matchedIps;
    public final boolean isUnicast;

    final InputPacketL2Context l2ctx;

    public InputPacketL3Context(InputPacketL2Context l2ctx, Collection<IP> matchedIps, boolean isUnicast) {
        super(l2ctx);
        this.matchedIps = matchedIps;
        this.isUnicast = isUnicast;

        this.l2ctx = l2ctx;
    }

    @Override
    public String toString() {
        return "InputPacketL3Context{" + super.toString() +
            ", matchedIps=" + matchedIps +
            ", isUnicast=" + isUnicast +
            '}';
    }
}
