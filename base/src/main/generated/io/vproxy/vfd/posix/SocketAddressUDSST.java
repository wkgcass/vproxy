package io.vproxy.vfd.posix;

import io.vproxy.pni.*;
import io.vproxy.pni.hack.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class SocketAddressUDSST extends AbstractNativeObject implements NativeObject {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        MemoryLayout.sequenceLayout(4096L, ValueLayout.JAVA_BYTE).withName("path")
    );
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
    }

    private final MemorySegment path;

    public String getPath() {
        return PanamaHack.getUtf8String(path, 0);
    }

    public void setPath(String path) {
        PanamaHack.setUtf8String(this.path, 0, path);
    }

    public SocketAddressUDSST(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        this.path = MEMORY.asSlice(OFFSET, 4096);
        OFFSET += 4096;
    }

    public SocketAddressUDSST(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT));
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        SB.append("SocketAddressUDSST{\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("path => ");
            if (CORRUPTED_MEMORY) SB.append("<?>");
            else SB.append(getPath());
        }
        SB.append("\n");
        SB.append(" ".repeat(INDENT)).append("}@").append(Long.toString(MEMORY.address(), 16));
    }

    public static class Array extends RefArray<SocketAddressUDSST> {
        public Array(MemorySegment buf) {
            super(buf, SocketAddressUDSST.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            super(allocator, SocketAddressUDSST.LAYOUT, len);
        }

        public Array(PNIBuf buf) {
            super(buf, SocketAddressUDSST.LAYOUT);
        }

        @Override
        protected void elementToString(io.vproxy.vfd.posix.SocketAddressUDSST ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "SocketAddressUDSST.Array";
        }

        @Override
        protected SocketAddressUDSST construct(MemorySegment seg) {
            return new SocketAddressUDSST(seg);
        }

        @Override
        protected MemorySegment getSegment(SocketAddressUDSST value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<SocketAddressUDSST> {
        private Func(io.vproxy.pni.CallSite<SocketAddressUDSST> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<SocketAddressUDSST> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<SocketAddressUDSST> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<SocketAddressUDSST> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "SocketAddressUDSST.Func";
        }

        @Override
        protected SocketAddressUDSST construct(MemorySegment seg) {
            return new SocketAddressUDSST(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.20
// sha256:f0c5f2384ed6d0e13c041313e5866f09e8e9aa2bf80fb4fa7ec5b51a27cbff4c
