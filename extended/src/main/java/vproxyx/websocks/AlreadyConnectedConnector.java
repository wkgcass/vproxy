package vproxyx.websocks;

import vfd.IPPort;
import vproxybase.connection.*;
import vproxybase.util.RingBuffer;

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
        RingBuffer oldI = conn.getInBuffer();
        RingBuffer oldO = conn.getOutBuffer();

        if (oldI.used() == 0 && oldO.used() == 0) {
            conn.UNSAFE_replaceBuffer(in, out, true);
        } else if (accepted.getInBuffer().used() == 0 && accepted.getOutBuffer().used() == 0) {
            // may try to replace the accepted connection buffers
            accepted.UNSAFE_replaceBuffer(oldO, oldI, false);
            if (!RingBuffer.haveRelationBetween(accepted.getInBuffer(), out)) {
                out.clean();
            }
            if (!RingBuffer.haveRelationBetween(accepted.getOutBuffer(), in)) {
                in.clean();
            }
        } else {
            throw new IOException("cannot replace buffers because they are not empty");
        }

        return conn;

        // NOTE: the opts is ignored in this impl
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
