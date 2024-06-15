package io.vproxy.vfd.posix;

import io.vproxy.pni.*;
import io.vproxy.pni.hack.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class SocketAddressIPv6ST extends AbstractNativeObject implements NativeObject {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        MemoryLayout.sequenceLayout(40L, ValueLayout.JAVA_BYTE).withName("ip"),
        ValueLayout.JAVA_SHORT.withName("port")
    ).withByteAlignment(2);
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
    }

    private final MemorySegment ip;

    public String getIp() {
        return PanamaHack.getUtf8String(ip, 0);
    }

    public void setIp(String ip) {
        PanamaHack.setUtf8String(this.ip, 0, ip);
    }

    private static final VarHandleW portVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("port")
        )
    );

    public short getPort() {
        return portVH.getShort(MEMORY);
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
        this(ALLOCATOR.allocate(LAYOUT));
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        SB.append("SocketAddressIPv6ST{\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("ip => ");
            if (CORRUPTED_MEMORY) SB.append("<?>");
            else SB.append(getIp());
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("port => ");
            SB.append(getPort());
        }
        SB.append("\n");
        SB.append(" ".repeat(INDENT)).append("}@").append(Long.toString(MEMORY.address(), 16));
    }

    public static class Array extends RefArray<SocketAddressIPv6ST> {
        public Array(MemorySegment buf) {
            super(buf, SocketAddressIPv6ST.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            super(allocator, SocketAddressIPv6ST.LAYOUT, len);
        }

        public Array(PNIBuf buf) {
            super(buf, SocketAddressIPv6ST.LAYOUT);
        }

        @Override
        protected void elementToString(io.vproxy.vfd.posix.SocketAddressIPv6ST ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "SocketAddressIPv6ST.Array";
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
        protected String toStringTypeName() {
            return "SocketAddressIPv6ST.Func";
        }

        @Override
        protected SocketAddressIPv6ST construct(MemorySegment seg) {
            return new SocketAddressIPv6ST(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.20
// sha256:82f7ff14b0a0ec51edd9fb4962d63710f8ce660d8bb1064b8933aba6f51bddf4
