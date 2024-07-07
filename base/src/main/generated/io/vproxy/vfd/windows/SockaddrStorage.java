package io.vproxy.vfd.windows;

import io.vproxy.pni.*;
import io.vproxy.pni.hack.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class SockaddrStorage extends AbstractNativeObject implements NativeObject {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_SHORT.withName("family"),
        MemoryLayout.sequenceLayout(48L, ValueLayout.JAVA_CHAR).withName("pad1"),
        MemoryLayout.sequenceLayout(6L, ValueLayout.JAVA_BYTE) /* padding */,
        ValueLayout.JAVA_LONG.withName("align"),
        MemoryLayout.sequenceLayout(8L, ValueLayout.JAVA_CHAR).withName("pad2")
    ).withByteAlignment(8);
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
    }

    private static final VarHandleW familyVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("family")
        )
    );

    public short getFamily() {
        return familyVH.getShort(MEMORY);
    }

    public void setFamily(short family) {
        familyVH.set(MEMORY, family);
    }

    private final CharArray pad1;

    public CharArray getPad1() {
        return this.pad1;
    }

    private static final VarHandleW alignVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("align")
        )
    );

    public long getAlign() {
        return alignVH.getLong(MEMORY);
    }

    public void setAlign(long align) {
        alignVH.set(MEMORY, align);
    }

    private final CharArray pad2;

    public CharArray getPad2() {
        return this.pad2;
    }

    public SockaddrStorage(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        OFFSET += ValueLayout.JAVA_SHORT_UNALIGNED.byteSize();
        this.pad1 = new CharArray(MEMORY.asSlice(OFFSET, 48 * ValueLayout.JAVA_CHAR_UNALIGNED.byteSize()));
        OFFSET += 48 * ValueLayout.JAVA_CHAR_UNALIGNED.byteSize();
        OFFSET += 6; /* padding */
        OFFSET += ValueLayout.JAVA_LONG_UNALIGNED.byteSize();
        this.pad2 = new CharArray(MEMORY.asSlice(OFFSET, 8 * ValueLayout.JAVA_CHAR_UNALIGNED.byteSize()));
        OFFSET += 8 * ValueLayout.JAVA_CHAR_UNALIGNED.byteSize();
    }

    public SockaddrStorage(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT));
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        SB.append("SockaddrStorage{\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("family => ");
            SB.append(getFamily());
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("pad1 => ");
            PanamaUtils.nativeObjectToString(getPad1(), SB, INDENT + 4, VISITED, CORRUPTED_MEMORY);
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("align => ");
            SB.append(getAlign());
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("pad2 => ");
            PanamaUtils.nativeObjectToString(getPad2(), SB, INDENT + 4, VISITED, CORRUPTED_MEMORY);
        }
        SB.append("\n");
        SB.append(" ".repeat(INDENT)).append("}@").append(Long.toString(MEMORY.address(), 16));
    }

    public static class Array extends RefArray<SockaddrStorage> {
        public Array(MemorySegment buf) {
            super(buf, SockaddrStorage.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            super(allocator, SockaddrStorage.LAYOUT, len);
        }

        public Array(PNIBuf buf) {
            super(buf, SockaddrStorage.LAYOUT);
        }

        @Override
        protected void elementToString(io.vproxy.vfd.windows.SockaddrStorage ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "SockaddrStorage.Array";
        }

        @Override
        protected SockaddrStorage construct(MemorySegment seg) {
            return new SockaddrStorage(seg);
        }

        @Override
        protected MemorySegment getSegment(SockaddrStorage value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<SockaddrStorage> {
        private Func(io.vproxy.pni.CallSite<SockaddrStorage> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<SockaddrStorage> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<SockaddrStorage> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<SockaddrStorage> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "SockaddrStorage.Func";
        }

        @Override
        protected SockaddrStorage construct(MemorySegment seg) {
            return new SockaddrStorage(seg);
        }
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:0934e3a9f0cdc553c6aa079f80178798650d2df94deec1dde8280d565b185cc3
