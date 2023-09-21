package io.vproxy.msquic;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class CxPlatProcessEventLocals extends AbstractNativeObject implements NativeObject {
    private static final MethodHandle __getLayoutByteSizeMH = PanamaUtils.lookupPNICriticalFunction(true, long.class, "JavaCritical_io_vproxy_msquic_CxPlatProcessEventLocals___getLayoutByteSize");

    private static long __getLayoutByteSize() {
        long RESULT;
        try {
            RESULT = (long) __getLayoutByteSizeMH.invokeExact();
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }

    public static final MemoryLayout LAYOUT = PanamaUtils.padLayout(__getLayoutByteSize(), MemoryLayout::structLayout,
        ValueLayout.ADDRESS.withName("worker"),
        ValueLayout.ADDRESS.withName("state"),
        ValueLayout.JAVA_INT.withName("waitTime"),
        MemoryLayout.sequenceLayout(4L, ValueLayout.JAVA_BYTE) /* padding */
    );
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
    }

    private static final VarHandle workerVH = LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("worker")
    );

    public MemorySegment getWorker() {
        var SEG = (MemorySegment) workerVH.get(MEMORY);
        if (SEG.address() == 0) return null;
        return SEG;
    }

    public void setWorker(MemorySegment worker) {
        if (worker == null) {
            workerVH.set(MEMORY, MemorySegment.NULL);
        } else {
            workerVH.set(MEMORY, worker);
        }
    }

    private static final VarHandle stateVH = LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("state")
    );

    public io.vproxy.msquic.CxPlatExecutionState getState() {
        var SEG = (MemorySegment) stateVH.get(MEMORY);
        if (SEG.address() == 0) return null;
        return new io.vproxy.msquic.CxPlatExecutionState(SEG);
    }

    public void setState(io.vproxy.msquic.CxPlatExecutionState state) {
        if (state == null) {
            stateVH.set(MEMORY, MemorySegment.NULL);
        } else {
            stateVH.set(MEMORY, state.MEMORY);
        }
    }

    private static final VarHandle waitTimeVH = LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("waitTime")
    );

    public int getWaitTime() {
        return (int) waitTimeVH.get(MEMORY);
    }

    public void setWaitTime(int waitTime) {
        waitTimeVH.set(MEMORY, waitTime);
    }

    public CxPlatProcessEventLocals(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        OFFSET += ValueLayout.ADDRESS_UNALIGNED.byteSize();
        OFFSET += 8;
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
        OFFSET += 4; /* padding */
    }

    public CxPlatProcessEventLocals(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT.byteSize()));
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        SB.append("CxPlatProcessEventLocals{\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("worker => ");
            SB.append(PanamaUtils.memorySegmentToString(getWorker()));
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("state => ");
            if (CORRUPTED_MEMORY) SB.append("<?>");
            else PanamaUtils.nativeObjectToString(getState(), SB, INDENT + 4, VISITED, CORRUPTED_MEMORY);
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("waitTime => ");
            SB.append(getWaitTime());
        }
        SB.append("\n");
        SB.append(" ".repeat(INDENT)).append("}@").append(Long.toString(MEMORY.address(), 16));
    }

    public static class Array extends RefArray<CxPlatProcessEventLocals> {
        public Array(MemorySegment buf) {
            super(buf, CxPlatProcessEventLocals.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            this(allocator.allocate(CxPlatProcessEventLocals.LAYOUT.byteSize() * len));
        }

        public Array(PNIBuf buf) {
            this(buf.get());
        }

        @Override
        protected void elementToString(io.vproxy.msquic.CxPlatProcessEventLocals ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "CxPlatProcessEventLocals.Array";
        }

        @Override
        protected CxPlatProcessEventLocals construct(MemorySegment seg) {
            return new CxPlatProcessEventLocals(seg);
        }

        @Override
        protected MemorySegment getSegment(CxPlatProcessEventLocals value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<CxPlatProcessEventLocals> {
        private Func(io.vproxy.pni.CallSite<CxPlatProcessEventLocals> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<CxPlatProcessEventLocals> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<CxPlatProcessEventLocals> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<CxPlatProcessEventLocals> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "CxPlatProcessEventLocals.Func";
        }

        @Override
        protected CxPlatProcessEventLocals construct(MemorySegment seg) {
            return new CxPlatProcessEventLocals(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.14
// sha256:3f286a9faa7b3b2ea28f915667f78687d48456aaa62038069499e21dddd53ee7
