package io.vproxy.panama;

import io.vproxy.base.util.Logger;
import io.vproxy.base.util.thread.VProxyThread;

import java.io.IOException;
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

    public void invokeReturnNothing(Invoke f) {
        invoke(f);
    }

    public int invokeReturnInt(Invoke f) {
        return invoke(f).returnI();
    }

    public long invokeReturnLong(Invoke f) {
        return invoke(f).returnJ();
    }

    public boolean invokeReturnBool(Invoke f) {
        return invoke(f).returnZ();
    }

    public MemorySegment invokeReturnPointer(Invoke f) {
        return invoke(f).returnP();
    }

    public <EX extends Exception> void invokeReturnNothingEx(Class<EX> exClass, Invoke f) throws EX {
        invokeEx(exClass, f);
    }

    public <EX extends Exception> int invokeReturnIntEx(Class<EX> exClass, Invoke f) throws EX {
        return invokeEx(exClass, f).returnI();
    }

    public <EX extends Exception> long invokeReturnLongEx(Class<EX> exClass, Invoke f) throws EX {
        return invokeEx(exClass, f).returnJ();
    }

    public <EX extends Exception> boolean invokeReturnBoolEx(Class<EX> exClass, Invoke f) throws EX {
        return invokeEx(exClass, f).returnZ();
    }

    public <EX extends Exception> MemorySegment invokeReturnPointerEx(Class<EX> exClass, Invoke f) throws EX {
        return invokeEx(exClass, f).returnP();
    }

    private Exception buildException(ExceptionStruct ex) {
        var type = ex.type();
        var msg = ex.message();
        if (msg.isBlank()) {
            msg = null;
        }
        switch (type) {
            case 1 -> {
                return new UnsupportedOperationException(msg);
            }
            case 2 -> {
                return new IOException(msg);
            }
            default -> {
                Logger.shouldNotHappen("unknown error type " + type + ": " + msg);
                throw new RuntimeException(msg);
            }
        }
    }

    private JEnv invoke(Invoke invoke) {
        try {
            return invokeEx(Exception.class, invoke);
        } catch (Exception e) {
            Logger.shouldNotHappen("unexpected exception thrown when calling native method", e);
            throw new RuntimeException(e);
        }
    }

    private <EX extends Exception> JEnv invokeEx(Class<EX> exClass, Invoke invoke) throws EX {
        var env = VProxyThread.current().getEnv();
        env.ex().type(0);
        env.returnP(null);

        try {
            invoke.invoke(handle, env.getMemory().getSegment());
        } catch (Throwable e) {
            Logger.shouldNotHappen("call native method throws exception", e);
            throw new RuntimeException(e);
        }

        if (env.ex().type() == 0) {
            return env;
        } else {
            var ex = buildException(env.ex());
            if (exClass.isInstance(ex)) {
                //noinspection unchecked
                throw (EX) ex;
            } else {
                Logger.shouldNotHappen("unexpected exception thrown when calling native method", ex);
                throw new RuntimeException(ex);
            }
        }
    }
}
