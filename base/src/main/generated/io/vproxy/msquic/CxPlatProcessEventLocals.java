package io.vproxy.msquic;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class CxPlatProcessEventLocals {
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
        ValueLayout.ADDRESS_UNALIGNED.withName("worker"),
        ValueLayout.ADDRESS_UNALIGNED.withName("state"),
        ValueLayout.JAVA_INT_UNALIGNED.withName("waitTime"),
        MemoryLayout.sequenceLayout(4L, ValueLayout.JAVA_BYTE) /* padding */
    );
    public final MemorySegment MEMORY;

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
        protected CxPlatProcessEventLocals construct(MemorySegment seg) {
            return new CxPlatProcessEventLocals(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.11
// sha256:514ae33ae7c29c6df8e9b6acb811a6d7d8d05aa1e739eef05e454bb0eee17b69
