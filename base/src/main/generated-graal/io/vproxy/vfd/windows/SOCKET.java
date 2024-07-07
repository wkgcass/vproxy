package io.vproxy.vfd.windows;

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

public class SOCKET extends AbstractNativeObject implements NativeObject {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(

    );
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
    }

    public SOCKET(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
    }

    public SOCKET(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT));
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        SB.append("SOCKET{\n");
        SB.append(" ".repeat(INDENT)).append("}@").append(Long.toString(MEMORY.address(), 16));
    }

    public static class Array extends RefArray<SOCKET> {
        public Array(MemorySegment buf) {
            super(buf, SOCKET.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            super(allocator, SOCKET.LAYOUT, len);
        }

        public Array(PNIBuf buf) {
            super(buf, SOCKET.LAYOUT);
        }

        @Override
        protected void elementToString(io.vproxy.vfd.windows.SOCKET ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "SOCKET.Array";
        }

        @Override
        protected SOCKET construct(MemorySegment seg) {
            return new SOCKET(seg);
        }

        @Override
        protected MemorySegment getSegment(SOCKET value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<SOCKET> {
        private Func(io.vproxy.pni.CallSite<SOCKET> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<SOCKET> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<SOCKET> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<SOCKET> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "SOCKET.Func";
        }

        @Override
        protected SOCKET construct(MemorySegment seg) {
            return new SOCKET(seg);
        }
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:aad2fc35f2e7964fe26deae84fe5ed2110970a6fab202bde54d4ef58eae2d417
