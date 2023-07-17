package io.vproxy.base.util.thread;

import io.vproxy.base.GlobalInspection;

public class VProxyThreadImpl extends Thread implements VProxyThread {
    private VProxyThreadVariable variable;

    public VProxyThreadImpl(Runnable runnable, String name) {
        super(GlobalInspection.getInstance().wrapThread(runnable), name);
    }

    public VProxyThreadVariable getVariable() {
        if (variable == null) {
            variable = new VProxyThreadVariable();
        }
        return variable;
    }

    @Override
    public Thread thread() {
        return this;
    }
}
