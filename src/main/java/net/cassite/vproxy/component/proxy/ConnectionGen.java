package net.cassite.vproxy.component.proxy;

import net.cassite.vproxy.connection.Connection;
import net.cassite.vproxy.util.Tuple;

import java.net.InetSocketAddress;

public interface ConnectionGen {
    // <remote, local>
    Tuple<InetSocketAddress, InetSocketAddress> genRemoteLocal(Connection accepted);
}
