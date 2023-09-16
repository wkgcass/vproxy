package io.vproxy.msquic;

import io.vproxy.pni.annotation.*;
import io.vproxy.vfd.posix.PNIAEFiredExtra;

import java.lang.foreign.MemorySegment;

@Function
@Include("msquic.h")
interface PNIMsQuicMod {
    @Impl(
        // language="c"
        c = """
            return sizeof(struct CxPlatProcessEventLocals);
            """
    )
    @Critical
    long getCxPlatProcessEventLocalsMemorySize();

    @Impl(
        // language="c"
        c = """
            return sizeof(CXPLAT_EXECUTION_STATE);
            """
    )
    @Critical
    long getCXPLAT_EXECUTION_STATEMemorySize();

    @Impl(
        // language="c"
        c = """
            MsQuicCxPlatWorkerThreadInit(CxPlatWorkerThreadLocals);
            """
    )
    @Critical
    void MsQuicCxPlatWorkerThreadInit(PNICxPlatProcessEventLocals CxPlatWorkerThreadLocals);

    @Impl(
        // language="c"
        c = """
            MsQuicCxPlatWorkerThreadBeforePoll(CxPlatProcessEventLocals);
            """
    )
    @Critical
    void MsQuicCxPlatWorkerThreadBeforePoll(PNICxPlatProcessEventLocals CxPlatProcessEventLocals);

    @Impl(
        include = "<stdio.h>",
        // language="c"
        c = """
            locals->CqeCount = num;
            for (int i = 0; i < num && i < CxPlatProcessCqesArraySize; ++i) {
            #ifdef __linux__
                locals->Cqes[i].data.ptr = events[i].ud;
                locals->Cqes[i].events = events[i].mask;
            #elif defined(__APPLE__)
                locals->Cqes[i].udata = events[i].ud;
                locals->Cqes[i].filter = events[i].mask;
            #else
                locals->CqeCount = 0;
                printf("unsupported platform\\n");
                fflush(stdout);
            #endif
            }
            int ret = MsQuicCxPlatWorkerThreadAfterPoll(locals);
            return ret;
            """
    )
    @Critical
    boolean MsQuicCxPlatWorkerThreadAfterPoll(PNICxPlatProcessEventLocals locals,
                                              int num,
                                              @Raw PNIAEFiredExtra[] events);

    @Impl(
        // language="c"
        c = """
            return MsQuicCxPlatWorkerThreadFinalize(CxPlatWorkerThreadLocals);
            """
    )
    @Critical
    int MsQuicCxPlatWorkerThreadFinalize(PNICxPlatProcessEventLocals CxPlatWorkerThreadLocals);

    @Impl(
        // language="c"
        c = """
            return MsQuicSetEventLoopThreadDispatcher(vproxy_MsQuicUpcall_dispatch);
            """
    )
    @Critical
    int MsQuicSetEventLoopThreadDispatcher();

    @Impl(
        // language="c"
        c = """
            return CxPlatGetCurThread((CXPLAT_THREAD*) Thread);
            """
    )
    @Critical
    int CxPlatGetCurThread(MemorySegment Thread);
}

@Struct(skip = true, typedef = false)
@Include("msquic.h")
class PNICxPlatProcessEventLocals {
    MemorySegment worker;
    MemorySegment state;
    @Unsigned int waitTime;

    // other fields are not used in java
}

@Upcall
@Include("msquic.h")
interface PNIMsQuicModUpcall {
    int dispatch(MemorySegment worker, int epfd, MemorySegment thread, MemorySegment context);
}
