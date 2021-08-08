package vproxy.base.util.bitwise;

import vproxy.base.util.Utils;

import java.util.Objects;

public class BitwiseIntMatcher {
    private final int matcher;
    private final int mask;

    public BitwiseIntMatcher(int matcher) {
        this.matcher = matcher;
        this.mask = -1;
    }

    public BitwiseIntMatcher(int matcher, int mask) {
        this.matcher = matcher;
        this.mask = mask;

        if ((matcher & mask) != matcher) {
            throw new IllegalArgumentException("the matcher does not correspond to the mask");
        }
    }

    public boolean match(int n) {
        return (n & mask) == matcher;
    }

    public int getMatcher() {
        return matcher;
    }

    public int getMask() {
        return mask;
    }

    public boolean maskAll() {
        return mask == -1;
    }

    @Override
    public String toString() {
        if (maskAll()) {
            return Utils.toHexString(matcher);
        } else {
            return Utils.toHexString(matcher) + "/" + Utils.toHexString(mask);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BitwiseIntMatcher that = (BitwiseIntMatcher) o;
        return matcher == that.matcher && mask == that.mask;
    }

    @Override
    public int hashCode() {
        return Objects.hash(matcher, mask);
    }
}
