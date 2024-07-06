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

public class AEFiredEvent extends AbstractNativeObject implements NativeObject {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("fd"),
        ValueLayout.JAVA_INT.withName("mask")
    ).withByteAlignment(4);
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
    }

    private static final VarHandleW fdVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("fd")
        )
    );

    public int getFd() {
        return fdVH.getInt(MEMORY);
    }

    public void setFd(int fd) {
        fdVH.set(MEMORY, fd);
    }

    private static final VarHandleW maskVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("mask")
        )
    );

    public int getMask() {
        return maskVH.getInt(MEMORY);
    }

    public void setMask(int mask) {
        maskVH.set(MEMORY, mask);
    }

    public AEFiredEvent(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
    }

    public AEFiredEvent(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT));
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        SB.append("AEFiredEvent{\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("fd => ");
            SB.append(getFd());
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("mask => ");
            SB.append(getMask());
        }
        SB.append("\n");
        SB.append(" ".repeat(INDENT)).append("}@").append(Long.toString(MEMORY.address(), 16));
    }

    public static class Array extends RefArray<AEFiredEvent> {
        public Array(MemorySegment buf) {
            super(buf, AEFiredEvent.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            super(allocator, AEFiredEvent.LAYOUT, len);
        }

        public Array(PNIBuf buf) {
            super(buf, AEFiredEvent.LAYOUT);
        }

        @Override
        protected void elementToString(io.vproxy.vfd.posix.AEFiredEvent ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "AEFiredEvent.Array";
        }

        @Override
        protected AEFiredEvent construct(MemorySegment seg) {
            return new AEFiredEvent(seg);
        }

        @Override
        protected MemorySegment getSegment(AEFiredEvent value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<AEFiredEvent> {
        private Func(io.vproxy.pni.CallSite<AEFiredEvent> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<AEFiredEvent> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<AEFiredEvent> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<AEFiredEvent> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "AEFiredEvent.Func";
        }

        @Override
        protected AEFiredEvent construct(MemorySegment seg) {
            return new AEFiredEvent(seg);
        }
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:b1e9f98ddaea4bb5b49e6423e3a2f173a7ae9bfc9918c0e0ef82d55f732440bf
