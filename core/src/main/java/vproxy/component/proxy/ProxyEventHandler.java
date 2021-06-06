package vproxy.component.proxy;

import vproxy.base.connection.ServerSock;

public interface ProxyEventHandler {
    void serverRemoved(ServerSock server);
}
