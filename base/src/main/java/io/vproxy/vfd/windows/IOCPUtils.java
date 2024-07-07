package io.vproxy.vfd.windows;

import java.lang.foreign.MemorySegment;

public class IOCPUtils {
    private IOCPUtils() {
    }

    public static VIOContext getIOContextOf(Overlapped overlapped) {
        return new VIOContext(MemorySegment.ofAddress(overlapped.MEMORY.address() - 8));
    }
}
