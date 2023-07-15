package io.vproxy.panama;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public class Memory {
    private final Arena arena;
    private final MemorySegment seg;

    public Memory(Arena arena, MemorySegment seg) {
        this.arena = arena;
        this.seg = seg;
    }

    public ByteBuffer asByteBuffer() {
        return seg.asByteBuffer();
    }

    public long byteSize() {
        return seg.byteSize();
    }

    public void release() {
        arena.close();
    }

    @Override
    public String toString() {
        return "Memory{" +
            "arena=" + arena +
            ", seg=" + seg +
            '}';
    }

    public MemorySegment getSegment() {
        return seg;
    }
}
