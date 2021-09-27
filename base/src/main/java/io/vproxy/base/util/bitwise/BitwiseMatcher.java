package vproxy.base.util.bitwise;

import vproxy.base.util.ByteArray;

import java.util.Objects;

public class BitwiseMatcher {
    private final ByteArray matcher;
    private final ByteArray mask;
    private final boolean maskAll;

    public BitwiseMatcher(ByteArray matcher) {
        this.matcher = matcher;
        this.mask = ByteArray.allocate(matcher.length());
        for (int i = 0; i < mask.length(); ++i) {
            mask.set(i, (byte) 0xff);
        }
        this.maskAll = true;
    }

    public BitwiseMatcher(ByteArray matcher, ByteArray mask) {
        this(matcher, mask, false);
    }

    public BitwiseMatcher(ByteArray matcher, ByteArray mask, boolean expandMask) {
        this.matcher = matcher;

        if (matcher.length() != mask.length()) {
            if (matcher.length() < mask.length() || !expandMask) {
                throw new IllegalArgumentException("matcher and mask length not the same: matcher: " + matcher.length() + ", mask: " + mask.length());
            }
            var mask2 = ByteArray.allocateInitZero(matcher.length());
            int i;
            for (i = 1; mask.length() - i >= 0; --i) {
                mask2.set(mask2.length() - i, mask.get(mask.length() - i));
            }
            mask = mask2;
        }
        this.mask = mask;

        boolean maskAll = true;
        for (int i = 0; i < matcher.length(); ++i) {
            byte b = matcher.get(i);
            byte m = mask.get(i);
            if ((b & m) != b) {
                throw new IllegalArgumentException("the matcher does not correspond to the mask");
            }
            if (m != ((byte) 0xff)) {
                maskAll = false;
            }
        }
        this.maskAll = maskAll;
    }

    public boolean match(byte[] bytes) {
        return match(ByteArray.from(bytes));
    }

    public boolean match(ByteArray input) {
        int matcherLength = matcher.length();
        int maskLength = mask.length();
        int inputLength = input.length();
        int i;
        for (i = 1; matcherLength - i >= 0 && maskLength - i >= 0 && inputLength - i >= 0; ++i) {
            byte matcherByte = matcher.get(matcherLength - i);
            byte maskByte = mask.get(maskLength - i);
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
            byte matcherByte = matcher.get(matcherLength - i);
            if (matcherByte != 0) {
                return false;
            }
        }
        return true;
    }

    public ByteArray getMatcher() {
        return matcher;
    }

    public ByteArray getMask() {
        return mask;
    }

    public boolean maskAll() {
        return maskAll;
    }

    @Override
    public String toString() {
        if (maskAll) {
            return "0x" + matcher.toHexString();
        } else {
            return "0x" + matcher.toHexString() + "/0x" + mask.toHexString();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BitwiseMatcher matcher1 = (BitwiseMatcher) o;
        return matcher.equals(matcher1.matcher) && mask.equals(matcher1.mask);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matcher, mask);
    }
}
