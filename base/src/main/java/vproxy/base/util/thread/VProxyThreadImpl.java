package vproxy.base.util.thread;

import vproxy.base.GlobalInspection;

public class VProxyThreadImpl extends Thread implements VProxyThread {
    private final VProxyThreadVariable variable = new VProxyThreadVariable();

    public VProxyThreadImpl(Runnable runnable, String name) {
        super(GlobalInspection.getInstance().wrapThread(runnable), name);
    }

    public VProxyThreadVariable getVariable() {
        return variable;
    }

    @Override
    public Thread thread() {
        return this;
    }
}
