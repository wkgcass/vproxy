package io.vproxy.base.util.bitwise;

import io.vproxy.base.util.Utils;
import io.vproxy.base.util.misc.IntMatcher;

import java.util.Objects;

public class BitwiseIntMatcher implements IntMatcher {
    private final int matcher;
    private final int mask;

    private BitwiseIntMatcher(int matcher) {
        this.matcher = matcher;
        this.mask = -1;
    }

    private BitwiseIntMatcher(int matcher, int mask) {
        this.matcher = matcher;
        this.mask = mask;

        if ((matcher & mask) != matcher) {
            throw new IllegalArgumentException("the matcher does not correspond to the mask");
        }
    }

    public static BitwiseIntMatcher from(int matcher) {
        return new BitwiseIntMatcher(matcher);
    }

    public static BitwiseIntMatcher from(int matcher, int mask) {
        return new BitwiseIntMatcher(matcher, mask);
    }

    @Override
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
