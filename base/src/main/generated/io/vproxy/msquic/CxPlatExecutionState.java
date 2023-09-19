package io.vproxy.msquic;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class CxPlatExecutionState {
    private static final MethodHandle __getLayoutByteSizeMH = PanamaUtils.lookupPNICriticalFunction(true, long.class, "JavaCritical_io_vproxy_msquic_CxPlatExecutionState___getLayoutByteSize");

    private static long __getLayoutByteSize() {
        long RESULT;
        try {
            RESULT = (long) __getLayoutByteSizeMH.invokeExact();
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }

    public static final MemoryLayout LAYOUT = PanamaUtils.padLayout(__getLayoutByteSize(), MemoryLayout::structLayout

    );
    public final MemorySegment MEMORY;

    public CxPlatExecutionState(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
    }

    public CxPlatExecutionState(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT.byteSize()));
    }

    public static class Array extends RefArray<CxPlatExecutionState> {
        public Array(MemorySegment buf) {
            super(buf, CxPlatExecutionState.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            this(allocator.allocate(CxPlatExecutionState.LAYOUT.byteSize() * len));
        }

        public Array(PNIBuf buf) {
            this(buf.get());
        }

        @Override
        protected CxPlatExecutionState construct(MemorySegment seg) {
            return new CxPlatExecutionState(seg);
        }

        @Override
        protected MemorySegment getSegment(CxPlatExecutionState value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<CxPlatExecutionState> {
        private Func(io.vproxy.pni.CallSite<CxPlatExecutionState> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<CxPlatExecutionState> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<CxPlatExecutionState> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<CxPlatExecutionState> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected CxPlatExecutionState construct(MemorySegment seg) {
            return new CxPlatExecutionState(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.11
// sha256:21f160fae7589c2b4fdf3822bf692759108049b35f228b42ec471a60911ff7fe
