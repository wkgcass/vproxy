package io.vproxy.msquic;

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

public class MsQuicMod2 {
    private MsQuicMod2() {
    }

    private static final MsQuicMod2 INSTANCE = new MsQuicMod2();

    public static MsQuicMod2 get() {
        return INSTANCE;
    }

    private static final MethodHandle WorkerThreadInitMH = PanamaUtils.lookupPNICriticalFunction(new PNILinkOptions(), void.class, "JavaCritical_io_vproxy_msquic_MsQuicMod2_WorkerThreadInit", io.vproxy.msquic.QuicExtraApiTable.LAYOUT.getClass() /* api */, io.vproxy.msquic.CxPlatProcessEventLocals.LAYOUT.getClass() /* CxPlatWorkerThreadLocals */);

    public void WorkerThreadInit(io.vproxy.msquic.QuicExtraApiTable api, io.vproxy.msquic.CxPlatProcessEventLocals CxPlatWorkerThreadLocals) {
        try {
            WorkerThreadInitMH.invokeExact((MemorySegment) (api == null ? MemorySegment.NULL : api.MEMORY), (MemorySegment) (CxPlatWorkerThreadLocals == null ? MemorySegment.NULL : CxPlatWorkerThreadLocals.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
    }

    private static final MethodHandle WorkerThreadBeforePollMH = PanamaUtils.lookupPNICriticalFunction(new PNILinkOptions(), void.class, "JavaCritical_io_vproxy_msquic_MsQuicMod2_WorkerThreadBeforePoll", io.vproxy.msquic.QuicExtraApiTable.LAYOUT.getClass() /* api */, io.vproxy.msquic.CxPlatProcessEventLocals.LAYOUT.getClass() /* CxPlatProcessEventLocals */);

    public void WorkerThreadBeforePoll(io.vproxy.msquic.QuicExtraApiTable api, io.vproxy.msquic.CxPlatProcessEventLocals CxPlatProcessEventLocals) {
        try {
            WorkerThreadBeforePollMH.invokeExact((MemorySegment) (api == null ? MemorySegment.NULL : api.MEMORY), (MemorySegment) (CxPlatProcessEventLocals == null ? MemorySegment.NULL : CxPlatProcessEventLocals.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
    }

    private static final MethodHandle WorkerThreadAfterPollMH = PanamaUtils.lookupPNICriticalFunction(new PNILinkOptions(), boolean.class, "JavaCritical_io_vproxy_msquic_MsQuicMod2_WorkerThreadAfterPoll", io.vproxy.msquic.QuicExtraApiTable.LAYOUT.getClass() /* api */, io.vproxy.msquic.CxPlatProcessEventLocals.LAYOUT.getClass() /* locals */, int.class /* num */, MemorySegment.class /* events */);

    public boolean WorkerThreadAfterPoll(io.vproxy.msquic.QuicExtraApiTable api, io.vproxy.msquic.CxPlatProcessEventLocals locals, int num, io.vproxy.vfd.posix.AEFiredExtra.Array events) {
        boolean RESULT;
        try {
            RESULT = (boolean) WorkerThreadAfterPollMH.invokeExact((MemorySegment) (api == null ? MemorySegment.NULL : api.MEMORY), (MemorySegment) (locals == null ? MemorySegment.NULL : locals.MEMORY), num, (MemorySegment) (events == null ? MemorySegment.NULL : events.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }

    private static final MethodHandle WorkerThreadFinalizeMH = PanamaUtils.lookupPNICriticalFunction(new PNILinkOptions(), int.class, "JavaCritical_io_vproxy_msquic_MsQuicMod2_WorkerThreadFinalize", io.vproxy.msquic.QuicExtraApiTable.LAYOUT.getClass() /* api */, io.vproxy.msquic.CxPlatProcessEventLocals.LAYOUT.getClass() /* CxPlatWorkerThreadLocals */);

    public int WorkerThreadFinalize(io.vproxy.msquic.QuicExtraApiTable api, io.vproxy.msquic.CxPlatProcessEventLocals CxPlatWorkerThreadLocals) {
        int RESULT;
        try {
            RESULT = (int) WorkerThreadFinalizeMH.invokeExact((MemorySegment) (api == null ? MemorySegment.NULL : api.MEMORY), (MemorySegment) (CxPlatWorkerThreadLocals == null ? MemorySegment.NULL : CxPlatWorkerThreadLocals.MEMORY));
        } catch (Throwable THROWABLE) {
            throw PanamaUtils.convertInvokeExactException(THROWABLE);
        }
        return RESULT;
    }
}
// metadata.generator-version: pni 22.0.0.20
// sha256:6d57a06ace3fdfe6e6b765d443fe4a8c028b2857654f55512e75ffea6158e3e7
