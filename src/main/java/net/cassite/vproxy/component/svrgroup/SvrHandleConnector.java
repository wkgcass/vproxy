package net.cassite.vproxy.component.svrgroup;

import net.cassite.vproxy.connection.ClientConnection;
import net.cassite.vproxy.connection.Connector;
import net.cassite.vproxy.util.RingBuffer;

import java.io.IOException;
import java.net.InetSocketAddress;

class SvrHandleConnector extends Connector {
    private final ServerGroup.ServerHandle serverHandle; // nullable

    SvrHandleConnector(ServerGroup.ServerHandle h) {
        super(h.server, new InetSocketAddress(h.local, 0));
        this.serverHandle = h;
    }

    @Override
    public ClientConnection connect(RingBuffer in, RingBuffer out) throws IOException {
        ClientConnection conn = super.connect(in, out);
        if (serverHandle != null) {
            conn.addNetFlowRecorder(serverHandle);
        }
        return conn;
    }
}
