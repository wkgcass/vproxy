package io.vproxy.vfd.posix;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class AEFiredExtra {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.ADDRESS_UNALIGNED.withName("ud"),
        ValueLayout.JAVA_INT_UNALIGNED.withName("mask"),
        MemoryLayout.sequenceLayout(4L, ValueLayout.JAVA_BYTE) /* padding */
    );
    public final MemorySegment MEMORY;

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
        this(ALLOCATOR.allocate(LAYOUT.byteSize()));
    }

    public static class Array extends RefArray<AEFiredExtra> {
        public Array(MemorySegment buf) {
            super(buf, AEFiredExtra.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            this(allocator.allocate(AEFiredExtra.LAYOUT.byteSize() * len));
        }

        public Array(PNIBuf buf) {
            this(buf.get());
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
        protected AEFiredExtra construct(MemorySegment seg) {
            return new AEFiredExtra(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.11
// sha256:15c6247915f3f7b14c22cad104c8405d5d848e24424aa9473da94f2d351ddd5b
