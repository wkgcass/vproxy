package io.vproxy.base.util.bitwise;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.ToByteArray;
import io.vproxy.vfd.MacAddress;

public class BitwiseMacAddressMatcher extends BitwiseMatcher {
    private final ByteArray fullMask = ByteArray.from(new byte[]{-1, -1, -1, -1, -1, -1});

    private final MacAddress mac;

    BitwiseMacAddressMatcher(MacAddress mac) {
        this.mac = mac;
    }

    @Override
    public boolean match(ToByteArray input) {
        if (input instanceof MacAddress) {
            return input.equals(mac);
        }
        return super.match(input);
    }

    @Override
    public ByteArray getMatcher() {
        return mac.bytes;
    }

    @Override
    public ByteArray getMask() {
        return fullMask;
    }

    @Override
    public boolean maskAll() {
        return true;
    }
}
