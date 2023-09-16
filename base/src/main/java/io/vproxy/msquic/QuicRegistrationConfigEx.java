package io.vproxy.msquic;

import io.vproxy.pni.Allocator;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

public class QuicRegistrationConfigEx extends QuicRegistrationConfig {
    private static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        QuicRegistrationConfig.LAYOUT,
        ValueLayout.ADDRESS_UNALIGNED.withName("Context")
    );
    public final MemorySegment MEMORY;

    public QuicRegistrationConfigEx(MemorySegment MEMORY) {
        super(MEMORY);
        this.MEMORY = super.MEMORY.reinterpret(LAYOUT.byteSize());
    }

    public QuicRegistrationConfigEx(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT.byteSize() + 8));
    }

    private static final VarHandle ContextVH = LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("Context")
    );

    public MemorySegment getContext() {
        return (MemorySegment) ContextVH.get(MEMORY);
    }

    public void setContext(MemorySegment Context) {
        ContextVH.set(MEMORY, Context);
    }
}
