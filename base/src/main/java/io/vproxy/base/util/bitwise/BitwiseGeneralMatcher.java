package io.vproxy.base.util.bitwise;

import io.vproxy.base.util.ByteArray;

public class BitwiseGeneralMatcher extends BitwiseMatcher {
    private final ByteArray matcher;
    private final ByteArray mask;
    private final boolean maskAll;

    BitwiseGeneralMatcher(ByteArray matcher) {
        this.matcher = matcher;
        this.mask = ByteArray.allocate(matcher.length());
        for (int i = 0; i < mask.length(); ++i) {
            mask.set(i, (byte) 0xff);
        }
        this.maskAll = true;
    }

    BitwiseGeneralMatcher(ByteArray matcher, ByteArray mask) {
        this(matcher, mask, false);
    }

    BitwiseGeneralMatcher(ByteArray matcher, ByteArray mask, boolean expandMask) {
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

    @Override
    public ByteArray getMatcher() {
        return matcher;
    }

    @Override
    public ByteArray getMask() {
        return mask;
    }

    @Override
    public boolean maskAll() {
        return maskAll;
    }
}
