package io.vproxy.vfd.posix;

import io.vproxy.pni.*;
import io.vproxy.pni.hack.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class UDPRecvResultIPv4ST extends AbstractNativeObject implements NativeObject {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        io.vproxy.vfd.posix.SocketAddressIPv4ST.LAYOUT.withName("addr"),
        ValueLayout.JAVA_INT.withName("len")
    ).withByteAlignment(4);
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
    }

    private final io.vproxy.vfd.posix.SocketAddressIPv4ST addr;

    public io.vproxy.vfd.posix.SocketAddressIPv4ST getAddr() {
        return this.addr;
    }

    private static final VarHandleW lenVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("len")
        )
    );

    public int getLen() {
        return lenVH.getInt(MEMORY);
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
        this(ALLOCATOR.allocate(LAYOUT));
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        SB.append("UDPRecvResultIPv4ST{\n");
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

    public static class Array extends RefArray<UDPRecvResultIPv4ST> {
        public Array(MemorySegment buf) {
            super(buf, UDPRecvResultIPv4ST.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            super(allocator, UDPRecvResultIPv4ST.LAYOUT, len);
        }

        public Array(PNIBuf buf) {
            super(buf, UDPRecvResultIPv4ST.LAYOUT);
        }

        @Override
        protected void elementToString(io.vproxy.vfd.posix.UDPRecvResultIPv4ST ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "UDPRecvResultIPv4ST.Array";
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

        private Func(io.vproxy.pni.CallSite<UDPRecvResultIPv4ST> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<UDPRecvResultIPv4ST> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<UDPRecvResultIPv4ST> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "UDPRecvResultIPv4ST.Func";
        }

        @Override
        protected UDPRecvResultIPv4ST construct(MemorySegment seg) {
            return new UDPRecvResultIPv4ST(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.20
// sha256:6f67a608af15fc4678c43299f3828e7e3dbe5dbac26eafcc6112bb2ba9c92f99
