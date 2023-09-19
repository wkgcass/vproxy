package io.vproxy.vfd.posix;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class SocketAddressIPv6ST {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        MemoryLayout.sequenceLayout(40L, ValueLayout.JAVA_BYTE).withName("ip"),
        ValueLayout.JAVA_SHORT_UNALIGNED.withName("port")
    );
    public final MemorySegment MEMORY;

    private final MemorySegment ip;

    public String getIp() {
        return ip.getUtf8String(0);
    }

    public void setIp(String ip) {
        this.ip.setUtf8String(0, ip);
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

    public SocketAddressIPv6ST(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        this.ip = MEMORY.asSlice(OFFSET, 40);
        OFFSET += 40;
        OFFSET += ValueLayout.JAVA_SHORT_UNALIGNED.byteSize();
    }

    public SocketAddressIPv6ST(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT.byteSize()));
    }

    public static class Array extends RefArray<SocketAddressIPv6ST> {
        public Array(MemorySegment buf) {
            super(buf, SocketAddressIPv6ST.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            this(allocator.allocate(SocketAddressIPv6ST.LAYOUT.byteSize() * len));
        }

        public Array(PNIBuf buf) {
            this(buf.get());
        }

        @Override
        protected SocketAddressIPv6ST construct(MemorySegment seg) {
            return new SocketAddressIPv6ST(seg);
        }

        @Override
        protected MemorySegment getSegment(SocketAddressIPv6ST value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<SocketAddressIPv6ST> {
        private Func(io.vproxy.pni.CallSite<SocketAddressIPv6ST> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<SocketAddressIPv6ST> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<SocketAddressIPv6ST> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<SocketAddressIPv6ST> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected SocketAddressIPv6ST construct(MemorySegment seg) {
            return new SocketAddressIPv6ST(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.11
// sha256:1ebc3fece005f7a913835d29f4a08f737d64fcfd0d23a46f77f4a5a28f9dac84
