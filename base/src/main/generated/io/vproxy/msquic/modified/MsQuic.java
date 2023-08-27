package io.vproxy.msquic.modified;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class MsQuic {
    private MsQuic() {
    }

    private static final MsQuic INSTANCE = new MsQuic();

    public static MsQuic get() {
        return INSTANCE;
    }

    private static final MethodHandle getCxPlatProcessEventLocalsMemorySizeMH = PanamaUtils.lookupPNICriticalFunction(false, long.class, "JavaCritical_io_vproxy_msquic_modified_MsQuic_getCxPlatProcessEventLocalsMemorySize");

    public long getCxPlatProcessEventLocalsMemorySize() {
        long RESULT;
        try {
            RESULT = (long) getCxPlatProcessEventLocalsMemorySizeMH.invokeExact();
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }

    private static final MethodHandle getCXPLAT_EXECUTION_STATEMemorySizeMH = PanamaUtils.lookupPNICriticalFunction(false, long.class, "JavaCritical_io_vproxy_msquic_modified_MsQuic_getCXPLAT_EXECUTION_STATEMemorySize");

    public long getCXPLAT_EXECUTION_STATEMemorySize() {
        long RESULT;
        try {
            RESULT = (long) getCXPLAT_EXECUTION_STATEMemorySizeMH.invokeExact();
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }

    private static final MethodHandle MsQuicCxPlatWorkerThreadInitMH = PanamaUtils.lookupPNICriticalFunction(false, void.class, "JavaCritical_io_vproxy_msquic_modified_MsQuic_MsQuicCxPlatWorkerThreadInit", io.vproxy.msquic.modified.CxPlatProcessEventLocals.LAYOUT.getClass() /* CxPlatWorkerThreadLocals */);

    public void MsQuicCxPlatWorkerThreadInit(io.vproxy.msquic.modified.CxPlatProcessEventLocals CxPlatWorkerThreadLocals) {
        try {
            MsQuicCxPlatWorkerThreadInitMH.invokeExact((MemorySegment) (CxPlatWorkerThreadLocals == null ? MemorySegment.NULL : CxPlatWorkerThreadLocals.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
    }

    private static final MethodHandle MsQuicCxPlatWorkerThreadBeforePollMH = PanamaUtils.lookupPNICriticalFunction(false, void.class, "JavaCritical_io_vproxy_msquic_modified_MsQuic_MsQuicCxPlatWorkerThreadBeforePoll", io.vproxy.msquic.modified.CxPlatProcessEventLocals.LAYOUT.getClass() /* CxPlatProcessEventLocals */);

    public void MsQuicCxPlatWorkerThreadBeforePoll(io.vproxy.msquic.modified.CxPlatProcessEventLocals CxPlatProcessEventLocals) {
        try {
            MsQuicCxPlatWorkerThreadBeforePollMH.invokeExact((MemorySegment) (CxPlatProcessEventLocals == null ? MemorySegment.NULL : CxPlatProcessEventLocals.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
    }

    private static final MethodHandle MsQuicCxPlatWorkerThreadAfterPollMH = PanamaUtils.lookupPNICriticalFunction(false, boolean.class, "JavaCritical_io_vproxy_msquic_modified_MsQuic_MsQuicCxPlatWorkerThreadAfterPoll", io.vproxy.msquic.modified.CxPlatProcessEventLocals.LAYOUT.getClass() /* locals */, int.class /* num */, MemorySegment.class /* events */);

    public boolean MsQuicCxPlatWorkerThreadAfterPoll(io.vproxy.msquic.modified.CxPlatProcessEventLocals locals, int num, io.vproxy.vfd.posix.AEFiredExtra.Array events) {
        boolean RESULT;
        try {
            RESULT = (boolean) MsQuicCxPlatWorkerThreadAfterPollMH.invokeExact((MemorySegment) (locals == null ? MemorySegment.NULL : locals.MEMORY), num, (MemorySegment) (events == null ? MemorySegment.NULL : events.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }

    private static final MethodHandle MsQuicCxPlatWorkerThreadFinalizeMH = PanamaUtils.lookupPNICriticalFunction(false, int.class, "JavaCritical_io_vproxy_msquic_modified_MsQuic_MsQuicCxPlatWorkerThreadFinalize", io.vproxy.msquic.modified.CxPlatProcessEventLocals.LAYOUT.getClass() /* CxPlatWorkerThreadLocals */);

    public int MsQuicCxPlatWorkerThreadFinalize(io.vproxy.msquic.modified.CxPlatProcessEventLocals CxPlatWorkerThreadLocals) {
        int RESULT;
        try {
            RESULT = (int) MsQuicCxPlatWorkerThreadFinalizeMH.invokeExact((MemorySegment) (CxPlatWorkerThreadLocals == null ? MemorySegment.NULL : CxPlatWorkerThreadLocals.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }

    private static final MethodHandle MsQuicSetEventLoopThreadDispatcherMH = PanamaUtils.lookupPNICriticalFunction(false, int.class, "JavaCritical_io_vproxy_msquic_modified_MsQuic_MsQuicSetEventLoopThreadDispatcher");

    public int MsQuicSetEventLoopThreadDispatcher() {
        int RESULT;
        try {
            RESULT = (int) MsQuicSetEventLoopThreadDispatcherMH.invokeExact();
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }

    private static final MethodHandle CxPlatGetCurThreadMH = PanamaUtils.lookupPNICriticalFunction(false, int.class, "JavaCritical_io_vproxy_msquic_modified_MsQuic_CxPlatGetCurThread", MemorySegment.class /* Thread */);

    public int CxPlatGetCurThread(MemorySegment Thread) {
        int RESULT;
        try {
            RESULT = (int) CxPlatGetCurThreadMH.invokeExact((MemorySegment) (Thread == null ? MemorySegment.NULL : Thread));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }
}
// metadata.generator-version: pni 21.0.0.8
// sha256:fb8f0a5d4d396e11cdb13f9190d043224537f5118a6c635551e6a0b7f1c2831c
