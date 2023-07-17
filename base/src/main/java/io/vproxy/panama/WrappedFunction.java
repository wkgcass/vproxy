package io.vproxy.panama;

import io.vproxy.base.util.Logger;
import io.vproxy.base.util.thread.VProxyThread;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationTargetException;

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
        var env = VProxyThread.current().getEnv();
        env.reset();

        int err;
        try {
            err = invoke.invoke(handle, env.getSegment());
        } catch (Throwable e) {
            Logger.shouldNotHappen("call native method throws exception", e);
            throw new RuntimeException(e);
        }
        checkException(env, err, exClass);

        return env;
    }

    private <EX extends Exception> void checkException(JEnv env, int err, Class<EX> exClass) throws EX {
        if (err == 0) {
            return;
        }
        var exType = env.ex().type();
        var msg = env.ex().message();
        if (exType == null) {
            throw new RuntimeException(msg);
        }
        if (exClass == null) {
            throw new RuntimeException("unexpected exception " + exType + ", original error message: " + msg);
        }
        Class<?> cls;
        try {
            cls = Class.forName(exType);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("exception type " + exType + " not found, original error message: " + msg, e);
        }
        if (!exClass.isAssignableFrom(cls)) {
            throw new RuntimeException("expected exception class " + exClass.getName() +
                " is not assignable from actual exception class " + cls.getName() + ", original error message: " + msg);
        }
        try {
            //noinspection unchecked
            throw (EX) cls.getConstructor(String.class).newInstance(msg);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException e) {
            throw new RuntimeException("constructing exception object failed, original error message: " + msg, e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("constructing exception object failed, original error message: " + msg, e.getCause());
        }
    }
}
