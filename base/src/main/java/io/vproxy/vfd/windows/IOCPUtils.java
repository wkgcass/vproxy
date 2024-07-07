package io.vproxy.vfd.windows;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public class IOCPUtils {
    private IOCPUtils() {
    }

    public static VIOContext getIOContextOf(Overlapped overlapped) {
        return new VIOContext(MemorySegment.ofAddress(overlapped.MEMORY.address() - 8));
    }

    public static void setPointer(VIOContext ctx) {
        ctx.setPtr(MemorySegment.ofAddress(ctx.MEMORY.address() +
            ValueLayout.ADDRESS.byteSize() +
            Overlapped.LAYOUT.byteSize()));
    }
}
