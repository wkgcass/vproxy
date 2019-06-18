package vproxy.component.svrgroup;

import vproxy.connection.ClientConnection;
import vproxy.connection.ConnectionOpts;
import vproxy.connection.Connector;
import vproxy.util.RingBuffer;

import java.io.IOException;

public class SvrHandleConnector extends Connector {
    private final ServerGroup.ServerHandle serverHandle;

    SvrHandleConnector(ServerGroup.ServerHandle h) {
        super(h.server);
        this.serverHandle = h;
    }

    @Override
    public ClientConnection connect(ConnectionOpts opts, RingBuffer in, RingBuffer out) throws IOException {
        ClientConnection conn = super.connect(opts, in, out);
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
