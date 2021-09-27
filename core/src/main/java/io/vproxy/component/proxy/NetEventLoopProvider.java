package io.vproxy.component.proxy;

import io.vproxy.base.connection.NetEventLoop;
import io.vproxy.base.connection.NetEventLoop;

public interface NetEventLoopProvider {
    NetEventLoop getHandleLoop(NetEventLoop acceptLoop);
}
