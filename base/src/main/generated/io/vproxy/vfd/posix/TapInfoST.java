package io.vproxy.vfd.posix;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class TapInfoST {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        MemoryLayout.sequenceLayout(16L, ValueLayout.JAVA_BYTE).withName("devName"),
        ValueLayout.JAVA_INT_UNALIGNED.withName("fd")
    );
    public final MemorySegment MEMORY;

    private final MemorySegment devName;

    public String getDevName() {
        return devName.getUtf8String(0);
    }

    public void setDevName(String devName) {
        this.devName.setUtf8String(0, devName);
    }

    private static final VarHandle fdVH = LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("fd")
    );

    public int getFd() {
        return (int) fdVH.get(MEMORY);
    }

    public void setFd(int fd) {
        fdVH.set(MEMORY, fd);
    }

    public TapInfoST(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        this.devName = MEMORY.asSlice(OFFSET, 16);
        OFFSET += 16;
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
    }

    public TapInfoST(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT.byteSize()));
    }

    public static class Array extends RefArray<TapInfoST> {
        public Array(MemorySegment buf) {
            super(buf, TapInfoST.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            this(allocator.allocate(TapInfoST.LAYOUT.byteSize() * len));
        }

        public Array(PNIBuf buf) {
            this(buf.get());
        }

        @Override
        protected TapInfoST construct(MemorySegment seg) {
            return new TapInfoST(seg);
        }

        @Override
        protected MemorySegment getSegment(TapInfoST value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<TapInfoST> {
        private Func(io.vproxy.pni.CallSite<TapInfoST> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<TapInfoST> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<TapInfoST> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<TapInfoST> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected TapInfoST construct(MemorySegment seg) {
            return new TapInfoST(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.11
// sha256:d6abf6c252f028d0730dd73132ce970bfe6160d3c1ef5f1d34a0a66617fda5f4
