package vproxy.component.proxy;

import vproxybase.connection.ServerSock;

public interface ProxyEventHandler {
    void serverRemoved(ServerSock server);
}
