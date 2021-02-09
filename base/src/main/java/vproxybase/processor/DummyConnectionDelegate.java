package vproxybase.processor;

import vfd.IPPort;

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
