package io.vproxy.vproxyx.nexus;

import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.msquic.QuicStream;
import io.vproxy.pni.Allocator;
import io.vproxy.pni.array.ShortArray;

public class NexusUtils {
    private NexusUtils() {
    }

    public static boolean isNotValidNodeName(String name) {
        var chars = name.toCharArray();
        for (var c : chars) {
            if ('a' <= c && c <= 'z')
                continue;
            if ('A' <= c && c <= 'Z')
                continue;
            if ('0' <= c && c <= '9')
                continue;
            if (c == '_' || c == '-')
                continue;
            return true;
        }
        return false;
    }

    public static void setControlStreamPriority(QuicStream stream) {
        try (var allocator = Allocator.ofConfined()) {
            short priority = (short) 0xffff;
            var n = new ShortArray(allocator, 1);
            n.set(0, priority);
            int err = stream.setParam(0x08000003, 2, n.MEMORY);
            if (err != 0) {
                Logger.warn(LogType.SYS_ERROR, "setting priority to " + (priority & 0xffff) + " failed with errcode=" + err);
            }
        }
    }
}
