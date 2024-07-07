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

public class VIOContext extends AbstractNativeObject implements NativeObject {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.ADDRESS.withName("ptr"),
        io.vproxy.vfd.windows.Overlapped.LAYOUT.withName("overlapped"),
        ValueLayout.JAVA_INT.withName("ctxType"),
        MemoryLayout.sequenceLayout(4L, ValueLayout.JAVA_BYTE) /* padding */,
        ValueLayout.ADDRESS.withName("ref"),
        ValueLayout.ADDRESS.withName("socket"),
        ValueLayout.JAVA_INT.withName("ioType"),
        MemoryLayout.sequenceLayout(4L, ValueLayout.JAVA_BYTE) /* padding */,
        MemoryLayout.sequenceLayout(2L, io.vproxy.vfd.windows.WSABUF.LAYOUT).withName("buffers"),
        ValueLayout.JAVA_INT.withName("bufferCount"),
        MemoryLayout.sequenceLayout(4L, ValueLayout.JAVA_BYTE) /* padding */,
        io.vproxy.vfd.windows.SockaddrStorage.LAYOUT.withName("addr"),
        ValueLayout.JAVA_INT.withName("addrLen"),
        MemoryLayout.sequenceLayout(4L, ValueLayout.JAVA_BYTE) /* padding */
    ).withByteAlignment(8);
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
    }

    private static final VarHandleW ptrVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("ptr")
        )
    );

    public MemorySegment getPtr() {
        var SEG = ptrVH.getMemorySegment(MEMORY);
        if (SEG.address() == 0) return null;
        return SEG;
    }

    public void setPtr(MemorySegment ptr) {
        if (ptr == null) {
            ptrVH.set(MEMORY, MemorySegment.NULL);
        } else {
            ptrVH.set(MEMORY, ptr);
        }
    }

    private final io.vproxy.vfd.windows.Overlapped overlapped;

    public io.vproxy.vfd.windows.Overlapped getOverlapped() {
        return this.overlapped;
    }

    private static final VarHandleW ctxTypeVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("ctxType")
        )
    );

    public int getCtxType() {
        return ctxTypeVH.getInt(MEMORY);
    }

    public void setCtxType(int ctxType) {
        ctxTypeVH.set(MEMORY, ctxType);
    }

    private static final VarHandleW refVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("ref")
        )
    );

    public PNIRef<?> getRef() {
        var SEG = refVH.getMemorySegment(MEMORY);
        if (SEG.address() == 0) return null;
        return PNIRef.of(SEG);
    }

    public void setRef(PNIRef<?> ref) {
        if (ref == null) {
            refVH.set(MEMORY, MemorySegment.NULL);
        } else {
            refVH.set(MEMORY, ref.MEMORY);
        }
    }

    private static final VarHandleW socketVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("socket")
        )
    );

    public io.vproxy.vfd.windows.SOCKET getSocket() {
        var SEG = socketVH.getMemorySegment(MEMORY);
        if (SEG.address() == 0) return null;
        return new io.vproxy.vfd.windows.SOCKET(SEG);
    }

    public void setSocket(io.vproxy.vfd.windows.SOCKET socket) {
        if (socket == null) {
            socketVH.set(MEMORY, MemorySegment.NULL);
        } else {
            socketVH.set(MEMORY, socket.MEMORY);
        }
    }

    private static final VarHandleW ioTypeVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("ioType")
        )
    );

    public int getIoType() {
        return ioTypeVH.getInt(MEMORY);
    }

    public void setIoType(int ioType) {
        ioTypeVH.set(MEMORY, ioType);
    }

    private final io.vproxy.vfd.windows.WSABUF.Array buffers;

    public io.vproxy.vfd.windows.WSABUF.Array getBuffers() {
        return this.buffers;
    }

    private static final VarHandleW bufferCountVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("bufferCount")
        )
    );

    public int getBufferCount() {
        return bufferCountVH.getInt(MEMORY);
    }

    public void setBufferCount(int bufferCount) {
        bufferCountVH.set(MEMORY, bufferCount);
    }

    private final io.vproxy.vfd.windows.SockaddrStorage addr;

    public io.vproxy.vfd.windows.SockaddrStorage getAddr() {
        return this.addr;
    }

    private static final VarHandleW addrLenVH = VarHandleW.of(
        LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("addrLen")
        )
    );

    public int getAddrLen() {
        return addrLenVH.getInt(MEMORY);
    }

    public void setAddrLen(int addrLen) {
        addrLenVH.set(MEMORY, addrLen);
    }

    public VIOContext(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
        OFFSET += ValueLayout.ADDRESS_UNALIGNED.byteSize();
        this.overlapped = new io.vproxy.vfd.windows.Overlapped(MEMORY.asSlice(OFFSET, io.vproxy.vfd.windows.Overlapped.LAYOUT.byteSize()));
        OFFSET += io.vproxy.vfd.windows.Overlapped.LAYOUT.byteSize();
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
        OFFSET += 4; /* padding */
        OFFSET += ValueLayout.ADDRESS_UNALIGNED.byteSize();
        OFFSET += 8;
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
        OFFSET += 4; /* padding */
        this.buffers = new io.vproxy.vfd.windows.WSABUF.Array(MEMORY.asSlice(OFFSET, 2 * io.vproxy.vfd.windows.WSABUF.LAYOUT.byteSize()));
        OFFSET += 2 * io.vproxy.vfd.windows.WSABUF.LAYOUT.byteSize();
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
        OFFSET += 4; /* padding */
        this.addr = new io.vproxy.vfd.windows.SockaddrStorage(MEMORY.asSlice(OFFSET, io.vproxy.vfd.windows.SockaddrStorage.LAYOUT.byteSize()));
        OFFSET += io.vproxy.vfd.windows.SockaddrStorage.LAYOUT.byteSize();
        OFFSET += ValueLayout.JAVA_INT_UNALIGNED.byteSize();
        OFFSET += 4; /* padding */
    }

    public VIOContext(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT));
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        SB.append("VIOContext{\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("ptr => ");
            SB.append(PanamaUtils.memorySegmentToString(getPtr()));
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("overlapped => ");
            PanamaUtils.nativeObjectToString(getOverlapped(), SB, INDENT + 4, VISITED, CORRUPTED_MEMORY);
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("ctxType => ");
            SB.append(getCtxType());
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("ref => ");
            if (CORRUPTED_MEMORY) SB.append("<?>");
            else PanamaUtils.nativeObjectToString(getRef(), SB, INDENT + 4, VISITED, CORRUPTED_MEMORY);
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("socket => ");
            if (CORRUPTED_MEMORY) SB.append("<?>");
            else PanamaUtils.nativeObjectToString(getSocket(), SB, INDENT + 4, VISITED, CORRUPTED_MEMORY);
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("ioType => ");
            SB.append(getIoType());
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("buffers => ");
            PanamaUtils.nativeObjectToString(getBuffers(), SB, INDENT + 4, VISITED, CORRUPTED_MEMORY);
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("bufferCount => ");
            SB.append(getBufferCount());
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("addr => ");
            PanamaUtils.nativeObjectToString(getAddr(), SB, INDENT + 4, VISITED, CORRUPTED_MEMORY);
        }
        SB.append(",\n");
        {
            SB.append(" ".repeat(INDENT + 4)).append("addrLen => ");
            SB.append(getAddrLen());
        }
        SB.append("\n");
        SB.append(" ".repeat(INDENT)).append("}@").append(Long.toString(MEMORY.address(), 16));
    }

    public static class Array extends RefArray<VIOContext> {
        public Array(MemorySegment buf) {
            super(buf, VIOContext.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            super(allocator, VIOContext.LAYOUT, len);
        }

        public Array(PNIBuf buf) {
            super(buf, VIOContext.LAYOUT);
        }

        @Override
        protected void elementToString(io.vproxy.vfd.windows.VIOContext ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "VIOContext.Array";
        }

        @Override
        protected VIOContext construct(MemorySegment seg) {
            return new VIOContext(seg);
        }

        @Override
        protected MemorySegment getSegment(VIOContext value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<VIOContext> {
        private Func(io.vproxy.pni.CallSite<VIOContext> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<VIOContext> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<VIOContext> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<VIOContext> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "VIOContext.Func";
        }

        @Override
        protected VIOContext construct(MemorySegment seg) {
            return new VIOContext(seg);
        }
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:235f368350829410e61332cf002376e32b36360e1422abffd5c3b931c57ea50e
