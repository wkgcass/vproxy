package net.cassite.vproxy.component.proxy;

import net.cassite.vproxy.connection.NetEventLoop;

public interface NetEventLoopProvider {
    NetEventLoop get();
}
