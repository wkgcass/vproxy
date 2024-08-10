package io.vproxy.vproxyx.websocks;

import io.vproxy.base.connection.*;
import io.vproxy.base.util.RingBuffer;
import io.vproxy.vfd.IPPort;

import java.io.IOException;

public class AlreadyConnectedConnector extends Connector {
    protected final ConnectableConnection conn;
    protected final NetEventLoop loop;

    public AlreadyConnectedConnector(IPPort remote, ConnectableConnection conn, NetEventLoop loop) {
        super(remote);
        this.conn = conn;
        this.loop = loop;
    }

    public ConnectableConnection getConnection() {
        return conn;
    }

    @Override
    public ConnectableConnection connect(Connection accepted, ConnectionOpts opts, RingBuffer in, RingBuffer out) throws IOException {
        var connI = conn.getInBuffer();
        var connO = conn.getOutBuffer();

        if (connI.used() == 0 && connO.used() == 0) {
            replaceConnBuffers(in, out);
        } else if (accepted != null && accepted.getInBuffer().used() == 0 && accepted.getOutBuffer().used() == 0) {
            replaceAcceptedBuffers(accepted, connO, connI, in, out);
        } else if (connO.used() == 0 && connI.used() <= in.free()) {
            replaceConnBuffers(in, out);
        } else if (accepted != null && accepted.getOutBuffer().used() == 0 && accepted.getInBuffer().used() <= connO.free()) {
            replaceAcceptedBuffers(accepted, connO, connI, in, out);
        } else {
            // just try and let it throw an exception (or maybe it can handle the situation)
            replaceConnBuffers(in, out);
        }

        return conn;

        // NOTE: the opts is ignored in this impl
    }

    private void replaceConnBuffers(RingBuffer in, RingBuffer out) throws IOException {
        conn.replaceBuffer(in, out, true, true);
    }

    private void replaceAcceptedBuffers(Connection accepted,
                                        RingBuffer connO, RingBuffer connI,
                                        RingBuffer in, RingBuffer out) throws IOException {
        var oldI = accepted.getInBuffer();
        var oldO = accepted.getOutBuffer();

        accepted.replaceBuffer(connO, connI, true, true);

        // just in case the in/out buffers are not related to the accepted connection
        if (oldI != out && oldO != out && !RingBuffer.haveRelationBetween(accepted.getInBuffer(), out)) {
            out.clean();
        }
        if (oldI != in && oldO != in && !RingBuffer.haveRelationBetween(accepted.getOutBuffer(), in)) {
            in.clean();
        }
    }

    @Override
    public NetEventLoop loop() {
        return loop;
    }

    @Override
    public void close() {
        conn.close();
    }

    @Override
    public String toString() {
        return "AlreadyConnectedConnector(" + conn + ", " + loop + ")";
    }
}
