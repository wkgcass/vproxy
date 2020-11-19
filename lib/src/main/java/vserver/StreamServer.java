package vserver;

import vlibbase.Conn;
import vserver.impl.StreamServerImpl;

public interface StreamServer extends GeneralServer {
    static StreamServer create() {
        return new StreamServerImpl();
    }

    StreamServer accept(AcceptHandler handler);

    interface AcceptHandler {
        void handle(Conn conn);
    }
}
