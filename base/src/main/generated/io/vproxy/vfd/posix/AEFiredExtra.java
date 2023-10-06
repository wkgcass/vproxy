package io.vproxy.vfd.posix;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class AEFiredExtra extends AbstractNativeObject implements NativeObject {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.ADDRESS.withName("ud"),
        ValueLayout.JAVA_INT.withName("mask"),
        MemoryLayout.sequenceLayout(4L, ValueLayout.JAVA_BYTE) /* padding */
    ).withByteAlignment(8);
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
    }

    private static final VarHandle udVH = LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("ud")
    );

    public MemorySegment getUd() {
        var SEG = (MemorySegment) udVH.get(MEMORY);
        if (SEG.address() == 0) return null;
        return SEG;
    }

    public void setUd(MemorySegment ud) {
        if (ud == null) {
            udVH.set(MEMORY, MemorySegment.NULL);
        } else {
            udVH.set(MEMORY, ud);
        }
    }

    private static final VarHandle maskVH = LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("mask")
    );

    public int getMask() {
        return (int) maskVH.get(MEMORY);
    }

    public void setMask(int mask) {
        maskVH.set(MEMORY, mask);
    }

    public AEFiredExtra(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        OFFSET += ValueLayout.ADDRESS_UNALIGNED.byteSize();
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
        OFFSET += 4; /* padding */
    }

    public AEFiredExtra(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT));
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        SB.append("AEFiredExtra{\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("ud => ");
            SB.append(PanamaUtils.memorySegmentToString(getUd()));
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("mask => ");
            SB.append(getMask());
        }
        SB.append("\n");
        SB.append(" ".repeat(INDENT)).append("}@").append(Long.toString(MEMORY.address(), 16));
    }

    public static class Array extends RefArray<AEFiredExtra> {
        public Array(MemorySegment buf) {
            super(buf, AEFiredExtra.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            super(allocator, AEFiredExtra.LAYOUT, len);
        }

        public Array(PNIBuf buf) {
            super(buf, AEFiredExtra.LAYOUT);
        }

        @Override
        protected void elementToString(io.vproxy.vfd.posix.AEFiredExtra ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "AEFiredExtra.Array";
        }

        @Override
        protected AEFiredExtra construct(MemorySegment seg) {
            return new AEFiredExtra(seg);
        }

        @Override
        protected MemorySegment getSegment(AEFiredExtra value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<AEFiredExtra> {
        private Func(io.vproxy.pni.CallSite<AEFiredExtra> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<AEFiredExtra> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<AEFiredExtra> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<AEFiredExtra> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "AEFiredExtra.Func";
        }

        @Override
        protected AEFiredExtra construct(MemorySegment seg) {
            return new AEFiredExtra(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.15
// sha256:4a2ba5fd8b3d221458d3944fdcadde9e7f683d1992ee1d36d3a316bf0b17eb63
