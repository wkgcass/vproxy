package vproxy.vserver;

import vproxy.vlibbase.Conn;
import vproxy.vserver.impl.NetServerImpl;

public interface NetServer extends GeneralServer {
    static NetServer create() {
        return new NetServerImpl();
    }

    NetServer accept(AcceptHandler handler);

    interface AcceptHandler {
        void handle(Conn conn);
    }
}
