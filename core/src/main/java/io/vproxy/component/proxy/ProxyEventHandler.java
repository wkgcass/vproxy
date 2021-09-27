package io.vproxy.component.proxy;

import io.vproxy.base.connection.ServerSock;
import io.vproxy.base.connection.ServerSock;

public interface ProxyEventHandler {
    void serverRemoved(ServerSock server);
}
