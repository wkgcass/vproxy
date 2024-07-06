package io.vproxy.fubuki;

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

public class FubukiHandle extends AbstractNativeObject implements NativeObject {
    public static final MemoryLayout LAYOUT = MemoryLayout.structLayout(

    );
    public final MemorySegment MEMORY;

    @Override
    public MemorySegment MEMORY() {
        return MEMORY;
    }

    public FubukiHandle(MemorySegment MEMORY) {
        MEMORY = MEMORY.reinterpret(LAYOUT.byteSize());
        this.MEMORY = MEMORY;
        long OFFSET = 0;
    }

    public FubukiHandle(Allocator ALLOCATOR) {
        this(ALLOCATOR.allocate(LAYOUT));
    }

    private static final MethodHandle fubukiBlockOnMH = PanamaUtils.lookupPNICriticalFunction(new PNILinkOptions(), int.class, "fubuki_block_on", MemorySegment.class /* self */, String.class /* errorMsg */);

    public int fubukiBlockOn(PNIString errorMsg) {
        int RESULT;
        try {
            RESULT = (int) fubukiBlockOnMH.invokeExact(MEMORY, (MemorySegment) (errorMsg == null ? MemorySegment.NULL : errorMsg.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }

    private static final MethodHandle sendMH = PanamaUtils.lookupPNICriticalFunction(new PNILinkOptions().setCritical(true), void.class, "if_to_fubuki", MemorySegment.class /* self */, MemorySegment.class /* data */, long.class /* len */);

    public void send(MemorySegment data, long len) {
        try {
            sendMH.invokeExact(MEMORY, (MemorySegment) (data == null ? MemorySegment.NULL : data), len);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
    }

    private static final MethodHandle stopMH = PanamaUtils.lookupPNICriticalFunction(new PNILinkOptions(), void.class, "fubuki_stop", MemorySegment.class /* self */);

    public void stop() {
        try {
            stopMH.invokeExact(MEMORY);
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
    }

    @Override
    public void toString(StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
        if (!VISITED.add(new NativeObjectTuple(this))) {
            SB.append("<...>@").append(Long.toString(MEMORY.address(), 16));
            return;
        }
        SB.append("FubukiHandle{\n");
        SB.append(" ".repeat(INDENT)).append("}@").append(Long.toString(MEMORY.address(), 16));
    }

    public static class Array extends RefArray<FubukiHandle> {
        public Array(MemorySegment buf) {
            super(buf, FubukiHandle.LAYOUT);
        }

        public Array(Allocator allocator, long len) {
            super(allocator, FubukiHandle.LAYOUT, len);
        }

        public Array(PNIBuf buf) {
            super(buf, FubukiHandle.LAYOUT);
        }

        @Override
        protected void elementToString(io.vproxy.fubuki.FubukiHandle ELEM, StringBuilder SB, int INDENT, java.util.Set<NativeObjectTuple> VISITED, boolean CORRUPTED_MEMORY) {
            ELEM.toString(SB, INDENT, VISITED, CORRUPTED_MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "FubukiHandle.Array";
        }

        @Override
        protected FubukiHandle construct(MemorySegment seg) {
            return new FubukiHandle(seg);
        }

        @Override
        protected MemorySegment getSegment(FubukiHandle value) {
            return value.MEMORY;
        }
    }

    public static class Func extends PNIFunc<FubukiHandle> {
        private Func(io.vproxy.pni.CallSite<FubukiHandle> func) {
            super(func);
        }

        private Func(io.vproxy.pni.CallSite<FubukiHandle> func, Options opts) {
            super(func, opts);
        }

        private Func(MemorySegment MEMORY) {
            super(MEMORY);
        }

        public static Func of(io.vproxy.pni.CallSite<FubukiHandle> func) {
            return new Func(func);
        }

        public static Func of(io.vproxy.pni.CallSite<FubukiHandle> func, Options opts) {
            return new Func(func, opts);
        }

        public static Func of(MemorySegment MEMORY) {
            return new Func(MEMORY);
        }

        @Override
        protected String toStringTypeName() {
            return "FubukiHandle.Func";
        }

        @Override
        protected FubukiHandle construct(MemorySegment seg) {
            return new FubukiHandle(seg);
        }
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:2e37e0e144ce55dd572da084d2b2bb72710cda9547773329523f0b43243909eb
