package io.vproxy.msquic;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class QuicRegistrationConfigEx extends io.vproxy.msquic.QuicRegistrationConfig {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        io.vproxy.msquic.QuicRegistrationConfig.LAYOUT,
        ValueLayout.ADDRESS_UNALIGNED.withName("Context")
    );
    public final MemorySegment MEMORY;

    private static final VarHandle ContextVH = LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("Context")
    );

    public MemorySegment getContext() {
        var SEG = (MemorySegment) ContextVH.get(MEMORY);
        if (SEG.address() == 0) return null;
        return SEG;
    }

    public void setContext(MemorySegment Context) {
        if (Context == null) {
            ContextVH.set(MEMORY, MemorySegment.NULL);
        } else {
            ContextVH.set(MEMORY, Context);
        }
    }

    public QuicRegistrationConfigEx(MemorySegment MEMORY) {
        super(MEMORY);
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        OFFSET += io.vproxy.msquic.QuicRegistrationConfig.LAYOUT.byteSize();
        OFFSET += ValueLayout.ADDRESS_UNALIGNED.byteSize();
    }

    public QuicRegistrationConfigEx(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT.byteSize()));
    }

    public static class Array extends RefArray<QuicRegistrationConfigEx> {
        public Array(MemorySegment buf) {
            super(buf, QuicRegistrationConfigEx.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            this(allocator.allocate(QuicRegistrationConfigEx.LAYOUT.byteSize() * len));
        }

        public Array(PNIBuf buf) {
            this(buf.get());
        }

        @Override
        protected QuicRegistrationConfigEx construct(MemorySegment seg) {
            return new QuicRegistrationConfigEx(seg);
        }

        @Override
        protected MemorySegment getSegment(QuicRegistrationConfigEx value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<QuicRegistrationConfigEx> {
        private Func(io.vproxy.pni.CallSite<QuicRegistrationConfigEx> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<QuicRegistrationConfigEx> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<QuicRegistrationConfigEx> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<QuicRegistrationConfigEx> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected QuicRegistrationConfigEx construct(MemorySegment seg) {
            return new QuicRegistrationConfigEx(seg);
        }
    }
}
// metadata.generator-version: pni 21.0.0.11
// sha256:25176c0c1dbc23252a589f6365757ed9a5e4db8b926069d3749b25f26b1cfd40
