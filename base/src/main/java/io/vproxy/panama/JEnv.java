package io.vproxy.panama;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

public class JEnv {
    public static final MemoryLayout layout = MemoryLayout.structLayout(
        ExceptionStruct.layout.withName("ex"),
        MemoryLayout.unionLayout(
            ValueLayout.JAVA_INT_UNALIGNED.withName("return_i"),
            ValueLayout.JAVA_LONG_UNALIGNED.withName("return_j"),
            ValueLayout.JAVA_BOOLEAN.withName("return_z"),
            ValueLayout.ADDRESS_UNALIGNED.withName("return_p")
        ).withName("union0")
    );

    private final Arena arena;
    private final MemorySegment seg;
    private final ExceptionStruct ex;

    public JEnv() {
        this.arena = Arena.ofConfined();
        this.seg = arena.allocate(layout.byteSize());
        this.ex = new ExceptionStruct(seg.asSlice(0, ExceptionStruct.layout.byteSize()));
    }

    public MemorySegment getSegment() {
        return seg;
    }

    public ExceptionStruct ex() {
        return ex;
    }

    private static final VarHandle return_iVH = layout.varHandle(
        MemoryLayout.PathElement.groupElement("union0"),
        MemoryLayout.PathElement.groupElement("return_i")
    );

    public int returnInt() {
        return (int) return_iVH.get(seg);
    }

    private static final VarHandle return_jVH = layout.varHandle(
        MemoryLayout.PathElement.groupElement("union0"),
        MemoryLayout.PathElement.groupElement("return_j")
    );

    public long returnLong() {
        return (long) return_jVH.get(seg);
    }

    private static final VarHandle return_zVH = layout.varHandle(
        MemoryLayout.PathElement.groupElement("union0"),
        MemoryLayout.PathElement.groupElement("return_z")
    );

    public boolean returnBool() {
        return (boolean) return_zVH.get(seg);
    }

    private static final VarHandle return_pVH = layout.varHandle(
        MemoryLayout.PathElement.groupElement("union0"),
        MemoryLayout.PathElement.groupElement("return_p")
    );

    public MemorySegment returnPointer() {
        var seg = (MemorySegment) return_pVH.get(this.seg);
        if (seg.address() == 0) {
            return null;
        }
        return seg;
    }

    public void reset() {
        return_pVH.set(seg, MemorySegment.NULL);
    }
}
