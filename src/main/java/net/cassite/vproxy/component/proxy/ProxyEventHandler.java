package net.cassite.vproxy.component.proxy;

import net.cassite.vproxy.connection.BindServer;

public interface ProxyEventHandler {
    void serverRemoved(BindServer server);
}
