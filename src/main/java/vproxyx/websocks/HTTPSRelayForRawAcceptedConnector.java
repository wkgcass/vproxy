package vproxyx.websocks;

import vproxy.connection.ConnectableConnection;
import vproxy.connection.Connection;
import vproxy.connection.NetEventLoop;
import vproxy.util.RingBuffer;
import vproxy.util.nio.ByteArrayChannel;
import vproxy.util.ringbuffer.SSLUtils;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.InetSocketAddress;

public class HTTPSRelayForRawAcceptedConnector extends AlreadyConnectedConnector {
    public HTTPSRelayForRawAcceptedConnector(InetSocketAddress remote, ConnectableConnection conn, NetEventLoop loop) {
        super(remote, conn, loop);
    }

    @Override
    public void beforeConnect(Connection accepted) throws IOException {
        // consume all data from the accepted connection with raw simple ring buffers
        RingBuffer oldIn = accepted.getInBuffer();
        ByteArrayChannel chnl = ByteArrayChannel.fromEmpty(oldIn.used());
        oldIn.writeTo(chnl);

        // construct and replace buffers
        SSLEngine engine = WebSocksUtils.getHTTPSRelaySSLContext().createSSLEngine();
        engine.setUseClientMode(false); // is server
        SSLUtils.SSLBufferPair pair = SSLUtils.genbuf(engine, RingBuffer.allocate(24576), RingBuffer.allocate(24576), loop.getSelectorEventLoop());
        accepted.UNSAFE_replaceBuffer(pair.left, pair.right);

        // write data back into the buffers
        RingBuffer newIn = accepted.getInBuffer();
        newIn.storeBytesFrom(chnl);
    }
}
