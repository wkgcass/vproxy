package net.cassite.vproxy.connection;

import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class ClientConnection extends Connection {
    private final String _id;

    public ClientConnection(SocketChannel channel, RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        super(channel, inBuffer, outBuffer);
        _id = (local == null ? "0.0.0.0:0" :
            (
                Utils.ipStr(local.getAddress().getAddress()) + ":" + local.getPort()
            )
        )
            + "/"
            + Utils.ipStr(remote.getAddress().getAddress()) + ":" + remote.getPort();
    }

    @Override
    public String id() {
        return _id;
    }

    @Override
    public String toString() {
        return "ClientConnection(" + id() + ")[" + (isClosed() ? "closed" : "open") + "]";
    }
}
