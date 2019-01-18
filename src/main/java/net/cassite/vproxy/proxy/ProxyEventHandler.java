package net.cassite.vproxy.proxy;

import net.cassite.vproxy.connection.Server;

public interface ProxyEventHandler {
    void serverRemoved(Server server);
}
