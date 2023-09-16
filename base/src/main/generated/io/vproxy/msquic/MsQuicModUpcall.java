package io.vproxy.msquic;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class MsQuicModUpcall {
    private static final Arena ARENA = Arena.ofShared();

    public static final MemorySegment dispatch;

    private static int dispatch(MemorySegment worker, int eventQ, MemorySegment thread) {
        if (IMPL == null) {
            System.out.println("io.vproxy.msquic.MsQuicModUpcall#dispatch");
            System.exit(1);
        }
        var RESULT = IMPL.dispatch(
            (worker.address() == 0 ? null : worker),
            eventQ,
            (thread.address() == 0 ? null : thread)
        );
        return RESULT;
    }

    static {
        MethodHandle dispatchMH;
        try {
            dispatchMH = MethodHandles.lookup().findStatic(io.vproxy.msquic.MsQuicModUpcall.class, "dispatch", MethodType.methodType(int.class, MemorySegment.class, int.class, MemorySegment.class));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        dispatch = PanamaUtils.defineCFunction(ARENA, dispatchMH, int.class, MemorySegment.class, int.class, MemorySegment.class);

        var initMH = PanamaUtils.lookupPNICriticalFunction(true, void.class, "JavaCritical_io_vproxy_msquic_MsQuicModUpcall_INIT", MemorySegment.class);
        try {
            initMH.invoke(dispatch);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static Interface IMPL = null;

    public static void setImpl(Interface impl) {
        java.util.Objects.requireNonNull(impl);
        IMPL = impl;
    }

    public interface Interface {
        int dispatch(MemorySegment worker, int eventQ, MemorySegment thread);
    }
}
// metadata.generator-version: pni 21.0.0.8
// sha256:c0ab7a4b9671d10ce767d683982d929a40856e84e13f0d94aa198a2fcd60a33a
