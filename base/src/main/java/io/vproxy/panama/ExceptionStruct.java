package io.vproxy.panama;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

public class ExceptionStruct {
    public static final MemoryLayout layout = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT_UNALIGNED.withName("type"),
        MemoryLayout.sequenceLayout(128, ValueLayout.JAVA_BYTE).withName("message")
    );
    private final MemorySegment seg;

    public ExceptionStruct(MemorySegment seg) {
        this.seg = seg;
    }

    private static final VarHandle typeVH = layout.varHandle(
        MemoryLayout.PathElement.groupElement("type")
    );

    public int type() {
        return (int) typeVH.get(seg);
    }

    public ExceptionStruct type(int v) {
        typeVH.set(seg, (byte) v);
        return this;
    }

    public String message() {
        return seg.getUtf8String(ValueLayout.JAVA_INT.byteSize());
    }
}
