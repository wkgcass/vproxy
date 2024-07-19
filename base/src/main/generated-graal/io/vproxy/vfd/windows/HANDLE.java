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

public class HANDLE extends AbstractNativeObject implements NativeObject {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(

    );
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
    }

    public HANDLE(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
    }

    public HANDLE(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT));
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        SB.append("HANDLE{\n");
        SB.append(" ".repeat(INDENT)).append("}@").append(Long.toString(MEMORY.address(), 16));
    }

    public static class Array extends RefArray<HANDLE> {
        public Array(MemorySegment buf) {
            super(buf, HANDLE.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            super(allocator, HANDLE.LAYOUT, len);
        }

        public Array(PNIBuf buf) {
            super(buf, HANDLE.LAYOUT);
        }

        @Override
        protected void elementToString(io.vproxy.vfd.windows.HANDLE ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "HANDLE.Array";
        }

        @Override
        protected HANDLE construct(MemorySegment seg) {
            return new HANDLE(seg);
        }

        @Override
        protected MemorySegment getSegment(HANDLE value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<HANDLE> {
        private Func(io.vproxy.pni.CallSite<HANDLE> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<HANDLE> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<HANDLE> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<HANDLE> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "HANDLE.Func";
        }

        @Override
        protected HANDLE construct(MemorySegment seg) {
            return new HANDLE(seg);
        }
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:436b18cb4cb1457da10c42a3245dab64d8f907362e4f8223f8bf5a4e0888c7b5
