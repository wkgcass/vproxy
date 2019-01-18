package net.cassite.vproxy.proxy;

import net.cassite.vproxy.connection.NetEventLoop;

public interface NetEventLoopProvider {
    NetEventLoop get();
}
