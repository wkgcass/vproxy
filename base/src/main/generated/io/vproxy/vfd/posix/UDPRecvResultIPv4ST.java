package io.vproxy.vfd.posix;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class UDPRecvResultIPv4ST {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        io.vproxy.vfd.posix.SocketAddressIPv4ST.LAYOUT.withName("addr"),
        ValueLayout.JAVA_INT_UNALIGNED.withName("len")
    );
    public final MemorySegment MEMORY;

    private final io.vproxy.vfd.posix.SocketAddressIPv4ST addr;

    public io.vproxy.vfd.posix.SocketAddressIPv4ST getAddr() {
        return this.addr;
    }

    private static final VarHandle lenVH = LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("len")
    );

    public int getLen() {
        return (int) lenVH.get(MEMORY);
    }

    public void setLen(int len) {
        lenVH.set(MEMORY, len);
    }

    public UDPRecvResultIPv4ST(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        this.addr = new io.vproxy.vfd.posix.SocketAddressIPv4ST(MEMORY.asSlice(OFFSET, io.vproxy.vfd.posix.SocketAddressIPv4ST.LAYOUT.byteSize()));
        OFFSET += io.vproxy.vfd.posix.SocketAddressIPv4ST.LAYOUT.byteSize();
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
    }

    public UDPRecvResultIPv4ST(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT.byteSize()));
    }

    public static class Array extends RefArray<UDPRecvResultIPv4ST> {
        public Array(MemorySegment buf) {
            super(buf, UDPRecvResultIPv4ST.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            this(allocator.allocate(UDPRecvResultIPv4ST.LAYOUT.byteSize() * len));
        }

        public Array(PNIBuf buf) {
            this(buf.get());
        }

        @Override
        protected UDPRecvResultIPv4ST construct(MemorySegment seg) {
            return new UDPRecvResultIPv4ST(seg);
        }

        @Override
        protected MemorySegment getSegment(UDPRecvResultIPv4ST value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<UDPRecvResultIPv4ST> {
        private Func(io.vproxy.pni.CallSite<UDPRecvResultIPv4ST> func) {
            super(func);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<UDPRecvResultIPv4ST> func) {
            return new Func(func);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected UDPRecvResultIPv4ST construct(MemorySegment seg) {
            return new UDPRecvResultIPv4ST(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.8
// sha256:c517072ad33da9ace81c63c2d95ca7f46af6db963c3093d72bc75ab3719fd812
