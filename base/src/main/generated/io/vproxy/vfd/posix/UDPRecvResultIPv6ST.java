package io.vproxy.vfd.posix;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class UDPRecvResultIPv6ST {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        io.vproxy.vfd.posix.SocketAddressIPv6ST.LAYOUT.withName("addr"),
        MemoryLayout.sequenceLayout(2L, ValueLayout.JAVA_BYTE) /* padding */,
        ValueLayout.JAVA_INT_UNALIGNED.withName("len")
    );
    public final MemorySegment MEMORY;

    private final io.vproxy.vfd.posix.SocketAddressIPv6ST addr;

    public io.vproxy.vfd.posix.SocketAddressIPv6ST getAddr() {
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

    public UDPRecvResultIPv6ST(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        this.addr = new io.vproxy.vfd.posix.SocketAddressIPv6ST(MEMORY.asSlice(OFFSET, io.vproxy.vfd.posix.SocketAddressIPv6ST.LAYOUT.byteSize()));
        OFFSET += io.vproxy.vfd.posix.SocketAddressIPv6ST.LAYOUT.byteSize();
        OFFSET += 2; /* padding */
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
    }

    public UDPRecvResultIPv6ST(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT.byteSize()));
    }

    public static class Array extends RefArray<UDPRecvResultIPv6ST> {
        public Array(MemorySegment buf) {
            super(buf, UDPRecvResultIPv6ST.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            this(allocator.allocate(UDPRecvResultIPv6ST.LAYOUT.byteSize() * len));
        }

        public Array(PNIBuf buf) {
            this(buf.get());
        }

        @Override
        protected UDPRecvResultIPv6ST construct(MemorySegment seg) {
            return new UDPRecvResultIPv6ST(seg);
        }

        @Override
        protected MemorySegment getSegment(UDPRecvResultIPv6ST value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<UDPRecvResultIPv6ST> {
        private Func(io.vproxy.pni.CallSite<UDPRecvResultIPv6ST> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<UDPRecvResultIPv6ST> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<UDPRecvResultIPv6ST> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<UDPRecvResultIPv6ST> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected UDPRecvResultIPv6ST construct(MemorySegment seg) {
            return new UDPRecvResultIPv6ST(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.11
// sha256:7da7dc052315d35c41a81499e00832088397db8378f37fc93e8c6fdfab52a1a2
