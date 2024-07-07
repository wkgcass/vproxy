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

public class Overlapped extends AbstractNativeObject implements NativeObject {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_LONG.withName("internal"),
        ValueLayout.JAVA_LONG.withName("internalHigh"),
        ValueLayout.ADDRESS.withName("dummy"),
        ValueLayout.ADDRESS.withName("event")
    ).withByteAlignment(8);
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
    }

    private static final VarHandleW internalVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("internal")
        )
    );

    public long getInternal() {
        return internalVH.getLong(MEMORY);
    }

    public void setInternal(long internal) {
        internalVH.set(MEMORY, internal);
    }

    private static final VarHandleW internalHighVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("internalHigh")
        )
    );

    public long getInternalHigh() {
        return internalHighVH.getLong(MEMORY);
    }

    public void setInternalHigh(long internalHigh) {
        internalHighVH.set(MEMORY, internalHigh);
    }

    private static final VarHandleW dummyVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("dummy")
        )
    );

    public MemorySegment getDummy() {
        var SEG = dummyVH.getMemorySegment(MEMORY);
        if (SEG.address() == 0) return null;
        return SEG;
    }

    public void setDummy(MemorySegment dummy) {
        if (dummy == null) {
            dummyVH.set(MEMORY, MemorySegment.NULL);
        } else {
            dummyVH.set(MEMORY, dummy);
        }
    }

    private static final VarHandleW eventVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("event")
        )
    );

    public io.vproxy.vfd.windows.HANDLE getEvent() {
        var SEG = eventVH.getMemorySegment(MEMORY);
        if (SEG.address() == 0) return null;
        return new io.vproxy.vfd.windows.HANDLE(SEG);
    }

    public void setEvent(io.vproxy.vfd.windows.HANDLE event) {
        if (event == null) {
            eventVH.set(MEMORY, MemorySegment.NULL);
        } else {
            eventVH.set(MEMORY, event.MEMORY);
        }
    }

    public Overlapped(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        OFFSET += ValueLayout.JAVA_LONG_UNALIGNED.byteSize();
        OFFSET += ValueLayout.JAVA_LONG_UNALIGNED.byteSize();
        OFFSET += ValueLayout.ADDRESS_UNALIGNED.byteSize();
        OFFSET += 8;
    }

    public Overlapped(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT));
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        SB.append("Overlapped{\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("internal => ");
            SB.append(getInternal());
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("internalHigh => ");
            SB.append(getInternalHigh());
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("dummy => ");
            SB.append(PanamaUtils.memorySegmentToString(getDummy()));
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("event => ");
            if (CORRUPTED_MEMORY) SB.append("<?>");
            else PanamaUtils.nativeObjectToString(getEvent(), SB, INDENT + 4, VISITED, CORRUPTED_MEMORY);
        }
        SB.append("\n");
        SB.append(" ".repeat(INDENT)).append("}@").append(Long.toString(MEMORY.address(), 16));
    }

    public static class Array extends RefArray<Overlapped> {
        public Array(MemorySegment buf) {
            super(buf, Overlapped.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            super(allocator, Overlapped.LAYOUT, len);
        }

        public Array(PNIBuf buf) {
            super(buf, Overlapped.LAYOUT);
        }

        @Override
        protected void elementToString(io.vproxy.vfd.windows.Overlapped ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "Overlapped.Array";
        }

        @Override
        protected Overlapped construct(MemorySegment seg) {
            return new Overlapped(seg);
        }

        @Override
        protected MemorySegment getSegment(Overlapped value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<Overlapped> {
        private Func(io.vproxy.pni.CallSite<Overlapped> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<Overlapped> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<Overlapped> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<Overlapped> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "Overlapped.Func";
        }

        @Override
        protected Overlapped construct(MemorySegment seg) {
            return new Overlapped(seg);
        }
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:b31e2361ed2aeaeeb97af3620db474bf0f73c52ee39bb31c4503acba65b4d4d9
