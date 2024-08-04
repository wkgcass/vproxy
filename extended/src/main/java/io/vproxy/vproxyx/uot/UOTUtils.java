package io.vproxy.vproxyx.uot;

import io.vproxy.base.util.ByteArray;

public class UOTUtils {
    public static final byte TYPE_CONN_ID = 1;
    public static final byte TYPE_PACKET = 2;

    public static final int HEADER_LEN = 1 /*type*/ + 1 /*flags*/ + 2 /*len*/;

    public static final int LARGE_PACKET_LIMIT = 1000;
    public static final int SMALL_PACKET_LIMIT = 200;

    private UOTUtils() {
    }

    public static ByteArray buildHeader(byte type, int len) {
        var ret = ByteArray.allocate(HEADER_LEN);
        ret.set(0, type);
        ret.int16(2, len);

        return ret;
    }
}
