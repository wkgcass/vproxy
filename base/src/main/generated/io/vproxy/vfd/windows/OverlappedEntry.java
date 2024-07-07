package io.vproxy.vfd.windows;

import io.vproxy.pni.*;
import io.vproxy.pni.hack.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class OverlappedEntry extends AbstractNativeObject implements NativeObject {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.ADDRESS.withName("completionKey"),
        ValueLayout.ADDRESS.withName("overlapped"),
        ValueLayout.ADDRESS.withName("internal"),
        ValueLayout.JAVA_INT.withName("numberOfBytesTransferred"),
        MemoryLayout.sequenceLayout(4L, ValueLayout.JAVA_BYTE) /* padding */
    ).withByteAlignment(8);
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
    }

    private static final VarHandleW completionKeyVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("completionKey")
        )
    );

    public MemorySegment getCompletionKey() {
        var SEG = completionKeyVH.getMemorySegment(MEMORY);
        if (SEG.address() == 0) return null;
        return SEG;
    }

    public void setCompletionKey(MemorySegment completionKey) {
        if (completionKey == null) {
            completionKeyVH.set(MEMORY, MemorySegment.NULL);
        } else {
            completionKeyVH.set(MEMORY, completionKey);
        }
    }

    private static final VarHandleW overlappedVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("overlapped")
        )
    );

    public io.vproxy.vfd.windows.Overlapped getOverlapped() {
        var SEG = overlappedVH.getMemorySegment(MEMORY);
        if (SEG.address() == 0) return null;
        return new io.vproxy.vfd.windows.Overlapped(SEG);
    }

    public void setOverlapped(io.vproxy.vfd.windows.Overlapped overlapped) {
        if (overlapped == null) {
            overlappedVH.set(MEMORY, MemorySegment.NULL);
        } else {
            overlappedVH.set(MEMORY, overlapped.MEMORY);
        }
    }

    private static final VarHandleW internalVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("internal")
        )
    );

    public MemorySegment getInternal() {
        var SEG = internalVH.getMemorySegment(MEMORY);
        if (SEG.address() == 0) return null;
        return SEG;
    }

    public void setInternal(MemorySegment internal) {
        if (internal == null) {
            internalVH.set(MEMORY, MemorySegment.NULL);
        } else {
            internalVH.set(MEMORY, internal);
        }
    }

    private static final VarHandleW numberOfBytesTransferredVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("numberOfBytesTransferred")
        )
    );

    public int getNumberOfBytesTransferred() {
        return numberOfBytesTransferredVH.getInt(MEMORY);
    }

    public void setNumberOfBytesTransferred(int numberOfBytesTransferred) {
        numberOfBytesTransferredVH.set(MEMORY, numberOfBytesTransferred);
    }

    public OverlappedEntry(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        OFFSET += ValueLayout.ADDRESS_UNALIGNED.byteSize();
        OFFSET += 8;
        OFFSET += ValueLayout.ADDRESS_UNALIGNED.byteSize();
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
        OFFSET += 4; /* padding */
    }

    public OverlappedEntry(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT));
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        SB.append("OverlappedEntry{\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("completionKey => ");
            SB.append(PanamaUtils.memorySegmentToString(getCompletionKey()));
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("overlapped => ");
            if (CORRUPTED_MEMORY) SB.append("<?>");
            else PanamaUtils.nativeObjectToString(getOverlapped(), SB, INDENT + 4, VISITED, CORRUPTED_MEMORY);
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("internal => ");
            SB.append(PanamaUtils.memorySegmentToString(getInternal()));
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("numberOfBytesTransferred => ");
            SB.append(getNumberOfBytesTransferred());
        }
        SB.append("\n");
        SB.append(" ".repeat(INDENT)).append("}@").append(Long.toString(MEMORY.address(), 16));
    }

    public static class Array extends RefArray<OverlappedEntry> {
        public Array(MemorySegment buf) {
            super(buf, OverlappedEntry.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            super(allocator, OverlappedEntry.LAYOUT, len);
        }

        public Array(PNIBuf buf) {
            super(buf, OverlappedEntry.LAYOUT);
        }

        @Override
        protected void elementToString(io.vproxy.vfd.windows.OverlappedEntry ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "OverlappedEntry.Array";
        }

        @Override
        protected OverlappedEntry construct(MemorySegment seg) {
            return new OverlappedEntry(seg);
        }

        @Override
        protected MemorySegment getSegment(OverlappedEntry value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<OverlappedEntry> {
        private Func(io.vproxy.pni.CallSite<OverlappedEntry> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<OverlappedEntry> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<OverlappedEntry> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<OverlappedEntry> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "OverlappedEntry.Func";
        }

        @Override
        protected OverlappedEntry construct(MemorySegment seg) {
            return new OverlappedEntry(seg);
        }
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:825f18663f786ae65814d09dc707e33bd72a56beb08ff921db4d647dddf442f6
