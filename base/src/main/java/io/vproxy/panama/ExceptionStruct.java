package io.vproxy.panama;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

public class ExceptionStruct {
    public static final MemoryLayout layout = MemoryLayout.structLayout(
        ValueLayout.ADDRESS_UNALIGNED.withName("type"),
        MemoryLayout.sequenceLayout(4096, ValueLayout.JAVA_BYTE).withName("message")
    );
    private final MemorySegment seg;

    public ExceptionStruct(MemorySegment seg) {
        this.seg = seg;
    }

    private static final VarHandle typeVH = layout.varHandle(
        MemoryLayout.PathElement.groupElement("type")
    );

    public String type() {
        var type = (MemorySegment) typeVH.get(seg);
        if (type.address() == 0) {
            return null;
        }
        return type.reinterpret(Integer.MAX_VALUE).getUtf8String(0);
    }

    public void resetType() {
        typeVH.set(seg, MemorySegment.NULL);
    }

    public String message() {
        return seg.getUtf8String(ValueLayout.JAVA_INT.byteSize());
    }
}
