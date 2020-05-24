package vproxy.component.proxy;

import vproxybase.connection.NetEventLoop;

public interface NetEventLoopProvider {
    NetEventLoop getHandleLoop(NetEventLoop acceptLoop);
}
