package io.vproxy.vfd.posix;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class SocketAddressIPv4ST {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT_UNALIGNED.withName("ip"),
        ValueLayout.JAVA_SHORT_UNALIGNED.withName("port"),
        MemoryLayout.sequenceLayout(2L, ValueLayout.JAVA_BYTE) /* padding */
    );
    public final MemorySegment MEMORY;

    private static final VarHandle ipVH = LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("ip")
    );

    public int getIp() {
        return (int) ipVH.get(MEMORY);
    }

    public void setIp(int ip) {
        ipVH.set(MEMORY, ip);
    }

    private static final VarHandle portVH = LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("port")
    );

    public short getPort() {
        return (short) portVH.get(MEMORY);
    }

    public void setPort(short port) {
        portVH.set(MEMORY, port);
    }

    public SocketAddressIPv4ST(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
        OFFSET += ValueLayout.JAVA_SHORT_UNALIGNED.byteSize();
        OFFSET += 2; /* padding */
    }

    public SocketAddressIPv4ST(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT.byteSize()));
    }

    public static class Array extends RefArray<SocketAddressIPv4ST> {
        public Array(MemorySegment buf) {
            super(buf, SocketAddressIPv4ST.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            this(allocator.allocate(SocketAddressIPv4ST.LAYOUT.byteSize() * len));
        }

        public Array(PNIBuf buf) {
            this(buf.get());
        }

        @Override
        protected SocketAddressIPv4ST construct(MemorySegment seg) {
            return new SocketAddressIPv4ST(seg);
        }

        @Override
        protected MemorySegment getSegment(SocketAddressIPv4ST value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<SocketAddressIPv4ST> {
        private Func(io.vproxy.pni.CallSite<SocketAddressIPv4ST> func) {
            super(func);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<SocketAddressIPv4ST> func) {
            return new Func(func);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected SocketAddressIPv4ST construct(MemorySegment seg) {
            return new SocketAddressIPv4ST(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.8
// sha256:e1b8f1a34dac93da0bc39175c7cfc3f14f5a9c9193cacd8860bc52b83b0baf5f
