package vproxy.component.proxy;

import vproxy.base.connection.NetEventLoop;

public interface NetEventLoopProvider {
    NetEventLoop getHandleLoop(NetEventLoop acceptLoop);
}
