package io.vproxy.msquic;

import io.vproxy.pni.*;
import io.vproxy.pni.hack.*;
import io.vproxy.pni.array.*;
import java.lang.foreign.*;
import java.lang.invoke.*;
import java.nio.ByteBuffer;

public class MsQuicMod2 {
    private MsQuicMod2() {
    }

    private static final MsQuicMod2 INSTANCE = new MsQuicMod2();

    public static MsQuicMod2 get() {
        return INSTANCE;
    }

    private static final MethodHandle MsQuicCxPlatWorkerThreadInitMH = PanamaUtils.lookupPNICriticalFunction(new PNILinkOptions(), void.class, "JavaCritical_io_vproxy_msquic_MsQuicMod2_MsQuicCxPlatWorkerThreadInit", io.vproxy.msquic.CxPlatProcessEventLocals.LAYOUT.getClass() /* CxPlatWorkerThreadLocals */);

    public void MsQuicCxPlatWorkerThreadInit(io.vproxy.msquic.CxPlatProcessEventLocals CxPlatWorkerThreadLocals) {
        try {
            MsQuicCxPlatWorkerThreadInitMH.invokeExact((MemorySegment) (CxPlatWorkerThreadLocals == null ? MemorySegment.NULL : CxPlatWorkerThreadLocals.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
    }

    private static final MethodHandle MsQuicCxPlatWorkerThreadBeforePollMH = PanamaUtils.lookupPNICriticalFunction(new PNILinkOptions(), void.class, "JavaCritical_io_vproxy_msquic_MsQuicMod2_MsQuicCxPlatWorkerThreadBeforePoll", io.vproxy.msquic.CxPlatProcessEventLocals.LAYOUT.getClass() /* CxPlatProcessEventLocals */);

    public void MsQuicCxPlatWorkerThreadBeforePoll(io.vproxy.msquic.CxPlatProcessEventLocals CxPlatProcessEventLocals) {
        try {
            MsQuicCxPlatWorkerThreadBeforePollMH.invokeExact((MemorySegment) (CxPlatProcessEventLocals == null ? MemorySegment.NULL : CxPlatProcessEventLocals.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
    }

    private static final MethodHandle MsQuicCxPlatWorkerThreadAfterPollMH = PanamaUtils.lookupPNICriticalFunction(new PNILinkOptions(), boolean.class, "JavaCritical_io_vproxy_msquic_MsQuicMod2_MsQuicCxPlatWorkerThreadAfterPoll", io.vproxy.msquic.CxPlatProcessEventLocals.LAYOUT.getClass() /* locals */, int.class /* num */, MemorySegment.class /* events */);

    public boolean MsQuicCxPlatWorkerThreadAfterPoll(io.vproxy.msquic.CxPlatProcessEventLocals locals, int num, io.vproxy.vfd.posix.AEFiredExtra.Array events) {
        boolean RESULT;
        try {
            RESULT = (boolean) MsQuicCxPlatWorkerThreadAfterPollMH.invokeExact((MemorySegment) (locals == null ? MemorySegment.NULL : locals.MEMORY), num, (MemorySegment) (events == null ? MemorySegment.NULL : events.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }

    private static final MethodHandle MsQuicCxPlatWorkerThreadFinalizeMH = PanamaUtils.lookupPNICriticalFunction(new PNILinkOptions(), int.class, "JavaCritical_io_vproxy_msquic_MsQuicMod2_MsQuicCxPlatWorkerThreadFinalize", io.vproxy.msquic.CxPlatProcessEventLocals.LAYOUT.getClass() /* CxPlatWorkerThreadLocals */);

    public int MsQuicCxPlatWorkerThreadFinalize(io.vproxy.msquic.CxPlatProcessEventLocals CxPlatWorkerThreadLocals) {
        int RESULT;
        try {
            RESULT = (int) MsQuicCxPlatWorkerThreadFinalizeMH.invokeExact((MemorySegment) (CxPlatWorkerThreadLocals == null ? MemorySegment.NULL : CxPlatWorkerThreadLocals.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }
}
// metadata.generator-version: pni 21.0.0.20
// sha256:357ebfe0ffd93741f4d9c30267ce1531089fcc94ab9f0b6f8c7db24c7d718cc3
