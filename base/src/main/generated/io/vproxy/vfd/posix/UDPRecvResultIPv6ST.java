package io.vproxy.vfd.posix;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class UDPRecvResultIPv6ST extends AbstractNativeObject implements NativeObject {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        io.vproxy.vfd.posix.SocketAddressIPv6ST.LAYOUT.withName("addr"),
        MemoryLayout.sequenceLayout(2L, ValueLayout.JAVA_BYTE) /* padding */,
        ValueLayout.JAVA_INT.withName("len")
    ).withByteAlignment(4);
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
    }

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
        this(ALLOCATOR.allocate(LAYOUT));
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        SB.append("UDPRecvResultIPv6ST{\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("addr => ");
            PanamaUtils.nativeObjectToString(getAddr(), SB, INDENT + 4, VISITED, CORRUPTED_MEMORY);
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("len => ");
            SB.append(getLen());
        }
        SB.append("\n");
        SB.append(" ".repeat(INDENT)).append("}@").append(Long.toString(MEMORY.address(), 16));
    }

    public static class Array extends RefArray<UDPRecvResultIPv6ST> {
        public Array(MemorySegment buf) {
            super(buf, UDPRecvResultIPv6ST.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            super(allocator, UDPRecvResultIPv6ST.LAYOUT, len);
        }

        public Array(PNIBuf buf) {
            super(buf, UDPRecvResultIPv6ST.LAYOUT);
        }

        @Override
        protected void elementToString(io.vproxy.vfd.posix.UDPRecvResultIPv6ST ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "UDPRecvResultIPv6ST.Array";
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
        protected String toStringTypeName() {
            return "UDPRecvResultIPv6ST.Func";
        }

        @Override
        protected UDPRecvResultIPv6ST construct(MemorySegment seg) {
            return new UDPRecvResultIPv6ST(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.15
// sha256:68089cd5891c215dc3cb4e91d1a5327a91ca37f999d3e94e93c6fa26b21b384e
