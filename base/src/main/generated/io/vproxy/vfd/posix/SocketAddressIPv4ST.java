package io.vproxy.vfd.posix;

import io.vproxy.pni.*;
import io.vproxy.pni.hack.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class SocketAddressIPv4ST extends AbstractNativeObject implements NativeObject {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("ip"),
        ValueLayout.JAVA_SHORT.withName("port"),
        MemoryLayout.sequenceLayout(2L, ValueLayout.JAVA_BYTE) /* padding */
    ).withByteAlignment(4);
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
    }

    private static final VarHandleW ipVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("ip")
        )
    );

    public int getIp() {
        return ipVH.getInt(MEMORY);
    }

    public void setIp(int ip) {
        ipVH.set(MEMORY, ip);
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

    public SocketAddressIPv4ST(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
        OFFSET += ValueLayout.JAVA_SHORT_UNALIGNED.byteSize();
        OFFSET += 2; /* padding */
    }

    public SocketAddressIPv4ST(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT));
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        SB.append("SocketAddressIPv4ST{\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("ip => ");
            SB.append(getIp());
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("port => ");
            SB.append(getPort());
        }
        SB.append("\n");
        SB.append(" ".repeat(INDENT)).append("}@").append(Long.toString(MEMORY.address(), 16));
    }

    public static class Array extends RefArray<SocketAddressIPv4ST> {
        public Array(MemorySegment buf) {
            super(buf, SocketAddressIPv4ST.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            super(allocator, SocketAddressIPv4ST.LAYOUT, len);
        }

        public Array(PNIBuf buf) {
            super(buf, SocketAddressIPv4ST.LAYOUT);
        }

        @Override
        protected void elementToString(io.vproxy.vfd.posix.SocketAddressIPv4ST ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "SocketAddressIPv4ST.Array";
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

        private Func(io.vproxy.pni.CallSite<SocketAddressIPv4ST> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<SocketAddressIPv4ST> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<SocketAddressIPv4ST> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "SocketAddressIPv4ST.Func";
        }

        @Override
        protected SocketAddressIPv4ST construct(MemorySegment seg) {
            return new SocketAddressIPv4ST(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.20
// sha256:6bc82fb7da365a2bcb2cdba1cfd30876e04d3c3f3a9997da7ffcd7262ef69e67
