package net.cassite.vproxy.connection;

import net.cassite.vproxy.util.RingBuffer;
import net.cassite.vproxy.util.Utils;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public class ClientConnection extends Connection {
    public ClientConnection(SocketChannel channel, RingBuffer inBuffer, RingBuffer outBuffer) throws IOException {
        super(channel, inBuffer, outBuffer);
    }

    @Override
    protected String genId() {
        return (local == null ? "[unbound]" :
            (
                Utils.ipStr(local.getAddress().getAddress()) + ":" + local.getPort()
            ))
            + "/"
            + Utils.ipStr(remote.getAddress().getAddress()) + ":" + remote.getPort();
    }

    @Override
    public String toString() {
        return "Client" + super.toString();
    }
}
