package net.cassite.vproxy.component.proxy;

import net.cassite.vproxy.connection.Server;

public interface ProxyEventHandler {
    void serverRemoved(Server server);
}
