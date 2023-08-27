package io.vproxy.msquic.modified;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class MsQuicUpcall {
    private static final Arena ARENA = Arena.ofShared();

    public static final MemorySegment dispatch;

    private static int dispatch(MemorySegment worker, int eventQ, MemorySegment thread) {
        if (IMPL == null) {
            System.out.println("io.vproxy.msquic.modified.MsQuicUpcall#dispatch");
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
            dispatchMH = MethodHandles.lookup().findStatic(io.vproxy.msquic.modified.MsQuicUpcall.class, "dispatch", MethodType.methodType(int.class, MemorySegment.class, int.class, MemorySegment.class));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        dispatch = PanamaUtils.defineCFunction(ARENA, dispatchMH, int.class, MemorySegment.class, int.class, MemorySegment.class);

        var initMH = PanamaUtils.lookupPNICriticalFunction(true, void.class, "JavaCritical_io_vproxy_msquic_modified_MsQuicUpcall_INIT", MemorySegment.class);
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
// sha256:1e1f5d22cc18f2dd0917d50eb97bcc2a3e62caabc011150c16c5dd3fd90c8407
