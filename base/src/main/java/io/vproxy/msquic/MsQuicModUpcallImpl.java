package io.vproxy.msquic;

import io.vproxy.base.component.elgroup.EventLoopGroup;
import io.vproxy.base.component.elgroup.EventLoopWrapper;
import io.vproxy.base.util.Annotations;
import io.vproxy.base.util.LogType;
import io.vproxy.base.util.Logger;
import io.vproxy.base.util.callback.BlockCallback;
import io.vproxy.base.util.unsafe.SunUnsafe;
import io.vproxy.pni.PNIRef;

import java.lang.foreign.MemorySegment;

public class MsQuicModUpcallImpl implements MsQuicModUpcall.Interface {
    private MsQuicModUpcallImpl() {
    }

    private static final MsQuicModUpcallImpl IMPL = new MsQuicModUpcallImpl();

    public static MsQuicModUpcallImpl get() {
        return IMPL;
    }

    @Override
    public int dispatch(MemorySegment worker, int epfd, MemorySegment thread, MemorySegment context) {
        try {
            var elg = getMsQuicEventLoopGroup(context);
            if (elg == null) {
                Logger.error(LogType.NO_EVENT_LOOP, "no event loop group provided for msquic");
                return 1;
            }
            var name = String.valueOf(epfd);
            var el = elg.add(name, epfd, new Annotations());
            if (initMsQuic(el, worker, thread)) {
                Logger.alert("msquic event loop is added, epfd=" + epfd + ", el=" + el);
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

    private EventLoopGroup getMsQuicEventLoopGroup(MemorySegment params) {
        if (params == null) {
            return null;
        }
        return PNIRef.getRef(params);
    }

    private boolean initMsQuic(EventLoopWrapper el, MemorySegment worker, MemorySegment thread) {
        var loop = el.getSelectorEventLoop();

        var locals = new CxPlatProcessEventLocals(
            SunUnsafe.allocateMemory(MsQuicModConsts.SizeOfCxPlatProcessEventLocals));
        var state = SunUnsafe.allocateMemory(MsQuicModConsts.SizeOfCXPLAT_EXECUTION_STATE);
        locals.setWorker(worker);
        locals.setState(state);

        var block = new BlockCallback<Void, Throwable>();
        loop.runOnLoop(() -> {
            try {
                MsQuicMod.get().CxPlatGetCurThread(thread);
                MsQuicMod.get().MsQuicCxPlatWorkerThreadInit(locals);
                MsQuicMod.get().MsQuicCxPlatWorkerThreadBeforePoll(locals);
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
            MsQuicMod.get().MsQuicCxPlatWorkerThreadBeforePoll(locals);
            return locals.getWaitTime();
        });
        loop.setAfterPoll((n, events) -> MsQuicMod.get().MsQuicCxPlatWorkerThreadAfterPoll(locals, n, events));
        loop.setFinalizer(() -> {
            MsQuicMod.get().MsQuicCxPlatWorkerThreadFinalize(locals);
            SunUnsafe.freeMemory(locals.MEMORY.address());
            SunUnsafe.freeMemory(state.address());
        });
        return true;
    }
}
