package vproxy.component.proxy;

import vproxy.connection.BindServer;

public interface ProxyEventHandler {
    void serverRemoved(BindServer server);
}
