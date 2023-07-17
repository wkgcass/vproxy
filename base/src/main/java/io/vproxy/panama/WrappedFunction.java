package io.vproxy.panama;

import io.vproxy.base.util.Logger;
import io.vproxy.base.util.thread.VProxyThread;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

public class WrappedFunction {
    private final MethodHandle handle;

    public WrappedFunction(MethodHandle handle) {
        this.handle = handle;
    }

    public interface Invoke {
        void invoke(MethodHandle handle, MemorySegment env) throws Throwable;
    }

    public JEnv invoke(Invoke invoke) {
        var env = VProxyThread.current().getEnv();
        env.resetAll();

        try {
            invoke.invoke(handle, env.getSegment());
        } catch (Throwable e) {
            Logger.shouldNotHappen("call native method throws exception", e);
            throw new RuntimeException(e);
        }

        return env;
    }
}
