package io.vproxy.msquic.modified;

import io.vproxy.base.component.elgroup.EventLoopWrapper;
import io.vproxy.base.util.AnnotationKeys;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.callback.BlockCallback;
import io.vproxy.base.util.unsafe.SunUnsafe;

import java.lang.foreign.MemorySegment;

public class MsQuicUpcallImpl implements MsQuicUpcall.Interface {
    private MsQuicUpcallImpl() {
    }

    private static final MsQuicUpcallImpl IMPL = new MsQuicUpcallImpl();

    public static MsQuicUpcallImpl get() {
        return IMPL;
    }

    @Override
    public int dispatch(MemorySegment worker, int eventQ, MemorySegment thread) {
        try {
            var elg = MsQuicInitializer.getMsQuicEventLoopGroup();
            if (elg == null) {
                Logger.error(LogType.NO_EVENT_LOOP, "no event loop group for msquic, " +
                    "you should create an eventloop with annotation " + AnnotationKeys.EventLoopGroup_UseMsQuic);
                return 1;
            }
            var name = String.valueOf(eventQ);
            var el = elg.add(name, eventQ, new Annotations());
            if (initMsQuic(el, worker, thread)) {
                Logger.alert("msquic event loop is added, eventQ=" + eventQ + ", el=" + el);
                return 0;
            } else {
                elg.remove(name);
                return 1;
            }
        } catch (Throwable t) {
            Logger.error(LogType.SYS_ERROR, "failed to dispatch the msquic thread", t);
            return 1;
        }
    }

    private boolean initMsQuic(EventLoopWrapper el, MemorySegment worker, MemorySegment thread) {
        var loop = el.getSelectorEventLoop();

        var locals = new CxPlatProcessEventLocals(
            SunUnsafe.allocateMemory(MsQuicConsts.SizeOfCxPlatProcessEventLocals));
        var state = SunUnsafe.allocateMemory(MsQuicConsts.SizeOfCXPLAT_EXECUTION_STATE);
        locals.setWorker(worker);
        locals.setState(state);

        var block = new BlockCallback<Void, Throwable>();
        loop.runOnLoop(() -> {
            try {
                MsQuic.get().CxPlatGetCurThread(thread);
                MsQuic.get().MsQuicCxPlatWorkerThreadInit(locals);
                MsQuic.get().MsQuicCxPlatWorkerThreadBeforePoll(locals);
            } catch (Throwable t) {
                block.failed(t);
                return;
            }
            block.succeeded();
        });
        try {
            block.block();
        } catch (Throwable t) {
            Logger.error(LogType.SYS_ERROR, "failed to initiate msquic thread", t);
            return false;
        }
        loop.setBeforePoll(() -> {
            MsQuic.get().MsQuicCxPlatWorkerThreadBeforePoll(locals);
            return locals.getWaitTime();
        });
        loop.setAfterPoll((n, events) -> MsQuic.get().MsQuicCxPlatWorkerThreadAfterPoll(locals, n, events));
        loop.setFinalizer(() -> {
            MsQuic.get().MsQuicCxPlatWorkerThreadFinalize(locals);
            SunUnsafe.freeMemory(locals.MEMORY.address());
            SunUnsafe.freeMemory(state.address());
        });
        return true;
    }
}
