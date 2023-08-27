package io.vproxy.vfd.posix;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class AEFiredEvent {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT_UNALIGNED.withName("fd"),
        ValueLayout.JAVA_INT_UNALIGNED.withName("mask")
    );
    public final MemorySegment MEMORY;

    private static final VarHandle fdVH = LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("fd")
    );

    public int getFd() {
        return (int) fdVH.get(MEMORY);
    }

    public void setFd(int fd) {
        fdVH.set(MEMORY, fd);
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

    public AEFiredEvent(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
    }

    public AEFiredEvent(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT.byteSize()));
    }

    public static class Array extends RefArray<AEFiredEvent> {
        public Array(MemorySegment buf) {
            super(buf, AEFiredEvent.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            this(allocator.allocate(AEFiredEvent.LAYOUT.byteSize() * len));
        }

        public Array(PNIBuf buf) {
            this(buf.get());
        }

        @Override
        protected AEFiredEvent construct(MemorySegment seg) {
            return new AEFiredEvent(seg);
        }

        @Override
        protected MemorySegment getSegment(AEFiredEvent value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<AEFiredEvent> {
        private Func(io.vproxy.pni.CallSite<AEFiredEvent> func) {
            super(func);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<AEFiredEvent> func) {
            return new Func(func);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected AEFiredEvent construct(MemorySegment seg) {
            return new AEFiredEvent(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.8
// sha256:932af2f98624b234d617fe4f815bb92a8263d769fd267c27b9a462e5ab7061bf
