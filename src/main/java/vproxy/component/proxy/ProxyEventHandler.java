package vproxy.component.proxy;

import vproxy.connection.ServerSock;

public interface ProxyEventHandler {
    void serverRemoved(ServerSock server);
}
