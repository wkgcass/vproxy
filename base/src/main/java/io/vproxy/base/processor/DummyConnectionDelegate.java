package io.vproxy.base.processor;

import io.vproxy.vfd.IPPort;

public class DummyConnectionDelegate extends ConnectionDelegate {
    private static final DummyConnectionDelegate INSTANCE = new DummyConnectionDelegate();

    public static DummyConnectionDelegate getInstance() {
        return INSTANCE;
    }

    private DummyConnectionDelegate() {
        super(IPPort.bindAnyAddress());
    }

    @Override
    public void pause() { // do nothing
    }

    @Override
    public void resume() { // do nothing

    }
}
