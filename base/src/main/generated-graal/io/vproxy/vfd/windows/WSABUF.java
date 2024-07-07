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

public class WSABUF extends AbstractNativeObject implements NativeObject {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("len"),
        MemoryLayout.sequenceLayout(4L, ValueLayout.JAVA_BYTE) /* padding */,
        ValueLayout.ADDRESS.withName("buf")
    ).withByteAlignment(8);
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
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

    private static final VarHandleW bufVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("buf")
        )
    );

    public MemorySegment getBuf() {
        var SEG = bufVH.getMemorySegment(MEMORY);
        if (SEG.address() == 0) return null;
        return SEG;
    }

    public void setBuf(MemorySegment buf) {
        if (buf == null) {
            bufVH.set(MEMORY, MemorySegment.NULL);
        } else {
            bufVH.set(MEMORY, buf);
        }
    }

    public WSABUF(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
        OFFSET += 4; /* padding */
        OFFSET += ValueLayout.ADDRESS_UNALIGNED.byteSize();
    }

    public WSABUF(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT));
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        SB.append("WSABUF{\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("len => ");
            SB.append(getLen());
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("buf => ");
            SB.append(PanamaUtils.memorySegmentToString(getBuf()));
        }
        SB.append("\n");
        SB.append(" ".repeat(INDENT)).append("}@").append(Long.toString(MEMORY.address(), 16));
    }

    public static class Array extends RefArray<WSABUF> {
        public Array(MemorySegment buf) {
            super(buf, WSABUF.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            super(allocator, WSABUF.LAYOUT, len);
        }

        public Array(PNIBuf buf) {
            super(buf, WSABUF.LAYOUT);
        }

        @Override
        protected void elementToString(io.vproxy.vfd.windows.WSABUF ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "WSABUF.Array";
        }

        @Override
        protected WSABUF construct(MemorySegment seg) {
            return new WSABUF(seg);
        }

        @Override
        protected MemorySegment getSegment(WSABUF value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<WSABUF> {
        private Func(io.vproxy.pni.CallSite<WSABUF> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<WSABUF> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<WSABUF> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<WSABUF> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "WSABUF.Func";
        }

        @Override
        protected WSABUF construct(MemorySegment seg) {
            return new WSABUF(seg);
        }
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:654ce8fad58cda6cc7d85caaafae22a636e6ec606f6b4d649f5baadf686e48ee
