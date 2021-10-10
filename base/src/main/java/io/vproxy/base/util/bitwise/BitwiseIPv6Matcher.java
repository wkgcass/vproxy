package io.vproxy.base.util.bitwise;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.ToByteArray;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.IPv6;

public class BitwiseIPv6Matcher extends BitwiseMatcher {
    private final ByteArray fullMask = ByteArray.from(new byte[]{
        -1, -1, -1, -1,
        -1, -1, -1, -1,
        -1, -1, -1, -1,
        -1, -1, -1, -1
    });

    private final IPv6 ip;

    BitwiseIPv6Matcher(IPv6 ip) {
        this.ip = ip;
    }

    @Override
    public boolean match(ToByteArray input) {
        if (input instanceof IP) {
            if (input instanceof IPv6) {
                return input.equals(ip);
            }
            return false;
        }
        return super.match(input);
    }

    @Override
    public ByteArray getMatcher() {
        return ip.bytes;
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
