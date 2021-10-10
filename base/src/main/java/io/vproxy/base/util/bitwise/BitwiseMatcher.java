package io.vproxy.base.util.bitwise;

import io.vproxy.base.util.ByteArray;
import io.vproxy.base.util.Network;
import io.vproxy.base.util.ToByteArray;
import io.vproxy.vfd.IP;
import io.vproxy.vfd.MacAddress;

import java.util.Objects;

abstract public class BitwiseMatcher {
    public static BitwiseMatcher from(ByteArray matcher) {
        if (matcher.length() == 6) {
            return new BitwiseMacAddressMatcher(new MacAddress(matcher));
        } else if (matcher.length() == 4) {
            return new BitwiseIPv4Matcher(IP.fromIPv4(matcher.toJavaArray()));
        } else if (matcher.length() == 16) {
            return new BitwiseIPv6Matcher(IP.fromIPv6(matcher.toJavaArray()));
        } else {
            return new BitwiseGeneralMatcher(matcher);
        }
    }

    public static BitwiseMatcher from(ByteArray matcher, ByteArray mask) {
        return from(matcher, mask, false);
    }

    public static BitwiseMatcher from(ByteArray matcher, ByteArray mask, boolean expandMask) {
        boolean maskAll = true;
        boolean maskPrefix = true;
        for (int i = 0; i < mask.length(); ++i) {
            byte b = mask.get(i);
            if (maskAll) {
                if (b != (byte) 0xff) {
                    maskAll = false;
                    if (b != (byte) 0b11111110 &&
                        b != (byte) 0b11111100 &&
                        b != (byte) 0b11111000 &&
                        b != (byte) 0b11110000 &&
                        b != (byte) 0b11100000 &&
                        b != (byte) 0b11000000 &&
                        b != (byte) 0b10000000 &&
                        b != (byte) 0
                    ) {
                        maskPrefix = false;
                    }
                }
            } else {
                if (b != 0) {
                    maskPrefix = false;
                }
            }
        }
        if (maskAll && matcher.length() == mask.length()) {
            return from(matcher);
        } else if (maskPrefix && (matcher.length() == 4 || matcher.length() == 16) &&
            ((matcher.length() == 4 && mask.length() == 4) || (matcher.length() == 16 && (mask.length() == 4 || mask.length() == 16)))
        ) {
            return new BitwiseNetworkMatcher(new Network(IP.from(matcher.toJavaArray()), mask));
        } else {
            return new BitwiseGeneralMatcher(matcher, mask, expandMask);
        }
    }

    public boolean match(byte[] bytes) {
        return match(ByteArray.from(bytes));
    }

    public boolean match(ToByteArray input) {
        return match(input.toByteArray());
    }

    public boolean match(ByteArray input) {
        int matcherLength = getMatcher().length();
        int maskLength = getMask().length();
        int inputLength = input.length();
        int i;
        for (i = 1; matcherLength - i >= 0 && maskLength - i >= 0 && inputLength - i >= 0; ++i) {
            byte matcherByte = getMatcher().get(matcherLength - i);
            byte maskByte = getMask().get(maskLength - i);
            byte inputByte = input.get(inputLength - i);

            if ((inputByte & maskByte) != matcherByte) {
                return false;
            }
        }
        if (inputLength >= matcherLength) {
            return true;
        }
        // input.length < matcher.length
        // need to check whether there are '1' bit in matcher
        for (; matcherLength - i >= 0 && maskLength - i >= 0; ++i) {
            byte matcherByte = getMatcher().get(matcherLength - i);
            if (matcherByte != 0) {
                return false;
            }
        }
        return true;
    }

    public abstract ByteArray getMatcher();

    public abstract ByteArray getMask();

    public abstract boolean maskAll();

    @Override
    public String toString() {
        if (maskAll()) {
            return "0x" + getMatcher().toHexString();
        } else {
            return "0x" + getMatcher().toHexString() + "/0x" + getMask().toHexString();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BitwiseMatcher matcher1 = (BitwiseMatcher) o;
        return getMatcher().equals(matcher1.getMatcher()) && getMask().equals(matcher1.getMask());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMatcher(), getMask());
    }
}
