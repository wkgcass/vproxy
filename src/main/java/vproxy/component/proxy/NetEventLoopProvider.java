package vproxy.component.proxy;

import vproxy.connection.NetEventLoop;

public interface NetEventLoopProvider {
    NetEventLoop getHandleLoop(NetEventLoop acceptLoop);
}
