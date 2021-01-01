package vproxybase.util;

import vproxybase.GlobalInspection;
import vproxybase.selector.SelectorEventLoop;

public class VProxyThread extends Thread {
    private static final ThreadLocal<VProxyThreadVariable> threadLocal = new ThreadLocal<>();

    public static final class VProxyThreadVariable {
        public SelectorEventLoop loop;
    }

    private final VProxyThreadVariable variable = new VProxyThreadVariable();

    public VProxyThread(Runnable runnable, String name) {
        super(GlobalInspection.getInstance().wrapThread(runnable), name);
    }

    public VProxyThreadVariable getVariable() {
        return variable;
    }

    public static VProxyThreadVariable current() {
        Thread t = Thread.currentThread();
        if (t instanceof VProxyThread) {
            return ((VProxyThread) t).variable;
        }
        VProxyThreadVariable vt = threadLocal.get();
        if (vt == null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (t) {
                vt = threadLocal.get();
                if (vt == null) {
                    vt = new VProxyThreadVariable();
                    threadLocal.set(vt);
                }
            }
        }
        return vt;
    }
}
