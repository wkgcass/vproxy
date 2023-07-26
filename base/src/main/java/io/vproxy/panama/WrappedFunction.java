package io.vproxy.panama;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

public class WrappedFunction {
    private final MethodHandle handle;

    public WrappedFunction(MethodHandle handle) {
        this.handle = handle;
    }

    public interface Invoke {
        int invoke(MethodHandle handle, MemorySegment env) throws Throwable;
    }

    public JEnv invoke(Invoke invoke) {
        return invoke(null, invoke);
    }

    public <EX extends Exception> JEnv invoke(Class<EX> exClass, Invoke invoke) throws EX {
        throw new UnsupportedOperationException();
    }
}
