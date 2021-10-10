package io.vproxy.base.util.bitwise;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.ToByteArray;
import io.vproxy.vfd.IP;

public class BitwiseNetworkMatcher extends BitwiseMatcher {
    private final Network network;

    BitwiseNetworkMatcher(Network network) {
        this.network = network;
    }

    @Override
    public boolean match(ToByteArray input) {
        if (input instanceof IP) {
            return network.contains((IP) input);
        }
        return super.match(input);
    }

    @Override
    public ByteArray getMatcher() {
        return network.getIp().toByteArray();
    }

    @Override
    public ByteArray getMask() {
        return network.getRawMaskByteArray();
    }

    @Override
    public boolean maskAll() {
        return false;
    }
}
