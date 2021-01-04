package vserver;

import vlibbase.Conn;
import vserver.impl.NetServerImpl;

public interface NetServer extends GeneralServer {
    static NetServer create() {
        return new NetServerImpl();
    }

    NetServer accept(AcceptHandler handler);

    interface AcceptHandler {
        void handle(Conn conn);
    }
}
