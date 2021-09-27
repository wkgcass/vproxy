package io.vproxy.base.component.svrgroup;

import io.vproxy.base.connection.ConnectableConnection;
import io.vproxy.base.connection.Connection;
import io.vproxy.base.connection.ConnectionOpts;
import io.vproxy.base.connection.Connector;
import io.vproxy.base.connection.ConnectableConnection;
import io.vproxy.base.connection.Connection;
import io.vproxy.base.connection.ConnectionOpts;
import io.vproxy.base.connection.Connector;
import io.vproxy.base.util.RingBuffer;

import java.io.IOException;

public class SvrHandleConnector extends Connector {
    private final ServerGroup.ServerHandle serverHandle;

    SvrHandleConnector(ServerGroup.ServerHandle h) {
        super(h.server);
        this.serverHandle = h;
    }

    @Override
    public ConnectableConnection connect(Connection accepted, ConnectionOpts opts, RingBuffer in, RingBuffer out) throws IOException {
        ConnectableConnection conn = super.connect(accepted, opts, in, out);
        conn.addNetFlowRecorder(serverHandle);
        serverHandle.attachConnection(conn);
        conn.addConnCloseHandler(serverHandle);
        return conn;
    }

    @Override
    public boolean isValid() {
        return serverHandle.valid;
    }

    @Override
    public void connectionFailed() {
        // accelerate the down process
        serverHandle.healthCheckClient.manuallyDownOnce();
    }

    public String getHostName() {
        String hostname = serverHandle.hostName;
        if (hostname == null) {
            String h = remote.getAddress().toString().split("/")[0].trim();
            if (!h.isEmpty()) {
                hostname = h;
            }
        }
        return hostname;
    }

    public Object getData() {
        return serverHandle.data;
    }
}
