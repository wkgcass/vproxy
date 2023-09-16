package io.vproxy.msquic;

import io.vproxy.pni.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class MsQuicMod {
    private MsQuicMod() {
    }

    private static final MsQuicMod INSTANCE = new MsQuicMod();

    public static MsQuicMod get() {
        return INSTANCE;
    }

    private static final MethodHandle getCxPlatProcessEventLocalsMemorySizeMH = PanamaUtils.lookupPNICriticalFunction(false, long.class, "JavaCritical_io_vproxy_msquic_MsQuicMod_getCxPlatProcessEventLocalsMemorySize");

    public long getCxPlatProcessEventLocalsMemorySize() {
        long RESULT;
        try {
            RESULT = (long) getCxPlatProcessEventLocalsMemorySizeMH.invokeExact();
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }

    private static final MethodHandle getCXPLAT_EXECUTION_STATEMemorySizeMH = PanamaUtils.lookupPNICriticalFunction(false, long.class, "JavaCritical_io_vproxy_msquic_MsQuicMod_getCXPLAT_EXECUTION_STATEMemorySize");

    public long getCXPLAT_EXECUTION_STATEMemorySize() {
        long RESULT;
        try {
            RESULT = (long) getCXPLAT_EXECUTION_STATEMemorySizeMH.invokeExact();
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }

    private static final MethodHandle MsQuicCxPlatWorkerThreadInitMH = PanamaUtils.lookupPNICriticalFunction(false, void.class, "JavaCritical_io_vproxy_msquic_MsQuicMod_MsQuicCxPlatWorkerThreadInit", io.vproxy.msquic.CxPlatProcessEventLocals.LAYOUT.getClass() /* CxPlatWorkerThreadLocals */);

    public void MsQuicCxPlatWorkerThreadInit(io.vproxy.msquic.CxPlatProcessEventLocals CxPlatWorkerThreadLocals) {
        try {
            MsQuicCxPlatWorkerThreadInitMH.invokeExact((MemorySegment) (CxPlatWorkerThreadLocals == null ? MemorySegment.NULL : CxPlatWorkerThreadLocals.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
    }

    private static final MethodHandle MsQuicCxPlatWorkerThreadBeforePollMH = PanamaUtils.lookupPNICriticalFunction(false, void.class, "JavaCritical_io_vproxy_msquic_MsQuicMod_MsQuicCxPlatWorkerThreadBeforePoll", io.vproxy.msquic.CxPlatProcessEventLocals.LAYOUT.getClass() /* CxPlatProcessEventLocals */);

    public void MsQuicCxPlatWorkerThreadBeforePoll(io.vproxy.msquic.CxPlatProcessEventLocals CxPlatProcessEventLocals) {
        try {
            MsQuicCxPlatWorkerThreadBeforePollMH.invokeExact((MemorySegment) (CxPlatProcessEventLocals == null ? MemorySegment.NULL : CxPlatProcessEventLocals.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
    }

    private static final MethodHandle MsQuicCxPlatWorkerThreadAfterPollMH = PanamaUtils.lookupPNICriticalFunction(false, boolean.class, "JavaCritical_io_vproxy_msquic_MsQuicMod_MsQuicCxPlatWorkerThreadAfterPoll", io.vproxy.msquic.CxPlatProcessEventLocals.LAYOUT.getClass() /* locals */, int.class /* num */, MemorySegment.class /* events */);

    public boolean MsQuicCxPlatWorkerThreadAfterPoll(io.vproxy.msquic.CxPlatProcessEventLocals locals, int num, io.vproxy.vfd.posix.AEFiredExtra.Array events) {
        boolean RESULT;
        try {
            RESULT = (boolean) MsQuicCxPlatWorkerThreadAfterPollMH.invokeExact((MemorySegment) (locals == null ? MemorySegment.NULL : locals.MEMORY), num, (MemorySegment) (events == null ? MemorySegment.NULL : events.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }

    private static final MethodHandle MsQuicCxPlatWorkerThreadFinalizeMH = PanamaUtils.lookupPNICriticalFunction(false, int.class, "JavaCritical_io_vproxy_msquic_MsQuicMod_MsQuicCxPlatWorkerThreadFinalize", io.vproxy.msquic.CxPlatProcessEventLocals.LAYOUT.getClass() /* CxPlatWorkerThreadLocals */);

    public int MsQuicCxPlatWorkerThreadFinalize(io.vproxy.msquic.CxPlatProcessEventLocals CxPlatWorkerThreadLocals) {
        int RESULT;
        try {
            RESULT = (int) MsQuicCxPlatWorkerThreadFinalizeMH.invokeExact((MemorySegment) (CxPlatWorkerThreadLocals == null ? MemorySegment.NULL : CxPlatWorkerThreadLocals.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }

    private static final MethodHandle MsQuicSetEventLoopThreadDispatcherMH = PanamaUtils.lookupPNICriticalFunction(false, int.class, "JavaCritical_io_vproxy_msquic_MsQuicMod_MsQuicSetEventLoopThreadDispatcher");

    public int MsQuicSetEventLoopThreadDispatcher() {
        int RESULT;
        try {
            RESULT = (int) MsQuicSetEventLoopThreadDispatcherMH.invokeExact();
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }

    private static final MethodHandle CxPlatGetCurThreadMH = PanamaUtils.lookupPNICriticalFunction(false, int.class, "JavaCritical_io_vproxy_msquic_MsQuicMod_CxPlatGetCurThread", MemorySegment.class /* Thread */);

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
// sha256:27f9e3ca55b13058dbe10182699b75fe380b1aa65374808913fde25ddcd364a8
