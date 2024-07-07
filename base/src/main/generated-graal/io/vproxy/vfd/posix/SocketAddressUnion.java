package io.vproxy.vfd.posix;

import io.vproxy.pni.*;
import io.vproxy.pni.hack.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;
import io.vproxy.pni.graal.*;
import org.graalvm.nativeimage.*;
import org.graalvm.nativeimage.c.function.*;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.WordFactory;

public class SocketAddressUnion extends AbstractNativeObject implements NativeObject {
    public static final MemoryLayout LAYOUT = MemoryLayout.unionLayout(
        io.vproxy.vfd.posix.SocketAddressIPv4ST.LAYOUT.withName("v4"),
        io.vproxy.vfd.posix.SocketAddressIPv6ST.LAYOUT.withName("v6")
    ).withByteAlignment(4);
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
    }

    private final io.vproxy.vfd.posix.SocketAddressIPv4ST v4;

    public io.vproxy.vfd.posix.SocketAddressIPv4ST getV4() {
        return this.v4;
    }

    private final io.vproxy.vfd.posix.SocketAddressIPv6ST v6;

    public io.vproxy.vfd.posix.SocketAddressIPv6ST getV6() {
        return this.v6;
    }

    public SocketAddressUnion(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        this.v4 = new io.vproxy.vfd.posix.SocketAddressIPv4ST(MEMORY.asSlice(OFFSET, io.vproxy.vfd.posix.SocketAddressIPv4ST.LAYOUT.byteSize()));
        OFFSET += io.vproxy.vfd.posix.SocketAddressIPv4ST.LAYOUT.byteSize();
        OFFSET = 0;
        this.v6 = new io.vproxy.vfd.posix.SocketAddressIPv6ST(MEMORY.asSlice(OFFSET, io.vproxy.vfd.posix.SocketAddressIPv6ST.LAYOUT.byteSize()));
        OFFSET += io.vproxy.vfd.posix.SocketAddressIPv6ST.LAYOUT.byteSize();
        OFFSET = 0;
    }

    public SocketAddressUnion(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT));
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        CORRUPTED_MEMORY = true;
        SB.append("SocketAddressUnion(\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("v4 => ");
            PanamaUtils.nativeObjectToString(getV4(), SB, INDENT + 4, VISITED, CORRUPTED_MEMORY);
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("v6 => ");
            PanamaUtils.nativeObjectToString(getV6(), SB, INDENT + 4, VISITED, CORRUPTED_MEMORY);
        }
        SB.append("\n");
        SB.append(" ".repeat(INDENT)).append(")@").append(Long.toString(MEMORY.address(), 16));
    }

    public static class Array extends RefArray<SocketAddressUnion> {
        public Array(MemorySegment buf) {
            super(buf, SocketAddressUnion.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            super(allocator, SocketAddressUnion.LAYOUT, len);
        }

        public Array(PNIBuf buf) {
            super(buf, SocketAddressUnion.LAYOUT);
        }

        @Override
        protected void elementToString(io.vproxy.vfd.posix.SocketAddressUnion ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "SocketAddressUnion.Array";
        }

        @Override
        protected SocketAddressUnion construct(MemorySegment seg) {
            return new SocketAddressUnion(seg);
        }

        @Override
        protected MemorySegment getSegment(SocketAddressUnion value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<SocketAddressUnion> {
        private Func(io.vproxy.pni.CallSite<SocketAddressUnion> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<SocketAddressUnion> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<SocketAddressUnion> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<SocketAddressUnion> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "SocketAddressUnion.Func";
        }

        @Override
        protected SocketAddressUnion construct(MemorySegment seg) {
            return new SocketAddressUnion(seg);
        }
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:fb4fb071fdeaff0e15f84c999e5b4096be2d32a79c5ce0d63c09b29cd6edd32f
